package com.mysocial.mesh.ui.settings

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.DatabaseStats
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.ui.qr.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _qrBitmap = MutableStateFlow<ImageBitmap?>(null)
    val qrBitmap: StateFlow<ImageBitmap?> = _qrBitmap.asStateFlow()

    init {
        generateQr()
    }

    fun generateQr() {
        viewModelScope.launch(Dispatchers.Default) {
            val qrPayload = "mysocial://follow?authorId=$authorId"
            val bitmap = QrCodeGenerator.generateQrBitmap(qrPayload, size = 512)
            _qrBitmap.value = bitmap
        }
    }

    fun followAuthorFromScan(targetPayload: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val clean = targetPayload.trim().let { raw ->
                val id = if (raw.contains("authorId=")) {
                    raw.substringAfter("authorId=").substringBefore("&")
                } else {
                    raw
                }.trim()
                if (!id.startsWith("@") && id.isNotBlank()) "@$id" else id
            }
            if (clean.isBlank()) {
                onComplete(false, "Invalid QR code payload")
                return@launch
            }
            val result = repository.toggleFollow(clean, true)
            onComplete(result.isSuccess, clean)
        }
    }
}
