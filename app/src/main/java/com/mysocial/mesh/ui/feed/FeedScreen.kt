package com.mysocial.mesh.ui.feed

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.model.PostWithAuthor
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.PostCard
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateToCompose: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val posts by viewModel.feedPosts.collectAsState()
    val isOpenMeshFilter by viewModel.openMeshFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val activeNearbyPeers by viewModel.activeNearbyPeers.collectAsState()
    val onlineAuthorIds by viewModel.onlineAuthorIds.collectAsState()

    var selectedPostForComments by remember { mutableStateOf<PostWithAuthor?>(null) }
    var autoFocusCommentInput by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Facebook Top App Bar
            FacebookTopBar(activePeersCount = activeNearbyPeers.size)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "What's on your mind?" Post Creator Box
                    item {
                        WhatsOnYourMindCard(onClick = onNavigateToCompose)
                    }

                    // Friendly Feed Filter Tabs (Nearby vs Friends)
                    item {
                        FeedFilterPills(
                            isNearbyFilter = isOpenMeshFilter,
                            onSelectFilter = { viewModel.setFilter(it) }
                        )
                    }

                    // Posts or Empty State
                    if (posts.isEmpty()) {
                        item {
                            FeedEmptyState(isNearby = isOpenMeshFilter, onComposeClick = onNavigateToCompose)
                        }
                    } else {
                        items(posts, key = { it.post.entryHash }) { postWithAuthor ->
                            val isOnline = (postWithAuthor.post.authorId == viewModel.currentAuthorId) ||
                                    onlineAuthorIds.contains(postWithAuthor.post.authorId)
                            PostCard(
                                postWithAuthor = postWithAuthor,
                                onLikeClick = { viewModel.likePost(postWithAuthor.post.entryHash) },
                                onReplyClick = {
                                    selectedPostForComments = postWithAuthor
                                    autoFocusCommentInput = true
                                },
                                onPostClick = {
                                    selectedPostForComments = postWithAuthor
                                    autoFocusCommentInput = false
                                },
                                onEditClick = { hash, newText -> viewModel.editPost(hash, newText) },
                                onDeleteClick = { hash -> viewModel.deletePost(hash) },
                                onPrioritizeDownload = { hash -> viewModel.prioritizeBlobDownload(hash) },
                                currentAuthorId = viewModel.currentAuthorId,
                                isAuthorOnline = isOnline
                            )
                        }
                    }
                }
            }
        }

        // Facebook-style Post Details & Comments Bottom Sheet
        if (selectedPostForComments != null) {
            val targetPost = selectedPostForComments!!
            val comments by viewModel.observeReplies(targetPost.post.entryHash).collectAsState(initial = emptyList())

            com.mysocial.mesh.ui.components.PostCommentsSheet(
                postWithAuthor = targetPost,
                comments = comments,
                currentAuthorId = viewModel.currentAuthorId,
                onlineAuthorIds = onlineAuthorIds,
                autoFocus = autoFocusCommentInput,
                onDismiss = {
                    selectedPostForComments = null
                    autoFocusCommentInput = false
                },
                onSendComment = { text ->
                    val root = targetPost.post.rootHash ?: targetPost.post.entryHash
                    viewModel.replyToPost(
                        text = text,
                        rootHash = root,
                        parentHash = targetPost.post.entryHash
                    )
                },
                onLikePost = {
                    viewModel.likePost(targetPost.post.entryHash)
                },
                onLikeComment = { commentHash ->
                    viewModel.likePost(commentHash)
                }
            )
        }
    }
}

@Composable
fun FacebookTopBar(
    activePeersCount: Int = 0
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FbSurface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Facebook-style Brand Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "mysocial",
                    color = FacebookBlue,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Online Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activePeersCount > 0) FbGreen.copy(alpha = 0.12f) else FacebookBlueLight)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (activePeersCount > 0) FbGreen else FacebookBlue)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (activePeersCount > 0) "$activePeersCount Nearby" else "Offline Mesh",
                        color = if (activePeersCount > 0) FbGreen else FacebookBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Top action buttons (Search, etc.)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { /* Search */ },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FbSurfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = FbTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WhatsOnYourMindCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(
                    seed = "local_user",
                    displayName = "You",
                    size = 40.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(FbBackground)
                        .border(1.dp, FbBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "What's on your mind?",
                        color = FbTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            HorizontalDivider(
                color = FbBorder,
                thickness = 0.8.dp,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = FbGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Photo / Post", color = FbTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Nearby", color = FbTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun FeedFilterPills(
    isNearbyFilter: Boolean,
    onSelectFilter: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nearby Feed Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isNearbyFilter) FacebookBlueLight else FbBackground)
                    .clickable { onSelectFilter(true) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = if (isNearbyFilter) FacebookBlue else FbTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Nearby Feed",
                        color = if (isNearbyFilter) FacebookBlue else FbTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isNearbyFilter) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Friends Only Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (!isNearbyFilter) FacebookBlueLight else FbBackground)
                    .clickable { onSelectFilter(false) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = if (!isNearbyFilter) FacebookBlue else FbTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Friends Only",
                        color = if (!isNearbyFilter) FacebookBlue else FbTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (!isNearbyFilter) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FeedEmptyState(
    isNearby: Boolean,
    onComposeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(FacebookBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = FacebookBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isNearby) "No Posts Nearby Yet" else "No Posts from Friends",
                color = FbTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isNearby)
                    "Share something with people around you! Posts sync automatically when devices come close to each other."
                else
                    "Connect with friends by scanning their QR code in the Settings tab.",
                color = FbTextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onComposeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FacebookBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Write a Post", fontWeight = FontWeight.Bold)
            }
        }
    }
}
