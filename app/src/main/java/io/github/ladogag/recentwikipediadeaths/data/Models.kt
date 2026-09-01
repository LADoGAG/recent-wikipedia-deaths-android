package io.github.ladogag.recentwikipediadeaths.data

data class WikiTarget(
    val lang: String,
    val category: String,
    val displayName: String,
    val englishName: String,
    val nativeName: String
)

data class DeathEvent(
    val id: String,
    val title: String,
    val wiki: String,
    val timestamp: Long,
    val user: String? = null,
    val comment: String? = null,
    val description: String? = null,
    val extract: String? = null
)

data class ErrorDetail(
    val lang: String,
    val message: String,
    val timestamp: Long
)