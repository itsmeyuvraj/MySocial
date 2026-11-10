package com.mysocial.mesh.ui.messages

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.model.ConversationSummary
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.VerifiedBadge
import com.mysocial.mesh.ui.components.formatRelativeTime
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import com.mysocial.mesh.utils.toFormattedBbid

import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
import com.mysocial.mesh.model.GroupEntity

@Composable
fun MessagesListScreen(
    viewModel: MessagesViewModel,
    onNavigateBack: () -> Unit,
    onOpenChat: (peerAuthorId: String, peerDisplayName: String) -> Unit,
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsState()
    val availableFriends by viewModel.availableFriends.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Direct, 1 = Groups
    var viewingGroup by remember { mutableStateOf<GroupEntity?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDesc by remember { mutableStateOf("") }
    var joinInviteUri by remember { mutableStateOf("") }

    if (viewingGroup != null) {
        GroupChatScreen(
            group = viewingGroup!!,
            repository = viewModel.meshRepository,
            onNavigateBack = { viewingGroup = null }
        )
        return
    }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            Surface(
                color = FbSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showBackButton) {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = FbTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Messages",
                                        color = FbTextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Encrypted",
                                        tint = FacebookBlue,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = "End-to-end encrypted • Peer-to-peer",
                                    color = FbTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Header Action Buttons for Groups
                            if (selectedTab == 1) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showJoinGroupDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Link,
                                            contentDescription = "Join Group",
                                            tint = FbTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showCreateGroupDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "New Group",
                                            tint = FbTextPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Modern Search Pill matching FeedSearchTopBar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(FbSurfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                        text = if (selectedTab == 0) "Search messages..." else "Search groups...",
                                        color = FbTextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = FbTextPrimary,
                                        fontSize = 14.sp
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setSearchQuery("") },
                                    modifier = Modifier.size(20.dp)
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

                    // Instagram-Style Clean Underline Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FbSurface)
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.clickable { selectedTab = 0 },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Direct",
                                fontSize = 14.5.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) FbTextPrimary else FbTextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (selectedTab == 0) FbTextPrimary else Color.Transparent)
                            )
                        }

                        Column(
                            modifier = Modifier.clickable { selectedTab = 1 },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (groups.isNotEmpty()) "Groups (${groups.size})" else "Groups",
                                fontSize = 14.5.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) FbTextPrimary else FbTextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (selectedTab == 1) FbTextPrimary else Color.Transparent)
                            )
                        }
                    }
                    HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                }
            }
        },
        containerColor = FbBackground
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedTab == 0) {
                if (conversations.isEmpty() && availableFriends.isEmpty()) {
                    // Empty state (no friends, no chats)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(FacebookBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ChatBubbleOutline,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No private chats yet",
                            color = FbTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Messages are encrypted directly between you and your friend's phone over the mesh. Add friends by scanning their QR code on the Profile tab or finding neighbors on the Radar tab to start chatting.",
                            color = FbTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    val conversationPeerIds = remember(conversations) {
                        conversations.map { it.peerId }.toSet()
                    }
                    val friendsNotInConversations = remember(availableFriends, conversationPeerIds) {
                        availableFriends.filter { !conversationPeerIds.contains(it.authorId) }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Instagram-style Horizontal Friends Tray at top
                        if (availableFriends.isNotEmpty()) {
                            item(key = "friends_tray_section") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(FbSurface)
                                        .padding(vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Friends",
                                            color = FbTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${availableFriends.size}",
                                            color = FbTextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(availableFriends, key = { "tray_${it.authorId}" }) { friend ->
                                            FriendDirectTrayItem(
                                                friend = friend,
                                                onClick = {
                                                    onOpenChat(friend.authorId, friend.displayName)
                                                }
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                            }
                        }

                        // Conversations list
                        if (conversations.isNotEmpty()) {
                            item(key = "conversations_header") {
                                Text(
                                    text = "Conversations",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(conversations, key = { it.peerId }) { conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    onClick = {
                                        onOpenChat(conversation.peerId, conversation.peerDisplayName ?: "Neighbor")
                                    }
                                )
                                HorizontalDivider(
                                    color = FbDivider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 76.dp)
                                )
                            }
                        }

                        // Friends list: When no conversations exist, list all friends; otherwise list friends not yet in conversations
                        val friendsToList = if (conversations.isEmpty()) availableFriends else friendsNotInConversations
                        if (friendsToList.isNotEmpty()) {
                            item(key = "friends_list_header") {
                                Text(
                                    text = if (conversations.isEmpty()) "Friends" else "All Friends",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
                                )
                            }
                            items(friendsToList, key = { "friend_${it.authorId}" }) { friend ->
                                FriendListItem(
                                    friend = friend,
                                    onClick = {
                                        onOpenChat(friend.authorId, friend.displayName)
                                    }
                                )
                                HorizontalDivider(
                                    color = FbDivider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 76.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                if (groups.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(FacebookBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No mesh group spaces yet",
                            color = FbTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Create a private group space encrypted with AES-256-GCM symmetric sender keys, or join using an invite link.",
                            color = FbTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showCreateGroupDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Group", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { showJoinGroupDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = FbSurfaceElevated, contentColor = FacebookBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Join via Link", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groups, key = { it.groupId }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewingGroup = group }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(FacebookBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Group,
                                        contentDescription = null,
                                        tint = FacebookBlue,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = group.groupName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = FbTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = "Encrypted",
                                            tint = FacebookBlue,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = if (group.description.isNotBlank()) group.description else "Encrypted private group space",
                                        fontSize = 13.sp,
                                        color = FbTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = FbDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 76.dp)
                            )
                        }
                    }
                }
            }
        }
    }



    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = {
                Text(
                    text = "Create Group Space",
                    fontWeight = FontWeight.Bold,
                    color = FbTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Group messages are encrypted with a shared AES-256-GCM symmetric sender key across the offline mesh.",
                        fontSize = 13.sp,
                        color = FbTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("Group Name (e.g. Camp Bravo)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupDesc,
                        onValueChange = { newGroupDesc = it },
                        placeholder = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            viewModel.createGroup(newGroupName, newGroupDesc)
                            newGroupName = ""
                            newGroupDesc = ""
                            showCreateGroupDialog = false
                        }
                    },
                    enabled = newGroupName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel", color = FbTextSecondary)
                }
            },
            containerColor = FbSurfaceElevated
        )
    }

    if (showJoinGroupDialog) {
        AlertDialog(
            onDismissRequest = { showJoinGroupDialog = false },
            title = {
                Text(
                    text = "Join Group via Invite Link",
                    fontWeight = FontWeight.Bold,
                    color = FbTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Paste a mysocial://group_invite link shared by a group admin:",
                        fontSize = 13.sp,
                        color = FbTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = joinInviteUri,
                        onValueChange = { joinInviteUri = it },
                        placeholder = { Text("mysocial://group_invite?gid=...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (joinInviteUri.isNotBlank()) {
                            viewModel.joinGroupFromInvite(joinInviteUri)
                            joinInviteUri = ""
                            showJoinGroupDialog = false
                        }
                    },
                    enabled = joinInviteUri.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Join Group", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinGroupDialog = false }) {
                    Text("Cancel", color = FbTextSecondary)
                }
            },
            containerColor = FbSurfaceElevated
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = conversation.peerDisplayName ?: "Neighbor"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            seed = conversation.peerAvatarSeed ?: conversation.peerId,
            displayName = displayName,
            size = 52.dp,
            showOnlineBadge = conversation.isPeerOnline
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = displayName,
                        color = FbTextPrimary,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (conversation.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 14.dp)
                    }
                }

                Text(
                    text = formatRelativeTime(conversation.lastMessageTimestamp),
                    color = if (conversation.unreadCount > 0) FacebookBlue else FbTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessageText,
                    color = if (conversation.unreadCount > 0) FbTextPrimary else FbTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(FacebookBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${conversation.unreadCount}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendListItem(
    friend: ProfileViewEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            seed = friend.avatarSeed,
            displayName = friend.displayName,
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = friend.displayName,
                    color = FbTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (friend.isVerifiedInPerson) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${friend.authorId.toFormattedBbid()} • Tap to chat",
                color = FbTextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Filled.ChatBubbleOutline,
            contentDescription = "Message",
            tint = FacebookBlue,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun FriendDirectTrayItem(
    friend: ProfileViewEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(62.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AvatarView(
                seed = friend.avatarSeed,
                displayName = friend.displayName,
                size = 52.dp
            )
            if (friend.isVerifiedInPerson) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(FbSurface, CircleShape)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VerifiedBadge(size = 14.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = friend.displayName,
            color = FbTextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
