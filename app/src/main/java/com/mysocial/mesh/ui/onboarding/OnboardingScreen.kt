package com.mysocial.mesh.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.LegalDocumentDialog
import com.mysocial.mesh.ui.components.LegalTextContent
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import com.mysocial.mesh.utils.toFormattedBbid

@Composable
fun OnboardingScreen(
    currentAuthorId: String,
    defaultName: String = "Neighbor",
    onFinishOnboarding: (displayName: String, bio: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf(if (defaultName.startsWith("SM-") || defaultName.length > 20) "Neighbor" else defaultName) }
    var bio by remember { mutableStateOf("Connecting with friends offline on MySocial") }
    var agreedToGuidelines by remember { mutableStateOf(false) }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = FbBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Top Navigation & Step Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 0) {
                    IconButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FbSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FbTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                // Step dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { idx ->
                        val isActive = idx == step
                        val isDone = idx < step
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isActive) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        isActive -> FacebookBlue
                                        isDone -> FbGreen
                                        else -> FbBorder
                                    }
                                )
                        )
                    }
                }

                Text(
                    text = "Step ${step + 1} of 3",
                    fontSize = 12.sp,
                    color = FbTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Animated Step Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "OnboardingTransition"
                ) { currentStep ->
                    when (currentStep) {
                        0 -> StepWelcome()
                        1 -> StepIdentity(
                            authorId = currentAuthorId,
                            displayName = displayName,
                            onNameChange = { displayName = it },
                            bio = bio,
                            onBioChange = { bio = it }
                        )
                        2 -> StepPermissionsAndTerms(
                            agreedToGuidelines = agreedToGuidelines,
                            onAgreedChange = { agreedToGuidelines = it },
                            onOpenPrivacyPolicy = { showPrivacyDialog = true },
                            onOpenTerms = { showTermsDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Continue / Finish Button
            val canProceed = when (step) {
                0 -> true
                1 -> displayName.trim().isNotBlank()
                2 -> agreedToGuidelines && displayName.trim().isNotBlank()
                else -> false
            }

            Button(
                onClick = {
                    if (step < 2) {
                        step++
                    } else {
                        onFinishOnboarding(displayName.trim(), bio.trim())
                    }
                },
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FacebookBlue,
                    disabledContainerColor = FacebookBlue.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (step == 2) "Join Mesh Network" else "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (step == 2) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
private fun StepWelcome() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(FacebookBlueLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.WifiTethering,
                contentDescription = null,
                tint = FacebookBlue,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Welcome to MySocial",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = FbTextPrimary,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "The 100% Offline, Peer-to-Peer Social Mesh",
            fontSize = 14.sp,
            color = FbTextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Feature Highlights
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureItem(
                icon = Icons.Filled.WifiTethering,
                iconTint = FacebookBlue,
                title = "Direct Device-to-Device Mesh",
                description = "Gossips posts, photos, and messages hop-by-hop via Bluetooth LE & Local Wi-Fi. Zero cell towers, zero Wi-Fi routers, zero internet needed."
            )

            FeatureItem(
                icon = Icons.Filled.Lock,
                iconTint = FbGreen,
                title = "Cryptographic Sovereign Identity",
                description = "Your account is secured by an Ed25519 key pair stored in your device hardware. No phone numbers, no emails, no passwords."
            )

            FeatureItem(
                icon = Icons.Filled.Security,
                iconTint = Color(0xFF8B5CF6),
                title = "Zero Cloud Tracking & Zero Ads",
                description = "No central servers, no algorithms manipulating your feed, no analytics trackers, and no company reading your messages."
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StepIdentity(
    authorId: String,
    displayName: String,
    onNameChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Create Your Mesh Identity",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = FbTextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose your public name so neighbors and friends can recognize you on the mesh.",
            fontSize = 13.5.sp,
            color = FbTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Live Avatar Preview
        AvatarView(
            seed = authorId,
            displayName = displayName.ifBlank { "Neighbor" },
            size = 80.dp,
            showOnlineBadge = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // BBID Chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(FbSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Mesh ID: ",
                color = FbTextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = authorId.toFormattedBbid(),
                color = FacebookBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Display Name input
        OutlinedTextField(
            value = displayName,
            onValueChange = onNameChange,
            label = { Text("Display Name / Nickname") },
            placeholder = { Text("e.g. Alex, Maya, Ranger") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = FbTextPrimary,
                unfocusedTextColor = FbTextPrimary,
                focusedBorderColor = FacebookBlue,
                unfocusedBorderColor = FbBorder,
                focusedContainerColor = FbSurface,
                unfocusedContainerColor = FbSurface
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Bio input
        OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            label = { Text("Bio / Status (Optional)") },
            placeholder = { Text("Connecting offline on MySocial") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = FbTextPrimary,
                unfocusedTextColor = FbTextPrimary,
                focusedBorderColor = FacebookBlue,
                unfocusedBorderColor = FbBorder,
                focusedContainerColor = FbSurface,
                unfocusedContainerColor = FbSurface
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You can update your display name and bio anytime from your Profile tab.",
            fontSize = 12.sp,
            color = FbTextTertiary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StepPermissionsAndTerms(
    agreedToGuidelines: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Permissions & Guidelines",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = FbTextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Review how MySocial uses physical radio permissions and our standards for a safe offline community.",
            fontSize = 13.5.sp,
            color = FbTextSecondary,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Radio Permissions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FbSurfaceElevated),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, FbBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WifiTethering,
                        contentDescription = null,
                        tint = FacebookBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Physical Mesh Radio Permissions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = FbTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "MySocial requests Bluetooth Low Energy and Nearby Wi-Fi permissions strictly to discover nearby mesh nodes and route packets hop-by-hop. We use the 'neverForLocation' system flag and never track or record your GPS coordinates.",
                    fontSize = 12.5.sp,
                    color = FbTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Community Guidelines & UGC Agreement Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FbSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, FbBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = FbGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Community Standards & Moderation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = FbTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Because MySocial operates directly over radio waves without cloud servers, community safety is enforced through local tools:\n• Report objectionable content to immediately hide it.\n• 1-tap block abusive authors to drop all incoming packets.\n• Prohibited: Hate speech, harassment, violence, CSAM, fraud, and illegal activity.",
                    fontSize = 12.5.sp,
                    color = FbTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = FbBorder, thickness = 0.6.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Legal links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenPrivacyPolicy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PrivacyTip,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Privacy Policy", fontSize = 12.sp, color = FacebookBlue)
                    }

                    OutlinedButton(
                        onClick = onOpenTerms,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terms of Service", fontSize = 12.sp, color = FacebookBlue)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Checkbox: Mandatory UGC Agreement
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onAgreedChange(!agreedToGuidelines) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreedToGuidelines,
                onCheckedChange = onAgreedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = FacebookBlue,
                    checkmarkColor = Color.White,
                    uncheckedColor = FbTextSecondary
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "I accept the Community Guidelines and Terms of Service. I agree not to post abusive, illegal, or harassing content.",
                fontSize = 12.5.sp,
                color = if (agreedToGuidelines) FbTextPrimary else FbTextSecondary,
                fontWeight = if (agreedToGuidelines) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FbSurface)
            .border(0.6.dp, FbBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = FbTextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 12.5.sp,
                color = FbTextSecondary,
                lineHeight = 17.5.sp
            )
        }
    }
}

