package com.mysocial.mesh.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.CanonicalJson
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import com.mysocial.mesh.utils.toFormattedBbid
import com.mysocial.mesh.model.PostWithAuthor
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCard(
    postWithAuthor: PostWithAuthor,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    onPostClick: (() -> Unit)? = null,
    onEditClick: ((entryHash: String, newText: String) -> Unit)? = null,
    onDeleteClick: ((entryHash: String) -> Unit)? = null,
    onShareClick: ((postText: String) -> Unit)? = null,
    onPrioritizeDownload: ((blobHash: String) -> Unit)? = null,
    onVotePoll: ((targetHash: String, optionIndex: Int) -> Unit)? = null,
    onTagClick: ((tag: String) -> Unit)? = null,
    currentAuthorId: String? = null,
    isAuthorOnline: Boolean = false,
    isFriend: Boolean = false,
    onToggleFriend: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember(context) { MeshDatabase.getInstance(context) }
    val post = postWithAuthor.post
    val profile = postWithAuthor.authorProfile

    val displayName = profile?.displayName ?: "Neighbor"
    val authorId = post.authorId
    val isMyPost = currentAuthorId != null && authorId == currentAuthorId

    var isLiked by remember { mutableStateOf(false) }
    val likeCount = if (isLiked) maxOf(post.likeCount, 1) else post.likeCount

    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editText by remember(post.text) { mutableStateOf(post.text) }

    val triggerShare: () -> Unit = {
        if (onShareClick != null) {
            onShareClick(post.text)
        } else {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "${post.text}\n\n— posted by $displayName on MySocial")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share post via"))
        }
    }

    val likeTint by animateColorAsState(
        targetValue = if (isLiked || post.likeCount > 0) FacebookBlue else FbTextSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "likeTint"
    )

    val tags: List<String> = remember(post.tagsJson) {
        try {
            CanonicalJson.decodeFromString<List<String>>(post.tagsJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (post.isSos) {
                    Modifier.border(2.dp, Color(0xFFD32F2F), RoundedCornerShape(8.dp))
                } else Modifier
            )
            .then(
                if (onPostClick != null) {
                    Modifier.clickable { onPostClick() }
                } else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(top = if (post.isSos) 0.dp else 12.dp, bottom = 6.dp)) {
            // SOS High-Priority Alert Banner
            if (post.isSos) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFEBEE))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "SOS Alert",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🚨 SOS / URGENT LOCAL BROADCAST",
                        color = Color(0xFFD32F2F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Header Row: Avatar, Name, Timestamp, Audience Badge, More button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(
                    seed = authorId,
                    displayName = displayName,
                    size = 42.dp,
                    showOnlineBadge = isAuthorOnline
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = displayName,
                            color = FbTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (postWithAuthor.isAuthorVerifiedInPerson) {
                            VerifiedShieldBadge(size = 15.dp)
                        } else if (isFriend) {
                            VerifiedBadge(size = 15.dp)
                        }
                        Text(
                            text = authorId.toFormattedBbid(),
                            color = FbTextSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(FbSurfaceElevated)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatRelativeTime(post.timestamp),
                            color = FbTextSecondary,
                            fontSize = 12.sp
                        )
                        Text(text = "•", color = FbTextTertiary, fontSize = 10.sp)
                        HopBadge(hopCount = post.hopCount, transportSource = post.transportSource)
                        if (post.expiresAt != null) {
                            Text(text = "•", color = FbTextTertiary, fontSize = 10.sp)
                            Text(
                                text = formatTtlRemaining(post.expiresAt),
                                color = if (post.expiresAt - System.currentTimeMillis() < 3600_000L) Color(0xFFD32F2F) else FbTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(text = "•", color = FbTextTertiary, fontSize = 10.sp)
                        Icon(
                            imageVector = if (post.isPublic) Icons.Filled.Public else Icons.Filled.Group,
                            contentDescription = if (post.isPublic) "Public" else "Friends only",
                            tint = FbTextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (post.isPublic) "Public" else "Friends",
                            color = FbTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // 3 Dots Menu Button
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "Options",
                            tint = FbTextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (isMyPost) {
                            DropdownMenuItem(
                                text = { Text("Edit post", color = FbTextPrimary) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Edit, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    editText = post.text
                                    showEditDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Delete post", color = FbRed) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = FbRed, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        } else if (isFriend && onToggleFriend != null) {
                            DropdownMenuItem(
                                text = { Text("Remove Friend", color = FbTextSecondary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.PersonRemove,
                                        contentDescription = null,
                                        tint = FbTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleFriend()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Share post", color = FbTextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, contentDescription = null, tint = FbTextSecondary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                triggerShare()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Copy text", color = FbTextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = FbTextSecondary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PostText", post.text))
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Post content
            Text(
                text = post.text,
                color = FbTextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            // Media attachment (Chunked Blob with micro-preview and crisp image transition)
            if (post.mediaBlobHash != null) {
                Spacer(modifier = Modifier.height(10.dp))
                PostMediaAttachmentView(
                    blobHash = post.mediaBlobHash,
                    chunkCount = post.mediaChunkCount ?: 1,
                    microPreviewBase64 = post.microPreviewBase64,
                    onPrioritizeDownload = { onPrioritizeDownload?.invoke(post.mediaBlobHash) },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }

            // 1-Tap Local Polls
            if (!post.pollOptionsJson.isNullOrBlank()) {
                val pollOptions: List<String> = remember(post.pollOptionsJson) {
                    try {
                        CanonicalJson.decodeFromString<List<String>>(post.pollOptionsJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                if (pollOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val allPollReactions by db.reactionDao().observePollVotesForPost(post.entryHash).collectAsState(initial = emptyList())
                    val activeVotes = remember(allPollReactions) {
                        allPollReactions.sortedBy { it.timestamp }.associate { it.authorId to it.pollOptionIndex }
                    }
                    val totalVotes = activeVotes.size
                    val myVotedIndex = currentAuthorId?.let { activeVotes[it] }
                    val voteCounts = remember(activeVotes) {
                        val map = mutableMapOf<Int, Int>()
                        activeVotes.values.filterNotNull().forEach { idx ->
                            map[idx] = (map[idx] ?: 0) + 1
                        }
                        map
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FbSurfaceElevated)
                            .border(1.dp, FbBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pollOptions.forEachIndexed { index, optionText ->
                            val votes = voteCounts[index] ?: 0
                            val percent = if (totalVotes > 0) (votes * 100) / totalVotes else 0
                            val isSelected = myVotedIndex == index

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) FacebookBlue.copy(alpha = 0.12f) else FbSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) FacebookBlue else FbBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onVotePoll?.invoke(post.entryHash, index) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = FacebookBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = optionText,
                                            color = if (isSelected) FacebookBlue else FbTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "$percent% ($votes)",
                                        color = if (isSelected) FacebookBlue else FbTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (totalVotes == 1) "1 vote total" else "$totalVotes votes total",
                            color = FbTextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Hashtags (Facebook Blue style, clickable)
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            color = FacebookBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onTagClick?.invoke(tag) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reaction Stats Row (e.g. 👍 ❤️ 12 • 3 comments)
            if (likeCount > 0 || post.replyCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Likes indicator icons
                    if (likeCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ThumbUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$likeCount",
                                color = FbTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Comments counter
                    if (post.replyCount > 0) {
                        Text(
                            text = "${post.replyCount} ${if (post.replyCount == 1) "comment" else "comments"}",
                            color = FbTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onPostClick?.invoke() ?: onReplyClick() }
                        )
                    }
                }
            }

            // Divider line
            HorizontalDivider(
                color = FbBorder,
                thickness = 0.8.dp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // Facebook Action Bar (Like, Comment, Share)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            isLiked = !isLiked
                            onLikeClick()
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked || post.likeCount > 0) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = likeTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Like",
                        color = likeTint,
                        fontSize = 13.sp,
                        fontWeight = if (isLiked || post.likeCount > 0) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Comment Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onReplyClick() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = FbTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Comment",
                        color = FbTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Share Button (Working with Android system share sheet)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { triggerShare() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = FbTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share",
                        color = FbTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Edit Post Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = FbSurface,
            title = { Text("Edit Post", color = FbTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    placeholder = { Text("What's on your mind?", color = FbTextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FbTextPrimary,
                        unfocusedTextColor = FbTextPrimary,
                        focusedBorderColor = FacebookBlue,
                        unfocusedBorderColor = FbBorder,
                        focusedContainerColor = FbSurface,
                        unfocusedContainerColor = FbSurface
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editText.isNotBlank()) {
                            onEditClick?.invoke(post.entryHash, editText)
                            showEditDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                    enabled = editText.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = FbTextSecondary)
                }
            }
        )
    }

    // Delete Post Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = FbSurface,
            title = { Text("Delete Post?", color = FbTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete this post? It will be removed from your timeline and feed.",
                    color = FbTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClick?.invoke(post.entryHash)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FbRed, contentColor = Color.White),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = FbTextSecondary)
                }
            }
        )
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}

fun formatTtlRemaining(expiresAt: Long): String {
    val diff = expiresAt - System.currentTimeMillis()
    if (diff <= 0) return "⏳ Expired"
    val minutes = (diff / (1000 * 60)) % 60
    val hours = diff / (1000 * 60 * 60)
    return when {
        hours > 0 -> "⏳ ${hours}h left"
        minutes > 0 -> "⏳ ${minutes}m left"
        else -> "⏳ <1m left"
    }
}

/**
 * Renders chunked media blobs in posts.
 * - Incomplete: Shows blurred micro-preview with circular progress percentage and tap to prioritize
 * - Complete: Automatically transitions to crisp full WebP image
 */
@Composable
fun PostMediaAttachmentView(
    blobHash: String,
    chunkCount: Int,
    microPreviewBase64: String?,
    onPrioritizeDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember(context) { MeshDatabase.getInstance(context) }
    val manifest by db.blobDao().observeManifest(blobHash).collectAsState(initial = null)
    val receivedChunks by db.blobDao().observeReceivedChunksCount(blobHash).collectAsState(initial = 0)

    val isComplete = manifest?.isComplete == true
    val localFile = remember(manifest?.localFilePath, isComplete) {
        manifest?.localFilePath?.let { File(it) } ?: MediaProcessor.getBlobFile(context, blobHash)
    }

    val fullBitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, localFile, isComplete) {
        value = if (isComplete && localFile.exists()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                MediaProcessor.decodeBlobFile(localFile)
            }
        } else {
            null
        }
    }

    val thumbBitmap = remember(microPreviewBase64) {
        MediaProcessor.decodeMicroPreview(microPreviewBase64)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, FbBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val resolvedBitmap = fullBitmap
        if (isComplete && resolvedBitmap != null) {
            // Full crisp WebP image
            Image(
                bitmap = resolvedBitmap.asImageBitmap(),
                contentDescription = "Post photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp)
            )
        } else {
            // Incomplete: Blurred micro-preview + circular progress indicator + tap to prioritize
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(FbSurfaceElevated)
                    .clickable { onPrioritizeDownload() },
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap.asImageBitmap(),
                        contentDescription = "Micro preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(16.dp)
                    )
                }

                // Darkened scrim overlay for contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )

                val total = if (chunkCount > 0) chunkCount else (manifest?.chunkCount ?: 1)
                val progress = (receivedChunks.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val percent = (progress * 100).toInt()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            color = FacebookBlue,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiTethering,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap to prioritize download",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

