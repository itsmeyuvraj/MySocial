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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import com.mysocial.mesh.ui.components.SettingsMenuButton
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.style.TextOverflow
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
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import androidx.compose.ui.platform.LocalContext
import com.mysocial.mesh.utils.PeerNotificationHelper

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysocial.mesh.ui.composer.ComposerMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateToCompose: (ComposerMode) -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToChat: (peerId: String, peerName: String) -> Unit = { _, _ -> },
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToMeshSync: () -> Unit = {},
    onNavigateToMuleStorage: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val posts by viewModel.feedPosts.collectAsState()
    val isOpenMeshFilter by viewModel.openMeshFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val activeNearbyPeers by viewModel.activeNearbyPeers.collectAsState()
    val onlineAuthorIds by viewModel.onlineAuthorIds.collectAsState()
    val followedAuthorIds by viewModel.followedAuthorIds.collectAsState()
    val likedPostHashes by viewModel.userLikedPostHashes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val pinnedHashtags by viewModel.pinnedHashtags.collectAsState()
    val mutedHashtags by viewModel.mutedHashtags.collectAsState()
    val unreadMessagesCount by viewModel.unreadMessagesCount.collectAsState()
    val activeStories by viewModel.activeStories.collectAsState()
    val selectedEmergencyChannel by viewModel.selectedEmergencyChannel.collectAsState()
    val compactFeedMode by viewModel.compactFeedMode.collectAsState()
    val showHopBadges by viewModel.showHopBadges.collectAsState()

    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var viewingStoryGroup by remember { mutableStateOf<List<com.mysocial.mesh.model.StoryEntity>?>(null) }

    val context = LocalContext.current
    var selectedPostForComments by remember { mutableStateOf<PostWithAuthor?>(null) }
    var autoFocusCommentInput by remember { mutableStateOf(false) }
    var selectedTagForAction by remember { mutableStateOf<String?>(null) }

    val isOnline = activeNearbyPeers.isNotEmpty()

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
                    onSearchClick = { viewModel.openSearch() },
                    onNavigateToAppearance = onNavigateToAppearance,
                    onNavigateToMeshSync = onNavigateToMeshSync,
                    onNavigateToMuleStorage = onNavigateToMuleStorage,
                    onNavigateToAbout = onNavigateToAbout
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
                    verticalArrangement = Arrangement.spacedBy(if (compactFeedMode) 5.dp else 8.dp)
                ) {
                    if (!isSearchActive) {
                        // Ephemeral Disappearing 24-Hour Stories Carousel
                        item {
                            StoryCarouselView(
                                stories = activeStories,
                                myAuthorId = viewModel.currentAuthorId,
                                onAddStoryClick = { showCreateStoryDialog = true },
                                onStoryClick = { stories, _ -> viewingStoryGroup = stories }
                            )
                        }

                        // "What's on your mind?" Post Creator Box
                        item {
                            WhatsOnYourMindCard(
                                myAuthorId = viewModel.currentAuthorId,
                                onPostClick = { onNavigateToCompose(ComposerMode.POST) },
                                onPhotoClick = { onNavigateToCompose(ComposerMode.PHOTO) },
                                onPollClick = { onNavigateToCompose(ComposerMode.POLL) },
                                onAlertClick = { onNavigateToCompose(ComposerMode.ALERT) },
                                compactMode = compactFeedMode
                            )
                        }

                        // Friendly Feed Filter Tabs (Nearby vs Friends)
                        item {
                            FeedFilterPills(
                                isNearbyFilter = isOpenMeshFilter,
                                onSelectFilter = { viewModel.setFilter(it) },
                                activePeersCount = activeNearbyPeers.size
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
                                    tags = listOf("#offline", "#nearby", "#sos", "#photo", "#friends", "#news"),
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
                                isLiked = postWithAuthor.post.entryHash in likedPostHashes,
                                compactMode = compactFeedMode,
                                showHopBadges = showHopBadges,
                                onReportPost = { entryHash, authorId -> viewModel.reportPost(entryHash = entryHash, authorId = authorId) },
                                onBlockAuthor = { authorId -> viewModel.blockAuthor(authorId) },
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
                },
                onReportComment = { commentHash, authorId ->
                    viewModel.reportPost(commentHash, authorId = authorId)
                },
                onBlockAuthor = { authorId ->
                    viewModel.blockAuthor(authorId)
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
                        text = "#$tag Topic",
                        color = FbTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Follow this topic or choose how photos are downloaded:",
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
                            Text(if (isPinned) "Unpin Topic" else "📌 Pin Topic (Always download photos)")
                        }

                        // Action 3: Mute / Unmute
                        Button(
                            onClick = {
                                if (isMuted) viewModel.unmuteHashtag(tag) else viewModel.muteHashtag(tag)
                                selectedTagForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMuted) FbSurfaceElevated else FbRed,
                                contentColor = if (isMuted) FbTextPrimary else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isMuted) "Unmute Topic" else "🔇 Mute Topic (Don't download photos)")
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

        if (showCreateStoryDialog) {
            CreateStoryDialog(
                onDismiss = { showCreateStoryDialog = false },
                onPublish = { text -> viewModel.publishStory(text) }
            )
        }

        viewingStoryGroup?.let { storyList ->
            FullscreenStoryViewerDialog(
                stories = storyList,
                onDismiss = { viewingStoryGroup = null },
                onStoryViewed = { storyId -> viewModel.markStoryViewed(storyId) }
            )
        }

    }
}

