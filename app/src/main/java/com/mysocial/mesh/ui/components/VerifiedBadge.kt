package com.mysocial.mesh.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mysocial.mesh.ui.theme.FacebookBlue

/**
 * Blue tick verification badge displayed beside trusted friends' names
 * across posts, comments, profiles, and nearby peers.
 */
@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    contentDescription: String = "Trusted Friend"
) {
    Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = contentDescription,
        tint = FacebookBlue,
        modifier = modifier.size(size)
    )
}
