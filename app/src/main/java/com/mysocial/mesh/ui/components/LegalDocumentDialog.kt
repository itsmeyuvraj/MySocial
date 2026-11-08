package com.mysocial.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary

@Composable
fun LegalDocumentDialog(
    title: String,
    content: String,
    isPrivacyPolicy: Boolean = false,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = FbSurface,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(0.8.dp, FbBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(FacebookBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPrivacyPolicy) Icons.Filled.PrivacyTip else Icons.Filled.Description,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                color = FbTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Legal & Compliance",
                                color = FbTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FbSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = FbTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = FbBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Body
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = content,
                        color = FbTextPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = FbBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

object LegalTextContent {
    val PRIVACY_POLICY = """
Privacy Policy for MySocial
Effective Date: November 8, 2026
Application: MySocial — Decentralized Offline Mesh Social Network
Developer: Yuvraj Poudyal
Repository: github.com/itsmeyuvraj/MySocial

1. Core Principle: 100% Offline, Zero Cloud Telemetry
MySocial was engineered from the ground up as a serverless, peer-to-peer (P2P) decentralized mesh network.
• No Central Servers: There are no backend databases, company servers, cloud logins, or remote relays operated by the developer or any third party.
• No Analytics or Trackers: MySocial contains zero tracking SDKs, zero advertising networks, zero user telemetry, and zero third-party analytics libraries.
• No Personal Account Registration: You do not register with an email address, phone number, Google account, or real name. Your identity is a purely local Ed25519 cryptographic key pair generated directly on your device.

2. Information Collected and Processed Locally
All data created or received by MySocial resides strictly on your physical Android device and is transmitted directly between nearby devices over physical radio waves (Bluetooth Low Energy and local Wi-Fi):
1. Cryptographic Identity:
   - When you set up MySocial, an Ed25519 public/private key pair is generated locally on your device.
   - Your private key never leaves your device's internal storage / Android Keystore.
   - Your public key is hashed using Crockford Base32 into an 8-character Base32 Badge ID (#XXXX-XXXX) to identify you to peers on the local mesh.
2. User-Generated Content (UGC):
   - Posts, photos, comments, poll votes, and 24-hour stories you publish are cryptographically signed with your private key and stored in a local SQLite/Room database on your device.
   - These are broadcast directly to nearby devices in physical radio range over Bluetooth Low Energy (BLE) and local Wi-Fi LAN / Wi-Fi Aware.
3. Private Direct Messages:
   - 1-on-1 private messages between verified friends are end-to-end encrypted (E2EE) using ephemeral X25519 Diffie-Hellman key exchange and authenticated AES-256-GCM encryption.
   - Intermediate "data mule" peers relaying encrypted packets have zero knowledge of message contents, sender identifiers, or recipients.
4. Hardware Radio Usage:
   - Bluetooth & Nearby Devices: Used strictly to discover and establish short-range peer-to-peer data links with nearby phones. MySocial uses the 'neverForLocation' flag on Android 12+ and never records your physical GPS coordinates.
   - Wi-Fi & Wi-Fi Aware: Used strictly for local TCP socket synchronization across nearby peers.
   - Microphone: Used strictly when you hold the Push-to-Talk (PTT) Walkie-Talkie button or record a voice note in direct chat. Audio is never recorded in the background.
   - Camera: Used strictly when you scan a friend's in-person Web of Trust QR code or capture a photo post.

3. Data Retention and Deletion
• Ephemeral TTL Posts: Posts configured with a Time-To-Live (1h, 6h, 24h) and 24-Hour Stories are automatically purged and deleted from your device storage once expired.
• Data Mule Cache Limit: Media cached for un-followed strangers is automatically capped via an in-app ring buffer (50 MB – 250 MB) and auto-evicts oldest content.
• Complete Data Eradication: Uninstalling MySocial or clearing app storage in Android System Settings permanently erases all cryptographic keys, databases, messages, and media. There are no cloud backups or remote traces.

4. Content Moderation & Reporting
MySocial provides local content moderation tools:
• Post Reporting & Hiding: Users can report objectionable, abusive, or spam content locally, which immediately hides the post and prevents further re-gossiping from their device.
• Peer Blocking & Sybil Eviction: Users can block any author with 1 tap. If 3 mutual verified friends block an author, their gossip packets are dropped at the physical radio transport layer.
""".trimIndent()

    val TERMS_OF_SERVICE = """
Terms of Service & Community Guidelines
Effective Date: November 8, 2026
Application: MySocial — Decentralized Offline Mesh Social Network
Developer: Yuvraj Poudyal
Repository: github.com/itsmeyuvraj/MySocial

1. Acceptance of Terms
By installing, launching, or using MySocial, you agree to these Terms of Service and Community Guidelines. If you do not agree to these terms, do not install or use the application.

2. Nature of Decentralized Peer-to-Peer Software
MySocial is an open-source, serverless peer-to-peer communications tool. It operates by broadcasting cryptographic data packets over local radio frequencies (Bluetooth Low Energy and local Wi-Fi) between devices in physical proximity.
• The developer does not operate servers, does not intermediate communications, and does not possess administrative backdoors or master cryptographic decryption keys.
• You are solely responsible for the cryptographic keys generated on your device and all content published under your key pair identity.

3. Community Guidelines & Prohibited Content
To maintain a safe environment for all participants in local radio mesh spaces, you agree not to use MySocial to publish or transmit:
1. Illegal or Harmful Content: Child sexual abuse material (CSAM), non-consensual intimate imagery, terrorism, human trafficking, or instructions for self-harm or violent crimes.
2. Harassment, Threats & Stalking: Explicit threats of violence, targeted harassment, hate speech inciting violence against protected groups, or doxxing private personal information.
3. Malicious Mesh Abuse & Flooding: Attempts to degrade the physical mesh network through radio flooding, Hashcash Proof-of-Work bypass attacks, or denial-of-service spam.
4. Fraud & Impersonation: Deceptive impersonation of public emergency services, law enforcement, or fraudulent misrepresentation of disaster relief signals.

4. Enforcement & Content Moderation
Because MySocial operates without centralized servers, enforcement is executed via local and consensus-driven moderation mechanisms:
• Local Post Reporting & Hiding: Any user may flag a post. Reporting a post immediately hides the content on the reporter's device and instructs the local gossip engine not to relay or re-broadcast the post to other peers.
• Peer Blocking & Packet Dropping: Users can block any author. Blocked authors have their broadcast packets immediately dropped at the radio transport layer before being stored in local SQLite databases.
• Web of Trust Sybil Eviction: If 3 mutual verified friends block an author, that author is marked as evicted from the mutual trust cluster.

5. Disclaimer of Warranties
MySocial is provided "AS IS" and "AS AVAILABLE" without warranties of any kind, whether express or implied. The developer does not warrant that physical radio mesh connectivity will be uninterrupted or error-free in all geographic environments, weather conditions, or hardware configurations.
Emergency channels (e.g. #emergency-sos) and the Compass Direction Finder are peer-to-peer crisis coordination aids; they are not certified replacements for official 911/112 public safety answering points when commercial cellular service is available.

6. Limitation of Liability
In no event shall the author or copyright holders be liable for any claim, damages, or other liability arising from, out of, or in connection with the software or the use or other dealings in the software.
""".trimIndent()
}

