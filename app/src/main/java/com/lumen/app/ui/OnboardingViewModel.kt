package com.lumen.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.app.data.AppContainer
import com.lumen.app.domain.TimeUtils
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    fun hasUsageAccess(): Boolean = container.usageRepository.hasUsageAccess()

    fun hasNotificationAccess(): Boolean = container.usageRepository.isNotificationAccessEnabled()

    fun complete() {
        viewModelScope.launch {
            container.settingsRepository.setOnboarded()
            container.usageRepository.persistDay(
                TimeUtils.today(),
                emptySet(),
                container.currentSettings.value.notificationTracking,
            )
        }
    }
}
