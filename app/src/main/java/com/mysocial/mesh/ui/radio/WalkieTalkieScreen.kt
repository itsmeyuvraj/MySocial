package com.mysocial.mesh.ui.radio

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mysocial.mesh.ui.theme.AppFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.model.PttVoiceBurstPayload
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary

@Composable
fun WalkieTalkieScreen(
    viewModel: WalkieTalkieViewModel,
    modifier: Modifier = Modifier
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val lastReceivedBurst by viewModel.lastReceivedBurst.collectAsState()
    val peersCount by viewModel.discoveredPeersCount.collectAsState()
    val bursts by viewModel.voiceBursts.collectAsState()

    val haptic = LocalHapticFeedback.current
    var showClearDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FbBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header Bar
            item {
                RadioHeader(
                    peersCount = peersCount,
                    isRecording = isRecording,
                    isPlaying = isPlaying
                )
                HorizontalDivider(color = FbBorder, thickness = 0.5.dp)
            }

            // Live Radio Status Sub-Banner
            item {
                RadioStatusSubBanner(
                    isRecording = isRecording,
                    isPlaying = isPlaying,
                    lastBurst = lastReceivedBurst
                )
            }

            // Big Central PTT Microphone Button Stage
            item {
                Spacer(modifier = Modifier.height(16.dp))
                CentralPttButtonStage(
                    isRecording = isRecording,
                    isPlaying = isPlaying,
                    onStartPtt = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startPtt()
                    },
                    onStopPtt = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.stopPtt()
                    }
                )
            }

            // Live Animated Audio Spectrum Waveform
            item {
                Spacer(modifier = Modifier.height(12.dp))
                AudioVisualizerBars(
                    isActive = isRecording || isPlaying,
                    isRecording = isRecording,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Recent Transmissions Section Header & Actions
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RECENT TRANSMISSIONS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFontFamily,
                            letterSpacing = 0.5.sp,
                            color = FbTextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = FbSurfaceElevated
                        ) {
                            Text(
                                text = "${bursts.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FacebookBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (bursts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = FbRed.copy(alpha = 0.10f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showClearDialog = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All",
                                    tint = FbRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clear All",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFontFamily,
                                    color = FbRed
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (bursts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = FbSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📻",
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ready to Transmit",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFontFamily,
                                color = FbTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No voice transmissions recorded yet.\nHold the mic button above to broadcast instantly to nearby peers.",
                                fontSize = 13.5.sp,
                                fontFamily = AppFontFamily,
                                color = FbTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items = bursts,
                    key = { it.burstId }
                ) { burst ->
                    VoiceBurstItem(
                        burst = burst,
                        isMe = burst.senderAuthorId == viewModel.currentAuthorId,
                        onReplay = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.replayBurst(burst)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Confirmation Dialog to Clear Received Voices
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = {
                    Text(
                        text = "Clear Voice Transmissions?",
                        fontWeight = FontWeight.Bold,
                        color = FbTextPrimary
                    )
                },
                text = {
                    Text(
                        text = "This will remove all recorded and received walkie-talkie voice bursts from your device history.",
                        color = FbTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            viewModel.clearAllVoices()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    ) {
                        Text("Clear All", color = FbRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel", color = FbTextSecondary)
                    }
                },
                containerColor = FbSurface
            )
        }
    }
}

@Composable
private fun RadioHeader(
    peersCount: Int,
    isRecording: Boolean,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FbSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FbRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Radio,
                    contentDescription = null,
                    tint = FbRed,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Walkie-Talkie",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppFontFamily,
                    color = FbTextPrimary
                )
                Text(
                    text = "OFF-GRID PTT RADIO • 16kHz PCM",
                    fontSize = 11.5.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = FbTextSecondary
                )
            }
        }

        // Peers Badge / Dynamic Status
        val statusBg = when {
            isRecording -> FbRed.copy(alpha = 0.15f)
            isPlaying -> FacebookBlue.copy(alpha = 0.15f)
            peersCount > 0 -> Color(0xFF2E7D32).copy(alpha = 0.15f)
            else -> FbSurfaceElevated
        }
        val statusText = when {
            isRecording -> "TRANSMITTING"
            isPlaying -> "RECEIVING"
            peersCount > 0 -> "$peersCount PEERS NEARBY"
            else -> "0 PEERS NEARBY"
        }
        val statusColor = when {
            isRecording -> FbRed
            isPlaying -> FacebookBlue
            peersCount > 0 -> Color(0xFF4CAF50)
            else -> FbTextSecondary
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = statusText,
                    fontSize = 11.5.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun RadioStatusSubBanner(
    isRecording: Boolean,
    isPlaying: Boolean,
    lastBurst: PttVoiceBurstPayload?
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isRecording -> FbRed.copy(alpha = 0.12f)
            isPlaying -> FacebookBlue.copy(alpha = 0.12f)
            else -> FbSurfaceElevated.copy(alpha = 0.5f)
        },
        label = "sub_banner_bg"
    )

    val textColor = when {
        isRecording -> FbRed
        isPlaying -> FacebookBlue
        else -> FbTextSecondary
    }

    val bannerText = when {
        isRecording -> "🎙️ Microphone Live • Transmitting..."
        isPlaying -> "🔊 Speaker Live • Incoming voice from ${lastBurst?.senderDisplayName ?: "Peer"}"
        else -> "⚡ Frequency clear • Ready to talk"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bannerText,
                fontSize = 13.5.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "HALF-DUPLEX",
                fontSize = 11.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = FbTextSecondary
            )
        }
    }
}

