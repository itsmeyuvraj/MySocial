package com.mysocial.mesh.ui.identity

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.DatabaseStats
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.ui.qr.QrCodeGenerator
import com.mysocial.mesh.utils.TrustQrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IdentityViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    val authorId: String = repository.cryptoManager.authorId

    val followedAuthorIds: StateFlow<Set<String>> = repository.observeFollowedAuthorIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val myProfile: StateFlow<ProfileViewEntity?> = repository.observeMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dbStats: StateFlow<DatabaseStats> = repository.observeDatabaseStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DatabaseStats(0, 0, 0))

    val myPosts: StateFlow<List<com.mysocial.mesh.model.PostWithAuthor>> = repository.observePostsByAuthor(authorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFollow(targetAuthorId: String, following: Boolean) {
        viewModelScope.launch {
            repository.toggleFollow(targetAuthorId, following)
        }
    }

    fun reactToPost(targetHash: String) {
        viewModelScope.launch {
            repository.reactToPost(targetHash)
        }
    }

    fun observeReplies(postHash: String): kotlinx.coroutines.flow.Flow<List<com.mysocial.mesh.model.PostWithAuthor>> {
        return repository.observeReplies(postHash)
    }

    fun replyToPost(text: String, rootHash: String, parentHash: String) {
        viewModelScope.launch {
            repository.createReply(text, rootHash, parentHash)
        }
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

    private val _qrBitmap = MutableStateFlow<ImageBitmap?>(null)
    val qrBitmap: StateFlow<ImageBitmap?> = _qrBitmap.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        generateQr()
    }

    fun generateQr() {
        viewModelScope.launch(Dispatchers.Default) {
            val name = myProfile.value?.displayName ?: "User"
            val qrPayload = TrustQrUtils.createTrustPayload(authorId, name, repository.cryptoManager)
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

    fun processScannedQr(scannedText: String, onResult: (success: Boolean, message: String) -> Unit): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            val trustData = TrustQrUtils.parseAndVerifyTrustPayload(scannedText)
            if (trustData != null && trustData.isValidSignature) {
                repository.toggleFollow(trustData.authorId, true)
                repository.setVerifiedInPerson(trustData.authorId, true)
                onResult(true, "Verified ${trustData.displayName} in person!")
            } else if (trustData != null) {
                repository.toggleFollow(trustData.authorId, true)
                onResult(true, "Followed ${trustData.displayName}")
            } else {
                val raw = scannedText.trim()
                val id = if (raw.contains("authorId=")) {
                    raw.substringAfter("authorId=").substringBefore("&")
                } else if (raw.isNotBlank()) {
                    raw
                } else null

                if (!id.isNullOrBlank()) {
                    val cleanId = if (!id.startsWith("@")) "@$id" else id
                    repository.toggleFollow(cleanId, true)
                    onResult(true, "Followed $cleanId")
                } else {
                    onResult(false, "Invalid QR code format")
                }
            }
        }
    }

    fun followAuthorFromScan(targetAuthorId: String, onComplete: (Boolean) -> Unit) {
        processScannedQr(targetAuthorId) { success, _ ->
            onComplete(success)
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
