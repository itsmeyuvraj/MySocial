package com.mysocial.mesh

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoManagerAndroidTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("mysocial_keystore_identity", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testDeterministicDeviceSeedConsistency() {
        val seed1 = CryptoManager.getDeterministicDeviceSeed(context)
        val seed2 = CryptoManager.getDeterministicDeviceSeed(context)

        assertNotNull(seed1)
        assertEquals(32, seed1.size)
        assertArrayEquals("Calling getDeterministicDeviceSeed on same context must return identical seeds", seed1, seed2)
    }

    @Test
    fun testDeterministicDeviceSeedDifferentiatesDifferentDevices() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "device_aaa_111")
        val seedA = CryptoManager.getDeterministicDeviceSeed(context)

        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "device_bbb_222")
        val seedB = CryptoManager.getDeterministicDeviceSeed(context)

        assertEquals(32, seedA.size)
        assertEquals(32, seedB.size)
        assertFalse("Distinct device IDs must derive distinct cryptographic seeds", seedA.contentEquals(seedB))
    }

    @Test
    fun testDeterministicDeviceSeedFallbackWhenAndroidIdNullOrDefault() {
        // Null Android ID
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, null)
        val seedNull = CryptoManager.getDeterministicDeviceSeed(context)
        assertNotNull(seedNull)
        assertEquals(32, seedNull.size)

        // Default emulator bug ID: 9774d56d682e549c
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "9774d56d682e549c")
        val seedDefault = CryptoManager.getDeterministicDeviceSeed(context)
        assertNotNull(seedDefault)
        assertEquals(32, seedDefault.size)
    }

    @Test
    fun testReinstallIdentityPersistenceViaDeviceSeed() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "persistent_user_device_99")
        val seed = CryptoManager.getDeterministicDeviceSeed(context)

        // Install 1: Derive keypair from device seed
        val privParams1 = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seed, 0)
        val pubParams1 = privParams1.generatePublicKey()
        val authorId1 = CryptoManager.formatAuthorId(pubParams1.encoded)

        // Simulating second install after app was deleted and reinstalled on the same device
        val seedReinstall = CryptoManager.getDeterministicDeviceSeed(context)
        val privParams2 = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seedReinstall, 0)
        val pubParams2 = privParams2.generatePublicKey()
        val authorId2 = CryptoManager.formatAuthorId(pubParams2.encoded)

        // Identity must match 100%
        assertEquals("Author ID must remain strictly identical across reinstalls", authorId1, authorId2)
        assertArrayEquals("Public key bytes must match identically", pubParams1.encoded, pubParams2.encoded)
        assertArrayEquals("Private key bytes must match identically", privParams1.encoded, privParams2.encoded)

        // Sign with install 1, verify with install 2
        val alice1 = CryptoManager.createInMemory(seed)
        val alice2 = CryptoManager.createInMemory(seedReinstall)

        val payload = "Offline message signed before app was uninstalled"
        val signatureBeforeUninstall = alice1.sign(payload)

        // Reinstalled app verifies the signature using authorId2
        assertTrue(CryptoManager.verify(alice2.authorId, payload, signatureBeforeUninstall))

        // Direct message encrypted for install 1 can be decrypted by install 2
        val bob = CryptoManager.createInMemory()
        val dmPayload = bob.encryptDirectMessage(alice1.authorId, "Important peer message during uninstall window")
        val decryptedByAlice2 = alice2.decryptDirectMessage(dmPayload)
        assertEquals("Important peer message during uninstall window", decryptedByAlice2)
    }
}
