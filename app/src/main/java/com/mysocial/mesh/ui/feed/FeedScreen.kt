package com.mysocial.mesh.ui.feed

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.LocalContext
import com.mysocial.mesh.utils.PeerNotificationHelper

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
    val followedAuthorIds by viewModel.followedAuthorIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val pinnedHashtags by viewModel.pinnedHashtags.collectAsState()
    val mutedHashtags by viewModel.mutedHashtags.collectAsState()

    val context = LocalContext.current
    var selectedPostForComments by remember { mutableStateOf<PostWithAuthor?>(null) }
    var autoFocusCommentInput by remember { mutableStateOf(false) }
    var selectedTagForAction by remember { mutableStateOf<String?>(null) }

    val isOnline = activeNearbyPeers.isNotEmpty()
    var wasOnline by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (isOnline && !wasOnline) {
            val peerName = activeNearbyPeers.firstOrNull()?.peerName
            PeerNotificationHelper.notifyPeerNearby(context, peerName)
        }
        wasOnline = isOnline
    }

    BackHandler(enabled = isSearchActive) {
        viewModel.closeSearch()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Facebook Top App Bar OR Search Top Bar
            AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FacebookTopBar(
                    activePeersCount = activeNearbyPeers.size,
                    onSearchClick = { viewModel.openSearch() }
                )
            }
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FeedSearchTopBar(
                    searchQuery = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onCloseSearch = { viewModel.closeSearch() }
                )
            }

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
                    if (!isSearchActive) {
                        // "What's on your mind?" Post Creator Box (Nearby Feed only)
                        if (isOpenMeshFilter) {
                            item {
                                WhatsOnYourMindCard(onClick = onNavigateToCompose)
                            }
                        }

                        // Friendly Feed Filter Tabs (Nearby vs Friends)
                        item {
                            FeedFilterPills(
                                isNearbyFilter = isOpenMeshFilter,
                                onSelectFilter = { viewModel.setFilter(it) }
                            )
                        }

                        // Pinned Hashtag Radio Channels
                        if (pinnedHashtags.isNotEmpty()) {
                            item {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item {
                                        Text(
                                            text = "Channels:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FbTextSecondary
                                        )
                                    }
                                    items(pinnedHashtags.toList()) { tag ->
                                        val isTagActive = searchQuery.equals("#$tag", ignoreCase = true) || searchQuery.equals(tag, ignoreCase = true)
                                        FilterChip(
                                            selected = isTagActive,
                                            onClick = {
                                                if (isTagActive) {
                                                    viewModel.closeSearch()
                                                } else {
                                                    viewModel.openSearch()
                                                    viewModel.setSearchQuery("#$tag")
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = "📌 #$tag",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isTagActive) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = FbSurfaceElevated,
                                                labelColor = FacebookBlue,
                                                selectedContainerColor = FacebookBlueLight,
                                                selectedLabelColor = FacebookBlue
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                borderColor = if (isTagActive) FacebookBlue else FbBorder,
                                                enabled = true,
                                                selected = isTagActive
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // In Search Mode:
                        if (searchQuery.isBlank()) {
                            item {
                                SuggestedTagsRow(
                                    tags = listOf("#mesh", "#offline", "#sos", "#photo", "#crypto", "#news"),
                                    onTagClick = { tag -> viewModel.setSearchQuery(tag) }
                                )
                            }
                        } else {
                            item {
                                SearchFilterHeader(
                                    query = searchQuery,
                                    resultCount = posts.size,
                                    onClear = { viewModel.setSearchQuery("") }
                                )
                            }
                        }

                        // Friendly Feed Filter Tabs (Nearby vs Friends)
                        item {
                            FeedFilterPills(
                                isNearbyFilter = isOpenMeshFilter,
                                onSelectFilter = { viewModel.setFilter(it) }
                            )
                        }

                        // Pinned Hashtag Radio Channels
                        if (pinnedHashtags.isNotEmpty()) {
                            item {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item {
                                        Text(
                                            text = "Channels:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FbTextSecondary
                                        )
                                    }
                                    items(pinnedHashtags.toList()) { tag ->
                                        val isTagActive = searchQuery.equals("#$tag", ignoreCase = true) || searchQuery.equals(tag, ignoreCase = true)
                                        FilterChip(
                                            selected = isTagActive,
                                            onClick = {
                                                if (isTagActive) {
                                                    viewModel.closeSearch()
                                                } else {
                                                    viewModel.openSearch()
                                                    viewModel.setSearchQuery("#$tag")
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = "📌 #$tag",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isTagActive) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = FbSurfaceElevated,
                                                labelColor = FacebookBlue,
                                                selectedContainerColor = FacebookBlueLight,
                                                selectedLabelColor = FacebookBlue
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                borderColor = if (isTagActive) FacebookBlue else FbBorder,
                                                enabled = true,
                                                selected = isTagActive
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Posts or Empty State
                    if (posts.isEmpty()) {
                        item {
                            if (isSearchActive && searchQuery.isNotBlank()) {
                                SearchEmptyState(
                                    query = searchQuery,
                                    onClearClick = { viewModel.setSearchQuery("") }
                                )
                            } else {
                                FeedEmptyState(
                                    isNearby = isOpenMeshFilter
                                )
                            }
                        }
                    } else {
                        items(posts, key = { it.post.entryHash }) { postWithAuthor ->
                            val isOnline = (postWithAuthor.post.authorId == viewModel.currentAuthorId) ||
                                    onlineAuthorIds.contains(postWithAuthor.post.authorId)
                            val isFriend = postWithAuthor.post.authorId != viewModel.currentAuthorId &&
                                    followedAuthorIds.contains(postWithAuthor.post.authorId)
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
                                onVotePoll = { hash, idx -> viewModel.votePoll(hash, idx) },
                                onTagClick = { tag -> selectedTagForAction = tag },
                                currentAuthorId = viewModel.currentAuthorId,
                                isAuthorOnline = isOnline,
                                isFriend = isFriend,
                                onToggleFriend = if (isFriend) {
                                    { viewModel.toggleFollow(postWithAuthor.post.authorId, false) }
                                } else null
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
                followedAuthorIds = followedAuthorIds,
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

        // Hashtag Radio Channel Action Dialog
        if (selectedTagForAction != null) {
            val tag = selectedTagForAction!!
            val isPinned = pinnedHashtags.contains(tag.lowercase())
            val isMuted = mutedHashtags.contains(tag.lowercase())

            AlertDialog(
                onDismissRequest = { selectedTagForAction = null },
                containerColor = FbSurface,
                title = {
                    Text(
                        text = "#$tag Radio Channel",
                        color = FbTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tune in to this channel or customize your mesh media sync rules:",
                            color = FbTextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action 1: Filter Feed
                        Button(
                            onClick = {
                                selectedTagForAction = null
                                viewModel.openSearch()
                                viewModel.setSearchQuery("#$tag")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Filter Feed by #$tag")
                        }

                        // Action 2: Pin / Unpin
                        Button(
                            onClick = {
                                if (isPinned) viewModel.unpinHashtag(tag) else viewModel.pinHashtag(tag)
                                selectedTagForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPinned) FbSurfaceElevated else FbGreen,
                                contentColor = if (isPinned) FbTextPrimary else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPinned) "Unpin Channel" else "📌 Pin Channel (Auto-download photos)")
                        }

                        // Action 3: Mute / Unmute
                        Button(
                            onClick = {
                                if (isMuted) viewModel.unmuteHashtag(tag) else viewModel.muteHashtag(tag)
                                selectedTagForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMuted) FbSurfaceElevated else Color(0xFFD32F2F),
                                contentColor = if (isMuted) FbTextPrimary else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isMuted) "Unmute Channel" else "🔇 Mute Channel (Skip photo downloads)")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedTagForAction = null }) {
                        Text("Close", color = FbTextSecondary)
                    }
                }
            )
        }

    }
}

@Composable
fun FacebookTopBar(
    activePeersCount: Int = 0,
    onSearchClick: () -> Unit = {}
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
                // Online / Offline Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activePeersCount > 0) FbGreen.copy(alpha = 0.12f) else FbSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (activePeersCount > 0) FbGreen else FbTextSecondary)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (activePeersCount > 0) "Online" else "Offline",
                        color = if (activePeersCount > 0) FbGreen else FbTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Top action button (Search)
            IconButton(
                onClick = onSearchClick,
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

@Composable
fun FeedSearchTopBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FbSurface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onCloseSearch()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit search",
                    tint = FbTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(
                    color = FbTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(FacebookBlue),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                    }
                ),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(FbSurfaceElevated)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = FbTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search posts, friends, tags...",
                                    color = FbTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = FbTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(6.dp))
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
                    imageVector = if (isNearby) Icons.Filled.Public else Icons.Filled.Group,
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
                    "Connect with friends by scanning their QR code in the Profile tab.",
                color = FbTextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SuggestedTagsRow(
    tags: List<String>,
    onTagClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Popular Topics & Tags",
                color = FbTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = FbSurfaceElevated,
                        modifier = Modifier.clickable { onTagClick(tag) }
                    ) {
                        Text(
                            text = tag,
                            color = FacebookBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchFilterHeader(
    query: String,
    resultCount: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Results for \"$query\" ($resultCount)",
            color = FbTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Clear",
            color = FacebookBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onClear() }
        )
    }
}

@Composable
fun SearchEmptyState(
    query: String,
    onClearClick: () -> Unit
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
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = FacebookBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "No Posts Found",
                color = FbTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "We couldn't find any posts matching \"$query\". Try searching by different keywords, author names, or hashtags.",
                color = FbTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onClearClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FacebookBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Clear Search", fontWeight = FontWeight.Bold)
            }
        }
    }
}

