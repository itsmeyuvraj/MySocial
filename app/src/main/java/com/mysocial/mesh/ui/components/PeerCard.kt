package com.mysocial.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.protocol.TransportType
import com.mysocial.mesh.transport.DiscoveredPeer
import com.mysocial.mesh.ui.components.VerifiedBadge
import com.mysocial.mesh.utils.toFormattedBbid
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import androidx.compose.material3.HorizontalDivider

@Composable
fun PeerCard(
    peer: DiscoveredPeer,
    onForceSyncClick: (() -> Unit)? = null,
    isFriend: Boolean = false,
    isVerifiedInPerson: Boolean = false,
    onToggleFriend: (() -> Unit)? = null,
    onMessageClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FbSurface,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onProfileClick != null) Modifier.clickable { onProfileClick() }
                else Modifier
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left info with Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AvatarView(
                        seed = peer.peerId,
                        displayName = peer.peerName,
                        size = 48.dp,
                        showOnlineBadge = (System.currentTimeMillis() - peer.lastSeenTimestamp) < 30_000L
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = peer.peerName,
                                color = FbTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isFriend || isVerifiedInPerson) {
                                VerifiedBadge(size = 14.dp)
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (peer.transportType == TransportType.MDNS_WIFI) Icons.Filled.Wifi else Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            val transportLabel = when {
                                peer.transportType == TransportType.LORA -> "LoRa 915MHz"
                                peer.transportType == TransportType.WIFI_AWARE -> "Wi-Fi Aware"
                                peer.transportType == TransportType.IOS_COMPANION -> "iOS L2CAP"
                                peer.transportType == TransportType.MDNS_WIFI -> "Local Wi-Fi"
                                peer.isExtendedAdvertising -> "Bluetooth"
                                else -> "Bluetooth"
                            }
                            Text(
                                text = transportLabel,
                                color = FbTextSecondary,
                                fontSize = 12.sp
                            )

                            if (peer.rssi != null) {
                                Text(text = "•", color = FbTextTertiary, fontSize = 10.sp)
                                Text(
                                    text = "Nearby",
                                    color = FbGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Right Action Row (Instagram Style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isFriend) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FbSurfaceElevated)
                                .border(0.5.dp, FbBorder, RoundedCornerShape(8.dp))
                                .then(
                                    if (onToggleFriend != null) Modifier.clickable { onToggleFriend() }
                                    else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Following",
                                color = FbTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (onToggleFriend != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FacebookBlue)
                                .clickable { onToggleFriend() }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Follow",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (onMessageClick != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FbSurfaceElevated)
                                .border(0.5.dp, FbBorder, RoundedCornerShape(8.dp))
                                .clickable { onMessageClick() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Message",
                                color = FbTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
        }
    }
}
