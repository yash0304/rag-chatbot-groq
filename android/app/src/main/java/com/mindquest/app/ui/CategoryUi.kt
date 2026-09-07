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
