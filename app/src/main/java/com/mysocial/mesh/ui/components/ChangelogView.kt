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

private fun parseReleaseNotes(notes: String): List<ParsedChangelogItem> {
    if (notes.isBlank()) return emptyList()

    val lines = notes.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("---") && !it.startsWith("### What's New") && !it.startsWith("*Automated") }

    val items = mutableListOf<ParsedChangelogItem>()
    var currentItem: ParsedChangelogItem? = null
    val currentSubItems = mutableListOf<String>()

    for (line in lines) {
        val clean = line.trimStart('-', '*', '•', ' ').trim()
        if (clean.isBlank()) continue

        // Check if sub-bullet
        if (line.startsWith("  -") || line.startsWith("    -") || (currentItem != null && !isCommitOrCategoryLine(clean))) {
            currentSubItems.add(clean)
            continue
        }

        if (currentItem != null) {
            items.add(currentItem.copy(subItems = currentSubItems.toList()))
            currentSubItems.clear()
        }

        val category = detectCategory(clean)
        val text = stripCommitPrefix(clean)
        currentItem = ParsedChangelogItem(category, text)
    }

    if (currentItem != null) {
        items.add(currentItem.copy(subItems = currentSubItems.toList()))
    }

    return items
}

private fun isCommitOrCategoryLine(line: String): Boolean {
    val lower = line.lowercase()
    return lower.startsWith("feat") || lower.startsWith("fix") || lower.startsWith("perf") ||
            lower.startsWith("refactor") || lower.startsWith("style") || lower.startsWith("docs") ||
            lower.startsWith("build") || lower.startsWith("ci") || lower.startsWith("security") ||
            lower.startsWith("add") || lower.startsWith("improve") || lower.startsWith("update")
}

private fun detectCategory(line: String): ChangelogCategory {
    val lower = line.lowercase()
    return when {
        lower.startsWith("feat") || lower.contains("feature") || lower.startsWith("add ") -> ChangelogCategory.FEATURE
        lower.startsWith("fix") || lower.contains("bug") || lower.contains("resolve") -> ChangelogCategory.FIX
        lower.startsWith("perf") || lower.startsWith("optimize") || lower.contains("performance") || lower.startsWith("refactor") -> ChangelogCategory.IMPROVEMENT
        lower.contains("security") || lower.contains("cve") || lower.contains("keystore") -> ChangelogCategory.SECURITY
        else -> ChangelogCategory.OTHER
    }
}

private fun stripCommitPrefix(line: String): String {
    val regex = Regex("^(feat|fix|perf|refactor|style|docs|test|build|ci)(\\([a-zA-Z0-9_-]+\\))?:\\s*", RegexOption.IGNORE_CASE)
    return line.replace(regex, "").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
