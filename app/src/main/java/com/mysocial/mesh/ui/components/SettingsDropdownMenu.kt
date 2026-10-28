package com.mysocial.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary

@Composable
fun SettingsMenuButton(
    onNavigateToAppearance: () -> Unit,
    onNavigateToMeshSync: () -> Unit,
    onNavigateToMuleStorage: () -> Unit,
    onNavigateToShareApk: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    iconTint: Color = FbTextPrimary
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = iconModifier
                .size(36.dp)
                .clip(CircleShape)
                .background(FbSurfaceElevated)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(FbSurface)
                .border(1.dp, FbBorder, RoundedCornerShape(16.dp))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Appearance",
                        color = FbTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Appearance",
                        tint = FacebookBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToAppearance()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Mesh & Sync",
                        color = FbTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Mesh & Sync",
                        tint = FacebookBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToMeshSync()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Data Mule Storage",
                        color = FbTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Data Mule Storage",
                        tint = FacebookBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToMuleStorage()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Share APK Offline",
                        color = FbTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = "Share APK Offline",
                        tint = FacebookBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToShareApk()
                }
            )

            HorizontalDivider(
                color = FbBorder,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "About",
                        color = FbTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About",
                        tint = FacebookBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToAbout()
                }
            )
        }
    }
}

