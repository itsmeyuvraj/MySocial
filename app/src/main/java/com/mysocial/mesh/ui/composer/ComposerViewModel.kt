package com.mysocial.mesh.ui.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MeshRepository
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

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun updateText(text: String) {
        _postText.value = text
        _statusMessage.value = null
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

    fun publishPost(onSuccess: () -> Unit) {
        val text = _postText.value.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _isPublishing.value = true
            val result = repository.createPost(text, _tags.value)
            _isPublishing.value = false

            if (result.isSuccess) {
                _postText.value = ""
                _statusMessage.value = "Post shared successfully!"
                onSuccess()
            } else {
                _statusMessage.value = "Failed to share post: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}
