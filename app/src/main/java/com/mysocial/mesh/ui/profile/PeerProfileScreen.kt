package com.mysocial.mesh.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.TransportType
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.PostCard
import com.mysocial.mesh.ui.components.VerifiedBadge
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.InstagramStoryGradient
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import com.mysocial.mesh.utils.toFormattedBbid
import kotlinx.coroutines.launch

@Composable
fun PeerProfileScreen(
    peerAuthorId: String,
    peerDisplayName: String,
    repository: MeshRepository,
    onNavigateBack: () -> Unit,
    onOpenChat: (peerAuthorId: String, peerDisplayName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val profile by repository.observeProfile(peerAuthorId).collectAsState(initial = null)
    val posts by repository.observePostsByAuthor(peerAuthorId).collectAsState(initial = emptyList())
    val followedAuthorIds by repository.observeFollowedAuthorIds().collectAsState(initial = emptyList())
    val likedPostHashes by repository.observeUserLikedPostHashes(repository.cryptoManager.authorId).collectAsState(initial = emptySet())
    val discoveredPeers by repository.discoveredPeers.collectAsState()
    val blockedAuthors by repository.observeBlockedAuthors().collectAsState(initial = emptyList())
    val isBlocked = remember(blockedAuthors, peerAuthorId) { blockedAuthors.any { it.authorId == peerAuthorId } }

    val currentPeer = discoveredPeers.find { it.peerId == peerAuthorId }
    val isNearby = currentPeer != null && (System.currentTimeMillis() - currentPeer.lastSeenTimestamp) < 45_000L
    val isFriend = followedAuthorIds.contains(peerAuthorId)
    val isVerified = profile?.isVerifiedInPerson == true

    val resolvedName = profile?.displayName?.takeIf { it.isNotBlank() } ?: peerDisplayName.ifBlank { "Neighbor" }
    val bio = profile?.bio?.takeIf { it.isNotBlank() } ?: "Active neighbor on the local mesh network."

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Instagram-Style Flat Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = FbSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FbTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = resolvedName.lowercase().replace(" ", "_"),
                        color = FbTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isFriend || isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 15.dp)
                    }
                }
            }
            HorizontalDivider(color = FbDivider, thickness = 0.5.dp)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Profile Header (Flat Instagram Layout)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FbSurface)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Avatar + Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(InstagramStoryGradient)
                                    .padding(2.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(FbSurface)
                                        .padding(2.dp)
                                ) {
                                    AvatarView(
                                        seed = peerAuthorId,
                                        displayName = resolvedName,
                                        size = 72.dp,
                                        showOnlineBadge = isNearby
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(28.dp))

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${posts.size}",
                                        color = FbTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "posts", color = FbTextSecondary, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isNearby) "Nearby" else "Mesh",
                                        color = if (isNearby) FbGreen else FbTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "radio", color = FbTextSecondary, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isFriend || isVerified) "Friend" else "Peer",
                                        color = if (isFriend || isVerified) FacebookBlue else FbTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "trust", color = FbTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display Name
                        Text(
                            text = resolvedName,
                            color = FbTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // BBID Pill with Copy Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FbSurfaceElevated)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("BBID", peerAuthorId))
                                    Toast.makeText(context, "BBID copied: ${peerAuthorId.toFormattedBbid()}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = peerAuthorId.toFormattedBbid(),
                                color = FacebookBlue,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy BBID",
                                tint = FacebookBlue,
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Connection Status Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isNearby) FbGreen else FbTextTertiary)
                            )
                            val statusText = if (isNearby) {
                                val trans = when (currentPeer?.transportType) {
                                    TransportType.LORA -> "LoRa 915MHz"
                                    TransportType.WIFI_AWARE -> "Wi-Fi Aware"
                                    TransportType.IOS_COMPANION -> "iOS L2CAP"
                                    TransportType.MDNS_WIFI -> "Local Wi-Fi"
                                    else -> "Bluetooth"
                                }
                                "In Radio Range • $trans"
                            } else {
                                "Connected via Mesh Relay"
                            }
                            Text(
                                text = statusText,
                                color = if (isNearby) FbGreen else FbTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bio
                        Text(
                            text = bio,
                            color = FbTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons Row (Instagram Style)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Follow / Following Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isFriend) FbSurfaceElevated else FacebookBlue)
                                    .border(
                                        0.5.dp,
                                        if (isFriend) FbBorder else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            val newFollowing = !isFriend
                                            repository.toggleFollow(peerAuthorId, following = newFollowing)
                                            val msg = if (newFollowing) "Following $resolvedName" else "Unfollowed $resolvedName"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isFriend) "Following" else "Follow",
                                    color = if (isFriend) FbTextPrimary else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Message Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FbSurfaceElevated)
                                    .border(0.5.dp, FbBorder, RoundedCornerShape(8.dp))
                                    .clickable { onOpenChat(peerAuthorId, resolvedName) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Message",
                                    color = FbTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // BLE Direct Voice Call Button
                            if (isFriend || isVerified) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(FbGreen.copy(alpha = 0.15f))
                                        .border(0.5.dp, FbGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { repository.voiceCallManager?.startCall(peerAuthorId, resolvedName) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = null,
                                            tint = FbGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Call",
                                            color = FbGreen,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Instagram Profile Posts Tab
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FbSurface)
                                .height(44.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = "Posts",
                                tint = FbTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                    }
                }

                // Posts Feed
                if (posts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = null,
                                tint = FbTextTertiary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No posts yet",
                                color = FbTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "When $resolvedName shares posts on the mesh, they'll show up here.",
                                color = FbTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(posts, key = { it.post.entryHash }) { postWithAuthor ->
                        PostCard(
                            postWithAuthor = postWithAuthor,
                            onLikeClick = {
                                coroutineScope.launch {
                                    repository.reactToPost(postWithAuthor.post.entryHash)
                                }
                            },
                            onReplyClick = {},
                            currentAuthorId = repository.cryptoManager.authorId,
                            isAuthorOnline = isNearby,
                            isFriend = isFriend,
                            isLiked = postWithAuthor.post.entryHash in likedPostHashes,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
