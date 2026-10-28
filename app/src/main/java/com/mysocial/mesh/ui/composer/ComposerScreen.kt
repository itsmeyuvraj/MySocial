package com.mysocial.mesh.ui.composer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbRed
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

    val canPublish = !isPublishing && (
        postText.isNotBlank() ||
        selectedImageUri != null ||
        (isPollEnabled && pollOptions.count { it.isNotBlank() } >= 2)
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setSelectedImageUri(uri)
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
            // Facebook Top Bar: "Create Post" + "Post" Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = FbSurface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        Text(
                            text = "Create Post",
                            color = FbTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { viewModel.publishPost(context, onPostPublished) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSos) Color(0xFFD32F2F) else FacebookBlue,
                            contentColor = Color.White,
                            disabledContainerColor = FbBorder,
                            disabledContentColor = FbTextTertiary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        enabled = canPublish
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Post", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // User Profile Header with Audience Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AvatarView(
                        seed = authorId,
                        displayName = displayName,
                        size = 48.dp
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

                        // Audience Selector (Facebook style)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPublic) FbSurfaceElevated else FacebookBlueLight)
                                .border(
                                    1.dp,
                                    if (isPublic) FbBorder else FacebookBlue.copy(alpha = 0.4f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { showAudienceDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isPublic) Icons.Filled.Public else Icons.Filled.Group,
                                contentDescription = null,
                                tint = if (isPublic) FbTextSecondary else FacebookBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPublic) "Public" else "Friends",
                                color = if (isPublic) FbTextSecondary else FacebookBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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

                Spacer(modifier = Modifier.height(12.dp))

                // SOS / Urgent Local Broadcast Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSos) Color(0xFFFFEBEE) else FbSurfaceElevated)
                        .border(
                            1.dp,
                            if (isSos) Color(0xFFD32F2F) else FbBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.setSos(!isSos) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "SOS",
                            tint = if (isSos) Color(0xFFD32F2F) else FbTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SOS / Urgent Local Broadcast",
                                color = if (isSos) Color(0xFFD32F2F) else FbTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Prioritizes delivery across all mesh nodes at top priority",
                                color = if (isSos) Color(0xFFB71C1C) else FbTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = isSos,
                        onCheckedChange = { viewModel.setSos(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFD32F2F),
                            uncheckedThumbColor = FbTextSecondary,
                            uncheckedTrackColor = FbSurfaceElevated
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Ephemeral TTL Chips Row: Forever (Default), 1h, 6h, 24h
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.HourglassEmpty,
                        contentDescription = "Expiry",
                        tint = FbTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Expiry:",
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
                                onClick = { viewModel.setExpiryOption(option) },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Post Content Input
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
                        .height(140.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = FbTextPrimary,
                        unfocusedTextColor = FbTextPrimary
                    )
                )

                // Selected Image Preview
                if (previewBitmap != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, FbBorder, RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "Selected photo preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop
                        )

                        // Remove Image Button
                        IconButton(
                            onClick = { viewModel.setSelectedImageUri(null) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove photo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // "Add to your post" bar with Photo/Video Picker Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { photoPickerLauncher.launch("image/*") },
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = FbSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FbBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selectedImageUri != null) "Change Photo" else "Add Photo to your post",
                            color = FbTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "Add Photo",
                                tint = FbGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // "Add Poll" Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setPollEnabled(!isPollEnabled) },
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (isPollEnabled) FacebookBlueLight else FbSurfaceElevated
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isPollEnabled) FacebookBlue else FbBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Poll,
                                contentDescription = "Add Poll",
                                tint = if (isPollEnabled) FacebookBlue else FbTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isPollEnabled) "Poll Attached (Tap to remove)" else "Create a Local Poll",
                                color = if (isPollEnabled) FacebookBlue else FbTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isPollEnabled) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove Poll",
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Poll Builder Options
                if (isPollEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = FbSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FbBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Poll Options (2 - 4 options)",
                                color = FbTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

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
                                        placeholder = { Text("Option ${index + 1}", fontSize = 13.sp, color = FbTextTertiary) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
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
                                                tint = FbTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (pollOptions.size < 4) {
                                Spacer(modifier = Modifier.height(6.dp))
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Friendly Security & Offline Reassurance Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(FbSurfaceElevated)
                        .border(1.dp, FbBorder, RoundedCornerShape(8.dp))
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
                                text = "Your post stays on your phone and shares automatically with nearby community members without internet.",
                                color = FbTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Success message
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
                                    text = "Anyone nearby on the offline mesh network",
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
                                    text = "Only peers you have added as friends",
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
