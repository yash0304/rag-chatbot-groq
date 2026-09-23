package com.mindquest.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.ProfileEntity

/**
 * The game side of the app on one tab: personal bests, the weekly review, charts, badges and
 * skills used to be six drawer entries. They are things you look at, not places you work, so
 * they share a screen and a row of sub-tabs.
 */
@Composable
fun ProgressScreen(repo: MindQuestRepository, profile: ProfileEntity?, notify: (String) -> Unit) {
    val tabs = listOf("Overview", "Review", "Stats", "Deeds", "Skills")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val xp = profile?.xp ?: 0L
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Rune,
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Column {
                    profile?.let { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { XpBar(it) } }
                    PersonalBestsScreen(repo, xp)
                }
                1 -> WeeklyReviewScreen(repo, notify)
                2 -> AnalyticsScreen(repo, xp)
                3 -> AchievementsScreen(repo)
                else -> SkillsScreen(repo, profile?.skillPoints ?: 0, notify)
            }
        }
    }
}

/** Your documents, and the assistant that answers from them — two views of one library. */
@Composable
fun ArchivesHub(
    repo: MindQuestRepository,
    notify: (String) -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Rune,
        ) {
            Tab(selected = tab == 0, onClick = { onTab(0) }, text = { Text("📚 Library") })
            Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text("✨ Ask") })
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) ArchivesScreen(repo, notify) else NarratorScreen(repo, notify)
        }
    }
}
