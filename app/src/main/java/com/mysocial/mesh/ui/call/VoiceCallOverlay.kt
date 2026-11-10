package com.mysocial.mesh.ui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mysocial.mesh.call.ActiveCallSession
import com.mysocial.mesh.call.CallState
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.VerifiedBadge
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.utils.toFormattedBbid

/**
 * Top-level voice call UI overlay supporting both Incoming Ringing Dialog and Active Call Full-Screen / Sheet.
 */
@Composable
fun VoiceCallOverlay(
    activeSession: ActiveCallSession?,
    incomingSession: ActiveCallSession?,
    durationSec: Int,
    isMuted: Boolean,
    isSpeakerphone: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Incoming Call Dialog (when callee is receiving an incoming call)
    if (incomingSession != null && incomingSession.state == CallState.RINGING) {
        IncomingCallDialog(
            session = incomingSession,
            onAccept = onAccept,
            onDecline = onDecline
        )
    }

    // 2. Active Call UI (when in DIALING, CONNECTING, or IN_CALL)
    if (activeSession != null && activeSession.state != CallState.IDLE && activeSession.state != CallState.ENDED) {
        ActiveCallSheet(
            session = activeSession,
            durationSec = durationSec,
            isMuted = isMuted,
            isSpeakerphone = isSpeakerphone,
            onEndCall = onEndCall,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            modifier = modifier
        )
    }
}

@Composable
fun IncomingCallDialog(
    session: ActiveCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = FbSurface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulsing Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(FbGreen.copy(alpha = 0.25f))
                    )
                    AvatarView(
                        seed = session.peerAuthorId,
                        displayName = session.peerDisplayName,
                        size = 72.dp,
                        showOnlineBadge = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.peerDisplayName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FbTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    VerifiedBadge(size = 18.dp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${session.peerAuthorId.toFormattedBbid()} • Verified Friend",
                    fontSize = 12.sp,
                    color = FbTextSecondary,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FbSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = FacebookBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Incoming Direct BLE Voice Call",
                        fontSize = 12.sp,
                        color = FacebookBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Actions: Decline (Red) & Accept (Green)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(FbRed),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "Decline",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Decline",
                            color = FbTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Accept Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(FbGreen),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Accept",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Accept",
                            color = FbTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallSheet(
    session: ActiveCallSession,
    durationSec: Int,
    isMuted: Boolean,
    isSpeakerphone: Boolean,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationText = String.format(
        java.util.Locale.US,
        "%02d:%02d",
        durationSec / 60,
        durationSec % 60
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = FbSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FbBorder)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Peer info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(
                    seed = session.peerAuthorId,
                    displayName = session.peerDisplayName,
                    size = 52.dp,
                    showOnlineBadge = true
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.peerDisplayName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = FbTextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 15.dp)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val statusString = when (session.state) {
                        CallState.DIALING -> "Calling..."
                        CallState.CONNECTING -> "Connecting BLE L2CAP..."
                        CallState.IN_CALL -> "$durationText • Direct BLE E2EE"
                        else -> "Call ended"
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (session.state == CallState.IN_CALL) FbGreen else FacebookBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusString,
                            fontSize = 13.sp,
                            fontWeight = if (session.state == CallState.IN_CALL) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (session.state == CallState.IN_CALL) FbGreen else FacebookBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // In-Call Controls: Mute, Speaker, Hangup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Button
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) FbRed.copy(alpha = 0.15f) else FbSurfaceElevated)
                        .border(1.dp, if (isMuted) FbRed else FbBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Mute",
                        tint = if (isMuted) FbRed else FbTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // End Call Button (Large Red)
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(FbRed),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Speakerphone Button
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isSpeakerphone) FacebookBlue.copy(alpha = 0.15f) else FbSurfaceElevated)
                        .border(1.dp, if (isSpeakerphone) FacebookBlue else FbBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isSpeakerphone) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = "Speaker",
                        tint = if (isSpeakerphone) FacebookBlue else FbTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
