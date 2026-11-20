package com.mysocial.mesh.ui.composer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.LocationAttachment
import com.mysocial.mesh.model.MediaAttachment
import com.mysocial.mesh.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ComposerMode {
    POST,
    PHOTO,
    POLL,
    ALERT
}

enum class ExpiryOption(val label: String, val durationMs: Long?) {
    FOREVER("Never", null),
    ONE_HOUR("1h", 3600_000L),
    SIX_HOURS("6h", 6 * 3600_000L),
    TWENTY_FOUR_HOURS("24h", 24 * 3600_000L)
}

class ComposerViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    private val _composerMode = MutableStateFlow(ComposerMode.POST)
    val composerMode: StateFlow<ComposerMode> = _composerMode.asStateFlow()

    private val _postText = MutableStateFlow("")
    val postText: StateFlow<String> = _postText.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _isPublic = MutableStateFlow(true)
    val isPublic: StateFlow<Boolean> = _isPublic.asStateFlow()

    // Mesh-native mechanics: Ephemeral TTL, SOS beacon, Polls
    private val _expiryOption = MutableStateFlow(ExpiryOption.FOREVER)
    val expiryOption: StateFlow<ExpiryOption> = _expiryOption.asStateFlow()

    private val _isSos = MutableStateFlow(false)
    val isSos: StateFlow<Boolean> = _isSos.asStateFlow()

    private val _isPollEnabled = MutableStateFlow(false)
    val isPollEnabled: StateFlow<Boolean> = _isPollEnabled.asStateFlow()

    private val _pollOptions = MutableStateFlow(listOf("", ""))
    val pollOptions: StateFlow<List<String>> = _pollOptions.asStateFlow()

    private val _locationAttachment = MutableStateFlow<LocationAttachment?>(null)
    val locationAttachment: StateFlow<LocationAttachment?> = _locationAttachment.asStateFlow()

    private val _isAcquiringLocation = MutableStateFlow(false)
    val isAcquiringLocation: StateFlow<Boolean> = _isAcquiringLocation.asStateFlow()

    // Tracks if user intentionally dismissed/removed location for this post
    private var locationExplicitlyRemoved = false

    val authorId: String = repository.cryptoManager.authorId
    val myProfile = repository.observeMyProfile()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setMode(mode: ComposerMode) {
        _composerMode.value = mode
        when (mode) {
            ComposerMode.POST -> {
                _isSos.value = false
                _isPollEnabled.value = false
            }
            ComposerMode.PHOTO -> {
                _isSos.value = false
                _isPollEnabled.value = false
            }
            ComposerMode.POLL -> {
                _isSos.value = false
                _isPollEnabled.value = true
                if (_pollOptions.value.size < 2) {
                    _pollOptions.value = listOf("", "")
                }
            }
            ComposerMode.ALERT -> {
                _isSos.value = true
                _isPollEnabled.value = false
                _isPublic.value = true
            }
        }
    }

    fun resetForMode(mode: ComposerMode) {
        _composerMode.value = mode
        _postText.value = ""
        _selectedImageUri.value = null
        _statusMessage.value = null
        _tags.value = emptyList()
        _isPublishing.value = false
        _isPublic.value = true
        _locationAttachment.value = null
        _isAcquiringLocation.value = false
        locationExplicitlyRemoved = false

        when (mode) {
            ComposerMode.POST -> {
                _isSos.value = false
                _isPollEnabled.value = false
                _expiryOption.value = ExpiryOption.FOREVER
                _pollOptions.value = listOf("", "")
            }
            ComposerMode.PHOTO -> {
                _isSos.value = false
                _isPollEnabled.value = false
                _expiryOption.value = ExpiryOption.FOREVER
                _pollOptions.value = listOf("", "")
            }
            ComposerMode.POLL -> {
                _isSos.value = false
                _isPollEnabled.value = true
                _expiryOption.value = ExpiryOption.TWENTY_FOUR_HOURS
                _pollOptions.value = listOf("", "")
            }
            ComposerMode.ALERT -> {
                _isSos.value = true
                _isPollEnabled.value = false
                _expiryOption.value = ExpiryOption.TWENTY_FOUR_HOURS
                _pollOptions.value = listOf("", "")
                _tags.value = listOf("ALERT", "SOS")
            }
        }
    }

    fun setExpiryOption(option: ExpiryOption) {
        _expiryOption.value = option
    }

    fun setSos(isSos: Boolean) {
        _isSos.value = isSos
    }

    fun setPollEnabled(enabled: Boolean) {
        _isPollEnabled.value = enabled
        if (enabled && _pollOptions.value.size < 2) {
            _pollOptions.value = listOf("", "")
        }
    }

    fun updatePollOption(index: Int, text: String) {
        val current = _pollOptions.value.toMutableList()
        if (index in current.indices) {
            current[index] = text
            _pollOptions.value = current
        }
    }

    fun addPollOption() {
        val current = _pollOptions.value.toMutableList()
        if (current.size < 4) {
            current.add("")
            _pollOptions.value = current
        }
    }

    fun removePollOption(index: Int) {
        val current = _pollOptions.value.toMutableList()
        if (current.size > 2 && index in current.indices) {
            current.removeAt(index)
            _pollOptions.value = current
        }
    }

    fun setLocation(name: String, latitude: Double? = null, longitude: Double? = null) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            locationExplicitlyRemoved = false
            _isAcquiringLocation.value = false
            _locationAttachment.value = LocationAttachment(
                name = trimmed,
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    fun clearLocation() {
        _locationAttachment.value = null
        _isAcquiringLocation.value = false
        locationExplicitlyRemoved = true
    }

    /**
     * Automatically acquires and attaches the device location to the post.
     * Uses fast-path cached coordinates for instantaneous UI display while simultaneously
     * querying hardware GNSS satellite lock in background for maximum accuracy.
     */
    fun autoAcquireLocation(context: Context) {
        if (locationExplicitlyRemoved || _locationAttachment.value != null || _isAcquiringLocation.value) return
        if (!LocationHelper.hasLocationPermission(context)) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAcquiringLocation.value = true
            try {
                // 1. Fast-path: immediately attach last known location if present
                val lastKnown = LocationHelper.getLastKnownLocation(context)
                if (lastKnown != null && !locationExplicitlyRemoved && _locationAttachment.value == null) {
                    val name = LocationHelper.getPlaceName(context, lastKnown.first, lastKnown.second)
                    _locationAttachment.value = LocationAttachment(
                        name = name,
                        latitude = lastKnown.first,
                        longitude = lastKnown.second
                    )
                }

                // 2. Active satellite acquisition for fresh, precise coordinates
                val fresh = LocationHelper.getOrAcquireLocation(context, timeoutMs = 8_000L)
                if (fresh != null && !locationExplicitlyRemoved) {
                    val name = LocationHelper.getPlaceName(context, fresh.first, fresh.second)
                    _locationAttachment.value = LocationAttachment(
                        name = name,
                        latitude = fresh.first,
                        longitude = fresh.second
                    )
                }
            } catch (_: Exception) {
                // Keep graceful fallback
            } finally {
                _isAcquiringLocation.value = false
            }
        }
    }

    fun setPublic(isPublic: Boolean) {
        _isPublic.value = isPublic
    }

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
        val validPollOptions = if (_isPollEnabled.value) {
            _pollOptions.value.map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.size >= 2 }
        } else null

        if (text.isBlank() && imageUri == null && validPollOptions == null && _locationAttachment.value == null && !LocationHelper.hasLocationPermission(context)) return

        viewModelScope.launch(Dispatchers.IO) {
            _isPublishing.value = true
            _statusMessage.value = null

            try {
                var location = _locationAttachment.value
                // Automatic location attachment: if not yet locked in and not explicitly removed, resolve best available fix
                if (location == null && !locationExplicitlyRemoved && LocationHelper.hasLocationPermission(context)) {
                    val fallback = LocationHelper.getLastKnownLocation(context)
                    if (fallback != null) {
                        val name = LocationHelper.getPlaceName(context, fallback.first, fallback.second)
                        location = LocationAttachment(
                            name = name,
                            latitude = fallback.first,
                            longitude = fallback.second
                        )
                        _locationAttachment.value = location
                    }
                }

                if (text.isBlank() && imageUri == null && validPollOptions == null && location == null) {
                    _isPublishing.value = false
                    return@launch
                }
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

                val postBody = when {
                    text.isNotBlank() -> text
                    mediaAttachment != null -> "Shared a photo"
                    validPollOptions != null -> "Participate in this poll"
                    location != null -> "At ${location.name}"
                    else -> "New update"
                }

                val expiresAt = _expiryOption.value.durationMs?.let { System.currentTimeMillis() + it }

                val result = repository.createPost(
                    text = postBody,
                    tags = _tags.value,
                    media = mediaAttachment,
                    isPublic = _isPublic.value,
                    expiresAt = expiresAt,
                    isSos = _isSos.value,
                    pollOptions = validPollOptions,
                    location = location
                )
                _isPublishing.value = false

                if (result.isSuccess) {
                    _postText.value = ""
                    _selectedImageUri.value = null
                    _isPublic.value = true
                    _isSos.value = false
                    _isPollEnabled.value = false
                    _pollOptions.value = listOf("", "")
                    _expiryOption.value = ExpiryOption.FOREVER
                    _locationAttachment.value = null
                    _isAcquiringLocation.value = false
                    locationExplicitlyRemoved = false
                    _statusMessage.value = "Post shared successfully!"
                    onSuccess()
                } else {
                    _statusMessage.value = "Failed to share post: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _isPublishing.value = false
                _statusMessage.value = "Error processing post: ${e.message}"
            }
        }
    }
}
