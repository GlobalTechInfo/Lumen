package com.lumen.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.app.data.AppContainer
import com.lumen.app.data.link.ChildSnapshot
import com.lumen.app.data.link.NearbyChild
import com.lumen.app.data.link.PeerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyState(
    val nickname: String = "My phone",
    val linkEnabled: Boolean = false,
    val qrPayload: String? = null,
    val peers: List<PeerInfo> = emptyList(),
    val snapshots: Map<String, ChildSnapshot> = emptyMap(),
    val histories: Map<String, List<com.lumen.app.domain.DayStats>> = emptyMap(),
    val myWeekTotal: Long = 0,
    val myWeekDays: List<com.lumen.app.domain.DayStats> = emptyList(),
    val celebrations: Map<String, Boolean> = emptyMap(),
    val nearby: List<NearbyChild> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class FamilyViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FamilyState())
    val state: StateFlow<FamilyState> = _state.asStateFlow()

    init {
        val repo = container.linkRepository
        viewModelScope.launch {
            _state.update { it.copy(nickname = repo.nickname()) }
            refreshQr()
        }
        viewModelScope.launch {
            repo.peers.collect { peers ->
                _state.update { it.copy(peers = peers) }
            }
        }
        viewModelScope.launch {
            repo.linkEnabled.collect { enabled ->
                _state.update { it.copy(linkEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            repo.snapshots.collect { snapshots ->
                _state.update { it.copy(snapshots = snapshots) }
                val settings = container.settingsRepository.settings.first()
                refreshCelebrations(settings)
            }
        }
        viewModelScope.launch {
            repo.histories.collect { histories ->
                _state.update { it.copy(histories = histories) }
            }
        }
        viewModelScope.launch {
            repo.nearby.collect { nearby ->
                _state.update { it.copy(nearby = nearby) }
            }
        }
        viewModelScope.launch {
            repo.incomingKnock.collect { knock ->
                if (knock != null) {
                    _state.update { it.copy(message = "Knock from ${knock.first}: ${knock.second}") }
                }
            }
        }
    }

    fun refreshQr() {
        viewModelScope.launch {
            val payload = container.linkRepository.pairingPayload()
            _state.update { it.copy(qrPayload = payload) }
        }
    }

    fun refreshMyWeek() {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            val today = com.lumen.app.domain.TimeUtils.today()
            val week = container.usageRepository.history(
                today.minusDays(6),
                settings.excludedPackages,
                settings.notificationTracking,
            )
            _state.update {
                it.copy(
                    myWeekTotal = week.sumOf { it.screenTimeMs },
                    myWeekDays = week,
                )
            }
            refreshCelebrations(settings)
        }
    }

    private suspend fun refreshCelebrations(settings: com.lumen.app.data.prefs.LumenSettings) {
        val current = _state.value
        val myDays = current.myWeekDays
        if (myDays.size < 7) return
        val myCompliant = myDays.all { day ->
            day.screenTimeMs <= settings.dailyGoalFor(day.date) * 60_000L
        }
        val next = HashMap(current.celebrations)
        for (peer in current.peers) {
            val snapshot = current.snapshots[peer.id] ?: continue
            if (snapshot.goalMinutes <= 0 || snapshot.week.isEmpty()) continue
            val theirCompliant = snapshot.week.all { it.screenTimeMs <= snapshot.goalMinutes * 60_000L }
            val celebrating = myCompliant && theirCompliant
            if (celebrating && next[peer.id] != true) {
                container.linkRepository.celebrateMutualWeek(peer.nickname)
            }
            next[peer.id] = celebrating
        }
        _state.update { it.copy(celebrations = next) }
    }

    fun setNickname(name: String) {
        viewModelScope.launch {
            container.linkRepository.setNickname(name)
            _state.update { it.copy(nickname = name.trim().take(24).ifEmpty { "My phone" }) }
            refreshQr()
        }
    }

    fun toggleLink(enabled: Boolean) {
        container.linkRepository.setLinkEnabled(enabled)
        if (enabled) {
            viewModelScope.launch {
                container.settingsRepository.setExcluded(emptySet())
            }
        }
        _state.update { it.copy(linkEnabled = enabled) }
    }

    fun pair(raw: String) {
        viewModelScope.launch {
            val (ok, name) = container.linkRepository.pairWith(raw)
            _state.update {
                it.copy(
                    message = if (ok) "Linked to $name" else name,
                    peers = container.linkRepository.peers.value,
                )
            }
        }
    }

    fun removePeer(peerId: String) {
        viewModelScope.launch { container.linkRepository.removePeer(peerId) }
    }

    fun syncWithNearby(nearby: NearbyChild) {
        viewModelScope.launch {
            val peers = _state.value.peers
            val peer = peers.firstOrNull { it.nickname == nearby.nickname }
                ?: peers.firstOrNull()
            if (peer == null) {
                _state.update { it.copy(message = "Link to this phone first.") }
                return@launch
            }
            sync(peer, nearby.host, nearby.port)
        }
    }

    fun manualSync(host: String, port: Int) {
        viewModelScope.launch {
            val peer = _state.value.peers.firstOrNull()
            if (peer == null) {
                _state.update { it.copy(message = "Link to this phone first.") }
                return@launch
            }
            sync(peer, host, port)
        }
    }

    fun sendKnock(peer: PeerInfo, host: String, port: Int, text: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = container.linkRepository.syncWith(peer, host, port, text)
            _state.update {
                it.copy(
                    busy = false,
                    message = result.getOrElse {
                        "Knock not delivered: ${it.message ?: "connection failed"}"
                    },
                )
            }
        }
    }

    private suspend fun sync(peer: PeerInfo, host: String, port: Int) {
        _state.update { it.copy(busy = true, message = null) }
        val result = container.linkRepository.syncWith(peer, host, port)
        _state.update {
            it.copy(
                busy = false,
                message = result.getOrElse { "Could not reach that phone: ${it.message ?: "connection failed"}" },
            )
        }
    }

    fun exportReport(pin: String, onReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(container.linkRepository.buildExportBytes(pin))
            } catch (e: Exception) {
                _state.update { it.copy(message = "Export failed: ${e.message ?: "try again"}") }
            }
        }
    }

    fun importReport(bytes: ByteArray, pin: String) {
        viewModelScope.launch {
            val result = container.linkRepository.importArchive(bytes, pin)
            _state.update {
                it.copy(message = result.getOrElse { "Import failed: ${it.message ?: "try again"}" })
            }
            refreshMyWeek()
        }
    }

    fun onVisible() {
        container.linkRepository.startDiscovery()
        refreshQr()
        refreshMyWeek()
        startAutoSync()
    }

    fun onHidden() {
        container.linkRepository.stopDiscovery()
        stopAutoSync()
    }

    private var autoSyncJob: kotlinx.coroutines.Job? = null

    private fun startAutoSync() {
        if (autoSyncJob != null) return
        autoSyncJob = viewModelScope.launch {
            while (true) {
                try {
                    val current = _state.value
                    if (current.peers.isNotEmpty()) {
                        for (peer in current.peers) {
                            val near = current.nearby.firstOrNull { it.nickname == peer.nickname }
                            val host = near?.host ?: peer.host ?: continue
                            container.linkRepository.syncWith(peer, host, near?.port ?: 8787)
                        }
                    }
                } catch (_: Exception) {
                }
                kotlinx.coroutines.delay(2 * 60 * 1000)
            }
        }
    }

    private fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun notify(text: String) {
        _state.update { it.copy(message = text) }
    }
}
