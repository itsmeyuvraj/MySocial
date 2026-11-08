package com.mysocial.mesh

import com.mysocial.mesh.utils.toBbid
import com.mysocial.mesh.utils.toFormattedBbid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BbidUtilsTest {

    @Test
    fun testBbidGenerationDeterminism() {
        val authorId = "@D3LYRoVp9Z5E7a1m2c3b4k5j6h7g8f9e0d1c2b3a4s5d6f7g8h9j0k1l2=.ed25519"
        val bbid1 = authorId.toBbid()
        val bbid2 = authorId.toBbid()

        assertEquals(bbid1, bbid2)
        assertEquals(9, bbid1.length) // 4 chars + '-' + 4 chars = 9
        assertEquals('-', bbid1[4])

        val regex = Regex("^[0-9A-HJKMNP-Z]{4}-[0-9A-HJKMNP-Z]{4}$")
        assertTrue("BBID $bbid1 should match Crockford format", regex.matches(bbid1))
    }

    @Test
    fun testBbidStripsPrefixAndSuffix() {
        val raw = "D3LYRoVp9Z5E7a1m2c3b4k5j6h7g8f9e0d1c2b3a4s5d6f7g8h9j0k1l2="
        val full = "@$raw.ed25519"

        assertEquals(raw.toBbid(), full.toBbid())
        assertEquals("#" + raw.toBbid(), full.toFormattedBbid())
    }

    @Test
    fun testBbidExcludesConfusingCharacters() {
        val authorId = "@test_author_key.ed25519"
        val bbid = authorId.toBbid()

        // Crockford excludes I, L, O, U
        assertTrue(!bbid.contains('I'))
        assertTrue(!bbid.contains('L'))
        assertTrue(!bbid.contains('O'))
        assertTrue(!bbid.contains('U'))
    }
}

