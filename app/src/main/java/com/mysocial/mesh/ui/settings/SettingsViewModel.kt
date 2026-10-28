package com.mysocial.mesh.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.DatabaseStats
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.ProfileViewEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    val authorId: String = repository.cryptoManager.authorId

    val myProfile: StateFlow<ProfileViewEntity?> = repository.observeMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dbStats: StateFlow<DatabaseStats> = repository.observeDatabaseStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DatabaseStats(0, 0, 0))

    val syncIntervalSeconds: StateFlow<Int> =
        repository.appSettings?.syncIntervalSeconds
            ?: MutableStateFlow(10).asStateFlow()

    fun setSyncInterval(seconds: Int) {
        repository.appSettings?.setSyncIntervalSeconds(seconds)
        repository.logActivity("Sync interval set to $seconds seconds")
    }

    val isDarkMode: StateFlow<Boolean> =
        repository.appSettings?.isDarkMode
            ?: MutableStateFlow(false).asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        repository.appSettings?.setDarkMode(enabled)
    }
}
