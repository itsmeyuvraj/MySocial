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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
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
    hopDistance: Int,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, textColor, icon) = when (hopDistance) {
        0 -> Quadruple("You", BadgeYouBg, BadgeYouText, Icons.Filled.Person)
        1 -> Quadruple("Directly Nearby", BadgeFriendsBg, BadgeFriendsText, Icons.Filled.Group)
        else -> Quadruple("Shared Nearby", BadgeNearbyBg, BadgeNearbyText, Icons.Filled.Public)
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
