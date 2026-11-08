package com.mysocial.mesh.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.emergency.CompassDirectionFinder
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import kotlin.math.abs

@Composable
fun CompassRadarView(
    senderAuthorId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val compassFinder = remember { CompassDirectionFinder(context) }

    DisposableEffect(compassFinder) {
        compassFinder.start()
        onDispose {
            compassFinder.stop()
        }
    }

    val currentAzimuth by compassFinder.azimuth.collectAsState()

    // Determine target relative bearing deterministically from author ID
    val targetBearing = remember(senderAuthorId) {
        val hash = abs(senderAuthorId.hashCode())
        (hash % 360).toFloat()
    }

    val relativeAngle = CompassDirectionFinder.calculateRelativeBearing(currentAzimuth, targetBearing)
    val animatedRotation by animateFloatAsState(targetValue = relativeAngle, label = "CompassNeedle")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = FbSurfaceElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val indicatorColor = FbRed
            // Radar Compass Dial
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(60.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f - 4f
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        radius = radius
                    )
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = 0.4f),
                        radius = radius,
                        style = Stroke(width = 2f)
                    )
                    // North indicator tick
                    drawLine(
                        color = indicatorColor,
                        start = Offset(center.x, center.y - radius),
                        end = Offset(center.x, center.y - radius + 8f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }

                // Needle arrow
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = "Direction Needle",
                    tint = FbRed,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(animatedRotation)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🧭 DIRECTION FINDER RADAR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FbRed
                )
                Spacer(modifier = Modifier.height(2.dp))
                val directionDescription = when {
                    abs(relativeAngle) < 20f -> "Straight Ahead"
                    relativeAngle in 20f..70f -> "Bear Right (~${relativeAngle.toInt()}°)"
                    relativeAngle in 70f..110f -> "Sharp Right (~${relativeAngle.toInt()}°)"
                    relativeAngle in -70f..-20f -> "Bear Left (~${abs(relativeAngle).toInt()}°)"
                    relativeAngle in -110f..-70f -> "Sharp Left (~${abs(relativeAngle).toInt()}°)"
                    else -> "Behind You (~${relativeAngle.toInt()}°)"
                }
                Text(
                    text = directionDescription,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FbTextPrimary
                )
                Text(
                    text = "Device Azimuth: ${currentAzimuth.toInt()}° • Target: ${targetBearing.toInt()}°",
                    fontSize = 11.sp,
                    color = FbTextSecondary
                )
            }
        }
    }
}
