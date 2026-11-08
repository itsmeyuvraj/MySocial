package com.mysocial.mesh.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import com.mysocial.mesh.ui.radio.WalkieTalkieScreen
import com.mysocial.mesh.ui.radio.WalkieTalkieViewModel
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.service.MeshSyncService
import com.mysocial.mesh.ui.composer.ComposerScreen
import com.mysocial.mesh.ui.composer.ComposerViewModel
import com.mysocial.mesh.ui.feed.FeedScreen
import com.mysocial.mesh.ui.feed.FeedViewModel
import com.mysocial.mesh.ui.identity.IdentityScreen
import com.mysocial.mesh.ui.identity.IdentityViewModel
import com.mysocial.mesh.ui.messages.ChatScreen
import com.mysocial.mesh.ui.messages.ChatViewModel
import com.mysocial.mesh.ui.messages.MessagesListScreen
import com.mysocial.mesh.ui.messages.MessagesViewModel
import com.mysocial.mesh.ui.radar.RadarScreen
import com.mysocial.mesh.ui.radar.RadarViewModel
import com.mysocial.mesh.ui.profile.PeerProfileScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import com.mysocial.mesh.ui.components.UpdateDialog
import com.mysocial.mesh.ui.settings.AboutScreen
import com.mysocial.mesh.ui.settings.AppearanceScreen
import com.mysocial.mesh.ui.settings.MeshSyncScreen
import com.mysocial.mesh.ui.settings.MuleStorageScreen
import com.mysocial.mesh.ui.settings.SettingsViewModel
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbRed
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import com.mysocial.mesh.ui.theme.FbTextTertiary
import com.mysocial.mesh.ui.theme.MySocialTheme
import com.mysocial.mesh.utils.AppUpdateInfo
import com.mysocial.mesh.utils.UpdateManager
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FEED("Feed", Icons.Filled.Home, Icons.Outlined.Home),
    RADAR("People", Icons.Filled.People, Icons.Outlined.People),
    RADIO("Radio", Icons.Filled.Radio, Icons.Outlined.Radio),
    CHATS("Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    IDENTITY("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

enum class SettingsSubScreen {
    APPEARANCE,
    MESH_SYNC,
    MULE_STORAGE,
    ABOUT
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: MeshRepository
    private var syncService: MeshSyncService? = null
    private var isBound = false

    private var hasPermissions by mutableStateOf(false)
    private var pendingUpdateInfo by mutableStateOf<AppUpdateInfo?>(null)
    private var targetTab by mutableIntStateOf(0)
    private var pendingChatPeerId by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra("open_tab", -1)
        if (tab != -1) {
            targetTab = tab
        }
        val chatPeerId = intent.getStringExtra("open_chat_peer_id")
        if (!chatPeerId.isNullOrBlank()) {
            pendingChatPeerId = chatPeerId
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        checkPermissions()
        startMeshSyncService()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MeshSyncService.LocalBinder
            syncService = binder?.getService()
            isBound = true

            // Forward discovered peers from background service to repository and trigger sync on publish
            syncService?.let { s ->
                repository.onContentPublished = {
                    s.forceSyncAll()
                }
                repository.onPttBroadcast = { burst ->
                    s.broadcastPttBurst(burst)
                }
                s.getSyncEngine().addPeerIdentifiedListener { connectionPeerId, remoteAuthorId, remoteDisplayName ->
                    repository.updatePeerNameAndId(connectionPeerId, remoteAuthorId, remoteDisplayName)
                }
                lifecycleScope.launch {
                    s.allDiscoveredPeers.collectLatest { peers ->
                        repository.updateDiscoveredPeers(peers)
                    }
                }
                lifecycleScope.launch {
                    s.getSyncEngine().syncEvents.collect { event ->
                        when (event) {
                            is com.mysocial.mesh.protocol.SyncEvent.Started -> {
                                // Routine background sync (omitted to avoid activity log spam)
                            }
                            is com.mysocial.mesh.protocol.SyncEvent.HandshakeCompleted -> {
                                // Routine connection handshake (omitted to avoid activity log spam)
                            }
                            is com.mysocial.mesh.protocol.SyncEvent.Progress -> {
                                // Handled internally
                            }
                            is com.mysocial.mesh.protocol.SyncEvent.Completed -> {
                                val name = repository.getPeerName(event.peerId)
                                if (event.entriesReceived > 0) {
                                    repository.logActivity("Received ${event.entriesReceived} new updates from $name")
                                }
                            }
                            is com.mysocial.mesh.protocol.SyncEvent.BlobProgress -> {
                                // Background chunk streaming
                            }
                            is com.mysocial.mesh.protocol.SyncEvent.BlobCompleted -> {
                                val name = repository.getPeerName(event.peerId)
                                repository.logActivity("Received photo from $name")
                            }
                            is com.mysocial.mesh.protocol.SyncEvent.Failed -> {
                                // Routine background connection retries (omitted from UI log)
                            }
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            syncService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Core Dependencies
        val db = MeshDatabase.getInstance(this)
        val cryptoManager = CryptoManager.getInstance(this)
        val initialName = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "User"
        val peerApkUpdateManager = com.mysocial.mesh.update.PeerApkUpdateManager.getInstance(this)
        val syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoManager,
            myDisplayName = initialName,
            openMeshMode = true,
            context = this,
            peerApkUpdateManager = peerApkUpdateManager
        )
        val appSettings = com.mysocial.mesh.data.AppSettings.getInstance(this)
        repository = MeshRepository(db, cryptoManager, syncEngine, appSettings)

        // Seed initial default profile if already onboarded
        if (appSettings.hasCompletedOnboarding.value) {
            lifecycleScope.launch(Dispatchers.IO) {
                if (db.profileDao().getProfile(cryptoManager.authorId) == null) {
                    repository.updateProfile(
                        displayName = initialName,
                        bio = "Connecting with friends offline on MySocial",
                        avatarSeed = cryptoManager.authorId
                    )
                }
            }
        }

        // Check Permissions and Start Foreground Service only if already onboarded
        checkPermissions()
        if (appSettings.hasCompletedOnboarding.value) {
            if (hasPermissions) {
                startMeshSyncService()
            } else {
                requestMeshPermissions()
            }
        }

        // Check for updates asynchronously on app launch
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updateMgr = UpdateManager(this@MainActivity)
                val info = updateMgr.checkForUpdate()
                if (info != null && info.isUpdateAvailable) {
                    pendingUpdateInfo = info
                }
            } catch (_: Exception) {}
        }

        val initialTab = intent?.getIntExtra("open_tab", -1) ?: -1
        if (initialTab != -1) {
            targetTab = initialTab
        }
        val initialChatPeerId = intent?.getStringExtra("open_chat_peer_id")
        if (!initialChatPeerId.isNullOrBlank()) {
            pendingChatPeerId = initialChatPeerId
        }

        setContent {
            val themeMode by appSettings.themeMode.collectAsState()
            val accentColor by appSettings.accentColor.collectAsState()
            val hasCompletedOnboarding by appSettings.hasCompletedOnboarding.collectAsState()

            MySocialTheme(themeMode = themeMode, accentColor = accentColor) {
                if (!hasCompletedOnboarding) {
                    com.mysocial.mesh.ui.onboarding.OnboardingScreen(
                        currentAuthorId = repository.cryptoManager.authorId,
                        defaultName = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Neighbor",
                        onFinishOnboarding = { chosenName, chosenBio ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                repository.updateProfile(
                                    displayName = chosenName,
                                    bio = chosenBio,
                                    avatarSeed = repository.cryptoManager.authorId
                                )
                                repository.syncEngine.myDisplayName = chosenName
                                appSettings.setAcceptedTerms(true)
                                appSettings.setCompletedOnboarding(true)
                            }
                            if (!hasPermissions) {
                                requestMeshPermissions()
                            } else {
                                startMeshSyncService()
                            }
                        }
                    )
                } else {
                    MainAppScaffold()
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = getRequiredPermissions()
        hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestMeshPermissions() {
        permissionLauncher.launch(getRequiredPermissions())
    }

    private fun getRequiredPermissions(): Array<String> {
        val list = mutableListOf<String>()

        list.add(Manifest.permission.CAMERA)
        list.add(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        return list.toTypedArray()
    }

    private fun startMeshSyncService() {
        MeshSyncService.start(this)
        val intent = Intent(this, MeshSyncService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    @Composable
    fun MainAppScaffold() {
        var selectedTab by remember { mutableIntStateOf(targetTab) }
        LaunchedEffect(targetTab) {
            selectedTab = targetTab
        }
        var showComposer by remember { mutableStateOf(false) }
        var currentSettingsSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }

        val peerApkUpdateManager = remember { com.mysocial.mesh.update.PeerApkUpdateManager.getInstance(this@MainActivity) }
        val availablePeerUpdate by peerApkUpdateManager.availableUpdate.collectAsState()
        val isDownloadingPeerUpdate by peerApkUpdateManager.isDownloading.collectAsState()
        val peerDownloadProgress by peerApkUpdateManager.downloadProgress.collectAsState()

        var showMessagesList by remember { mutableStateOf(false) }
        var activeChatPeer by remember { mutableStateOf<Pair<String, String>?>(null) }
        var activeProfilePeer by remember { mutableStateOf<Pair<String, String>?>(null) }

        LaunchedEffect(pendingChatPeerId) {
            val peerId = pendingChatPeerId
            if (peerId != null) {
                val peerName = repository.getPeerDisplayName(peerId)
                activeChatPeer = Pair(peerId, peerName)
                pendingChatPeerId = null
            }
        }

        val feedViewModel = remember { FeedViewModel(repository) }
        val composerViewModel = remember { ComposerViewModel(repository) }
        val radarViewModel = remember { RadarViewModel(repository, getService = { syncService }) }
        val identityViewModel = remember { IdentityViewModel(repository) }
        val settingsViewModel = remember { SettingsViewModel(repository) }

        val updateScope = rememberCoroutineScope()
        val updateManager = remember { UpdateManager(this@MainActivity) }
        var isDownloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf<Float?>(null) }
        val totalUnreadCount by repository.observeTotalUnreadMessagesCount().collectAsState(initial = 0)

        if (pendingUpdateInfo != null) {
            UpdateDialog(
                updateInfo = pendingUpdateInfo!!,
                downloadProgress = downloadProgress,
                isDownloading = isDownloadingUpdate,
                onConfirmUpdate = {
                    val url = pendingUpdateInfo?.downloadUrl
                    if (!url.isNullOrBlank()) {
                        updateScope.launch {
                            isDownloadingUpdate = true
                            val file = updateManager.downloadApk(url) { progress ->
                                downloadProgress = progress
                            }
                            isDownloadingUpdate = false
                            if (file != null) {
                                pendingUpdateInfo = null
                                updateManager.promptInstall(file)
                            }
                        }
                    }
                },
                onDismiss = {
                    if (!isDownloadingUpdate) {
                        pendingUpdateInfo = null
                    }
                }
            )
        }

        Scaffold(
            bottomBar = {
                if (currentSettingsSubScreen == null && activeChatPeer == null && activeProfilePeer == null) {
                    Surface(
                        color = FbSurface,
                        shadowElevation = 6.dp
                    ) {
                        Column {
                            HorizontalDivider(color = FbBorder, thickness = 0.5.dp)
                            NavigationBar(
                                containerColor = FbSurface,
                                contentColor = FbTextPrimary,
                                tonalElevation = 0.dp
                            ) {
                                NavigationTab.values().forEachIndexed { index, tab ->
                                    val isSelected = selectedTab == index
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            if (tab == NavigationTab.CHATS && totalUnreadCount > 0) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge(
                                                            containerColor = FbRed,
                                                            contentColor = Color.White
                                                        ) {
                                                            Text(
                                                                text = if (totalUnreadCount > 99) "99+" else "$totalUnreadCount",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.title,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.title,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = tab.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = FacebookBlue,
                                            selectedTextColor = FacebookBlue,
                                            indicatorColor = FacebookBlueLight,
                                            unselectedIconColor = FbTextSecondary,
                                            unselectedTextColor = FbTextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (activeChatPeer != null) {
                    val peer = activeChatPeer!!
                    val chatViewModel = remember(peer.first) {
                        ChatViewModel(peer.first, repository)
                    }
                    ChatScreen(
                        viewModel = chatViewModel,
                        peerDisplayName = peer.second,
                        onNavigateBack = { activeChatPeer = null }
                    )
                } else if (activeProfilePeer != null) {
                    val peer = activeProfilePeer!!
                    BackHandler { activeProfilePeer = null }
                    PeerProfileScreen(
                        peerAuthorId = peer.first,
                        peerDisplayName = peer.second,
                        repository = repository,
                        onNavigateBack = { activeProfilePeer = null },
                        onOpenChat = { peerId, peerName ->
                            activeChatPeer = Pair(peerId, peerName)
                        }
                    )
                } else if (currentSettingsSubScreen != null) {
                    BackHandler { currentSettingsSubScreen = null }
                    when (currentSettingsSubScreen) {
                        SettingsSubScreen.APPEARANCE -> AppearanceScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { currentSettingsSubScreen = null }
                        )
                        SettingsSubScreen.MESH_SYNC -> MeshSyncScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { currentSettingsSubScreen = null }
                        )
                        SettingsSubScreen.MULE_STORAGE -> MuleStorageScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { currentSettingsSubScreen = null }
                        )
                        SettingsSubScreen.ABOUT -> AboutScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { currentSettingsSubScreen = null }
                        )
                        null -> {}
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Permission Warning Banner if permissions are missing
                        AnimatedVisibility(visible = !hasPermissions) {
                            PermissionWarningBanner(onRequestPermissions = { requestMeshPermissions() })
                        }

                        // P2P APK Auto-Update Notification Banner
                        AnimatedVisibility(visible = availablePeerUpdate != null) {
                            availablePeerUpdate?.let { update ->
                                PeerUpdateReadyBanner(
                                    updateInfo = update,
                                    onInstall = { peerApkUpdateManager.installUpdate(this@MainActivity, update) },
                                    onDismiss = { peerApkUpdateManager.dismissUpdate() }
                                )
                            }
                        }

                        // P2P APK Downloading in Background Banner
                        AnimatedVisibility(visible = isDownloadingPeerUpdate) {
                            PeerUpdateDownloadingBanner(progress = peerDownloadProgress)
                        }

                        // Screen Body
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                0 -> FeedScreen(
                                    viewModel = feedViewModel,
                                    onNavigateToCompose = { mode ->
                                        composerViewModel.resetForMode(mode)
                                        showComposer = true
                                    },
                                    onNavigateToMessages = { selectedTab = 3 },
                                    onNavigateToChat = { peerId, peerName ->
                                        activeChatPeer = Pair(peerId, peerName)
                                    },
                                    onNavigateToAppearance = { currentSettingsSubScreen = SettingsSubScreen.APPEARANCE },
                                    onNavigateToMeshSync = { currentSettingsSubScreen = SettingsSubScreen.MESH_SYNC },
                                    onNavigateToMuleStorage = { currentSettingsSubScreen = SettingsSubScreen.MULE_STORAGE },
                                    onNavigateToAbout = { currentSettingsSubScreen = SettingsSubScreen.ABOUT },
                                    onRefresh = {
                                        feedViewModel.refresh {
                                            syncService?.forceSyncAll()
                                        }
                                    }
                                )
                                1 -> RadarScreen(
                                    viewModel = radarViewModel,
                                    onNavigateToPeerProfile = { peerId, peerName ->
                                        activeProfilePeer = Pair(peerId, peerName)
                                    },
                                    onNavigateToChat = { peerId, peerName ->
                                        activeChatPeer = Pair(peerId, peerName)
                                    }
                                )
                                2 -> {
                                    val walkieTalkieViewModel = remember { WalkieTalkieViewModel(repository) }
                                    WalkieTalkieScreen(viewModel = walkieTalkieViewModel)
                                }
                                3 -> {
                                    val messagesViewModel = remember { MessagesViewModel(repository) }
                                    MessagesListScreen(
                                        viewModel = messagesViewModel,
                                        onNavigateBack = { selectedTab = 0 },
                                        onOpenChat = { peerId, peerName ->
                                            activeChatPeer = Pair(peerId, peerName)
                                        }
                                    )
                                }
                                4 -> IdentityScreen(viewModel = identityViewModel)
                            }

                            // Fullscreen Facebook-style Composer Dialog
                            if (showComposer) {
                                Dialog(
                                    onDismissRequest = { showComposer = false },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    ComposerScreen(
                                        viewModel = composerViewModel,
                                        onPostPublished = { showComposer = false },
                                        onDismiss = { showComposer = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionWarningBanner(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FacebookBlueLight)
            .border(1.dp, FacebookBlue.copy(alpha = 0.2f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                tint = FacebookBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nearby permissions needed",
                    color = FacebookBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Allow nearby permissions to connect and share with people around you.",
                    color = FbTextPrimary,
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FacebookBlue,
                    contentColor = Color.White
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PeerUpdateReadyBanner(
    updateInfo: com.mysocial.mesh.update.PeerUpdateInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = com.mysocial.mesh.ui.theme.FbGreenLight),
        border = BorderStroke(1.dp, com.mysocial.mesh.ui.theme.FbGreen.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(com.mysocial.mesh.ui.theme.FbGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = com.mysocial.mesh.ui.theme.FbGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "App Update Ready (${updateInfo.remoteVersionName})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = FbTextPrimary
                )
                Text(
                    text = "Received offline from ${updateInfo.peerName}. Tap to install.",
                    fontSize = 11.sp,
                    color = com.mysocial.mesh.ui.theme.FbGreen
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = com.mysocial.mesh.ui.theme.FbGreen),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Update", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = FbTextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PeerUpdateDownloadingBanner(progress: Float?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = FacebookBlueLight),
        border = BorderStroke(1.dp, FacebookBlue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = FacebookBlue
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (progress != null) "Downloading app update from nearby peer (${(progress * 100).toInt()}%)..." else "Downloading app update from nearby peer...",
                fontSize = 12.sp,
                color = FacebookBlue,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
