package com.mysocial.mesh.ui.composer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.MediaAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ComposerViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    private val _postText = MutableStateFlow("")
    val postText: StateFlow<String> = _postText.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun updateText(text: String) {
        _postText.value = text
        _statusMessage.value = null
    }

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun addTag(tag: String) {
        val clean = tag.trim().replace("#", "")
        if (clean.isNotBlank() && !_tags.value.contains(clean)) {
            _tags.value = _tags.value + clean
        }
    }

    fun removeTag(tag: String) {
        _tags.value = _tags.value - tag
    }

    fun publishPost(context: Context, onSuccess: () -> Unit) {
        val text = _postText.value.trim()
        val imageUri = _selectedImageUri.value
        if (text.isBlank() && imageUri == null) return

        viewModelScope.launch {
            _isPublishing.value = true
            _statusMessage.value = null

            try {
                var mediaAttachment: MediaAttachment? = null
                if (imageUri != null) {
                    val processed = MediaProcessor.processImageUri(context, imageUri)
                    repository.registerCompletedBlob(processed)
                    mediaAttachment = MediaAttachment(
                        blobHash = processed.blobHash,
                        mimeType = processed.mimeType,
                        totalBytes = processed.totalBytes,
                        chunkCount = processed.chunkCount,
                        microPreviewBase64 = processed.microPreviewBase64
                    )
                }

                val postBody = if (text.isBlank() && mediaAttachment != null) "Shared a photo" else text
                val result = repository.createPost(postBody, _tags.value, media = mediaAttachment)
                _isPublishing.value = false

                if (result.isSuccess) {
                    _postText.value = ""
                    _selectedImageUri.value = null
                    _statusMessage.value = "Post shared successfully!"
                    onSuccess()
                } else {
                    _statusMessage.value = "Failed to share post: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _isPublishing.value = false
                _statusMessage.value = "Error processing media: ${e.message}"
            }
        }
    }
}
