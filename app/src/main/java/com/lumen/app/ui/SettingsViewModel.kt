package com.lumen.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.app.data.AppContainer
import com.lumen.app.data.prefs.LumenSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppCandidate(
    val packageName: String,
    val label: String,
    val excluded: Boolean,
)

data class SettingsState(
    val settings: LumenSettings = LumenSettings(),
    val candidateApps: List<AppCandidate> = emptyList(),
    val notificationAccessEnabled: Boolean = false,
    val usageAccessEnabled: Boolean = false,
    val underTracking: Boolean = false,
    val extensions: List<Triple<Long, String, String?>> = emptyList(),
    val orphans: List<AppCandidate> = emptyList(),
    val usedCount: Int = 0,
    val installedCount: Int = 0,
)

data class AppLimit(
    val packageName: String,
    val label: String,
    val minutesPerDay: Int,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.linkRepository.linkEnabled.collect { enabled ->
                _state.update { it.copy(underTracking = enabled) }
            }
        }
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                val apps = if (container.usageRepository.hasUsageAccess()) {
                    container.usageRepository.allAppPackages().map { pkg ->
                        AppCandidate(
                            packageName = pkg,
                            label = container.appInfo.label(pkg),
                            excluded = pkg in settings.excludedPackages,
                        )
                    }
                } else {
                    emptyList()
                }
                val usedWeek = if (apps.isNotEmpty()) {
                    container.usageRepository.usedPackagesSince(com.lumen.app.domain.TimeUtils.today().minusDays(6))
                } else {
                    emptySet()
                }
                val orphans = if (apps.isNotEmpty()) {
                    container.usageRepository.orphanApps(30, settings.excludedPackages)
                        .map { pkg ->
                            AppCandidate(
                                packageName = pkg,
                                label = container.appInfo.label(pkg),
                                excluded = false,
                            )
                        }
                } else {
                    emptyList()
                }
                _state.update {
                    it.copy(
                        settings = settings,
                        candidateApps = apps,
                        notificationAccessEnabled = container.usageRepository.isNotificationAccessEnabled(),
                        usageAccessEnabled = container.usageRepository.hasUsageAccess(),
                        extensions = container.settingsRepository.allExtensions(),
                        orphans = orphans,
                        usedCount = usedWeek.size,
                        installedCount = apps.size,
                    )
                }
            }
        }
    }

    fun refreshPermissions() {
        _state.update {
            it.copy(
                notificationAccessEnabled = container.usageRepository.isNotificationAccessEnabled(),
                usageAccessEnabled = container.usageRepository.hasUsageAccess(),
            )
        }
    }

    fun setGoal(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setDailyGoal(minutes) }
    }

    fun setFocus(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setFocusMinutes(minutes) }
    }

    fun setDark(dark: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDarkTheme(dark) }
    }

    fun setNotificationTracking(enabled: Boolean) {
        container.notificationRepository.enabled = enabled
        viewModelScope.launch { container.settingsRepository.setNotificationTracking(enabled) }
    }

    fun toggleExcluded(packageName: String) {
        if (_state.value.underTracking) return
        val current = _state.value.settings.excludedPackages
        val next = if (packageName in current) current - packageName else current + packageName
        viewModelScope.launch { container.settingsRepository.setExcluded(next) }
    }

    fun setAppLimit(packageName: String, minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setAppLimit(packageName, minutes) }
    }

    fun removeAppLimit(packageName: String) {
        viewModelScope.launch { container.settingsRepository.removeAppLimit(packageName) }
    }

    fun setBedtime(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch { container.settingsRepository.setBedtime(enabled, hour, minute) }
    }

    fun toggleRestDay(day: Int) {
        val current = _state.value.settings.restDays
        val next = if (day in current) current - day else current + day
        viewModelScope.launch { container.settingsRepository.setRestDays(next) }
    }

    fun setWeekendMode(enabled: Boolean) {
        val settings = _state.value.settings
        viewModelScope.launch {
            container.settingsRepository.setWeekendMode(enabled, settings.weekendGoalMinutes)
        }
    }

    fun setWeekendGoal(minutes: Int) {
        viewModelScope.launch {
            container.settingsRepository.setWeekendMode(
                _state.value.settings.weekendEnabled,
                minutes,
            )
        }
    }

    fun exportData(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val settings = _state.value.settings
            onReady(
                container.usageRepository.exportJson(
                    settings.excludedPackages,
                    settings.notificationTracking,
                ),
            )
        }
    }

    fun importData(json: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val count = container.usageRepository.importJson(json)
                onDone(
                    if (count < 0) "That file isn't a Lumen backup."
                    else "Imported $count days of history.",
                )
            } catch (e: Exception) {
                onDone("Import failed: ${e.message ?: "unreadable file"}")
            }
        }
    }
}
