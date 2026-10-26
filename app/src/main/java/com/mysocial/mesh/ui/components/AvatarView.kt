package com.mysocial.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import kotlin.math.absoluteValue

val AvatarPastelColors = listOf(
    Color(0xFF1877F2), // Facebook Blue
    Color(0xFF00A400), // Vibrant Green
    Color(0xFF6B4EE6), // Violet
    Color(0xFFFA383E), // Rose Red
    Color(0xFFF7821B), // Orange
    Color(0xFF0099B8)  // Teal
)

@Composable
fun AvatarView(
    seed: String,
    displayName: String,
    size: Dp = 44.dp,
    showOnlineBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hash = seed.hashCode().absoluteValue
    val bgColor = AvatarPastelColors[hash % AvatarPastelColors.size]
    val initial = displayName.trim().firstOrNull()?.uppercase() ?: "U"

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor)
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontSize = (size.value * 0.44f).sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showOnlineBadge) {
            val badgeSize = (size.value * 0.28f).coerceAtLeast(10f).dp
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .clip(CircleShape)
                    .background(FbGreen)
                    .border(2.dp, FbSurface, CircleShape)
            )
        }
    }
}
