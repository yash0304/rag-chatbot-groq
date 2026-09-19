package com.mindquest.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mindquest.app.domain.Categories

/**
 * Shared category widgets. Inbox, Archives and Quests all show the same chip and the same
 * filter row — one implementation so the three screens can't drift apart in look or
 * behaviour as they get edited.
 */

/** Tappable category chip. Tapping opens the picker so a wrong guess is one tap from fixed. */
@Composable
fun CategoryChip(categoryId: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = Categories.of(categoryId)
    Box {
        AssistChip(
            onClick = { open = true },
            label = {
                Text(
                    "${current.icon} ${current.label}",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Categories.all.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.icon} ${category.label}") },
                    onClick = { open = false; onPick(category.id) },
                )
            }
        }
    }
}

/**
 * The Inbox folders: one per category that actually holds something, with its open count.
 *
 * Selecting a folder narrows the list to it; selecting it again goes back to everything.
 * Folders are derived from the categories already on the notes rather than being created
 * and managed separately — there is nothing to file into, and nothing to leave empty.
 */
@Composable
fun FolderRow(
    counts: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (counts.isEmpty()) return
    val total = counts.values.sum()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("🗃 All ($total)", style = MaterialTheme.typography.labelSmall) },
        )
        Categories.all.filter { counts.containsKey(it.id) }.forEach { category ->
            val n = counts.getValue(category.id)
            FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(if (selected == category.id) null else category.id) },
                label = {
                    Text(
                        "${category.icon} ${category.label} ($n)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

/**
 * Horizontal filter row. Only categories actually present are offered — an empty filter
 * that returns nothing is worse than no filter at all.
 */
@Composable
fun CategoryFilterRow(
    present: Set<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (present.size < 2) return // nothing to narrow down
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All", style = MaterialTheme.typography.labelSmall) },
        )
        Categories.all.filter { it.id in present }.forEach { category ->
            FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(if (selected == category.id) null else category.id) },
                label = {
                    Text(
                        "${category.icon} ${category.label}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}
