package com.mysocial.mesh.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.model.DirectMessageEntity
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.FullScreenImageViewerDialog
import com.mysocial.mesh.ui.components.VerifiedBadge
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import com.mysocial.mesh.utils.VoicePlayer
import com.mysocial.mesh.utils.toFormattedBbid
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    peerDisplayName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val profile by viewModel.peerProfile.collectAsState()
    val isOnline by viewModel.isPeerOnline.collectAsState()
    val isSending by viewModel.isSending.collectAsState()

    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDurationMs by viewModel.recordingDurationMs.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<DirectMessageEntity?>(null) }
    val listState = rememberLazyListState()

    BackHandler {
        if (isRecording) {
            viewModel.cancelVoiceRecording()
        } else {
            onNavigateBack()
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // Audio permission launcher
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startVoiceRecording(context)
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = FbSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = FbTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        val resolvedHeaderName = profile?.displayName?.takeIf { it.isNotBlank() && it != "User" && it != "MeshPeer" }
                            ?: peerDisplayName.takeIf { it.isNotBlank() && it != "User" && it != "MeshPeer" }
                            ?: "Friend"

                        AvatarView(
                            seed = profile?.avatarSeed ?: viewModel.peerAuthorId,
                            displayName = resolvedHeaderName,
                            size = 38.dp,
                            showOnlineBadge = isOnline
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = resolvedHeaderName,
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                                if (profile?.isVerifiedInPerson == true) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerifiedBadge(size = 14.dp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isOnline) "Active now • End-to-end encrypted" else "${viewModel.peerAuthorId.toFormattedBbid()} • Encrypted",
                                    color = if (isOnline) FacebookBlue else FbTextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = if (isOnline) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }

                        // Direct Voice Call Button
                        IconButton(
                            onClick = { viewModel.startVoiceCall() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Direct Voice Call",
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                }
            }
        },
        bottomBar = {
            Surface(
                color = FbSurface,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column {
                    HorizontalDivider(color = FbDivider, thickness = 0.5.dp)

                    // Image attachment preview strip
                    if (selectedImageUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FbSurfaceElevated)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, FacebookBlue, RoundedCornerShape(8.dp))
                            ) {
                                val previewBitmap = remember(selectedImageUri) {
                                    try {
                                        context.contentResolver.openInputStream(selectedImageUri!!)?.use {
                                            BitmapFactory.decodeStream(it)
                                        }
                                    } catch (_: Exception) { null }
                                }
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = "Selected photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = "Photo attached",
                                color = FbTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove photo",
                                    tint = FbTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = FbBorder, thickness = 0.5.dp)
                    }

                    // Input Controls Row
                    if (isRecording) {
                        // Live Voice Recording State Row
                        VoiceRecordingBar(
                            durationMs = recordingDurationMs,
                            onCancel = { viewModel.cancelVoiceRecording() },
                            onSend = { viewModel.finishAndSendVoiceRecording(context) }
                        )
                    } else {
                        // Standard Input Bar (Photo, Text, Mic / Send)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Attach Photo Button
                            IconButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = "Attach Photo",
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        text = if (selectedImageUri != null) "Add a caption..." else "Encrypted message...",
                                        color = FbTextTertiary,
                                        fontSize = 14.sp
                                    )
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = FbSurfaceElevated,
                                    unfocusedContainerColor = FbSurfaceElevated,
                                    focusedBorderColor = FacebookBlue,
                                    unfocusedBorderColor = FbBorder,
                                    focusedTextColor = FbTextPrimary,
                                    unfocusedTextColor = FbTextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            val hasContentToSend = inputText.isNotBlank() || selectedImageUri != null

                            if (hasContentToSend) {
                                // Send Button
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(FacebookBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (!isSending) {
                                                val uri = selectedImageUri
                                                val text = inputText
                                                if (uri != null) {
                                                    viewModel.sendImageMessage(context, uri, text)
                                                    selectedImageUri = null
                                                    inputText = ""
                                                } else if (text.isNotBlank()) {
                                                    viewModel.sendMessage(text)
                                                    inputText = ""
                                                }
                                            }
                                        },
                                        enabled = !isSending
                                    ) {
                                        if (isSending) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Voice Note Button
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(FacebookBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (hasAudioPermission) {
                                                viewModel.startVoiceRecording(context)
                                            } else {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Mic,
                                            contentDescription = "Record Voice Message",
                                            tint = FacebookBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = FbSurface
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                // Encryption notice & empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val resolvedCenterName = profile?.displayName?.takeIf { it.isNotBlank() && it != "User" && it != "MeshPeer" }
                        ?: peerDisplayName.takeIf { it.isNotBlank() && it != "User" && it != "MeshPeer" }
                        ?: "Friend"

                    AvatarView(
                        seed = profile?.avatarSeed ?: viewModel.peerAuthorId,
                        displayName = resolvedCenterName,
                        size = 64.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = resolvedCenterName,
                        color = FbTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = viewModel.peerAuthorId.toFormattedBbid(),
                        color = FbTextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = FbSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .border(0.8.dp, FbBorder, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Messages, photos, and voice notes are secured with End-to-End Encryption. Only you and this person can decrypt them.",
                                color = FbTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(messages, key = { _, message -> message.messageId }) { index, message ->
                        val showDateDivider = index == 0 || !isSameDay(message.timestamp, messages[index - 1].timestamp)
                        if (showDateDivider) {
                            ChatDateDivider(timestamp = message.timestamp)
                        }
                        MessageBubble(
                            message = message,
                            voicePlayer = viewModel.voicePlayer,
                            viewModel = viewModel,
                            onLongClick = { selectedMessageForActions = message }
                        )
                    }
                }
            }
        }

        // Long-Press Message Actions Dialog
        if (selectedMessageForActions != null) {
            val msg = selectedMessageForActions!!
            val clipboardManager = LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { selectedMessageForActions = null },
                containerColor = FbSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Message Options",
                        color = FbTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (msg.text.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(msg.text))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        selectedMessageForActions = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Copy text",
                                    color = FbTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.deleteMessage(msg.messageId)
                                    Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                                    selectedMessageForActions = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = FbRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Delete for me",
                                color = FbRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedMessageForActions = null }) {
                        Text("Cancel", color = FbTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun VoiceRecordingBar(
    durationMs: Long,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_recording")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot"
    )

    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 60000)
    val timeLabel = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Red Recording Dot & Timer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(FbRed)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recording...",
                color = FbRed,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeLabel,
                color = FbTextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        // Action Buttons: Cancel and Send
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FbSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Cancel Recording",
                    tint = FbTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(FacebookBlue)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Voice Message",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: DirectMessageEntity,
    voicePlayer: VoicePlayer,
    viewModel: ChatViewModel,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isOutgoing = message.isOutgoing
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isOutgoing) 16.dp else 4.dp,
            bottomEnd = if (isOutgoing) 4.dp else 16.dp
        )
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(bubbleShape)
                .background(if (isOutgoing) FacebookBlue else FbSurfaceElevated)
                .border(
                    width = if (isOutgoing) 0.dp else 0.8.dp,
                    color = if (isOutgoing) Color.Transparent else FbBorder,
                    shape = bubbleShape
                )
                .combinedClickable(
                    onClick = { /* Bubble tap */ },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                // 1. Image Media Attachment
                if (message.mediaType == "IMAGE" && message.mediaBlobHash != null) {
                    DirectMessageImageView(
                        blobHash = message.mediaBlobHash,
                        microPreviewBase64 = message.mediaMicroPreviewBase64,
                        isOutgoing = isOutgoing,
                        viewModel = viewModel
                    )
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // 2. Audio Voice Note Attachment
                if (message.mediaType == "AUDIO" && message.mediaBlobHash != null) {
                    DirectMessageAudioView(
                        messageId = message.messageId,
                        blobHash = message.mediaBlobHash,
                        durationMs = message.mediaDurationMs ?: 0L,
                        isOutgoing = isOutgoing,
                        voicePlayer = voicePlayer,
                        viewModel = viewModel
                    )
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // 3. Text Message / Caption
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = if (isOutgoing) Color.White else FbTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Timestamp & Delivery status ticks
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        color = if (isOutgoing) Color.White.copy(alpha = 0.75f) else FbTextTertiary,
                        fontSize = 10.sp
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            0 -> Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = "Sending",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(11.dp)
                            )
                            1 -> Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Sent",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(12.dp)
                            )
                            2 -> Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Delivered",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                            3 -> Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Read",
                                tint = Color(0xFF60CDFF), // Vibrant cyan-blue double ticks on FacebookBlue
                                modifier = Modifier.size(13.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Delivered",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DirectMessageImageView(
    blobHash: String,
    microPreviewBase64: String?,
    isOutgoing: Boolean,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val manifest by viewModel.observeBlobManifest(blobHash).collectAsState(initial = null)
    val blobFile = remember(blobHash) { MediaProcessor.getBlobFile(context, blobHash) }
    val isComplete = (manifest?.isComplete == true) || (blobFile.exists() && blobFile.length() > 0)

    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, blobHash, isComplete) {
        value = if (isComplete) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                MediaProcessor.decodeBlobFile(blobFile)
            }
        } else {
            null
        }
    }

    val thumbBitmap = remember(microPreviewBase64) {
        MediaProcessor.decodeMicroPreview(microPreviewBase64)
    }

    var showLightbox by remember { mutableStateOf(false) }

    if (showLightbox && bitmap != null) {
        FullScreenImageViewerDialog(
            bitmap = bitmap,
            title = "Encrypted photo",
            onDismiss = { showLightbox = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isOutgoing) Color.White.copy(alpha = 0.1f) else FbSurface)
            .clickable {
                if (isComplete && bitmap != null) {
                    showLightbox = true
                } else if (!isComplete) {
                    viewModel.prioritizeBlob(blobHash)
                }
            }
    ) {
        val resolvedBitmap = bitmap
        if (resolvedBitmap != null) {
            Image(
                bitmap = resolvedBitmap.asImageBitmap(),
                contentDescription = "Encrypted photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 260.dp)
            )
        } else if (thumbBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = thumbBitmap.asImageBitmap(),
                    contentDescription = "Photo thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = FacebookBlue,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
fun AudioWaveformBars(
    progress: Float,
    isOutgoing: Boolean,
    isPlaying: Boolean,
    blobHash: String,
    modifier: Modifier = Modifier
) {
    val barCount = 22
    val heights = remember(blobHash) {
        val hash = blobHash.hashCode().absoluteValue
        val basePattern = listOf(7, 12, 18, 9, 15, 22, 16, 11, 20, 13, 24, 11, 17, 20, 14, 9, 13, 19, 10, 15, 9, 6)
        List(barCount) { i ->
            val variation = ((hash shr (i % 16)) and 0x7) - 3 // -3 to +4
            (basePattern[i % basePattern.size] + variation).coerceIn(6, 24).dp
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { index, height ->
            val barFraction = (index + 1).toFloat() / barCount.toFloat()
            val isFilled = barFraction <= progress

            val barColor = if (isOutgoing) {
                if (isFilled) Color.White else Color.White.copy(alpha = 0.35f)
            } else {
                if (isFilled) FacebookBlue else FbTextTertiary.copy(alpha = 0.4f)
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun DirectMessageAudioView(
    messageId: String,
    blobHash: String,
    durationMs: Long,
    isOutgoing: Boolean,
    voicePlayer: VoicePlayer,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val manifest by viewModel.observeBlobManifest(blobHash).collectAsState(initial = null)
    val blobFile = remember(blobHash) { MediaProcessor.getBlobFile(context, blobHash) }
    val isAvailable = (manifest?.isComplete == true) || (blobFile.exists() && blobFile.length() > 0)

    val currentPlayingId by voicePlayer.currentPlayingMessageId.collectAsState()
    val isPlaying by voicePlayer.isPlaying.collectAsState()
    val progress by voicePlayer.playbackProgress.collectAsState()
    val currentPositionMs by voicePlayer.currentPositionMs.collectAsState()

    val isThisPlaying = currentPlayingId == messageId && isPlaying
    val isThisActive = currentPlayingId == messageId

    val displayDuration = if (isThisActive && currentPositionMs > 0) currentPositionMs else durationMs
    val seconds = (displayDuration / 1000) % 60
    val minutes = (displayDuration / 60000)
    val timeLabel = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isOutgoing) Color.White.copy(alpha = 0.15f) else FbSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause Circle Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) Color.White else FacebookBlue)
                .clickable {
                    if (isAvailable) {
                        voicePlayer.togglePlay(messageId, blobFile)
                    } else {
                        viewModel.prioritizeBlob(blobHash)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isAvailable) {
                CircularProgressIndicator(
                    color = if (isOutgoing) FacebookBlue else Color.White,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isThisPlaying) "Pause" else "Play",
                    tint = if (isOutgoing) FacebookBlue else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Dynamic Audio Waveform & Timer
        Column(modifier = Modifier.weight(1f)) {
            AudioWaveformBars(
                progress = if (isThisActive) progress else 0f,
                isOutgoing = isOutgoing,
                isPlaying = isThisPlaying,
                blobHash = blobHash,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clickable {
                        if (isAvailable) {
                            voicePlayer.togglePlay(messageId, blobFile)
                        } else {
                            viewModel.prioritizeBlob(blobHash)
                        }
                    }
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeLabel,
                    color = if (isOutgoing) Color.White.copy(alpha = 0.85f) else FbTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (isOutgoing) Color.White.copy(alpha = 0.75f) else FacebookBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Voice",
                        color = if (isOutgoing) Color.White.copy(alpha = 0.75f) else FacebookBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun formatChatDateDivider(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) -> "Today"
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun ChatDateDivider(timestamp: Long) {
    val label = remember(timestamp) { formatChatDateDivider(timestamp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = FbSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.6.dp, FbBorder)
        ) {
            Text(
                text = label,
                color = FbTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}
