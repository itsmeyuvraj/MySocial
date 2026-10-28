package com.mysocial.mesh.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.DatabaseStats
import com.mysocial.mesh.data.MeshRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    val dbStats: StateFlow<DatabaseStats> = repository.observeDatabaseStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DatabaseStats(0, 0, 0))

    val syncIntervalSeconds: StateFlow<Int> =
        repository.appSettings?.syncIntervalSeconds
            ?: MutableStateFlow(10).asStateFlow()

    fun setSyncInterval(seconds: Int) {
        repository.appSettings?.setSyncIntervalSeconds(seconds)
        repository.logActivity("Update check set to $seconds seconds")
    }

    val isDarkMode: StateFlow<Boolean> =
        repository.appSettings?.isDarkMode
            ?: MutableStateFlow(false).asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        repository.appSettings?.setDarkMode(enabled)
    }

    val maxStrangerMediaCacheMb: StateFlow<Int> = (repository.appSettings?.maxStrangerMediaCacheMb ?: flowOf(AppSettings.DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings.DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB)

    private val _storageUsageBytes = MutableStateFlow(0L)
    val storageUsageBytes: StateFlow<Long> = _storageUsageBytes.asStateFlow()

    fun refreshStorageUsage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _storageUsageBytes.value = repository.getBlobsDirectorySizeBytes(context)
        }
    }

    fun setMaxStrangerMediaCacheMb(mb: Int) {
        viewModelScope.launch {
            repository.appSettings?.setMaxStrangerMediaCacheMb(mb)
        }
    }
}
