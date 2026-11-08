package com.mysocial.mesh.ui.feed

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.emergency.EmergencyChannels
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EmergencyChannelAndPttBar(
    selectedChannel: String?,
    onChannelSelected: (String?) -> Unit,
    isPttRecording: Boolean,
    isPttPlaying: Boolean,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channels = EmergencyChannels.CHANNELS
    var isExpanded by remember { mutableStateOf(selectedChannel != null || isPttRecording || isPttPlaying) }

    LaunchedEffect(selectedChannel, isPttRecording, isPttPlaying) {
        if (selectedChannel != null || isPttRecording || isPttPlaying) {
            isExpanded = true
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = FbSurface,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (selectedChannel != null || isPttRecording) FbRed.copy(alpha = 0.5f) else FbBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Compact Header / Expander Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (selectedChannel != null || isPttRecording) FbRed.copy(alpha = 0.15f) else FbSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Radio,
                            contentDescription = "Channels",
                            tint = if (selectedChannel != null || isPttRecording) FbRed else FbTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Emergency Hub & Walkie-Talkie",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedChannel != null || isPttRecording) FbRed else FbTextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedChannel != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FbRed.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = selectedChannel,
                                color = FbRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (!isExpanded) {
                        Text(
                            text = "Standby",
                            color = FbTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = FbTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal channel chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(channels, key = { it.tag }) { channel ->
                            val isSelected = selectedChannel == channel.tag
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        onChannelSelected(null) // Unselect to show all
                                    } else {
                                        onChannelSelected(channel.tag)
                                    }
                                },
                                label = {
                                    Text(
                                        text = "${channel.icon} ${channel.tag}",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FbRed.copy(alpha = 0.15f),
                                    selectedLabelColor = FbRed,
                                    containerColor = FbSurfaceElevated,
                                    labelColor = FbTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) FbRed else FbBorder,
                                    selectedBorderColor = FbRed
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Push-To-Talk (PTT) Bar
                    val activeChannel = selectedChannel ?: EmergencyChannels.DEFAULT_CHANNEL
                    val pttBackgroundColor = if (isPttRecording) FbRed else FbSurfaceElevated
                    val pttContentColor = if (isPttRecording) Color.White else FbTextPrimary

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onStartPtt()
                                        tryAwaitRelease()
                                        onStopPtt()
                                    }
                                )
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = pttBackgroundColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isPttPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Mic,
                                contentDescription = "PTT",
                                tint = pttContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val statusText = when {
                                isPttRecording -> "🔴 TRANSMITTING ON $activeChannel (Release to Send)"
                                isPttPlaying -> "🔊 INCOMING VOICE BURST..."
                                else -> "🎙️ Hold to Talk on $activeChannel"
                            }
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = pttContentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

