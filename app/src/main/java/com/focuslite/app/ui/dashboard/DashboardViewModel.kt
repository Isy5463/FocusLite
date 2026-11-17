package com.focuslite.app.ui.dashboard

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focuslite.app.data.local.datastore.FocusPreferences
import com.focuslite.app.data.repository.FocusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: FocusRepository) : ViewModel() {

    val preferences: StateFlow<FocusPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusPreferences()
    )

    val blockedTodayCount: StateFlow<Int> = repository.getTodayBlockedFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    val totalBlockedCount: StateFlow<Int> = repository.totalBlockedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    fun updateSetting(key: Preferences.Key<Boolean>, enabled: Boolean) {
        viewModelScope.launch {
            repository.setToggle(key, enabled)
        }
    }

    fun setPauseMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.activatePauseForMinutes(minutes)
        }
    }

    class Factory(private val repository: FocusRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository) as T
        }
    }
}
