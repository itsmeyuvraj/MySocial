package com.mysocial.mesh.ui.messages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.GroupEntity
import com.mysocial.mesh.model.GroupMessageEntity
import com.mysocial.mesh.ui.components.AvatarView
import com.mysocial.mesh.ui.components.formatRelativeTime
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import kotlinx.coroutines.launch

@Composable
fun GroupChatScreen(
    group: GroupEntity,
    repository: MeshRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val groupManager = repository.groupManager

    val messages by groupManager.observeGroupMessages(group.groupId).collectAsState(initial = emptyList())
    val members by groupManager.observeGroupMembers(group.groupId).collectAsState(initial = emptyList())

    var inputText by remember { mutableStateOf("") }
    var showInviteDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = FbSurface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
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

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FacebookBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.groupName,
                                color = FbTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Group Encrypted",
                                tint = FacebookBlue,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = "${members.size} members • AES-256-GCM Space",
                            color = FbTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = { showInviteDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Invite to Group",
                            tint = FacebookBlue
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = FbSurface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message group...", color = FbTextTertiary, fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FbSurfaceElevated,
                            unfocusedContainerColor = FbSurfaceElevated,
                            focusedBorderColor = FacebookBlue,
                            unfocusedBorderColor = FbBorder,
                            focusedTextColor = FbTextPrimary,
                            unfocusedTextColor = FbTextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val textToSend = inputText
                                inputText = ""
                                coroutineScope.launch {
                                    groupManager.sendGroupMessage(
                                        groupId = group.groupId,
                                        text = textToSend,
                                        myDisplayName = "Me"
                                    )
                                }
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) FacebookBlue else FbSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.White else FbTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .background(FbBackground)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.messageId }) { msg ->
                val isOutgoing = msg.isOutgoing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                ) {
                    if (!isOutgoing) {
                        AvatarView(
                            seed = msg.senderAuthorId,
                            displayName = msg.senderDisplayName,
                            size = 32.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Column(
                        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        if (!isOutgoing) {
                            Text(
                                text = msg.senderDisplayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FacebookBlue,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isOutgoing) FacebookBlue else FbSurfaceElevated
                        ) {
                            Text(
                                text = msg.plaintextSnippet ?: "[Encrypted Mesh Message]",
                                color = if (isOutgoing) Color.White else FbTextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        Text(
                            text = formatRelativeTime(msg.timestamp),
                            fontSize = 10.sp,
                            color = FbTextTertiary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        val inviteUri = groupManager.generateInviteUri(group)
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = {
                Text(
                    text = "Invite to ${group.groupName}",
                    fontWeight = FontWeight.Bold,
                    color = FbTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Share this secure group invite link with nearby peers or display as QR code to grant them the AES-256 group root key:",
                        fontSize = 13.sp,
                        color = FbTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = FbBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = inviteUri,
                            fontSize = 10.sp,
                            color = FbTextPrimary,
                            maxLines = 3,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Group Invite", inviteUri))
                        Toast.makeText(context, "Invite copied to clipboard", Toast.LENGTH_SHORT).show()
                        showInviteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Copy Link", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Close", color = FbTextSecondary)
                }
            },
            containerColor = FbSurfaceElevated
        )
    }
}

