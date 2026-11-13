package com.mysocial.mesh.ui.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.StoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import kotlinx.coroutines.delay

@Composable
fun StoryCarouselView(
    stories: List<StoryEntity>,
    myAuthorId: String,
    onAddStoryClick: () -> Unit,
    onStoryClick: (List<StoryEntity>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Group stories by authorId
    val groupedByAuthor = remember(stories) {
        stories.groupBy { it.authorId }
    }
    val myStories = remember(groupedByAuthor, myAuthorId) {
        groupedByAuthor[myAuthorId] ?: emptyList()
    }
    val otherAuthors = remember(groupedByAuthor, myAuthorId) {
        groupedByAuthor.filter { it.key != myAuthorId }.entries.toList()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FbSurface)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Your Story" with Instagram-style avatar and + badge
            item {
                val hasMyStories = myStories.isNotEmpty()
                val allMyStoriesViewed = hasMyStories && myStories.all { it.isViewed }
                val myBorder = if (!hasMyStories) {
                    null
                } else if (allMyStoriesViewed) {
                    Brush.linearGradient(listOf(FbBorder, FbBorder))
                } else {
                    com.mysocial.mesh.ui.theme.InstagramStoryGradient
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (hasMyStories) {
                                onStoryClick(myStories, 0)
                            } else {
                                onAddStoryClick()
                            }
                        }
                        .padding(horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarBoxModifier = if (myBorder != null) {
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(myBorder)
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(FbSurface)
                                .padding(2.5.dp)
                        } else {
                            Modifier.size(68.dp)
                        }

                        Box(
                            modifier = avatarBoxModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarView(
                                seed = myAuthorId,
                                displayName = "You",
                                size = if (myBorder != null) 56.dp else 62.dp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0095F6))
                                .border(2.dp, FbSurface, CircleShape)
                                .clickable { onAddStoryClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Story",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Your story",
                        fontSize = 11.5.sp,
                        fontWeight = if (hasMyStories && !allMyStoriesViewed) FontWeight.Medium else FontWeight.Normal,
                        color = FbTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Other Authors' Stories with iconic Instagram gradient ring
            items(otherAuthors, key = { it.key }) { (authorId, authorStories) ->
                val allViewed = authorStories.all { it.isViewed }
                val latestStory = authorStories.last()
                val storyBorder = if (allViewed) {
                    Brush.linearGradient(listOf(FbBorder, FbBorder))
                } else {
                    com.mysocial.mesh.ui.theme.InstagramStoryGradient
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onStoryClick(authorStories, 0) }
                        .padding(horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(storyBorder)
                            .padding(2.5.dp)
                            .clip(CircleShape)
                            .background(FbSurface)
                            .padding(2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarView(
                            seed = authorId,
                            displayName = latestStory.authorDisplayName,
                            size = 56.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = latestStory.authorDisplayName,
                        fontSize = 11.5.sp,
                        fontWeight = if (allViewed) FontWeight.Normal else FontWeight.Medium,
                        color = FbTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
    }
}

@Composable
fun FullscreenStoryViewerDialog(
    stories: List<StoryEntity>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onStoryViewed: (String) -> Unit
) {
    if (stories.isEmpty()) return

    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    val currentStory = stories.getOrNull(currentIndex) ?: return
    var progress by remember(currentIndex) { mutableFloatStateOf(0f) }

    LaunchedEffect(currentStory.storyId) {
        onStoryViewed(currentStory.storyId)
    }

    // Load media blob if story has a photo
    val storyBlobHash = currentStory.mediaBlobHash
    val db = remember(context) { MeshDatabase.getInstance(context) }
    val manifest by if (storyBlobHash != null) {
        db.blobDao().observeManifest(storyBlobHash).collectAsState(initial = null)
    } else remember { mutableStateOf(null) }

    val isComplete = manifest?.isComplete == true
    val localFile = remember(manifest?.localFilePath, isComplete, storyBlobHash) {
        if (storyBlobHash != null) {
            manifest?.localFilePath?.let { File(it) } ?: MediaProcessor.getBlobFile(context, storyBlobHash)
        } else null
    }

    val storyBitmap by produceState<Bitmap?>(initialValue = null, localFile, isComplete) {
        value = if (localFile != null && localFile.exists()) {
            withContext(Dispatchers.IO) {
                MediaProcessor.decodeBlobFile(localFile)
            }
        } else null
    }

    LaunchedEffect(storyBlobHash, isComplete) {
        if (storyBlobHash != null && !isComplete) {
            db.onBlobDiscovered?.invoke(storyBlobHash)
        }
    }

    LaunchedEffect(currentIndex, isComplete, storyBlobHash) {
        progress = 0f
        val stepMs = 50L
        val totalMs = 5000L
        val increment = stepMs.toFloat() / totalMs
        var waitPhotoMs = 0L
        val maxWaitPhotoMs = 15000L
        while (progress < 1f) {
            delay(stepMs)
            val isWaitingForPhoto = storyBlobHash != null && !isComplete && waitPhotoMs < maxWaitPhotoMs
            if (isWaitingForPhoto) {
                waitPhotoMs += stepMs
            } else {
                progress += increment
            }
        }
        if (currentIndex < stories.size - 1) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    if (currentIndex < stories.size - 1) {
                        currentIndex++
                    } else {
                        onDismiss()
                    }
                }
        ) {
            // Full-screen Story Image if present
            if (storyBitmap != null) {
                Image(
                    bitmap = storyBitmap!!.asImageBitmap(),
                    contentDescription = "Story photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (storyBlobHash != null && !isComplete) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Downloading story photo...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
            }

            // Story Text Content (Centered if text-only, bottom overlay if photo/loading)
            if (currentStory.text.isNotBlank()) {
                if (storyBitmap != null || (storyBlobHash != null && !isComplete)) {
                    // Bottom Caption Pill on Photo or loading state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp, start = 20.dp, end = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentStory.text,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Text-Only Story Center Canvas
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentStory.text,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 30.sp
                        )
                    }
                }
            }

            // Top Header: progress bar, avatar, author name, close button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                // Segmented progress bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { index, _ ->
                        val barProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { barProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(
                        seed = currentStory.authorId,
                        displayName = currentStory.authorDisplayName,
                        size = 36.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentStory.authorDisplayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Disappearing in 24h",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateStoryDialog(
    onDismiss: () -> Unit,
    onPublish: (text: String, imageUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    var storyText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create 24-Hour Story",
                fontWeight = FontWeight.Bold,
                color = FbTextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Stories disappear from the local mesh automatically after 24 hours.",
                    fontSize = 13.sp,
                    color = FbTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Optional Photo Preview if selected
                if (previewBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "Story photo preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { photoPickerLauncher.launch("image/*") }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Change", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    // Add Photo Button Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FbSurface)
                            .border(0.5.dp, FbBorder, RoundedCornerShape(10.dp))
                            .clickable { photoPickerLauncher.launch("image/*") }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Add Photo",
                            tint = Color(0xFF45BD62),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Add Photo to Story",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FbTextPrimary
                            )
                            Text(
                                text = "Pick a photo from your gallery",
                                fontSize = 11.sp,
                                color = FbTextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = storyText,
                    onValueChange = { storyText = it },
                    placeholder = {
                        Text(
                            text = if (selectedImageUri != null) "Add a caption (optional)..." else "What's happening right now?",
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FacebookBlue,
                        unfocusedBorderColor = FbBorder
                    )
                )
            }
        },
        confirmButton = {
            val canShare = storyText.isNotBlank() || selectedImageUri != null
            Button(
                onClick = {
                    if (canShare) {
                        onPublish(storyText, selectedImageUri)
                        onDismiss()
                    }
                },
                enabled = canShare,
                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Share Story", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FbTextSecondary)
            }
        },
        containerColor = FbSurfaceElevated
    )
}

