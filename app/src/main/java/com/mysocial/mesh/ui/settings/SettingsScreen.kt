package com.mysocial.mesh.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.components.ChangelogView
import com.mysocial.mesh.ui.components.LegalDocumentDialog
import com.mysocial.mesh.ui.components.LegalTextContent
import com.mysocial.mesh.ui.components.UpdateDialog
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import com.mysocial.mesh.utils.AppUpdateInfo
import com.mysocial.mesh.utils.UpdateManager
import com.mysocial.mesh.utils.toFormattedBbid
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val compactFeedMode by viewModel.compactFeedMode.collectAsState()
    val showHopBadges by viewModel.showHopBadges.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTopBar(title = "Appearance & Theme", onNavigateBack = onNavigateBack)

            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Live Theme Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DarkMode,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Preview",
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "$themeMode Mode • $accentColor Accent",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Mini badge indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FacebookBlueLight)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Active",
                                color = FacebookBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mini sample message bubble to show accent & surface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FbSurfaceElevated)
                            .border(0.6.dp, FbBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MySocial Offline Mesh",
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (compactFeedMode) "Compact timeline density active" else "Standard comfortable timeline",
                                    color = FbTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (showHopBadges) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(FacebookBlueLight)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("⚡ 1 hop", color = FacebookBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Theme Scheme Selector (System, Light, Dark, AMOLED)
            Text(
                text = "THEME SCHEME",
                color = FbTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val themes = listOf(
                        Triple("SYSTEM", "System Default", "Matches device light / dark setting"),
                        Triple("LIGHT", "Light Mode", "Crisp slate canvas, soft cards"),
                        Triple("DARK", "Dark Mode", "Midnight obsidian, zero eye strain"),
                        Triple("AMOLED", "AMOLED Pitch Black", "Pure #000000 canvas, saves OLED battery")
                    )

                    themes.forEachIndexed { index, (key, title, subtitle) ->
                        val isSelected = themeMode == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) FacebookBlueLight else Color.Transparent)
                                .clickable { viewModel.setThemeMode(key) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        if (isSelected) FacebookBlue else FbBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(FacebookBlue)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = if (isSelected) FacebookBlue else FbTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = subtitle,
                                    color = FbTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (index < themes.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = FbDivider,
                                thickness = 0.6.dp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Accent Color Selector
            Text(
                text = "ACCENT COLOR PALETTE",
                color = FbTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Highlight & Interactive Accent",
                        color = FbTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Customizes buttons, highlights, badges, and tabs across the app",
                        color = FbTextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val accents = listOf(
                        Pair("BLUE", Color(0xFF38BDF8)) to "Electric Blue",
                        Pair("EMERALD", Color(0xFF10B981)) to "Emerald Mint",
                        Pair("PURPLE", Color(0xFFA855F7)) to "Cyber Violet",
                        Pair("ROSE", Color(0xFFF43F5E)) to "Neon Rose",
                        Pair("AMBER", Color(0xFFF59E0B)) to "Radiant Amber"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        accents.forEach { (pair, label) ->
                            val (key, color) = pair
                            val isSelected = accentColor == key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setAccentColor(key) }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) FbTextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = key.lowercase().replaceFirstChar { it.uppercase() },
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) FacebookBlue else FbTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Feed Layout & Timeline Density
            Text(
                text = "FEED LAYOUT & TIMELINE DENSITY",
                color = FbTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Compact Mode Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Compact Feed Density",
                                color = FbTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Tighter card margins and condensed post cards for cleaner feeds",
                                color = FbTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = compactFeedMode,
                            onCheckedChange = { viewModel.setCompactFeedMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FacebookBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = FbBorder
                            )
                        )
                    }

                    HorizontalDivider(color = FbDivider, thickness = 0.6.dp)

                    // Multi-Hop Passport Badges Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Multi-Hop Routing Badges",
                                color = FbTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Display ⚡ hop distance badges on posts relayed across the mesh",
                                color = FbTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = showHopBadges,
                            onCheckedChange = { viewModel.setShowHopBadges(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FacebookBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = FbBorder
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun MeshSyncScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dbStats by viewModel.dbStats.collectAsState()
    val syncIntervalSeconds by viewModel.syncIntervalSeconds.collectAsState()
    val blockedAuthors by viewModel.blockedAuthors.collectAsState()
    val isNearbyPeerNotificationsEnabled by viewModel.isNearbyPeerNotificationsEnabled.collectAsState()

    val loraBridge = remember { com.mysocial.mesh.lora.LoraCompanionBridge.getInstance(context) }
    val loraState by loraBridge.connectionState.collectAsState()
    val loraInfo by loraBridge.bridgeInfo.collectAsState()
    val loraPackets by loraBridge.relayedPacketsCount.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTopBar(title = "Nearby Sharing & Sync", onNavigateBack = onNavigateBack)

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Frequency Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Check Frequency",
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "How often to check for nearby updates",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            color = FacebookBlueLight,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Every ${formatIntervalShort(syncIntervalSeconds)}",
                                color = FacebookBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val options = listOf(
                        5 to "5s",
                        10 to "10s",
                        30 to "30s",
                        60 to "1m",
                        300 to "5m"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (seconds, label) ->
                            val isSelected = syncIntervalSeconds == seconds
                            IntervalChip(
                                label = label,
                                isSelected = isSelected,
                                onClick = { viewModel.setSyncInterval(seconds) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Nearby Friend Alerts Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FacebookBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Nearby Friend Alerts",
                                color = FbTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Notify when a friend is discovered nearby",
                                color = FbTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Switch(
                        checked = isNearbyPeerNotificationsEnabled,
                        onCheckedChange = { viewModel.setNearbyPeerNotificationsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FacebookBlue,
                            uncheckedThumbColor = FbTextSecondary,
                            uncheckedTrackColor = FbSurfaceElevated
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Network Diagnostics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Connection Status",
                        color = FbTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Wifi, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Local Wi-Fi", color = FbTextSecondary, fontSize = 13.sp)
                        }
                        Text("Active", color = FbGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = FbBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bluetooth", color = FbTextSecondary, fontSize = 13.sp)
                        }
                        Text("Active", color = FbGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = FbBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Storage, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saved updates on your device", color = FbTextSecondary, fontSize = 13.sp)
                        }
                        Text("${dbStats.totalEntries}", color = FbTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Background Reliability & Doze Exemption Card
            val isDozeExempt = remember { mutableStateOf(viewModel.isIgnoringBatteryOptimizations(context)) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDozeExempt.value) FbGreen.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = if (isDozeExempt.value) FbGreen else Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Background Reliability",
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Android Doze Mode Status",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            color = if (isDozeExempt.value) FbGreen.copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isDozeExempt.value) "Exempted" else "Optimized",
                                color = if (isDozeExempt.value) FbGreen else Color(0xFFD97706),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isDozeExempt.value) {
                            "MySocial is exempted from aggressive battery killing. Physical BLE & Wi-Fi mesh relays will continue syncing hops even when your screen is locked."
                        } else {
                            "Android battery optimization may pause mesh packet hopping when the screen turns off. Request exemption to guarantee 24/7 background packet hopping."
                        },
                        color = FbTextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp
                    )

                    if (!isDozeExempt.value) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.requestIgnoreBatteryOptimizations(context)
                                isDozeExempt.value = viewModel.isIgnoringBatteryOptimizations(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Text("Request Continuous Background Mesh", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LoRa & ESP32 Hardware Companion Bridge Card (IoT Repeater Seeds)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "LoRa Companion Bridge (IoT)",
                                color = FbTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Sub-GHz 868/915 MHz 5–15 km emergency seed",
                                color = FbTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        val (statusText, statusColor) = when (loraState) {
                            com.mysocial.mesh.lora.LoraConnectionState.CONNECTED -> "Connected" to FbGreen
                            com.mysocial.mesh.lora.LoraConnectionState.SIMULATED -> "Simulated" to FacebookBlue
                            com.mysocial.mesh.lora.LoraConnectionState.CONNECTING -> "Connecting..." to FacebookBlue
                            com.mysocial.mesh.lora.LoraConnectionState.DISCONNECTED -> "Off" to FbTextTertiary
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hardware: ${loraInfo.deviceModel}",
                        color = FbTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Packets Relayed: $loraPackets",
                        color = FbTextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Frequency selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("915 MHz (US)", "868 MHz (EU)").forEach { freq ->
                            val selected = loraInfo.frequency.contains(freq.take(3))
                            Surface(
                                color = if (selected) FacebookBlue else FbSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    loraBridge.setFrequency(freq)
                                }
                            ) {
                                Text(
                                    text = freq,
                                    color = if (selected) Color.White else FbTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (loraState == com.mysocial.mesh.lora.LoraConnectionState.DISCONNECTED) {
                                    loraBridge.connectSimulated()
                                } else {
                                    loraBridge.disconnect()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (loraState != com.mysocial.mesh.lora.LoraConnectionState.DISCONNECTED) Color(0xFFEF4444) else FacebookBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (loraState != com.mysocial.mesh.lora.LoraConnectionState.DISCONNECTED) "Disconnect Bridge" else "Enable Companion Bridge",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Decentralized Blocklist & Sybil Eviction Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Blocked Authors (${blockedAuthors.size})",
                            color = FbTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (blockedAuthors.isEmpty()) {
                        Text(
                            text = "No authors blocked. Radio packets from malicious actors evicted by 3 mutual friends are dropped before saving.",
                            color = FbTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        blockedAuthors.forEach { blocked ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = blocked.authorId.toFormattedBbid(),
                                        color = FbTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = blocked.reason,
                                        color = FbTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                androidx.compose.material3.TextButton(
                                    onClick = { viewModel.unblockAuthor(blocked.authorId) }
                                ) {
                                    Text("Unblock", color = FacebookBlue, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MuleStorageScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxStrangerMediaCacheMb by viewModel.maxStrangerMediaCacheMb.collectAsState()
    val storageUsageBytes by viewModel.storageUsageBytes.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshStorageUsage(context)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTopBar(title = "Offline Message Storage", onNavigateBack = onNavigateBack)

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storage,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Offline Message Storage",
                                    color = FbTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Temporary photos limit",
                                    color = FbTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        val usedMb = storageUsageBytes / (1024 * 1024)
                        Surface(
                            color = FacebookBlueLight,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$usedMb MB used",
                                color = FacebookBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "When this limit is reached, older photos from people you don't follow are automatically cleared to free up space. Photos from your friends are kept safe.",
                        color = FbTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val storageOptions = listOf(50, 100, 250)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        storageOptions.forEach { mb ->
                            val isSelected = maxStrangerMediaCacheMb == mb
                            IntervalChip(
                                label = "$mb MB",
                                isSelected = isSelected,
                                onClick = { viewModel.setMaxStrangerMediaCacheMb(mb) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var isExportingDiagnostics by remember { mutableStateOf(false) }

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            downloadProgress = downloadProgress,
            isDownloading = isDownloadingUpdate,
            onConfirmUpdate = {
                val url = updateInfo?.downloadUrl
                if (!url.isNullOrBlank()) {
                    scope.launch {
                        isDownloadingUpdate = true
                        val file = updateManager.downloadApk(url) { progress ->
                            downloadProgress = progress
                        }
                        isDownloadingUpdate = false
                        if (file != null) {
                            showUpdateDialog = false
                            updateManager.promptInstall(file)
                        } else {
                            Toast.makeText(context, "Download failed. Please check internet connection.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDismiss = {
                if (!isDownloadingUpdate) {
                    showUpdateDialog = false
                }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTopBar(title = "About", onNavigateBack = onNavigateBack)

            Spacer(modifier = Modifier.height(16.dp))

            // App Overview & Description Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(FacebookBlue)
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "MySocial",
                                color = FbTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Private Offline Social Network",
                                color = FacebookBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "MySocial connects you directly with people around you without internet, mobile data, or central servers. Share posts, photos, and messages with complete privacy—your data stays safely on your phone and syncs device-to-device.",
                        color = FbTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AboutFeatureChip(
                            icon = Icons.Filled.WifiOff,
                            label = "100% Offline",
                            modifier = Modifier.weight(1f)
                        )
                        AboutFeatureChip(
                            icon = Icons.Filled.Security,
                            label = "Zero Cloud",
                            modifier = Modifier.weight(1f)
                        )
                        AboutFeatureChip(
                            icon = Icons.Filled.Sync,
                            label = "Peer-to-Peer",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Version & Updates Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "App Version & Updates",
                                color = FbTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Surface(
                            color = FacebookBlueLight,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "v${updateManager.currentVersionName.removePrefix("v")}",
                                color = FacebookBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Build ${updateManager.currentVersionCode} • Up to date with latest features",
                        color = FbTextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isCheckingUpdate = true
                                try {
                                    val info = updateManager.checkForUpdate()
                                    isCheckingUpdate = false
                                    if (info != null && info.isUpdateAvailable) {
                                        updateInfo = info
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "You're using the latest version (v${updateManager.currentVersionName})",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    isCheckingUpdate = false
                                    Toast.makeText(
                                        context,
                                        "Update check failed: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FacebookBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking for Updates...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // What's New in This Version Card (Directly below App Version & Updates)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "What's New in This Version",
                            color = FbTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    val embeddedNotes = remember {
                        updateManager.getEmbeddedReleaseNotes().ifBlank {
                            """
                            * Feature: Ultra-Clean Decluttered Feed with sleek collapsible emergency hub, compact composer card, and modern segmented filter pills
                            * Feature: Expanded Appearance Studio with AMOLED Pitch Black (#000000), 5 accent color palettes (Blue, Emerald, Violet, Rose, Amber), and live preview
                            * Feature: Timeline Density Controls with Compact Feed Mode and Multi-Hop Passport routing badges toggle
                            * Improvement: Collapsible direction-finder radar on emergency alerts for clean vertical timeline flow
                            * Feature: Opportunistic Internet Gateway Uplink (Mule-to-Cloud Bridge) relaying queued SOS alerts to cloud endpoints with signed delivery receipts
                            * Feature: Android Wi-Fi Aware (NAN) high-bandwidth zero-configuration P2P discovery and direct streaming
                            * Feature: Cross-Platform iOS Companion Bridge via CoreBluetooth L2CAP Connection-Oriented Channels (CoC)
                            * Feature: Ephemeral Disappearing 24-Hour Mesh Stories with top avatar carousel and full-screen viewer
                            * Feature: Private Multi-Party Mesh Group Spaces encrypted with AES-256-GCM symmetric sender keys and QR invite links
                            * Feature: Topic & Radius Emergency Channels (#emergency-sos, #medical-aid, #shelter-updates, #food-and-water, #lost-and-found, #power-and-fuel)
                            * Feature: Push-to-Talk (PTT) Half-Duplex Emergency Mesh Walkie-Talkie with real-time voice bursts
                            * Feature: Compass Bearing Direction Finder Radar pointing toward emergency senders with magnetic heading and distance estimation
                            * Feature: LoRa & ESP32 Hardware Companion Bridge bridging SOS emergency alerts over 868/915 MHz across 5–15 km
                            * Feature: Adaptive Epidemic Scoping & Congestion Dampening dynamically preventing RF broadcast collisions in dense crowds
                            * Security: Decentralized Blocklist & Web-of-Trust Sybil Eviction with 3-friend consensus dropping malicious packets at the radio layer
                            * Performance: Native Rust core (mysocial-core) with ARMv8 NEON SIMD crypto, zero-copy wire validation & Merkle chunk tree hashing
                            * Bug Fix: Resolved Wi-Fi NsdManager unregistration timeouts on legacy Android devices with thread-safe state tracking
                            * Database: Room DB v10 schema migration with groups, stories, and gateway relay tracking
                            """.trimIndent()
                        }
                    }

                    ChangelogView(
                        releaseNotes = embeddedNotes,
                        maxHeight = 260.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Developer Credits Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(FacebookBlue.copy(alpha = 0.12f))
                                .border(1.dp, FacebookBlue.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Developer Credits",
                                color = FacebookBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Yuvraj Poudyal",
                                color = FbTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Creator & Lead Developer",
                                color = FbTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = FbBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FbSurfaceElevated)
                            .clickable {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/itsmeyuvraj")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Code,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "github.com/itsmeyuvraj",
                                color = FbTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = FbTextTertiary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legal & Community Guidelines Card (Google Play Compliance)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(FacebookBlueLight)
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Legal & Compliance",
                                color = FbTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Privacy terms and community guidelines",
                                color = FbTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "MySocial is built with strict zero-tracking principles. You can read our official offline policies anytime below:",
                        color = FbTextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPrivacyDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Privacy Policy", fontSize = 12.sp, color = FacebookBlue)
                        }

                        OutlinedButton(
                            onClick = { showTermsDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Terms of Service", fontSize = 12.sp, color = FacebookBlue)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Offline Diagnostics & Bug Exporter Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(0.5.dp, FbBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = FbSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                        ) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Diagnostics & Mesh Telemetry",
                                color = FbTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Export hardware radio metrics & crash logs",
                                color = FbTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Generate a sanitized diagnostic report containing radio capabilities, active peer counts, local database statistics, and recent activity logs for offline debugging.",
                        color = FbTextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isExportingDiagnostics = true
                                    try {
                                        val report = viewModel.generateDiagnosticReport(context)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "MySocial Diagnostics Report")
                                            putExtra(Intent.EXTRA_TEXT, report)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Export Diagnostics"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isExportingDiagnostics = false
                                    }
                                }
                            },
                            enabled = !isExportingDiagnostics,
                            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            if (isExportingDiagnostics) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val report = viewModel.generateDiagnosticReport(context)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("MySocial Diagnostics", report))
                                    Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Copy Report", fontSize = 12.sp, color = FacebookBlue)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Footer Privacy Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = FbGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "100% Private & Direct • No Internet or Servers Needed",
                    color = FbTextTertiary,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showPrivacyDialog) {
        LegalDocumentDialog(
            title = "Privacy Policy",
            content = LegalTextContent.PRIVACY_POLICY,
            isPrivacyPolicy = true,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showTermsDialog) {
        LegalDocumentDialog(
            title = "Terms of Service",
            content = LegalTextContent.TERMS_OF_SERVICE,
            isPrivacyPolicy = false,
            onDismiss = { showTermsDialog = false }
        )
    }
}

@Composable
private fun AboutFeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = FbSurfaceElevated,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FacebookBlue,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = FbTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTopBar(title = "Settings", onNavigateBack = onNavigateBack ?: {})
            // Placeholder / Unified Screen
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    onNavigateBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FbSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = FbTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    color = FbTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
        }
    }
}

@Composable
fun IntervalChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) FacebookBlue else FbSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) FacebookBlue else FbBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else FbTextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

fun formatIntervalShort(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds % 60 == 0 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}