@Composable
fun FacebookTopBar(
    activePeersCount: Int = 0,
    onSearchClick: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToMeshSync: () -> Unit = {},
    onNavigateToMuleStorage: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
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
            // Modern Brand Title with Mesh Nexus Emblem
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiTethering,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "mysocial",
                    color = FacebookBlue,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Online / Offline Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activePeersCount > 0) FbGreen.copy(alpha = 0.12f) else FbSurfaceElevated)
                        .border(
                            0.6.dp,
                            if (activePeersCount > 0) FbGreen.copy(alpha = 0.35f) else FbBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (activePeersCount > 0) FbGreen else FbTextTertiary)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (activePeersCount > 0) "$activePeersCount Online" else "Mesh Ready",
                        color = if (activePeersCount > 0) FbGreen else FbTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Top action buttons (Search & Settings Dropdown Menu Button)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                SettingsMenuButton(
                    onNavigateToAppearance = onNavigateToAppearance,
                    onNavigateToMeshSync = onNavigateToMeshSync,
                    onNavigateToMuleStorage = onNavigateToMuleStorage,
                    onNavigateToAbout = onNavigateToAbout
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
fun WhatsOnYourMindCard(
    myAuthorId: String?,
    onPostClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onPollClick: () -> Unit,
    onAlertClick: () -> Unit,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compactMode) 8.dp else 12.dp, vertical = 2.dp)
            .border(0.8.dp, FbBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compactMode) 8.dp else 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPostClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(
                    seed = myAuthorId ?: "local_user",
                    displayName = "You",
                    size = if (compactMode) 32.dp else 36.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compactMode) 34.dp else 38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(FbSurfaceElevated)
                        .border(0.8.dp, FbBorder, RoundedCornerShape(19.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Share with nearby peers...",
                        color = FbTextSecondary,
                        fontSize = if (compactMode) 13.sp else 13.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compactMode) 6.dp else 8.dp))
            HorizontalDivider(color = FbDivider, thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(if (compactMode) 4.dp else 6.dp))

            // Quick Actions Strip (Photo, Poll, Alert)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Quick Action
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoClick() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = "Photo",
                        tint = FbGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Photo",
                        color = FbTextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Poll Quick Action
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPollClick() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Poll,
                        contentDescription = "Poll",
                        tint = FacebookBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Poll",
                        color = FbTextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Alert Quick Action
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAlertClick() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Alert",
                        tint = FbRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "SOS",
                        color = FbRed,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FeedFilterPills(
    isNearbyFilter: Boolean,
    onSelectFilter: (Boolean) -> Unit,
    activePeersCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = FbSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(0.6.dp, FbBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Nearby Feed Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isNearbyFilter) FbSurface else Color.Transparent)
                    .then(
                        if (isNearbyFilter) Modifier.border(0.8.dp, FacebookBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable { onSelectFilter(true) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
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
                        text = if (activePeersCount > 0) "Nearby ($activePeersCount)" else "Nearby Feed",
                        color = if (isNearbyFilter) FacebookBlue else FbTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isNearbyFilter) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Friends Only Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isNearbyFilter) FbSurface else Color.Transparent)
                    .then(
                        if (!isNearbyFilter) Modifier.border(0.8.dp, FacebookBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable { onSelectFilter(false) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
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
                        fontSize = 12.sp,
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
                    "Share something with people around you! Posts are shared automatically when other phones with MySocial are nearby."
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

