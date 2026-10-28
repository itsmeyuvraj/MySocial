package com.mysocial.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.theme.BadgeFriendsBg
import com.mysocial.mesh.ui.theme.BadgeFriendsText
import com.mysocial.mesh.ui.theme.BadgeNearbyBg
import com.mysocial.mesh.ui.theme.BadgeNearbyText
import com.mysocial.mesh.ui.theme.BadgeYouBg
import com.mysocial.mesh.ui.theme.BadgeYouText

@Composable
fun HopBadge(
    hopCount: Int,
    transportSource: String = "LOCAL",
    modifier: Modifier = Modifier
) {
    val transportLabel = if (transportSource.contains("WIFI", ignoreCase = true)) "Wi-Fi" else "BLE"
    val (label, bgColor, textColor, icon) = when {
        hopCount == 0 -> Quadruple("You", BadgeYouBg, BadgeYouText, Icons.Filled.Person)
        hopCount == 1 -> Quadruple("Direct • $transportLabel", BadgeFriendsBg, BadgeFriendsText, Icons.Filled.NearMe)
        else -> Quadruple("⚡ $hopCount hops • via Mule", BadgeNearbyBg, BadgeNearbyText, Icons.Filled.Bolt)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