@Composable
private fun CentralPttButtonStage(
    isRecording: Boolean,
    isPlaying: Boolean,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording || isPlaying) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val buttonBgColor by animateColorAsState(
        targetValue = when {
            isRecording -> FbRed
            isPlaying -> FacebookBlue
            else -> FbSurfaceElevated
        },
        animationSpec = tween(250),
        label = "btn_bg"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isRecording || isPlaying -> Color.White
            else -> FbRed
        },
        label = "btn_icon_tint"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        // Concentric Expanding Outer Ripples when active
        if (isRecording || isPlaying) {
            val rippleColor = if (isRecording) FbRed else FacebookBlue
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(rippleColor.copy(alpha = 0.12f))
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale * 0.95f)
                    .clip(CircleShape)
                    .background(rippleColor.copy(alpha = 0.22f))
            )
        }

        // Main Heavy Physical-Style PTT Push Button
        Surface(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onStartPtt()
                            tryAwaitRelease()
                            onStopPtt()
                        }
                    )
                },
            shape = CircleShape,
            color = buttonBgColor,
            shadowElevation = if (isRecording) 16.dp else 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = if (isRecording) 4.dp else 2.5.dp,
                brush = Brush.radialGradient(
                    colors = if (isRecording) {
                        listOf(Color.White.copy(alpha = 0.6f), FbRed)
                    } else if (isPlaying) {
                        listOf(Color.White.copy(alpha = 0.6f), FacebookBlue)
                    } else {
                        listOf(FbBorder, FbSurfaceElevated)
                    }
                )
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isPlaying -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = "Push To Talk",
                    tint = iconTint,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isRecording -> "RELEASE\nTO SEND"
                        isPlaying -> "INCOMING\nAUDIO"
                        else -> "HOLD\nTO TALK"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = AppFontFamily,
                    letterSpacing = 1.sp,
                    color = if (isRecording || isPlaying) Color.White else FbTextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun AudioVisualizerBars(
    isActive: Boolean,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = if (isRecording) FbRed else FacebookBlue
    val infiniteTransition = rememberInfiniteTransition(label = "bars")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isActive) 24f else 4f,
        animationSpec = infiniteRepeatable(tween(220, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isActive) 34f else 4f,
        animationSpec = infiniteRepeatable(tween(180, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isActive) 20f else 4f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isActive) 28f else 4f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h4"
    )

    val barHeights = listOf(h1, h2, h3, h4, h2, h1, h3, h4)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        barHeights.forEach { height ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.5.dp)
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) barColor else FbBorder)
            )
        }
    }
}

@Composable
private fun VoiceBurstItem(
    burst: PttVoiceBurstPayload,
    isMe: Boolean,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeAgo = DateUtils.getRelativeTimeSpanString(
        burst.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FbSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, FbBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Initial Avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isMe) FacebookBlue else FbSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = burst.senderDisplayName.take(1).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFontFamily,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isMe) "${burst.senderDisplayName} (You)" else burst.senderDisplayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = FbTextPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = FbRed.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${burst.durationMs / 1000}.${(burst.durationMs % 1000) / 100}s",
                                fontSize = 11.5.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = FbRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$timeAgo • Voice Burst",
                        fontSize = 12.5.sp,
                        fontFamily = AppFontFamily,
                        color = FbTextSecondary
                    )
                }
            }

            // Replay Button Pill
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onReplay() },
                shape = RoundedCornerShape(8.dp),
                color = FacebookBlue.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, FacebookBlue.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Replay",
                        tint = FacebookBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Replay",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFontFamily,
                        color = FacebookBlue
                    )
                }
            }
        }
    }
}
