package com.mysocial.mesh.ui.radar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.components.PeerCard
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary

import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun RadarScreen(
    viewModel: RadarViewModel,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.discoveredPeers.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()
    val followedAuthorIds by viewModel.followedAuthorIds.collectAsState()
    var showDirectSyncDialog by remember { mutableStateOf(false) }
    var targetIpInput by remember { mutableStateOf("") }

    if (showDirectSyncDialog) {
        AlertDialog(
            onDismissRequest = { showDirectSyncDialog = false },
            title = {
                Text(
                    text = "Direct Connect via IP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = FbTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter the local Wi-Fi IP address or host bridge IP to sync directly with nearby devices:",
                        fontSize = 13.sp,
                        color = FbTextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = targetIpInput,
                        onValueChange = { targetIpInput = it },
                        placeholder = { Text("e.g. 192.168.1.5 or 10.0.2.2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FacebookBlue,
                            cursorColor = FacebookBlue
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ip = targetIpInput.trim()
                        if (ip.isNotBlank()) {
                            viewModel.syncWithDirectIp(ip)
                        }
                        showDirectSyncDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                ) {
                    Text("Connect & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectSyncDialog = false }) {
                    Text("Cancel", color = FbTextSecondary)
                }
            },
            containerColor = FbSurface
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            NearbyHeader(peerCount = peers.size)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section Title: People Nearby
                item {
                    Text(
                        text = "People & Devices Nearby (${peers.size})",
                        color = FbTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (peers.isEmpty()) {
                    item {
                        NearbyScanningCard(onDirectConnectClick = { showDirectSyncDialog = true })
                    }
                } else {
                    itemsIndexed(peers, key = { index, peer -> "${peer.peerId}_$index" }) { _, peer ->
                        val isFriend = followedAuthorIds.contains(peer.peerId)
                        PeerCard(
                            peer = peer,
                            isFriend = isFriend,
                            onToggleFriend = { viewModel.toggleFollow(peer.peerId, !isFriend) }
                        )
                    }
                }

                // Section: Recent Sync Activity
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Activity",
                            color = FbTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    ActivityLogCard(logs = syncLogs)
                }
            }
        }
    }
}

@Composable
fun NearbyHeader(
    peerCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FbSurface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NearbyPulseIcon()
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Find Friends",
                        color = FbTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scanning Bluetooth & Wi-Fi",
                        color = FbTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Real-time Mesh Discovery Status Pill (No manual sync buttons needed)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (peerCount > 0) FbGreen.copy(alpha = 0.12f) else FacebookBlueLight)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (peerCount > 0) FbGreen else FacebookBlue)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (peerCount > 0) "$peerCount Active" else "Radar Active",
                    color = if (peerCount > 0) FbGreen else FacebookBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun NearbyPulseIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "nearby_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nearby_scale"
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(FacebookBlueLight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(FacebookBlue.copy(alpha = 0.2f))
        )
        Icon(
            imageVector = Icons.Filled.NearMe,
            contentDescription = null,
            tint = FacebookBlue,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun NearbyScanningCard(
    onDirectConnectClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                tint = FacebookBlue,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Looking for people nearby...",
                color = FbTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Devices with MySocial open will connect and exchange posts automatically without internet.",
                color = FbTextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onDirectConnectClick,
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Lan, contentDescription = null, modifier = Modifier.size(16.dp), tint = FacebookBlue)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Connect via Direct IP", color = FacebookBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 4.dp,
    thumbColor: Color = FacebookBlue.copy(alpha = 0.5f),
    trackColor: Color = FbBorder.copy(alpha = 0.4f)
): Modifier = drawWithContent {
    drawContent()
    val totalHeight = size.height
    val contentHeight = scrollState.maxValue + scrollState.viewportSize
    if (contentHeight > scrollState.viewportSize && scrollState.viewportSize > 0) {
        val strokeWidth = width.toPx()
        val x = size.width - strokeWidth

        // Draw track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(x, 0f),
            size = Size(strokeWidth, totalHeight),
            cornerRadius = CornerRadius(strokeWidth / 2, strokeWidth / 2)
        )

        // Calculate thumb height and offset
        val thumbHeight = (scrollState.viewportSize.toFloat() / contentHeight * totalHeight).coerceAtLeast(28.dp.toPx())
        val scrollableRange = totalHeight - thumbHeight
        val thumbOffset = (scrollState.value.toFloat() / scrollState.maxValue) * scrollableRange

        // Draw thumb
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(x, thumbOffset),
            size = Size(strokeWidth, thumbHeight),
            cornerRadius = CornerRadius(strokeWidth / 2, strokeWidth / 2)
        )
    }
}

@Composable
fun ActivityLogCard(logs: List<String>) {
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (logs.isEmpty()) {
                Text(
                    text = "No recent transfer activity yet. Updates will appear here automatically.",
                    color = FbTextSecondary,
                    fontSize = 13.sp
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FbBackground)
                        .border(1.dp, FbBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .verticalScrollbar(scrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 8.dp)
                            .verticalScroll(scrollState)
                    ) {
                        logs.forEachIndexed { index, log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (log.contains("Updated")) FbGreen else FacebookBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = log,
                                    color = FbTextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                            if (index < logs.size - 1) {
                                HorizontalDivider(
                                    color = FbBorder.copy(alpha = 0.6f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
