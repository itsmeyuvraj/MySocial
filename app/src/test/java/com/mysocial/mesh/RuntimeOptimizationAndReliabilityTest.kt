package com.mysocial.mesh

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.PostViewEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeOptimizationAndReliabilityTest {

    private lateinit var db: MeshDatabase
    private lateinit var crypto: CryptoManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        crypto = CryptoManager.createInMemory(seed = ByteArray(32) { 42 })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBitmapDownsamplingCalculationForHighMegapixelImages() {
        // Simulating a 48 Megapixel camera image (8000 x 6000 px)
        val options48Mp = BitmapFactory.Options().apply {
            outWidth = 8000
            outHeight = 6000
        }
        val targetDim = 2560
        val sampleSize48Mp = MediaProcessor.calculateInSampleSize(options48Mp, targetDim, targetDim)
        // Expected sample size should scale down by at least 2x (4x for 8000x6000)
        assertTrue("InSampleSize for 8000x6000 should be >= 2, got: $sampleSize48Mp", sampleSize48Mp >= 2)

        // Simulating standard image (1600 x 1200 px) - should remain 1:1
        val optionsStandard = BitmapFactory.Options().apply {
            outWidth = 1600
            outHeight = 1200
        }
        val sampleSizeStandard = MediaProcessor.calculateInSampleSize(optionsStandard, targetDim, targetDim)
        assertEquals(1, sampleSizeStandard)
    }

    @Test
    fun testIndexedMediaBlobHashLookupInDatabase() = runBlocking {
        val post = PostViewEntity(
            entryHash = "test_hash_blob_lookup",
            authorId = crypto.authorId,
            text = "Post with indexed media",
            mediaBlobHash = "sha256_mock_blob_12345",
            replyToHash = null,
            rootHash = null,
            timestamp = System.currentTimeMillis()
        )
        db.postViewDao().upsert(post)

        val retrieved = db.postViewDao().getPostByBlobHash("sha256_mock_blob_12345")
        assertNotNull("Should find post via indexed mediaBlobHash", retrieved)
        assertEquals("test_hash_blob_lookup", retrieved?.entryHash)
    }

    @Test
    fun testBoundedLruEvictionBehavior() {
        val lruSet = java.util.Collections.synchronizedSet(
            object : java.util.LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 50
                }
            }.let { java.util.Collections.newSetFromMap(it) }
        )

        for (i in 0 until 100) {
            lruSet.add("burst_$i")
        }

        assertEquals("LRU Set should be clamped to max 50 entries", 50, lruSet.size)
        // Oldest entries 0-49 should have been evicted
        assertTrue("Oldest burst_0 should be evicted", !lruSet.contains("burst_0"))
        // Most recent entries should be retained
        assertTrue("Recent burst_99 should be present", lruSet.contains("burst_99"))
    }
}
