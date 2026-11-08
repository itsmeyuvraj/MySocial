package com.mysocial.mesh

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.ui.feed.FeedViewModel
import com.mysocial.mesh.ui.settings.SettingsViewModel
import com.mysocial.mesh.ui.theme.AmoledFbColorScheme
import com.mysocial.mesh.ui.theme.DarkFbColorScheme
import com.mysocial.mesh.ui.theme.LightFbColorScheme
import com.mysocial.mesh.ui.theme.withAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppearanceAndDeclutterTest {

    private lateinit var context: Context
    private lateinit var appSettings: AppSettings
    private lateinit var repository: MeshRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("mysocial_user_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        AppSettings.resetInstanceForTesting()
        appSettings = AppSettings.getInstance(context)

        val db = MeshDatabase.buildInMemory(context)
        val crypto = CryptoManager.createInMemory(seed = ByteArray(32) { 42 })
        val syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = crypto,
            myDisplayName = "Tester",
            openMeshMode = true,
            appSettings = appSettings
        )
        repository = MeshRepository(
            db = db,
            cryptoManager = crypto,
            syncEngine = syncEngine,
            appSettings = appSettings
        )
    }

    @After
    fun tearDown() {
        AppSettings.resetInstanceForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun testThemeModePersistenceAndSyncWithDarkMode() {
        // Default theme is DARK
        assertEquals(AppSettings.THEME_DARK, appSettings.themeMode.value)
        assertTrue(appSettings.isDarkMode.value)

        // Switch to LIGHT
        appSettings.setThemeMode(AppSettings.THEME_LIGHT)
        assertEquals(AppSettings.THEME_LIGHT, appSettings.themeMode.value)
        assertFalse(appSettings.isDarkMode.value)

        // Switch to AMOLED
        appSettings.setThemeMode(AppSettings.THEME_AMOLED)
        assertEquals(AppSettings.THEME_AMOLED, appSettings.themeMode.value)
        assertTrue(appSettings.isDarkMode.value)

        // Switch to SYSTEM
        appSettings.setThemeMode(AppSettings.THEME_SYSTEM)
        assertEquals(AppSettings.THEME_SYSTEM, appSettings.themeMode.value)

        // Invalid fallback
        appSettings.setThemeMode("UNKNOWN_THEME")
        assertEquals(AppSettings.THEME_DARK, appSettings.themeMode.value)
    }

    @Test
    fun testAccentColorPersistence() {
        // Default is BLUE
        assertEquals(AppSettings.ACCENT_BLUE, appSettings.accentColor.value)

        // Set EMERALD
        appSettings.setAccentColor(AppSettings.ACCENT_EMERALD)
        assertEquals(AppSettings.ACCENT_EMERALD, appSettings.accentColor.value)

        // Set PURPLE
        appSettings.setAccentColor(AppSettings.ACCENT_PURPLE)
        assertEquals(AppSettings.ACCENT_PURPLE, appSettings.accentColor.value)

        // Set ROSE
        appSettings.setAccentColor(AppSettings.ACCENT_ROSE)
        assertEquals(AppSettings.ACCENT_ROSE, appSettings.accentColor.value)

        // Set AMBER
        appSettings.setAccentColor(AppSettings.ACCENT_AMBER)
        assertEquals(AppSettings.ACCENT_AMBER, appSettings.accentColor.value)

        // Invalid fallback
        appSettings.setAccentColor("INVALID_ACCENT")
        assertEquals(AppSettings.ACCENT_BLUE, appSettings.accentColor.value)
    }

    @Test
    fun testCompactFeedModeAndHopBadgesPersistence() {
        // Default compact mode is false
        assertFalse(appSettings.compactFeedMode.value)
        // Default hop badges is true
        assertTrue(appSettings.showHopBadges.value)

        // Toggle compact mode
        appSettings.setCompactFeedMode(true)
        assertTrue(appSettings.compactFeedMode.value)

        // Toggle hop badges
        appSettings.setShowHopBadges(false)
        assertFalse(appSettings.showHopBadges.value)
    }

    @Test
    fun testAmoledColorSchemeHasPitchBlackBackground() {
        assertEquals(Color(0xFF000000), AmoledFbColorScheme.background)
        assertTrue(AmoledFbColorScheme.isDark)
    }

    @Test
    fun testDynamicAccentColorTransformation() {
        val darkEmerald = DarkFbColorScheme.withAccent(AppSettings.ACCENT_EMERALD)
        assertEquals(Color(0xFF10B981), darkEmerald.blue)

        val lightPurple = LightFbColorScheme.withAccent(AppSettings.ACCENT_PURPLE)
        assertEquals(Color(0xFF7C3AED), lightPurple.blue)

        val darkRose = DarkFbColorScheme.withAccent(AppSettings.ACCENT_ROSE)
        assertEquals(Color(0xFFF43F5E), darkRose.blue)

        val darkAmber = DarkFbColorScheme.withAccent(AppSettings.ACCENT_AMBER)
        assertEquals(Color(0xFFF59E0B), darkAmber.blue)

        val defaultBlue = DarkFbColorScheme.withAccent(AppSettings.ACCENT_BLUE)
        assertEquals(DarkFbColorScheme.blue, defaultBlue.blue)
    }

    @Test
    fun testSettingsViewModelReactivity() {
        val settingsVm = SettingsViewModel(repository)

        settingsVm.setThemeMode(AppSettings.THEME_AMOLED)
        assertEquals(AppSettings.THEME_AMOLED, settingsVm.themeMode.value)

        settingsVm.setAccentColor(AppSettings.ACCENT_ROSE)
        assertEquals(AppSettings.ACCENT_ROSE, settingsVm.accentColor.value)

        settingsVm.setCompactFeedMode(true)
        assertTrue(settingsVm.compactFeedMode.value)

        settingsVm.setShowHopBadges(false)
        assertFalse(settingsVm.showHopBadges.value)
    }

    @Test
    fun testFeedViewModelObservesCompactAndHopBadges() {
        val feedVm = FeedViewModel(repository)

        // Initial values
        assertFalse(feedVm.compactFeedMode.value)
        assertTrue(feedVm.showHopBadges.value)

        // Update settings
        appSettings.setCompactFeedMode(true)
        appSettings.setShowHopBadges(false)

        assertTrue(feedVm.compactFeedMode.value)
        assertFalse(feedVm.showHopBadges.value)
    }
}
