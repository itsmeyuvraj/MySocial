package com.mysocial.mesh.ui.radar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NearMe
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.transport.DiscoveredPeer
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.PeerCard
import com.mysocial.mesh.ui.components.VerifiedBadge
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RadarScreen(
    viewModel: RadarViewModel,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.discoveredPeers.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()
    val followedAuthorIds by viewModel.followedAuthorIds.collectAsState()
    val verifiedAuthorIds by viewModel.verifiedAuthorIds.collectAsState()
    val myProfile by viewModel.myProfile.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clean Header
            NearbyHeader()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ShareIt-style Animated Radar
                item {
                    ShareItRadarView(
                        myProfile = myProfile,
                        myAuthorId = viewModel.myAuthorId,
                        peers = peers,
                        verifiedAuthorIds = verifiedAuthorIds
                    )
                }

                // Section Title: People Nearby
                item {
                    Text(
                        text = "People Nearby (${peers.size})",
                        color = FbTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (peers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = FbSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No one nearby yet",
                                    color = FbTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "When other phones with MySocial are close by, they will appear on the radar above and connect automatically.",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                } else {
                    items(peers, key = { it.peerId }) { peer ->
                        val isFriend = followedAuthorIds.contains(peer.peerId)
                        val isVerified = verifiedAuthorIds.contains(peer.peerId)
                        PeerCard(
                            peer = peer,
                            isFriend = isFriend,
                            isVerifiedInPerson = isVerified,
                            onToggleFriend = if (isFriend) {
                                { viewModel.toggleFollow(peer.peerId, false) }
                            } else null
                        )
                    }
                }

                // Section: Recent Activity
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

/**
 * Top Header for Nearby Friends tab.
 */
@Composable
fun NearbyHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FbSurface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NearbyPulseIcon()
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Find Friends",
                    color = FbTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Looking for nearby phones",
                    color = FbTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Animated Pulse Icon beside header title.
 */
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

/**
 * ShareIt-style Radar View:
 * Displays concentric sonar rings, continuous rotating sweep beam, expanding ripple pulses,
 * central "You" avatar, and nearby discovered peer nodes plotted dynamically around the radar.
 */
@Composable
fun ShareItRadarView(
    myProfile: ProfileViewEntity?,
    myAuthorId: String,
    peers: List<DiscoveredPeer>,
    verifiedAuthorIds: Set<String>,
    modifier: Modifier = Modifier,
    onPeerClick: (DiscoveredPeer) -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shareit_radar")

    // 1. Radar sweep line rotation (0° to 360°)
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    // 2. Outward expanding sonar ripple wave progress (0f to 1f)
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_wave"
    )

    // 3. Center avatar subtle breathing halo
    val centerHaloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "center_pulse"
    )

    // 4. Floating vertical bobbing for discovered peer avatar nodes
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "peer_float"
    )

    val blueColor = FacebookBlue

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val center = Offset(widthPx / 2f, heightPx / 2f)
            val maxRadiusPx = min(widthPx, heightPx) * 0.44f

            // Radar Canvas: Sonar rings, animated expanding ripples, rotating beam
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Static Concentric Guide Rings
                val ringFractions = listOf(0.35f, 0.65f, 0.98f)
                ringFractions.forEach { frac ->
                    drawCircle(
                        color = blueColor.copy(alpha = 0.12f),
                        radius = maxRadiusPx * frac,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Expanding Sonar Ripple Waves (3 waves with staggered phases)
                val wavePhases = listOf(0f, 0.33f, 0.66f)
                val minWaveRadius = 26.dp.toPx()
                wavePhases.forEach { phase ->
                    val progress = (waveProgress + phase) % 1f
                    val r = minWaveRadius + progress * (maxRadiusPx - minWaveRadius)
                    val alpha = (1f - progress) * 0.35f

                    // Subtle filled wave
                    drawCircle(
                        color = blueColor.copy(alpha = alpha * 0.18f),
                        radius = r,
                        center = center
                    )
                    // Wave border line
                    drawCircle(
                        color = blueColor.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx() * (1f - progress * 0.4f))
                    )
                }

                // Rotating Radar Sweep Arc & Leading Line (ShareIt style)
                val sweepRect = Rect(center - Offset(maxRadiusPx, maxRadiusPx), Size(maxRadiusPx * 2, maxRadiusPx * 2))
                rotate(degrees = sweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.radialGradient(
                            colors = listOf(blueColor.copy(alpha = 0.22f), Color.Transparent),
                            center = center,
                            radius = maxRadiusPx
                        ),
                        startAngle = -50f,
                        sweepAngle = 50f,
                        useCenter = true,
                        topLeft = sweepRect.topLeft,
                        size = sweepRect.size
                    )
                    // Leading radar sweep line
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(blueColor, blueColor.copy(alpha = 0.25f)),
                            start = center,
                            end = center + Offset(maxRadiusPx, 0f)
                        ),
                        start = center,
                        end = center + Offset(maxRadiusPx, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Center Node: "You" Avatar
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .scale(centerHaloScale)
                            .clip(CircleShape)
                            .background(FacebookBlue.copy(alpha = 0.18f))
                    )
                    AvatarView(
                        seed = myProfile?.avatarSeed ?: myAuthorId,
                        displayName = myProfile?.displayName ?: "You",
                        size = 46.dp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    color = FacebookBlue,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "You",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Discovered Peers: Positioned around radar like ShareIt avatar blips
            val maxVisiblePeers = minOf(peers.size, 6)
            peers.take(maxVisiblePeers).forEachIndexed { index, peer ->
                val angleStep = 360f / maxOf(maxVisiblePeers, 1)
                val peerHash = peer.peerId.hashCode().absoluteValue
                // Distribute evenly around circle with deterministic jitter
                val angleDeg = (index * angleStep + (peerHash % 25) - 12f)
                val angleRad = Math.toRadians(angleDeg.toDouble())

                // Distance between 0.52 and 0.82 of maxRadiusPx
                val distanceFraction = 0.52f + ((peerHash % 30) / 100f)
                val distancePx = maxRadiusPx * distanceFraction

                val peerX = center.x + (cos(angleRad) * distancePx).toFloat()
                val peerY = center.y + (sin(angleRad) * distancePx).toFloat()

                val xDp = with(density) { (peerX - 22.dp.toPx()).toDp() }
                val yDp = with(density) { (peerY - 24.dp.toPx() + (if (index % 2 == 0) floatOffset else -floatOffset).dp.toPx()).toDp() }

                val isVerified = verifiedAuthorIds.contains(peer.peerId)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .clickable { onPeerClick(peer) }
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        AvatarView(
                            seed = peer.peerId,
                            displayName = peer.peerName,
                            size = 40.dp,
                            showOnlineBadge = true
                        )
                        if (isVerified) {
                            Box(modifier = Modifier.offset(x = 4.dp, y = (-2).dp)) {
                                VerifiedBadge(size = 14.dp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = FbSurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier.border(0.5.dp, FbBorder, RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = peer.peerName.take(9),
                            color = FbTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Radar Status Pill at Bottom Center of Radar Card
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FbSurfaceElevated.copy(alpha = 0.92f))
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (peers.isNotEmpty()) FbGreen else FacebookBlue)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (peers.isNotEmpty()) {
                        "${peers.size} ${if (peers.size == 1) "phone" else "phones"} nearby"
                    } else {
                        "Scanning for nearby phones..."
                    },
                    color = if (peers.isNotEmpty()) FbGreen else FbTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
                    text = "No recent activity yet. When you share or receive updates, they will appear here.",
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
