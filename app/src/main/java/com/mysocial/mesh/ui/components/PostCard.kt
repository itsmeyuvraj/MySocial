package com.mysocial.mesh.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbRedLight
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
    onBlockAuthor: ((authorId: String) -> Unit)? = null,
    onReportPost: ((entryHash: String, authorId: String) -> Unit)? = null,
    isLiked: Boolean = false,
    compactMode: Boolean = false,
    showHopBadges: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val post = postWithAuthor.post
    val profile = postWithAuthor.authorProfile

    val displayName = profile?.displayName ?: "Neighbor"
    val authorId = post.authorId
    val isMyPost = currentAuthorId != null && authorId == currentAuthorId

    val likeCount = post.likeCount

    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showSosRadar by remember { mutableStateOf(false) }
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

    val cardCornerRadius = if (compactMode) 12.dp else 16.dp
    val cardHorizontalPadding = if (compactMode) 8.dp else 12.dp
    val cardVerticalPadding = if (compactMode) 3.dp else 5.dp
    val cardTopPadding = if (post.isSos) 0.dp else (if (compactMode) 8.dp else 12.dp)
    val cardBottomPadding = if (compactMode) 8.dp else 12.dp
    val avatarSize = if (compactMode) 36.dp else 42.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = cardHorizontalPadding, vertical = cardVerticalPadding)
            .clip(cardShape)
            .background(FbSurface)
            .border(1.dp, FbBorder, cardShape)
            .then(
                if (onPostClick != null) {
                    Modifier.clickable { onPostClick() }
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cardTopPadding, bottom = cardBottomPadding)
        ) {
            // SOS High-Priority Alert Banner with Collapsible Radar
            if (post.isSos) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FbRedLight)
                        .clickable { showSosRadar = !showSosRadar }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "SOS Alert",
                        tint = FbRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🚨 URGENT LOCAL ALERT",
                        color = FbRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (post.gatewayRelayedAt != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "☁️ Uplinked",
                            color = Color(0xFF16A34A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(FbRed.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (showSosRadar) "🧭 Hide Radar" else "🧭 Find Direction",
                            color = FbRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                AnimatedVisibility(visible = showSosRadar) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        CompassRadarView(senderAuthorId = authorId)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Header Row: Avatar, Name, Timestamp, More Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(
                    seed = authorId,
                    displayName = displayName,
                    size = 36.dp,
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
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isFriend || postWithAuthor.isAuthorVerifiedInPerson) {
                            VerifiedBadge(size = 14.dp)
                        }
                        Text(
                            text = "•",
                            color = FbTextTertiary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = formatRelativeTime(post.timestamp),
                            color = FbTextSecondary,
                            fontSize = 12.sp
                        )
                        if (showHopBadges && post.hopCount > 1) {
                            Text(text = "•", color = FbTextTertiary, fontSize = 10.sp)
                            HopBadge(hopCount = post.hopCount, transportSource = post.transportSource)
                        }
                    }
                }

                // 3 Dots Menu Button
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "Options",
                            tint = FbTextPrimary,
                            modifier = Modifier.size(20.dp)
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

                        if (!isMyPost && onReportPost != null) {
                            DropdownMenuItem(
                                text = { Text("Report post", color = FbRed) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Flag, contentDescription = null, tint = FbRed, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    showReportDialog = true
                                }
                            )
                        }

                        if (!isMyPost && onBlockAuthor != null) {
                            DropdownMenuItem(
                                text = { Text("Block author & drop packets", color = Color(0xFFEF4444)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Block, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onBlockAuthor(authorId)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Media attachment first if available (Instagram signature layout)
            if (post.mediaBlobHash != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, FbBorder, RoundedCornerShape(10.dp))
                ) {
                    PostMediaAttachmentView(
                        blobHash = post.mediaBlobHash,
                        chunkCount = post.mediaChunkCount ?: 1,
                        microPreviewBase64 = post.microPreviewBase64,
                        onPrioritizeDownload = { onPrioritizeDownload?.invoke(post.mediaBlobHash) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Text-only post
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = post.text,
                    color = FbTextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    val pollDb = remember(context) { MeshDatabase.getInstance(context) }
                    val allPollReactions by pollDb.reactionDao().observePollVotesForPost(post.entryHash).collectAsState(initial = emptyList())
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
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(FbSurfaceElevated)
                            .border(0.8.dp, FbBorder, RoundedCornerShape(10.dp))
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF0095F6).copy(alpha = 0.12f) else FbSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF0095F6) else FbBorder,
                                        RoundedCornerShape(8.dp)
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
                                                tint = Color(0xFF0095F6),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = optionText,
                                            color = if (isSelected) Color(0xFF0095F6) else FbTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "$percent% ($votes)",
                                        color = if (isSelected) Color(0xFF0095F6) else FbTextSecondary,
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

            HorizontalDivider(
                color = FbDivider.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )

            // Modern Action Bar (Heart, Comment Bubble, Share Paper Plane, Bookmark)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart / Like Button
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFED4956) else FbTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Comment Button
                IconButton(
                    onClick = onReplyClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = FbTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Share / Direct Send Button
                IconButton(
                    onClick = triggerShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share",
                        tint = FbTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Right Bookmark / Save Icon
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = FbTextPrimary,
                    modifier = Modifier.size(25.dp)
                )
            }

            // Likes Counter (Instagram style: "15 likes")
            if (likeCount > 0) {
                Text(
                    text = if (likeCount == 1) "1 like" else "$likeCount likes",
                    color = FbTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 1.dp)
                )
            }

            // Caption (For photo posts, Instagram renders: username caption)
            if (post.mediaBlobHash != null && post.text.isNotBlank()) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FbTextPrimary)) {
                            append(displayName)
                            append("  ")
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = FbTextPrimary)) {
                            append(post.text)
                        }
                    },
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }

            // Hashtags (Clean Instagram Blue, clickable)
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            color = Color(0xFF0095F6),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { onTagClick?.invoke(tag) }
                                .padding(vertical = 1.dp)
                        )
                    }
                }
            }

            // View all comments preview link
            if (post.replyCount > 0) {
                Text(
                    text = if (post.replyCount == 1) "View 1 comment" else "View all ${post.replyCount} comments",
                    color = FbTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { onPostClick?.invoke() ?: onReplyClick() }
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }

            // Post Relative Timestamp (Instagram uppercase style)
            Text(
                text = formatRelativeTime(post.timestamp).uppercase(),
                color = FbTextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
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

    // Report Post UGC Compliance Dialog
    if (showReportDialog) {
        ReportPostDialog(
            onDismiss = { showReportDialog = false },
            onConfirm = { reason, blockAuthor ->
                showReportDialog = false
                onReportPost?.invoke(post.entryHash, authorId)
                if (blockAuthor && onBlockAuthor != null) {
                    onBlockAuthor(authorId)
                }
                Toast.makeText(context, "Post reported and hidden from your feed", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ReportPostDialog(
    onDismiss: () -> Unit,
    onConfirm: (reason: String, blockAuthor: Boolean) -> Unit
) {
    val reportReasons = listOf(
        "Spam or commercial advertising",
        "Harassment, hate speech, or abuse",
        "Misinformation or impersonation",
        "Inappropriate, sexually explicit, or violent content",
        "Dangerous or illegal activity"
    )
    var selectedReason by remember { mutableStateOf(reportReasons[0]) }
    var alsoBlockAuthor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FbSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = FbRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Report Content", color = FbTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Help maintain a safe community. Reported posts will be immediately hidden from your device and filtered locally.",
                    color = FbTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = FbBorder, thickness = 0.5.dp)

                Text(
                    text = "Why are you reporting this?",
                    color = FbTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                reportReasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = FacebookBlue,
                                unselectedColor = FbTextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reason,
                            color = FbTextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }

                HorizontalDivider(color = FbBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { alsoBlockAuthor = !alsoBlockAuthor }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = alsoBlockAuthor,
                        onCheckedChange = { alsoBlockAuthor = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = FbRed,
                            checkmarkColor = Color.White,
                            uncheckedColor = FbTextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Also block author (drops packets)",
                        color = if (alsoBlockAuthor) FbRed else FbTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (alsoBlockAuthor) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedReason, alsoBlockAuthor) },
                colors = ButtonDefaults.buttonColors(containerColor = FbRed, contentColor = Color.White),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Report & Hide", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FbTextSecondary)
            }
        }
    )
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

    var showLightbox by remember { mutableStateOf(false) }

    if (showLightbox && fullBitmap != null) {
        FullScreenImageViewerDialog(
            bitmap = fullBitmap,
            title = "Post photo",
            onDismiss = { showLightbox = false }
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
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
                    .clickable { showLightbox = true }
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
                            text = "Tap to download photo faster",
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

