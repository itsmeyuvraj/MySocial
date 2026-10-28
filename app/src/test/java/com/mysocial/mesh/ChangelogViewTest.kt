package com.mysocial.mesh

import com.mysocial.mesh.ui.components.ChangelogCategory
import com.mysocial.mesh.ui.components.detectCategory
import com.mysocial.mesh.ui.components.parseReleaseNotes
import com.mysocial.mesh.ui.components.stripCommitPrefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogViewTest {

    @Test
    fun testParseReleaseNotes_withMarkdownHeaderAndAutomatedFooter() {
        val rawNotes = """
            ### 🚀 What's New in MySocial
            fix: use shared debug keystore for update compatibility and resolve radar peer visibility

            ---
            *Automated CI/CD build for commit `432161945d68f1c8460513b8103d69cc430edc9a`.*
        """.trimIndent()

        val items = parseReleaseNotes(rawNotes)
        assertEquals(1, items.size)

        val item = items[0]
        assertEquals(ChangelogCategory.FIX, item.category)
        assertEquals("Use shared debug keystore for update compatibility and resolve radar peer visibility", item.text)
        assertTrue(item.subItems.isEmpty())
    }

    @Test
    fun testParseReleaseNotes_withConventionalCommitPrefixes() {
        val rawNotes = "refactor(ui): replace technical jargon with human-friendly words across all screens and notifications"
        val items = parseReleaseNotes(rawNotes)

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(ChangelogCategory.IMPROVEMENT, item.category)
        assertEquals("Replace technical jargon with human-friendly words across all screens and notifications", item.text)
    }

    @Test
    fun testParseReleaseNotes_withBulletsAndIndentedSubBullets() {
        val rawNotes = """
            ## What's Changed
            * Added verified blue checkmark
            * Simplified settings menu
              - Moved mule storage to settings
              - Removed redundant buttons
            * Fixed radar peer visibility
        """.trimIndent()

        val items = parseReleaseNotes(rawNotes)
        assertEquals(3, items.size)

        // Item 1
        assertEquals("Added verified blue checkmark", items[0].text)
        assertEquals(ChangelogCategory.FEATURE, items[0].category)
        assertTrue(items[0].subItems.isEmpty())

        // Item 2
        assertEquals("Simplified settings menu", items[1].text)
        assertEquals(ChangelogCategory.IMPROVEMENT, items[1].category)
        assertEquals(2, items[1].subItems.size)
        assertEquals("Moved mule storage to settings", items[1].subItems[0])
        assertEquals("Removed redundant buttons", items[1].subItems[1])

        // Item 3 (Must NOT be swallowed as sub-item of Item 2)
        assertEquals("Fixed radar peer visibility", items[2].text)
        assertEquals(ChangelogCategory.FIX, items[2].category)
        assertTrue(items[2].subItems.isEmpty())
    }

    @Test
    fun testStripCommitPrefix_cleansMarkdownAndHashes() {
        val input = "feat(radar): added `PeerCard` badge with **bold** text (d4c6779)"
        val cleaned = stripCommitPrefix(input)
        assertEquals("Added PeerCard badge with bold text", cleaned)
    }

    @Test
    fun testDetectCategory_keywordMatching() {
        assertEquals(ChangelogCategory.FEATURE, detectCategory("Added verified blue checkmark"))
        assertEquals(ChangelogCategory.FIX, detectCategory("Fixed radar peer visibility issue"))
        assertEquals(ChangelogCategory.SECURITY, detectCategory("Updated keystore signing certificate"))
        assertEquals(ChangelogCategory.IMPROVEMENT, detectCategory("Simplified menus and replaced technical jargon"))
    }
}
