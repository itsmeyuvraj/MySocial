package com.mysocial.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FacebookBlueLight
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbGreen
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbSurfaceElevated
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary

enum class ChangelogCategory(
    val label: String,
    val iconEmoji: String,
    val badgeBg: Color,
    val badgeText: Color
) {
    FEATURE(
        label = "FEATURE",
        iconEmoji = "✨",
        badgeBg = Color(0xFF0D3E24),
        badgeText = Color(0xFF34D399)
    ),
    IMPROVEMENT(
        label = "IMPROVED",
        iconEmoji = "⚡",
        badgeBg = Color(0xFF1E293B),
        badgeText = Color(0xFF38BDF8)
    ),
    FIX(
        label = "FIX",
        iconEmoji = "🐛",
        badgeBg = Color(0xFF3B1818),
        badgeText = Color(0xFFF87171)
    ),
    SECURITY(
        label = "SECURITY",
        iconEmoji = "🔒",
        badgeBg = Color(0xFF2E1065),
        badgeText = Color(0xFFA78BFA)
    ),
    OTHER(
        label = "UPDATE",
        iconEmoji = "📦",
        badgeBg = Color(0xFF262626),
        badgeText = Color(0xFFD4D4D4)
    )
}

data class ParsedChangelogItem(
    val category: ChangelogCategory,
    val text: String,
    val subItems: List<String> = emptyList()
)

@Composable
fun ChangelogView(
    releaseNotes: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 260.dp
) {
    val items = remember(releaseNotes) { parseReleaseNotes(releaseNotes) }

    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(FbSurfaceElevated)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Performance enhancements and stability fixes.",
                color = FbTextSecondary,
                fontSize = 13.sp
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(FbSurface)
            .border(1.dp, FbBorder, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            ChangelogItemRow(item = item)
        }
    }
}

@Composable
private fun ChangelogItemRow(item: ParsedChangelogItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Category Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(item.category.badgeBg)
                    .border(0.5.dp, item.category.badgeText.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${item.category.iconEmoji} ${item.category.label}",
                    color = item.category.badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Main Text
            Text(
                text = item.text,
                color = FbTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // Sub items (bullet points)
        if (item.subItems.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item.subItems.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(FbTextSecondary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sub,
                            color = FbTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

internal fun parseReleaseNotes(notes: String): List<ParsedChangelogItem> {
    if (notes.isBlank()) return emptyList()

    val rawLines = notes.lines()
    val validLines = mutableListOf<Pair<String, Boolean>>() // (cleanLine, isIndented)

    for (raw in rawLines) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) continue
        // Skip markdown headings
        if (trimmed.startsWith("#")) continue
        // Skip markdown horizontal dividers
        if (trimmed.matches(Regex("^[-*_]{3,}$"))) continue
        // Skip automated CI/CD footers and commit metadata
        if (trimmed.startsWith("*Automated", ignoreCase = true) ||
            trimmed.contains("Automated CI/CD", ignoreCase = true) ||
            trimmed.contains("Full Changelog", ignoreCase = true) ||
            trimmed.contains("github.com/", ignoreCase = true) ||
            trimmed.startsWith("Merge branch", ignoreCase = true) ||
            trimmed.startsWith("Merge pull request", ignoreCase = true)
        ) continue

        val isIndented = raw.startsWith("  ") || raw.startsWith("\t")
        validLines.add(Pair(trimmed, isIndented))
    }

    val items = mutableListOf<ParsedChangelogItem>()
    var currentItem: ParsedChangelogItem? = null
    val currentSubItems = mutableListOf<String>()

    for ((line, isIndented) in validLines) {
        val text = stripCommitPrefix(line)
        if (text.isBlank()) continue

        if (isIndented && currentItem != null) {
            currentSubItems.add(text)
        } else {
            if (currentItem != null) {
                items.add(currentItem.copy(subItems = currentSubItems.toList()))
                currentSubItems.clear()
            }
            val category = detectCategory(text)
            currentItem = ParsedChangelogItem(category, text)
        }
    }

    if (currentItem != null) {
        items.add(currentItem.copy(subItems = currentSubItems.toList()))
    }

    return items
}

internal fun detectCategory(line: String): ChangelogCategory {
    val lower = line.lowercase()
    return when {
        lower.startsWith("feat") || lower.contains("feature") || lower.startsWith("add") ||
            lower.contains("add ") || lower.contains("added") || lower.contains("new") ||
            lower.contains("support") || lower.contains("badge") || lower.contains("checkmark") -> ChangelogCategory.FEATURE
        lower.startsWith("fix") || lower.contains("bug") || lower.contains("resolve") ||
            lower.contains("crash") || lower.contains("patch") || lower.contains("restore") -> ChangelogCategory.FIX
        lower.contains("security") || lower.contains("keystore") || lower.contains("cve") ||
            lower.contains("verify") || lower.contains("private") || lower.contains("privacy") -> ChangelogCategory.SECURITY
        lower.startsWith("perf") || lower.startsWith("optimize") || lower.contains("performance") ||
            lower.startsWith("refactor") || lower.startsWith("improve") || lower.contains("clean") ||
            lower.contains("simplif") || lower.contains("human") || lower.contains("friendly") ||
            lower.contains("faster") || lower.contains("speed") -> ChangelogCategory.IMPROVEMENT
        else -> ChangelogCategory.OTHER
    }
}

internal fun stripCommitPrefix(line: String): String {
    var text = line.trim()
    // Strip bullet markers
    text = text.replace(Regex("^[-*•+]\\s*"), "")
    text = text.replace(Regex("^\\d+\\.\\s*"), "")
    // Strip conventional commit prefix
    text = text.replace(Regex("^(feat|fix|perf|refactor|style|docs|test|build|ci|chore|add|update|remove|revert)(\\([a-zA-Z0-9_.-]+\\))?:\\s*", RegexOption.IGNORE_CASE), "")
    // Strip markdown formatting
    text = text.replace(Regex("`([^`]+)`"), "$1")
    text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
    // Strip trailing commit hashes like (abc1234) or `abc1234`
    text = text.replace(Regex("\\s*\\([0-9a-f]{7,40}\\)\\s*$"), "")
    text = text.replace(Regex("\\s*by\\s+@[a-zA-Z0-9_-]+\\s*$"), "")
    text = text.trim()
    return if (text.isNotEmpty()) {
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } else {
        line
    }
}
