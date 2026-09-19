package com.mindquest.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.FolderEntity
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
 * The folders you made yourself — "Tuesday vegetable market", "Diwali gifts".
 *
 * These sit above the category row because they are the ones you named: a category is the
 * app's guess at where a line belongs, a folder is your own decision, and a decision should
 * not have to be looked for underneath a guess. Selecting one narrows the Inbox to it and
 * makes everything typed or spoken afterwards land inside it, which is what turns a folder
 * into a checklist rather than just another filter.
 */
@Composable
fun CustomFolderRow(
    folders: List<FolderEntity>,
    counts: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onRename: (FolderEntity) -> Unit = {},
    onDelete: (FolderEntity) -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        folders.forEach { folder ->
            val n = counts[folder.id] ?: 0
            FilterChip(
                selected = selected == folder.id,
                onClick = { onSelect(if (selected == folder.id) null else folder.id) },
                label = {
                    Text(
                        "${folder.icon} ${folder.name} ($n)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            // Rename and delete live next to the open folder rather than behind a long-press:
            // a hidden gesture is a feature nobody finds.
            if (selected == folder.id) {
                var menu by remember(folder.id) { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { menu = true },
                        label = { Text("⋯", style = MaterialTheme.typography.labelSmall) },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menu = false; onRename(folder) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete folder") },
                            onClick = { menu = false; onDelete(folder) },
                        )
                    }
                }
            }
        }
        AssistChip(
            onClick = onCreate,
            label = { Text("+ New folder", style = MaterialTheme.typography.labelSmall) },
        )
    }
}

/**
 * Name-a-folder dialog. One field, because a folder is a name and nothing else — the icon is
 * picked from the name so that "Tuesday vegetable market" arrives already looking like a
 * shopping list without asking you to choose a picture first.
 */
@Composable
fun NewFolderDialog(
    initial: String = "",
    title: String = "New folder",
    confirm: String = "Create",
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Give it a name and everything you add while it's open goes inside it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Tuesday vegetable market") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
