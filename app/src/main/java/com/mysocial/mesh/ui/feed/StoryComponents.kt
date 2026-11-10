package com.mysocial.mesh.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mysocial.mesh.model.StoryEntity
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onAddStoryClick() }
                        .padding(horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarView(
                            seed = myAuthorId,
                            displayName = "You",
                            size = 62.dp
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0095F6))
                                .border(2.dp, FbSurface, CircleShape),
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
                        fontWeight = FontWeight.Normal,
                        color = FbTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Other Authors' Stories with iconic Instagram gradient ring
            items(groupedByAuthor.entries.toList(), key = { it.key }) { (authorId, authorStories) ->
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

    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    val currentStory = stories.getOrNull(currentIndex) ?: return
    var progress by remember(currentIndex) { mutableFloatStateOf(0f) }

    LaunchedEffect(currentStory.storyId) {
        onStoryViewed(currentStory.storyId)
    }

    LaunchedEffect(currentIndex) {
        progress = 0f
        val stepMs = 50L
        val totalMs = 5000L
        val increment = stepMs.toFloat() / totalMs
        while (progress < 1f) {
            delay(stepMs)
            progress += increment
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
            // Story Text content in center
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

            // Top Header: progress bar, avatar, author name, close button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
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
    onPublish: (String) -> Unit
) {
    var storyText by remember { mutableStateOf("") }

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
                OutlinedTextField(
                    value = storyText,
                    onValueChange = { storyText = it },
                    placeholder = { Text("What's happening right now?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FacebookBlue,
                        unfocusedBorderColor = FbBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (storyText.isNotBlank()) {
                        onPublish(storyText)
                        onDismiss()
                    }
                },
                enabled = storyText.isNotBlank(),
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

