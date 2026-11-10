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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.mysocial.mesh.ui.theme.FbDivider
import com.mysocial.mesh.ui.theme.InstagramStoryGradient
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
    val myPosts by viewModel.myPosts.collectAsState()
    val followedAuthorIds by viewModel.followedAuthorIds.collectAsState()
    val likedPostHashes by viewModel.userLikedPostHashes.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()

    var isEditingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var isEditingBio by remember { mutableStateOf(false) }
    var tempBio by remember { mutableStateOf("") }
    val nameFocusRequester = remember { FocusRequester() }
    val bioFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingName) {
        if (isEditingName) {
            nameFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isEditingBio) {
        if (isEditingBio) {
            bioFocusRequester.requestFocus()
        }
    }

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
        Column(modifier = Modifier.fillMaxSize()) {
            // Instagram-Style Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = FbSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName.lowercase().replace(" ", "_"),
                            color = FbTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        VerifiedBadge(size = 16.dp)
                    }

                    IconButton(
                        onClick = { showTrustQrDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = "My QR Code",
                            tint = FbTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = FbDivider, thickness = 0.5.dp)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Profile Header (Instagram Layout)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FbSurface)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Avatar + Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(InstagramStoryGradient)
                                    .padding(2.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(FbSurface)
                                        .padding(2.dp)
                                ) {
                                    AvatarView(
                                        seed = authorId,
                                        displayName = displayName,
                                        size = 72.dp,
                                        showOnlineBadge = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(28.dp))

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileStatColumn(count = "${myPosts.size}", label = "posts")
                                ProfileStatColumn(count = "${followedAuthorIds.size}", label = "friends")
                                ProfileStatColumn(count = "Mesh", label = "verified")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display Name
                        if (isEditingName) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = tempName,
                                    onValueChange = { tempName = it },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(nameFocusRequester),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = FbTextPrimary,
                                        unfocusedTextColor = FbTextPrimary,
                                        focusedBorderColor = FacebookBlue,
                                        unfocusedBorderColor = FbBorder,
                                        cursorColor = FacebookBlue
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            val clean = tempName.trim()
                                            if (clean.isNotBlank()) {
                                                viewModel.saveProfile(clean, bio)
                                            }
                                            isEditingName = false
                                        }
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val clean = tempName.trim()
                                        if (clean.isNotBlank()) {
                                            viewModel.saveProfile(clean, bio)
                                        }
                                        isEditingName = false
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = "Save", tint = FacebookBlue)
                                }
                                IconButton(onClick = { isEditingName = false }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = FbTextSecondary)
                                }
                            }
                        } else {
                            Text(
                                text = displayName,
                                color = FbTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // Deterministic BBID Pill with Copy Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FbSurfaceElevated)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("ID", authorId.toFormattedBbid()))
                                    Toast.makeText(context, "ID copied: ${authorId.toFormattedBbid()}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = authorId.toFormattedBbid(),
                                color = FacebookBlue,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = FacebookBlue,
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bio
                        if (isEditingBio) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = tempBio,
                                    onValueChange = { tempBio = it },
                                    maxLines = 3,
                                    placeholder = { Text("Write a bio...", color = FbTextTertiary, fontSize = 13.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(bioFocusRequester),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = FbTextPrimary,
                                        unfocusedTextColor = FbTextPrimary,
                                        focusedBorderColor = FacebookBlue,
                                        unfocusedBorderColor = FbBorder,
                                        cursorColor = FacebookBlue
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { isEditingBio = false }) {
                                        Text("Cancel", color = FbTextSecondary, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Button(
                                        onClick = {
                                            viewModel.saveProfile(displayName, tempBio.trim())
                                            isEditingBio = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = bio,
                                color = FbTextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Edit profile | Share QR | Scan QR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FbSurfaceElevated)
                                    .clickable {
                                        tempName = displayName
                                        tempBio = bio
                                        isEditingName = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Edit profile",
                                    color = FbTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FbSurfaceElevated)
                                    .clickable { showTrustQrDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Share QR",
                                    color = FbTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FbSurfaceElevated)
                                    .clickable { showScannerDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Scan QR",
                                    color = FbTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Instagram Profile Posts Tab
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FbSurface)
                                .height(44.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = "Posts",
                                tint = FbTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        HorizontalDivider(color = FbDivider, thickness = 0.5.dp)
                    }
                }

                // User's Posts list
                if (myPosts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = null,
                                tint = FbTextTertiary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No posts yet",
                                color = FbTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "When you share photos or updates on MySocial, they'll show up here.",
                                color = FbTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
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
                            isLiked = postItem.post.entryHash in likedPostHashes,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Instagram-style Post Details & Comments Bottom Sheet
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
private fun ProfileStatColumn(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            color = FbTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = FbTextSecondary,
            fontSize = 12.sp
        )
    }
}
