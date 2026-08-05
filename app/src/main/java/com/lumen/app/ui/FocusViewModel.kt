package com.lumen.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.app.data.AppContainer
import com.lumen.app.data.prefs.LumenSettings
import com.lumen.app.domain.FocusSession
import com.lumen.app.domain.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusState(
    val settings: LumenSettings = LumenSettings(),
    val remainingSeconds: Long = 0,
    val running: Boolean = false,
    val sessionId: Long? = null,
    val todaySessions: List<FocusSession> = emptyList(),
)

class FocusViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                loadSessions()
            }
        }
    }

    fun start() {
        val s = _state.value
        if (s.running || s.sessionId != null) return
        viewModelScope.launch {
            val id = container.focusRepository.start(s.settings.focusMinutes)
            _state.update {
                it.copy(
                    sessionId = id,
                    remainingSeconds = s.settings.focusMinutes * 60L,
                    running = true,
                )
            }
            tick()
        }
    }

    fun pause() {
        ticker?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun resume() {
        if (_state.value.sessionId == null) return
        _state.update { it.copy(running = true) }
        tick()
    }

    fun abandon() {
        ticker?.cancel()
        val sessionId = _state.value.sessionId
        viewModelScope.launch {
            if (sessionId != null) container.focusRepository.finish(sessionId, false)
            _state.update { it.copy(sessionId = null, remainingSeconds = 0, running = false) }
            loadSessions()
        }
    }

    private suspend fun loadSessions() {
        val start = TimeUtils.startOfDay(TimeUtils.today())
        val sessions = container.focusRepository.getBetween(start, System.currentTimeMillis())
        _state.update { it.copy(todaySessions = sessions) }
    }

    private fun tick() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val s = _state.value
                if (!s.running) break
                delay(1000)
                val remaining = s.remainingSeconds - 1
                if (remaining <= 0) {
                    val id = s.sessionId
                    _state.update { it.copy(remainingSeconds = 0, running = false, sessionId = null) }
                    if (id != null) container.focusRepository.finish(id, true)
                    loadSessions()
                    break
                }
                _state.update { it.copy(remainingSeconds = remaining) }
            }
        }
    }
}
