package com.mysocial.mesh.ui.composer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbAmber
import com.mysocial.mesh.ui.theme.FbAmberLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbRedLight
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary

@Composable
fun ComposerScreen(
    viewModel: ComposerViewModel,
    onPostPublished: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val composerMode by viewModel.composerMode.collectAsState()
    val postText by viewModel.postText.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val isPublishing by viewModel.isPublishing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isPublic by viewModel.isPublic.collectAsState()
    val myProfile by viewModel.myProfile.collectAsState(initial = null)
    val expiryOption by viewModel.expiryOption.collectAsState()
    val isSos by viewModel.isSos.collectAsState()
    val isPollEnabled by viewModel.isPollEnabled.collectAsState()
    val pollOptions by viewModel.pollOptions.collectAsState()

    var showAudienceDialog by remember { mutableStateOf(false) }

    val authorId = viewModel.authorId
    val displayName = myProfile?.displayName ?: "You"

    val canPublish = !isPublishing && when (composerMode) {
        ComposerMode.PHOTO -> selectedImageUri != null || postText.isNotBlank()
        ComposerMode.POLL -> postText.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2
        ComposerMode.ALERT -> postText.isNotBlank()
        ComposerMode.POST -> postText.isNotBlank()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setSelectedImageUri(uri)
    }

    // Auto-prompt photo picker when launched into PHOTO mode with no photo yet
    LaunchedEffect(composerMode) {
        if (composerMode == ComposerMode.PHOTO && selectedImageUri == null) {
            photoPickerLauncher.launch("image/*")
        }
    }

    val previewBitmap = remember(selectedImageUri) {
        selectedImageUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbSurface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mode-specific Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (composerMode == ComposerMode.ALERT) FbRedLight else FbSurface
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDismiss != null) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = FbTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // Mode Icon
                        when (composerMode) {
                            ComposerMode.PHOTO -> {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = FbGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            ComposerMode.POLL -> {
                                Icon(
                                    imageVector = Icons.Filled.Poll,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            ComposerMode.ALERT -> {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = FbRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            ComposerMode.POST -> {}
                        }

                        Text(
                            text = when (composerMode) {
                                ComposerMode.PHOTO -> "Share Photo"
                                ComposerMode.POLL -> "Create Poll"
                                ComposerMode.ALERT -> "Emergency Alert"
                                ComposerMode.POST -> "Create Post"
                            },
                            color = if (composerMode == ComposerMode.ALERT) FbRed else FbTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { viewModel.publishPost(context, onPostPublished) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (composerMode == ComposerMode.ALERT || isSos) FbRed else FacebookBlue,
                            contentColor = Color.White,
                            disabledContainerColor = FbBorder,
                            disabledContentColor = FbTextTertiary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = canPublish
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = when (composerMode) {
                                    ComposerMode.PHOTO -> "Share Photo"
                                    ComposerMode.POLL -> "Create Poll"
                                    ComposerMode.ALERT -> "🚨 Broadcast"
                                    ComposerMode.POST -> "Post"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (composerMode) {
                    ComposerMode.PHOTO -> {
                        // ==================== PHOTO WINDOW ====================
                        // User Profile Header with Audience Pill
                        UserProfileHeader(
                            authorId = authorId,
                            displayName = displayName,
                            isPublic = isPublic,
                            onAudienceClick = { showAudienceDialog = true }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Photo Drop/Preview Area
                        if (previewBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, FbBorder, RoundedCornerShape(14.dp))
                            ) {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Selected photo preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp),
                                    contentScale = ContentScale.Crop
                                )

                                // Action Buttons on top of image
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .clickable { photoPickerLauncher.launch("image/*") }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Change",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.setSelectedImageUri(null) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clickable { photoPickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = FbSurfaceElevated),
                                border = BorderStroke(0.5.dp, FbBorder)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(FbGreen.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AddPhotoAlternate,
                                            contentDescription = "Select Photo",
                                            tint = FbGreen,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Tap to choose a photo",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = FbTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Select an image from your gallery",
                                        fontSize = 12.sp,
                                        color = FbTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Caption Input
                        TextField(
                            value = postText,
                            onValueChange = { viewModel.updateText(it) },
                            placeholder = {
                                Text(
                                    text = "Write a caption for this photo...",
                                    color = FbTextTertiary,
                                    fontSize = 15.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = FbSurfaceElevated,
                                unfocusedContainerColor = FbSurfaceElevated,
                                focusedIndicatorColor = FacebookBlue,
                                unfocusedIndicatorColor = FbBorder,
                                focusedTextColor = FbTextPrimary,
                                unfocusedTextColor = FbTextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Expiry Duration Row
                        ExpiryDurationRow(
                            expiryOption = expiryOption,
                            onSelect = { viewModel.setExpiryOption(it) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        PrivacyReassuranceBanner()
                    }

                    ComposerMode.POLL -> {
                        // ==================== POLL WINDOW ====================
                        UserProfileHeader(
                            authorId = authorId,
                            displayName = displayName,
                            isPublic = isPublic,
                            onAudienceClick = { showAudienceDialog = true }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Poll Question Input
                        Text(
                            text = "Poll Question",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = FbTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = postText,
                            onValueChange = { viewModel.updateText(it) },
                            placeholder = {
                                Text(
                                    text = "Ask a question for your poll...",
                                    color = FbTextTertiary,
                                    fontSize = 15.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = FbSurfaceElevated,
                                unfocusedContainerColor = FbSurfaceElevated,
                                focusedIndicatorColor = FacebookBlue,
                                unfocusedIndicatorColor = FbBorder,
                                focusedTextColor = FbTextPrimary,
                                unfocusedTextColor = FbTextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dedicated Poll Options Builder Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = FbSurfaceElevated),
                            border = BorderStroke(0.5.dp, FbBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Poll Options (2 to 4 options)",
                                        color = FbTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${pollOptions.size}/4",
                                        color = FbTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                pollOptions.forEachIndexed { index, optionText ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = optionText,
                                            onValueChange = { viewModel.updatePollOption(index, it) },
                                            placeholder = {
                                                Text(
                                                    "Option ${index + 1}",
                                                    fontSize = 13.sp,
                                                    color = FbTextTertiary
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = FacebookBlue,
                                                unfocusedBorderColor = FbBorder,
                                                focusedTextColor = FbTextPrimary,
                                                unfocusedTextColor = FbTextPrimary
                                            )
                                        )

                                        if (pollOptions.size > 2) {
                                            IconButton(
                                                onClick = { viewModel.removePollOption(index) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Remove option",
                                                    tint = FbRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (pollOptions.size < 4) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { viewModel.addPollOption() },
                                        modifier = Modifier.align(Alignment.Start)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Add option",
                                            tint = FacebookBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Add Option",
                                            color = FacebookBlue,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Poll Duration Row
                        ExpiryDurationRow(
                            title = "Poll active for:",
                            expiryOption = expiryOption,
                            onSelect = { viewModel.setExpiryOption(it) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Poll Helper Info
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(FacebookBlueLight)
                                .border(1.dp, FacebookBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Poll,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Nearby peers can vote directly over the mesh network. Votes are tallied locally in real-time.",
                                    color = FacebookBlue,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    ComposerMode.ALERT -> {
                        // ==================== ALERT WINDOW ====================
                        // Prominent High-Priority Emergency Banner
                        Card(
                            colors = CardDefaults.cardColors(containerColor = FbRedLight),
                            border = BorderStroke(0.5.dp, FbRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Alert",
                                    tint = FbRed,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "HIGH-PRIORITY EMERGENCY BROADCAST",
                                        fontWeight = FontWeight.Bold,
                                        color = FbRed,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "This alert bypasses standard queueing and is pushed instantly to every nearby phone in radio range. Use for emergencies, urgent notices, or local safety hazards.",
                                        color = FbTextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        UserProfileHeader(
                            authorId = authorId,
                            displayName = displayName,
                            isPublic = true,
                            audienceOverrideText = "Public (Emergency)",
                            onAudienceClick = {}
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Emergency Alert Description",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = FbRed
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Emergency Text Field
                        TextField(
                            value = postText,
                            onValueChange = { viewModel.updateText(it) },
                            placeholder = {
                                Text(
                                    text = "Describe the emergency, hazard, or urgent request clearly...",
                                    color = FbTextTertiary,
                                    fontSize = 15.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = FbSurfaceElevated,
                                unfocusedContainerColor = FbSurfaceElevated,
                                focusedIndicatorColor = FbRed,
                                unfocusedIndicatorColor = FbRed.copy(alpha = 0.4f),
                                focusedTextColor = FbTextPrimary,
                                unfocusedTextColor = FbTextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Emergency Hashtags
                        Text(
                            text = "Quick Tags (tap to append):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FbTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("SOS", "EMERGENCY", "URGENT", "HAZARD", "WEATHER", "MEDICAL", "LOST_PET").forEach { tag ->
                                val tagWithHash = "#$tag"
                                FilterChip(
                                    selected = postText.contains(tagWithHash, ignoreCase = true),
                                    onClick = {
                                        if (!postText.contains(tagWithHash, ignoreCase = true)) {
                                            val newText = if (postText.isBlank()) tagWithHash else "$postText $tagWithHash"
                                            viewModel.updateText(newText)
                                            viewModel.addTag(tag)
                                        }
                                    },
                                    label = { Text(tagWithHash, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FbRedLight,
                                        selectedLabelColor = FbRed,
                                        containerColor = FbSurfaceElevated,
                                        labelColor = FbRed
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = FbRed.copy(alpha = 0.4f),
                                        enabled = true,
                                        selected = true
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Alert Duration Row
                        ExpiryDurationRow(
                            title = "Broadcast duration:",
                            expiryOption = expiryOption,
                            onSelect = { viewModel.setExpiryOption(it) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Emergency Offline Reassurance
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(FbAmberLight)
                                .border(1.dp, FbAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = FbAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Alert broadcasts peer-to-peer over Bluetooth & Wi-Fi Direct even when completely off-grid.",
                                    color = FbAmber,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    ComposerMode.POST -> {
                        // ==================== STRICTLY TEXT POST WINDOW ====================
                        UserProfileHeader(
                            authorId = authorId,
                            displayName = displayName,
                            isPublic = isPublic,
                            onAudienceClick = { showAudienceDialog = true }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Text Content Input
                        TextField(
                            value = postText,
                            onValueChange = { viewModel.updateText(it) },
                            placeholder = {
                                Text(
                                    text = "What's on your mind?",
                                    color = FbTextTertiary,
                                    fontSize = 18.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = FbTextPrimary,
                                unfocusedTextColor = FbTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Post duration options row
                        ExpiryDurationRow(
                            expiryOption = expiryOption,
                            onSelect = { viewModel.setExpiryOption(it) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        PrivacyReassuranceBanner()
                    }
                }

                // Status message
                AnimatedVisibility(visible = statusMessage != null) {
                    statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(FbGreen.copy(alpha = 0.1f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FbGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = msg, color = FbGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Audience Selection Modal Dialog
        if (showAudienceDialog) {
            AlertDialog(
                onDismissRequest = { showAudienceDialog = false },
                containerColor = FbSurface,
                title = {
                    Column {
                        Text(
                            text = "Select Audience",
                            color = FbTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Who can see your post?",
                            color = FbTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Option 1: Public
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPublic) FacebookBlueLight else FbBackground)
                                .border(
                                    1.dp,
                                    if (isPublic) FacebookBlue else FbBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.setPublic(true)
                                    showAudienceDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isPublic) FacebookBlue else FbSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = if (isPublic) Color.White else FbTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Public",
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Anyone nearby, even if not added as friends",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (isPublic) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Option 2: Friends Only
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isPublic) FacebookBlueLight else FbBackground)
                                .border(
                                    1.dp,
                                    if (!isPublic) FacebookBlue else FbBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.setPublic(false)
                                    showAudienceDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (!isPublic) FacebookBlue else FbSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = if (!isPublic) Color.White else FbTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Friends Only",
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Only people you have added as friends",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (!isPublic) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudienceDialog = false }) {
                        Text("Done", color = FacebookBlue, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun UserProfileHeader(
    authorId: String,
    displayName: String,
    isPublic: Boolean,
    audienceOverrideText: String? = null,
    onAudienceClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        AvatarView(
            seed = authorId,
            displayName = displayName,
            size = 46.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = displayName,
                color = FbTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (audienceOverrideText != null) FbRedLight else if (isPublic) FbSurfaceElevated else FacebookBlueLight)
                    .border(
                        1.dp,
                        if (audienceOverrideText != null) FbRed.copy(alpha = 0.5f) else if (isPublic) FbBorder else FacebookBlue.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onAudienceClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (audienceOverrideText != null) Icons.Filled.Warning else if (isPublic) Icons.Filled.Public else Icons.Filled.Group,
                    contentDescription = null,
                    tint = if (audienceOverrideText != null) FbRed else if (isPublic) FbTextSecondary else FacebookBlue,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = audienceOverrideText ?: if (isPublic) "Public" else "Friends",
                    color = if (audienceOverrideText != null) FbRed else if (isPublic) FbTextSecondary else FacebookBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (audienceOverrideText == null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Change Audience",
                        tint = if (isPublic) FbTextSecondary else FacebookBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiryDurationRow(
    title: String = "Expires in:",
    expiryOption: ExpiryOption,
    onSelect: (ExpiryOption) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.HourglassEmpty,
            contentDescription = title,
            tint = FbTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = FbTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpiryOption.values().forEach { option ->
                val isSelected = expiryOption == option
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            text = option.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FacebookBlue,
                        selectedLabelColor = Color.White,
                        containerColor = FbSurfaceElevated,
                        labelColor = FbTextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) FacebookBlue else FbBorder,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }
    }
}

@Composable
private fun PrivacyReassuranceBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FbSurfaceElevated)
            .border(1.dp, FbBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = FbGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Saved Privately & Securely",
                    color = FbTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your post stays on your phone and shares directly with people nearby without internet.",
                    color = FbTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
