package com.mysocial.mesh.ui.components

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.model.PostWithAuthor
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Facebook-style Post Details & Comments Modal Bottom Sheet.
 * Displays the original post summary, a reactive scrollable thread of all comments,
 * and an always-visible sticky comment composer at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCommentsSheet(
    postWithAuthor: PostWithAuthor,
    comments: List<PostWithAuthor>,
    currentAuthorId: String,
    onlineAuthorIds: Set<String>,
    followedAuthorIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSendComment: (text: String) -> Unit,
    onLikePost: (() -> Unit)? = null,
    onLikeComment: ((commentHash: String) -> Unit)? = null,
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var commentText by remember { mutableStateOf("") }
    val post = postWithAuthor.post
    val postAuthor = postWithAuthor.authorProfile?.displayName ?: "Neighbor"

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(250)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FbSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = FbBorder,
                height = 4.dp,
                width = 36.dp
            )
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header Bar: "Comments", count badge, close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Comments",
                        color = FbTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (comments.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(FacebookBlueLight)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${comments.size}",
                                color = FacebookBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = FbTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = FbBorder, thickness = 0.8.dp)

            // Scrollable Content: Post Context Preview + Comments Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Post Summary Card
                item(key = "post_summary") {
                    PostContextSummaryCard(
                        postWithAuthor = postWithAuthor,
                        isOnline = onlineAuthorIds.contains(post.authorId) || post.authorId == currentAuthorId,
                        isFriend = followedAuthorIds.contains(post.authorId) || post.authorId == currentAuthorId,
                        onLikePost = onLikePost
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = FbBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
                }

                // Empty State if no comments
                if (comments.isEmpty()) {
                    item(key = "empty_comments") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No comments yet",
                                color = FbTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Be the first to share your thoughts with your neighbors.",
                                color = FbTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.post.entryHash }) { commentItem ->
                        CommentBubbleItem(
                            commentWithAuthor = commentItem,
                            isOnline = onlineAuthorIds.contains(commentItem.post.authorId) || commentItem.post.authorId == currentAuthorId,
                            isFriend = followedAuthorIds.contains(commentItem.post.authorId) || commentItem.post.authorId == currentAuthorId,
                            onLike = { onLikeComment?.invoke(commentItem.post.entryHash) }
                        )
                    }
                }
            }

            HorizontalDivider(color = FbBorder, thickness = 0.8.dp)

            // Sticky Bottom Comment Input Bar
            Surface(
                color = FbSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(
                        seed = currentAuthorId,
                        displayName = "You",
                        size = 36.dp,
                        showOnlineBadge = true
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = {
                            Text(
                                text = "Write a comment...",
                                color = FbTextTertiary,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .clip(RoundedCornerShape(22.dp)),
                        shape = RoundedCornerShape(22.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (commentText.isNotBlank()) {
                                    val textToSend = commentText.trim()
                                    onSendComment(textToSend)
                                    commentText = ""
                                    coroutineScope.launch {
                                        if (comments.isNotEmpty()) {
                                            listState.animateScrollToItem(comments.size)
                                        }
                                    }
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FbTextPrimary,
                            unfocusedTextColor = FbTextPrimary,
                            focusedBorderColor = FacebookBlue,
                            unfocusedBorderColor = FbBorder,
                            focusedContainerColor = FbBackground,
                            unfocusedContainerColor = FbBackground
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canSend = commentText.isNotBlank()
                    IconButton(
                        onClick = {
                            if (canSend) {
                                val textToSend = commentText.trim()
                                onSendComment(textToSend)
                                commentText = ""
                                coroutineScope.launch {
                                    if (comments.isNotEmpty()) {
                                        listState.animateScrollToItem(comments.size)
                                    }
                                }
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (canSend) FacebookBlue else FbBorder.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Comment",
                            tint = if (canSend) Color.White else FbTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Facebook-style comment bubble item.
 */
@Composable
private fun CommentBubbleItem(
    commentWithAuthor: PostWithAuthor,
    isOnline: Boolean,
    isFriend: Boolean = false,
    onLike: () -> Unit
) {
    val post = commentWithAuthor.post
    val author = commentWithAuthor.authorProfile?.displayName ?: "Neighbor"
    var isLiked by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        AvatarView(
            seed = post.authorId,
            displayName = author,
            size = 36.dp,
            showOnlineBadge = isOnline
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Rounded comment bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(FbBackground)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = author,
                            color = FbTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (isFriend) {
                            VerifiedBadge(size = 13.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = post.text,
                        color = FbTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Meta row under bubble (Time, Like button, Like counter)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = formatRelativeTime(post.timestamp),
                    color = FbTextTertiary,
                    fontSize = 11.sp
                )

                Text(
                    text = if (isLiked) "Liked" else "Like",
                    color = if (isLiked) FacebookBlue else FbTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        isLiked = !isLiked
                        onLike()
                    }
                )

                if (post.likeCount > 0 || isLiked) {
                    val count = if (isLiked) maxOf(post.likeCount, 1) else post.likeCount
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ThumbUp,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "$count",
                            color = FbTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact summary of the original post shown at the top of the comments sheet.
 */
@Composable
private fun PostContextSummaryCard(
    postWithAuthor: PostWithAuthor,
    isOnline: Boolean,
    isFriend: Boolean = false,
    onLikePost: (() -> Unit)?
) {
    val post = postWithAuthor.post
    val author = postWithAuthor.authorProfile?.displayName ?: "Neighbor"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FbBackground.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AvatarView(
            seed = post.authorId,
            displayName = author,
            size = 38.dp,
            showOnlineBadge = isOnline
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = author,
                        color = FbTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (isFriend) {
                        VerifiedBadge(size = 14.dp)
                    }
                }
                Text(
                    text = formatRelativeTime(post.timestamp),
                    color = FbTextTertiary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = post.text,
                color = FbTextSecondary,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (post.likeCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = null,
                        tint = FacebookBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${post.likeCount}",
                        color = FbTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
