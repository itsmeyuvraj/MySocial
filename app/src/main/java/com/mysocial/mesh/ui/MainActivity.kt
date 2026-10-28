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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import com.mysocial.mesh.ui.radar.RadarScreen
import com.mysocial.mesh.ui.radar.RadarViewModel
import com.mysocial.mesh.ui.components.ShareApkBottomSheet
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

enum class NavigationTab(val title: String, val icon: ImageVector) {
    FEED("Feed", Icons.Filled.Home),
    RADAR("People Nearby", Icons.Filled.People),
    IDENTITY("Profile", Icons.Filled.Person)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra("open_tab", -1)
        if (tab != -1) {
            targetTab = tab
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
                                repository.logActivity("Downloaded high-res media from $name")
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
        val syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoManager,
            myDisplayName = initialName,
            openMeshMode = true
        )
        val appSettings = com.mysocial.mesh.data.AppSettings.getInstance(this)
        repository = MeshRepository(db, cryptoManager, syncEngine, appSettings)

        // Seed initial default profile if not set yet
        lifecycleScope.launch(Dispatchers.IO) {
            if (db.profileDao().getProfile(cryptoManager.authorId) == null) {
                repository.updateProfile(
                    displayName = initialName,
                    bio = "Connecting on MySocial offline mesh",
                    avatarSeed = cryptoManager.authorId
                )
            }
        }

        // Check Permissions and Start Foreground Service
        checkPermissions()
        if (hasPermissions) {
            startMeshSyncService()
        } else {
            requestMeshPermissions()
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

        setContent {
            val isDarkMode by appSettings.isDarkMode.collectAsState()
            MySocialTheme(darkTheme = isDarkMode) {
                MainAppScaffold()
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
        if (isFinishing) {
            MeshSyncService.stop(this)
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
        var showShareApkSheet by remember { mutableStateOf(false) }

        val feedViewModel = remember { FeedViewModel(repository) }
        val composerViewModel = remember { ComposerViewModel(repository) }
        val radarViewModel = remember { RadarViewModel(repository, getService = { syncService }) }
        val identityViewModel = remember { IdentityViewModel(repository) }
        val settingsViewModel = remember { SettingsViewModel(repository) }

        val updateScope = rememberCoroutineScope()
        val updateManager = remember { UpdateManager(this@MainActivity) }
        var isDownloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf<Float?>(null) }

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
                if (currentSettingsSubScreen == null) {
                    NavigationBar(
                        containerColor = FbSurface,
                        contentColor = FbTextPrimary,
                        tonalElevation = 6.dp
                    ) {
                        NavigationTab.values().forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(24.dp)
                                    )
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
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (currentSettingsSubScreen != null) {
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

                        // Screen Body
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                0 -> FeedScreen(
                                    viewModel = feedViewModel,
                                    onNavigateToCompose = { showComposer = true },
                                    onNavigateToAppearance = { currentSettingsSubScreen = SettingsSubScreen.APPEARANCE },
                                    onNavigateToMeshSync = { currentSettingsSubScreen = SettingsSubScreen.MESH_SYNC },
                                    onNavigateToMuleStorage = { currentSettingsSubScreen = SettingsSubScreen.MULE_STORAGE },
                                    onNavigateToShareApk = { showShareApkSheet = true },
                                    onNavigateToAbout = { currentSettingsSubScreen = SettingsSubScreen.ABOUT },
                                    onRefresh = {
                                        feedViewModel.refresh {
                                            syncService?.forceSyncAll()
                                        }
                                    }
                                )
                                1 -> RadarScreen(viewModel = radarViewModel)
                                2 -> IdentityScreen(viewModel = identityViewModel)
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

                if (showShareApkSheet) {
                    ShareApkBottomSheet(onDismiss = { showShareApkSheet = false })
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
                    text = "Nearby permissions required",
                    color = FacebookBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Allow nearby connections to connect with people around you.",
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
