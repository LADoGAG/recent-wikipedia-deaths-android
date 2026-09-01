package io.github.ladogag.recentwikipediadeaths.data

import android.content.Context
import android.os.LocaleList
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object WikiRepository {

    private const val TAG = "WikiRepository"
    private const val USER_AGENT = "RecentWikipediaDeaths/1.0 (learning project)"
    private const val PREFS_NAME = "wiki_cache"
    private const val KEY_TARGETS = "cached_targets"

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private var cachedTargets: List<Pair<String, String>> = emptyList()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadCachedTargets()
    }

    private fun loadCachedTargets() {
        try {
            val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs?.getString(KEY_TARGETS, null)
            if (!json.isNullOrEmpty()) {
                val arr = JSONArray(json)
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list += obj.getString("lang") to obj.getString("category")
                }
                cachedTargets = list
                Log.i(TAG, "Loaded ${cachedTargets.size} targets from cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
        }
    }

    private fun saveCachedTargets() {
        try {
            val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
            val arr = JSONArray()
            cachedTargets.forEach { (lang, cat) ->
                arr.put(JSONObject().put("lang", lang).put("category", cat))
            }
            prefs.edit { putString(KEY_TARGETS, arr.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }

    private const val KEY_TIME_LIVE = "time_live_only"
    private const val KEY_TIME_HOURS = "time_hours"

    fun saveTimeSettings(liveOnly: Boolean, hours: Int) {
        try {
            val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
            prefs.edit {
                putBoolean(KEY_TIME_LIVE, liveOnly)
                putInt(KEY_TIME_HOURS, hours)
            }
        } catch (_: Exception) { }
    }

    fun loadTimeSettings(): Pair<Boolean, Int>? {
        return try {
            val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?: return null
            if (!prefs.contains(KEY_TIME_LIVE)) return null
            prefs.getBoolean(KEY_TIME_LIVE, false) to prefs.getInt(KEY_TIME_HOURS, 24)
        } catch (_: Exception) { null }
    }

    private const val KEY_SELECTION = "saved_selection"

    fun saveSelection(langs: Set<String>) {
        try {
            val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
            prefs.edit { putString(KEY_SELECTION, langs.joinToString(",")) }
        } catch (e: Exception) {
            Log.w(TAG, "saveSelection failed", e)
        }
    }

    fun loadSelection(): Set<String>? {
        return try {
            val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?: return null
            val raw = prefs.getString(KEY_SELECTION, null) ?: return null
            raw.split(",").filter { it.isNotBlank() }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "loadSelection failed", e)
            null
        }
    }

    private val livePatterns = listOf(
        Regex("\\b(died|death|passed\\s+away|obit)\\b", RegexOption.IGNORE_CASE),
        Regex("(умер|скончался|погиб|умерла|скончалась|дата\\s+смерти|похоронен)", RegexOption.IGNORE_CASE),
        Regex("category:.*deaths", RegexOption.IGNORE_CASE),
        Regex("категория:.*умершие", RegexOption.IGNORE_CASE),
        Regex("\\b(18\\d{2}|19\\d{2}|20[0-2]\\d)\\s*[–-—]\\s*(20\\d{2})\\b")
    )

    suspend fun fetchAllTargets(): List<WikiTarget> {
        if (cachedTargets.isNotEmpty()) {
            Log.i(TAG, "Returning ${cachedTargets.size} cached targets")
            CoroutineScope(Dispatchers.IO).launch { refreshTargetsFromLanglinks() }
            return localizedTargets()
        }
        return withContext(Dispatchers.IO) {
            refreshTargetsFromLanglinks()
            localizedTargets()
        }
    }

    private suspend fun refreshTargetsFromLanglinks() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val collected = mutableListOf<Pair<String, String>>()
        var attempt = 0

        while (attempt < 3 && collected.isEmpty()) {
            attempt++
            try {
                var continueToken: String? = null
                do {
                    val builder = HttpUrl.Builder()
                        .scheme("https").host("en.wikipedia.org")
                        .addPathSegments("w/api.php")
                        .addQueryParameter("action", "query")
                        .addQueryParameter("titles", "Category:$year deaths")
                        .addQueryParameter("prop", "langlinks")
                        .addQueryParameter("lllimit", "500")
                        .addQueryParameter("format", "json")
                    continueToken?.let { builder.addQueryParameter("llcontinue", it) }

                    val body = restClient.newCall(request(builder.build())).execute()
                        .use { resp ->
                            when {
                                resp.code == 429 -> {
                                    Log.w(TAG, "langlinks: 429, backing off 5s")
                                    delay(5000)
                                    null
                                }
                                !resp.isSuccessful -> {
                                    Log.e(TAG, "langlinks HTTP ${resp.code}")
                                    null
                                }
                                else -> resp.body.string()
                            }
                        } ?: break

                    if (!body.trimStart().startsWith("{")) {
                        Log.w(TAG, "langlinks returned non-JSON, backing off")
                        delay(5000)
                        break
                    }

                    val json = JSONObject(body)
                    val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: break
                    val names = pages.names() ?: break
                    val page = pages.getJSONObject(names.getString(0))
                    val links = page.optJSONArray("langlinks") ?: break

                    for (i in 0 until links.length()) {
                        val l = links.getJSONObject(i)
                        val lang = l.optString("lang")
                        val title = l.optString("*")
                        if (lang.isNotEmpty() && title.isNotEmpty()) {
                            collected += lang to title
                        }
                    }
                    continueToken = json.optJSONObject("continue")?.optString("llcontinue")
                } while (continueToken != null)
            } catch (e: Exception) {
                Log.e(TAG, "refreshTargets attempt $attempt failed", e)
                delay(3000)
            }
        }

        if (collected.isEmpty()) {
            Log.w(TAG, "langlinks unavailable -> fallback list")
            collected += fallbackCategories(year)
        }
        if (collected.none { it.first == "en" }) {
            collected += "en" to "Category:$year deaths"
        }

        cachedTargets = collected.distinctBy { it.first }
        saveCachedTargets()
        Log.i(TAG, "Cached ${cachedTargets.size} targets")
    }

    fun localizedTargets(): List<WikiTarget> {
        if (cachedTargets.isEmpty()) return emptyList()
        val display = Locale.getDefault()
        val priority = mutableListOf("en")
        val locales = LocaleList.getDefault()
        for (i in 0 until locales.size()) {
            val code = locales[i].language.lowercase()
            if (code !in priority) priority += code
        }

        fun target(pair: Pair<String, String>): WikiTarget {
            val code = pair.first
            val nativeLocale = runCatching { Locale.forLanguageTag(code) }.getOrDefault(Locale.ENGLISH)
            return WikiTarget(
                lang = code,
                category = pair.second,
                displayName = displayName(code, display),
                englishName = displayName(code, Locale.ENGLISH),
                nativeName = displayName(code, nativeLocale)
            )
        }

        val head = priority.mapNotNull { p ->
            cachedTargets.firstOrNull { it.first == p }?.let { target(it) }
        }
        val rest = cachedTargets.filter { it.first !in priority }
            .map { target(it) }
            .sortedBy { it.displayName.lowercase(display) }
        return head + rest
    }

    suspend fun fetchCategoryMembers(target: WikiTarget): Pair<List<DeathEvent>?, String?> =
        withContext(Dispatchers.IO) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("${target.lang}.wikipedia.org")
                .addPathSegments("w/api.php")
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "categorymembers")
                .addQueryParameter("cmtitle", target.category)
                .addQueryParameter("cmprop", "title|timestamp")
                .addQueryParameter("cmsort", "timestamp")
                .addQueryParameter("cmdir", "desc")
                .addQueryParameter("cmlimit", "15")
                .addQueryParameter("format", "json")
                .build()

            var attempt = 0
            var lastError = "unknown"
            while (attempt < 2) {
                try {
                    val body = restClient.newCall(request(url)).execute()
                        .use { resp ->
                            when {
                                resp.code == 429 -> {
                                    Log.w(TAG, "${target.lang}: 429")
                                    delay(3000)
                                    "rate_limited"
                                }
                                resp.code == 404 -> {
                                    Log.e(TAG, "${target.lang}: 404")
                                    "not_found"
                                }
                                resp.code >= 500 -> {
                                    Log.e(TAG, "${target.lang}: HTTP ${resp.code}")
                                    "server_error_${resp.code}"
                                }
                                !resp.isSuccessful -> {
                                    Log.e(TAG, "${target.lang}: HTTP ${resp.code}")
                                    "http_${resp.code}"
                                }
                                else -> resp.body.string()
                            }
                        }

                    when (body) {
                        "rate_limited" -> { lastError = "rate_limited"; attempt++; continue }
                        "not_found" -> { lastError = "not_found"; attempt++; continue }
                        else -> {
                            if (body.startsWith("server_error") || body.startsWith("http_")) {
                                lastError = body; attempt++; continue
                            }
                        }
                    }

                    if (!body.trimStart().startsWith("{")) {
                        Log.w(TAG, "${target.lang}: non-JSON response")
                        lastError = "invalid_response"
                        delay(3000); attempt++; continue
                    }

                    val members = JSONObject(body)
                        .optJSONObject("query")
                        ?.optJSONArray("categorymembers")

                    val list = (0 until (members?.length() ?: 0)).mapNotNull { i ->
                        val m = members!!.getJSONObject(i)
                        if (m.optInt("ns") != 0) null else DeathEvent(
                            id = java.util.UUID.randomUUID().toString(),
                            title = m.getString("title"),
                            wiki = target.lang,
                            timestamp = parseTimestamp(m.optString("timestamp"))
                                ?: System.currentTimeMillis()
                        )
                    }
                    return@withContext list to null
                } catch (e: java.net.UnknownHostException) {
                    lastError = "no_internet"
                    Log.e(TAG, "${target.lang}: no internet", e)
                    attempt++
                } catch (e: java.net.SocketTimeoutException) {
                    lastError = "timeout"
                    Log.e(TAG, "${target.lang}: timeout", e)
                    attempt++
                    delay(2000)
                } catch (e: javax.net.ssl.SSLException) {
                    lastError = "ssl_error"
                    Log.e(TAG, "${target.lang}: SSL", e)
                    attempt++
                } catch (e: java.io.IOException) {
                    lastError = "network_error"
                    Log.e(TAG, "${target.lang}: IO", e)
                    attempt++
                    delay(2000)
                } catch (e: Exception) {
                    lastError = "parse_error"
                    Log.e(TAG, "${target.lang}: parse", e)
                    attempt++
                    delay(2000)
                }
            }
            null to lastError
        }

    suspend fun enrichEvents(events: List<DeathEvent>): List<DeathEvent> =
        withContext(Dispatchers.IO) {
            events.groupBy { it.wiki }.flatMap { (lang, group) ->
                group.chunked(5).map { batch ->
                    try {
                        val titles = batch.joinToString("|") { it.title }
                        val url = HttpUrl.Builder()
                            .scheme("https").host("$lang.wikipedia.org")
                            .addPathSegments("w/api.php")
                            .addQueryParameter("action", "query")
                            .addQueryParameter("prop", "extracts|pageprops")
                            .addQueryParameter("ppprop", "description")
                            .addQueryParameter("exintro", "true")
                            .addQueryParameter("explaintext", "true")
                            .addQueryParameter("exlimit", "5")
                            .addQueryParameter("exchars", "300")
                            .addQueryParameter("redirects", "1")
                            .addQueryParameter("titles", titles)
                            .addQueryParameter("format", "json")
                            .build()

                        val body = restClient.newCall(request(url)).execute()
                            .use { it.body.string() }

                        if (!body.trimStart().startsWith("{")) {
                            Log.w(TAG, "enrichEvents($lang): non-JSON response")
                            return@map batch
                        }

                        val pages = JSONObject(body)
                            .optJSONObject("query")
                            ?.optJSONObject("pages") ?: return@map batch

                        val pageMap = mutableMapOf<String, JSONObject>()
                        val names = pages.names() ?: return@map batch
                        for (i in 0 until names.length()) {
                            val p = pages.getJSONObject(names.getString(i))
                            val t = p.optString("title")
                            if (t.isNotEmpty()) pageMap[t] = p
                        }

                        batch.map { ev ->
                            val p = pageMap[ev.title]
                            if (p == null) ev
                            else ev.copy(
                                description = p.optJSONObject("pageprops")
                                    ?.optString("description"),
                                extract = p.optString("extract").takeIf { it.isNotBlank() }
                                    ?.split('\n')?.firstOrNull()
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "enrichEvents($lang) failed", e)
                        batch
                    }
                }.flatten()
            }
        }

    fun openStream(onEvent: (DeathEvent) -> Unit) {
        Thread {
            var backoff = 2_000L
            val maxBackoff = 60_000L
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val request = Request.Builder()
                        .url("https://stream.wikimedia.org/v2/stream/recentchange")
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/event-stream")
                        .build()

                    restClient.newBuilder()
                        .readTimeout(0, TimeUnit.MILLISECONDS)
                        .build()
                        .newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Log.w(TAG, "stream HTTP ${response.code}")
                                Thread.sleep(backoff)
                                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                                return@use
                            }
                            backoff = 2_000L
                            val reader = java.io.BufferedReader(
                                java.io.InputStreamReader(response.body.byteStream())
                            )
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val data = line?.trim() ?: continue
                                if (!data.startsWith("data:")) continue
                                val jsonStr = data.removePrefix("data:").trim()
                                if (jsonStr.isEmpty()) continue
                                try {
                                    val json = JSONObject(jsonStr)
                                    if (json.optInt("namespace") != 0) continue
                                    if (json.optString("project") != "wikipedia") continue
                                    val lang = json.optString("server_name").substringBefore('.')
                                    val title = json.optString("title", "")
                                    val comment = json.optString("comment", "")
                                    if (!livePatterns.any {
                                            it.containsMatchIn(title) || it.containsMatchIn(comment)
                                        }) continue

                                    onEvent(
                                        DeathEvent(
                                            id = java.util.UUID.randomUUID().toString(),
                                            title = title,
                                            wiki = lang,
                                            timestamp = json.optLong("timestamp") * 1000,
                                            user = json.optString("user", ""),
                                            comment = comment
                                        )
                                    )
                                } catch (_: Exception) { }
                            }
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "Stream broken, reconnecting in ${backoff}ms", e)
                }
                try { Thread.sleep(backoff) } catch (_: InterruptedException) { break }
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
            }
        }.apply {
            isDaemon = true
            name = "WikiStream"
        }.start()
    }

    private fun request(url: HttpUrl): Request =
        Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

    private fun parseTimestamp(iso: String): Long? {
        if (iso.isBlank()) return null
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(iso)?.time
        } catch (_: Exception) { null }
    }

    private fun displayName(code: String, display: Locale): String {
        val locale = Locale.forLanguageTag(code)

        var name = locale.getDisplayLanguage(display)

        if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) {
            name = locale.getDisplayLanguage(Locale.ENGLISH)
        }

        return if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) {
            code.uppercase()
        } else {
            name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(display) else it.toString()
            }
        }
    }

    private fun fallbackCategories(year: Int): List<Pair<String, String>> = listOf(
        "en" to "Category:$year deaths",
        "ru" to "Категория:Умершие в $year году",
        "fr" to "Catégorie:Décès en $year",
        "es" to "Categoría:Fallecidos en $year",
        "de" to "Kategorie:Gestorben $year",
        "it" to "Categoria:Morti nel $year",
        "ja" to "Category:${year}年没",
        "zh" to "Category:${year}年逝世",
        "pt" to "Categoria:Mortos em $year",
        "nl" to "Categorie:Overleden in $year",
        "pl" to "Kategoria:Zmarli w $year",
        "uk" to "Категорія:Померли $year",
        "ar" to "تصنيف:وفيات $year",
        "fi" to "Luokka:Vuonna $year kuolleet",
        "sv" to "Kategori:Avlidna $year",
        "sk" to "Kategória:Úmrtia v $year",
        "cs" to "Kategorie:Úmrtí v $year"
    )
}