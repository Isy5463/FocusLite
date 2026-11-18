package com.focuslite.app.ui.dashboard
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focuslite.app.data.repository.FocusRepository
import com.focuslite.app.data.local.datastore.FocusPreferences
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
class DashboardViewModel(private val repository: FocusRepository) : ViewModel() { val preferences = repository.preferencesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusPreferences()); val blockedTodayCount = repository.getTodayBlockedFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0); val totalBlockedCount = repository.totalBlockedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0); fun updateSetting(key: Preferences.Key<Boolean>, enabled: Boolean) { viewModelScope.launch { repository.setToggle(key, enabled) } }; fun setPauseMinutes(minutes: Int) { viewModelScope.launch { repository.activatePauseForMinutes(minutes) } }; class Factory(private val repository: FocusRepository) : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository) as T } }