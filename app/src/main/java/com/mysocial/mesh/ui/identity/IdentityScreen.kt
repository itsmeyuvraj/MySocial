package com.mysocial.mesh.ui.identity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.model.PostWithAuthor
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.PostCard
import com.mysocial.mesh.ui.components.VerifiedBadge
import com.mysocial.mesh.ui.qr.QrScannerDialog
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import com.mysocial.mesh.utils.toFormattedBbid

@Composable
fun IdentityScreen(
    viewModel: IdentityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val myProfile by viewModel.myProfile.collectAsState()
    val dbStats by viewModel.dbStats.collectAsState()
    val myPosts by viewModel.myPosts.collectAsState()
    val followedAuthorIds by viewModel.followedAuthorIds.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var showTrustQrDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var selectedPostForComments by remember { mutableStateOf<com.mysocial.mesh.model.PostWithAuthor?>(null) }
    var autoFocusCommentInput by remember { mutableStateOf(false) }

    val authorId = viewModel.authorId
    val displayName = myProfile?.displayName ?: "User"
    val bio = myProfile?.bio ?: "Connecting with friends offline on MySocial."

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FbBackground
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Profile Header Card (Facebook Style Profile)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FbSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AvatarView(
                            seed = authorId,
                            displayName = displayName,
                            size = 84.dp,
                            showOnlineBadge = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = displayName,
                            color = FbTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Deterministic BBID Row with 1-tap Copy
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FbSurfaceElevated)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("ID", authorId.toFormattedBbid()))
                                    Toast.makeText(context, "ID copied: ${authorId.toFormattedBbid()}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = authorId.toFormattedBbid(),
                                color = FacebookBlue,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = FacebookBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Expandable Raw Public Key Row
                        var isRawKeyExpanded by remember { mutableStateOf(false) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { isRawKeyExpanded = !isRawKeyExpanded }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isRawKeyExpanded) "Hide Full Account Key" else "Advanced Account Key",
                                    color = FbTextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isRawKeyExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = FbTextTertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            if (isRawKeyExpanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(FbBackground)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("AccountKey", authorId))
                                            Toast.makeText(context, "Account key copied", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = authorId,
                                        color = FbTextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = FbTextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = bio,
                            color = FbTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Edit Profile Button (Full Width Facebook Style)
                        Button(
                            onClick = {
                                editName = displayName
                                editBio = bio
                                showEditDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FbSurfaceElevated,
                                contentColor = FbTextPrimary
                            )
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // 2. Overview Stats Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = FbSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ProfileStatItem(number = "${myPosts.size}", label = "Your Posts")
                        ProfileStatItem(number = "${dbStats.totalAuthors}", label = "Friends & Nearby")
                        ProfileStatItem(number = "Active", label = "Offline Mode")
                    }
                }
            }

            // In-Person Web of Trust Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = FbSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VerifiedBadge(size = 20.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Friend Verification",
                                color = FbTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verify friends in person by scanning their QR code to give them a verified blue tick on their posts.",
                            color = FbTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showTrustQrDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                            ) {
                                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("My QR Code", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { showScannerDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FbGreen)
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Friend's QR", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. Section Header: My Posts
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        tint = FacebookBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "My Posts",
                        color = FbTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${myPosts.size})",
                        color = FbTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 4. User's Posts list
            if (myPosts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        colors = CardDefaults.cardColors(containerColor = FbSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Article,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No posts yet",
                                color = FbTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Posts you share on the Feed will appear here on your profile.",
                                color = FbTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(myPosts, key = { it.post.entryHash }) { postItem ->
                    PostCard(
                        postWithAuthor = postItem,
                        onLikeClick = { viewModel.reactToPost(postItem.post.entryHash) },
                        onReplyClick = {
                            selectedPostForComments = postItem
                            autoFocusCommentInput = true
                        },
                        onPostClick = {
                            selectedPostForComments = postItem
                            autoFocusCommentInput = false
                        },
                        onEditClick = { hash, newText -> viewModel.editPost(hash, newText) },
                        onDeleteClick = { hash -> viewModel.deletePost(hash) },
                        currentAuthorId = authorId,
                        isAuthorOnline = true,
                        isFriend = false,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }

        // Facebook-style Post Details & Comments Bottom Sheet
        if (selectedPostForComments != null) {
            val targetPost = selectedPostForComments!!
            val comments by viewModel.observeReplies(targetPost.post.entryHash).collectAsState(initial = emptyList())

            com.mysocial.mesh.ui.components.PostCommentsSheet(
                postWithAuthor = targetPost,
                comments = comments,
                currentAuthorId = authorId,
                onlineAuthorIds = emptySet(),
                followedAuthorIds = followedAuthorIds,
                autoFocus = autoFocusCommentInput,
                onDismiss = {
                    selectedPostForComments = null
                    autoFocusCommentInput = false
                },
                onSendComment = { text ->
                    val root = targetPost.post.rootHash ?: targetPost.post.entryHash
                    viewModel.replyToPost(
                        text = text,
                        rootHash = root,
                        parentHash = targetPost.post.entryHash
                    )
                },
                onLikePost = {
                    viewModel.reactToPost(targetPost.post.entryHash)
                },
                onLikeComment = { commentHash ->
                    viewModel.reactToPost(commentHash)
                }
            )
        }

        // Edit Profile Dialog
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                containerColor = FbSurface,
                title = { Text("Edit Profile", color = FbTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Name", color = FbTextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FbTextPrimary,
                                unfocusedTextColor = FbTextPrimary,
                                focusedBorderColor = FacebookBlue,
                                unfocusedBorderColor = FbBorder
                            )
                        )
                        OutlinedTextField(
                            value = editBio,
                            onValueChange = { editBio = it },
                            label = { Text("Bio", color = FbTextSecondary) },
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FbTextPrimary,
                                unfocusedTextColor = FbTextPrimary,
                                focusedBorderColor = FacebookBlue,
                                unfocusedBorderColor = FbBorder
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveProfile(editName, editBio)
                            showEditDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel", color = FbTextSecondary)
                    }
                }
            )
        }

        // Show My Trust QR Dialog
        if (showTrustQrDialog) {
            AlertDialog(
                onDismissRequest = { showTrustQrDialog = false },
                containerColor = FbSurface,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VerifiedBadge(size = 20.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "My Verification QR Code",
                                color = FbTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(onClick = { showTrustQrDialog = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = FbTextSecondary)
                        }
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ask a friend to scan this QR code with their camera to verify each other directly without internet.",
                            color = FbTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        qrBitmap?.let { bmp ->
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Verification QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = authorId.toFormattedBbid(),
                            color = FacebookBlue,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showTrustQrDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Done")
                    }
                }
            )
        }

        // Scan Friend's Trust QR Dialog
        if (showScannerDialog) {
            QrScannerDialog(
                onDismiss = { showScannerDialog = false },
                onQrScanned = { payload ->
                    viewModel.processScannedQr(payload) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) {
                            showScannerDialog = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileStatItem(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = number, color = FacebookBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = FbTextSecondary, fontSize = 12.sp)
    }
}
