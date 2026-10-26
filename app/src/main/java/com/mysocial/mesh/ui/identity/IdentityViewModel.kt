package com.mysocial.mesh.ui.identity

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
import kotlinx.coroutines.withContext

class IdentityViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    val authorId: String = repository.cryptoManager.authorId

    val myProfile: StateFlow<ProfileViewEntity?> = repository.observeMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dbStats: StateFlow<DatabaseStats> = repository.observeDatabaseStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DatabaseStats(0, 0, 0))

    val myPosts: StateFlow<List<com.mysocial.mesh.model.PostWithAuthor>> = repository.observePostsByAuthor(authorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reactToPost(targetHash: String) {
        viewModelScope.launch {
            repository.reactToPost(targetHash)
        }
    }

    private val _qrBitmap = MutableStateFlow<ImageBitmap?>(null)
    val qrBitmap: StateFlow<ImageBitmap?> = _qrBitmap.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        generateQr()
    }

    private fun generateQr() {
        viewModelScope.launch(Dispatchers.Default) {
            val qrPayload = "mysocial://follow?authorId=$authorId"
            val bitmap = QrCodeGenerator.generateQrBitmap(qrPayload, size = 480)
            _qrBitmap.value = bitmap
        }
    }

    fun saveProfile(displayName: String, bio: String) {
        viewModelScope.launch {
            _isSaving.value = true
            repository.updateProfile(displayName, bio, avatarSeed = authorId)
            _isSaving.value = false
            generateQr()
        }
    }

    fun followAuthorFromScan(targetAuthorId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val clean = targetAuthorId.trim().let { raw ->
                val id = if (raw.contains("authorId=")) {
                    raw.substringAfter("authorId=").substringBefore("&")
                } else {
                    raw
                }.trim()
                if (!id.startsWith("@") && id.isNotBlank()) "@$id" else id
            }
            val result = repository.toggleFollow(clean, true)
            onComplete(result.isSuccess)
        }
    }

    fun deletePost(entryHash: String) {
        viewModelScope.launch {
            repository.deletePost(entryHash)
        }
    }

    fun editPost(entryHash: String, newText: String) {
        viewModelScope.launch {
            repository.editPost(entryHash, newText)
        }
    }
}
