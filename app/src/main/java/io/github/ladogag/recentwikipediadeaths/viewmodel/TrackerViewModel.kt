package io.github.ladogag.recentwikipediadeaths.viewmodel

import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ladogag.recentwikipediadeaths.data.DeathEvent
import io.github.ladogag.recentwikipediadeaths.data.ErrorDetail
import io.github.ladogag.recentwikipediadeaths.data.WikiRepository
import io.github.ladogag.recentwikipediadeaths.data.WikiTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class TimeMode {
    data object LiveOnly : TimeMode()
    data class LastHours(val hours: Int) : TimeMode()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModel : ViewModel() {

    private val _targetsState = MutableStateFlow<List<WikiTarget>>(emptyList())
    val targets: StateFlow<List<WikiTarget>> = _targetsState

    private val _history = MutableStateFlow<List<DeathEvent>>(emptyList())
    private val _live = MutableStateFlow<List<DeathEvent>>(emptyList())
    private val _selectedLangs = MutableStateFlow<Set<String>>(emptySet())
    private val _timeMode = MutableStateFlow<TimeMode>(TimeMode.LastHours(24))
    private val _now = MutableStateFlow(System.currentTimeMillis())
    private val _isLoading = MutableStateFlow(true)
    private val _failedLangs = MutableStateFlow<List<ErrorDetail>>(emptyList())
    private val _successCount = MutableStateFlow(0)

    val selectedLangs: StateFlow<Set<String>> = _selectedLangs
    val timeMode: StateFlow<TimeMode> = _timeMode
    private val _hours = MutableStateFlow(24)
    val hours: StateFlow<Int> = _hours
    val isLoading: StateFlow<Boolean> = _isLoading
    val failedLangs: StateFlow<List<ErrorDetail>> = _failedLangs
    val successCount: StateFlow<Int> = _successCount

    val visibleEvents: StateFlow<List<DeathEvent>> = combine(
        _history, _live, _selectedLangs, _timeMode, _now
    ) { history, live, langs, mode, now ->
        val source = when (mode) {
            is TimeMode.LiveOnly -> live
            is TimeMode.LastHours -> live + history
        }
        source.filter { e ->
            e.wiki in langs && when (mode) {
                is TimeMode.LiveOnly -> true
                is TimeMode.LastHours -> now - e.timestamp <= mode.hours * 3_600_000L
            }
        }.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pendingLangs = Channel<String>(Channel.UNLIMITED)
    private val fetchedLangs = mutableSetOf<String>()

    init {
        WikiRepository.loadTimeSettings()?.let { (liveOnly, savedHours) ->
            _hours.value = savedHours
            _timeMode.value = if (liveOnly) TimeMode.LiveOnly else TimeMode.LastHours(savedHours)
        }

        viewModelScope.launch {
            val all = WikiRepository.fetchAllTargets()
            _targetsState.value = all

            val available = all.map { it.lang }.toSet()
            val saved = WikiRepository.loadSelection()
            val initial = saved?.intersect(available) ?: defaultSelection(all)

            _selectedLangs.value = initial
            requestHistory(initial)
        }

        viewModelScope.launch {
            for (lang in pendingLangs) {
                _isLoading.value = true
                if (lang !in fetchedLangs) {
                    fetchedLangs += lang
                    val target = _targetsState.value.firstOrNull { it.lang == lang }
                    if (target != null) {
                        val items = WikiRepository.fetchCategoryMembers(target)
                        if (items == null) {
                            _failedLangs.update {
                                it + ErrorDetail(lang, "unavailable", System.currentTimeMillis())
                            }
                        } else {
                            _successCount.value += 1
                            if (items.isNotEmpty()) {
                                val enriched = WikiRepository.enrichEvents(items)
                                _history.update { old ->
                                    (old + enriched).distinctBy { e -> e.wiki to e.title }
                                }
                            }
                        }
                    }
                    delay(1000)
                }
                if (pendingLangs.isEmpty) _isLoading.value = false
            }
        }

        WikiRepository.openStream { event ->
            _live.update { (it + event).takeLast(300) }
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val upd = WikiRepository.enrichEvents(listOf(event)).firstOrNull() ?: return@launch
                _live.update { list -> list.map { if (it.id == upd.id) upd else it } }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                _now.value = System.currentTimeMillis()
            }
        }
    }

    private fun defaultSelection(targets: List<WikiTarget>): Set<String> {
        val available = targets.map { it.lang }.toSet()
        val sel = mutableSetOf<String>()
        val locales = LocaleList.getDefault()
        for (i in 0 until locales.size()) {
            val code = locales[i].language.lowercase()
            if (code in available) sel += code
        }
        if (sel.isEmpty()) sel += "en"
        return sel
    }

    private fun requestHistory(langs: Set<String>) {
        langs.forEach { pendingLangs.trySend(it) }
    }

    fun toggleLang(lang: String) {
        val wasSelected = lang in _selectedLangs.value
        _selectedLangs.update { if (wasSelected) it - lang else it + lang }
        WikiRepository.saveSelection(_selectedLangs.value)
        if (!wasSelected) requestHistory(setOf(lang))
    }

    fun setAllLangs(selectAll: Boolean) {
        val next = if (selectAll) _targetsState.value.map { it.lang }.toSet() else emptySet()
        _selectedLangs.value = next
        WikiRepository.saveSelection(next)
        if (selectAll) requestHistory(next)
    }

    fun setLiveMode() {
        _timeMode.value = TimeMode.LiveOnly
        WikiRepository.saveTimeSettings(liveOnly = true, hours = _hours.value)
    }

    fun setLastHoursMode() {
        _timeMode.value = TimeMode.LastHours(_hours.value)
        WikiRepository.saveTimeSettings(liveOnly = false, hours = _hours.value)
    }

    fun setHours(hours: Int) {
        _hours.value = hours
        _timeMode.value = TimeMode.LastHours(hours)
        WikiRepository.saveTimeSettings(liveOnly = false, hours = hours)
    }

    fun refreshLocalization() {
        _targetsState.value = WikiRepository.localizedTargets()
    }
}