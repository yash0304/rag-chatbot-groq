package com.mindquest.app.domain

import com.mindquest.app.data.AchievementEntity
import com.mindquest.app.data.CollectibleEntity
import com.mindquest.app.data.SkillEntity

/**
 * Original MindQuest lore catalogs. Ported from backend/app/services/gamification.py so the
 * offline app matches the web app's progression exactly. Seeding is idempotent (Repository
 * only inserts when the tables are empty), and unlock/acquire state lives on these same rows.
 */
object Catalogs {

    val achievements = listOf(
        AchievementEntity("first_light", "First Light", "Upload your first document to the archives.", "📜", 20),
        AchievementEntity("archivist", "Archivist", "Have 10 documents processed into the archives.", "🗄️", 50),
        AchievementEntity("lorekeeper", "Lorekeeper", "Have 50 documents processed into the archives.", "🏛️", 200),
        AchievementEntity("first_quest", "The First Step", "Complete your first quest.", "⚔️", 20),
        AchievementEntity("quest_veteran", "Quest Veteran", "Complete 25 quests.", "🛡️", 100),
        AchievementEntity("epic_slayer", "Epic Undertaking", "Complete an epic-difficulty quest.", "🐉", 100),
        AchievementEntity("week_of_iron", "Week of Iron", "Reach a 7-day streak on any daily mission.", "🔥", 60),
        AchievementEntity("month_of_iron", "Month of Iron", "Reach a 30-day streak on any daily mission.", "🌋", 250),
        AchievementEntity("seeker", "Seeker of Answers", "Consult the Narrator about your knowledge base.", "🔮", 15),
        AchievementEntity("cartographer", "Cartographer", "Chart 5 distinct knowledge domains.", "🗺️", 80),
        AchievementEntity("arc_closer", "End of an Arc", "Complete a full story arc (goal).", "📖", 120),
        AchievementEntity("level_5", "Rising Hero", "Reach level 5.", "⭐", 50),
        AchievementEntity("level_10", "Renowned Hero", "Reach level 10.", "🌟", 150),
    )

    /** achievement code -> collectible granted alongside it */
    val achievementCollectibles = mapOf(
        "first_light" to "ember_quill",
        "week_of_iron" to "iron_hourglass",
        "cartographer" to "astral_compass",
        "arc_closer" to "arcstone",
        "level_10" to "crown_of_daybreak",
    )

    val collectibles = listOf(
        CollectibleEntity("ember_quill", "Ember Quill", "rare", "A quill that glows when new knowledge enters the archive."),
        CollectibleEntity("iron_hourglass", "Iron Hourglass", "rare", "Forged from seven unbroken days; its sand never runs out."),
        CollectibleEntity("astral_compass", "Astral Compass", "epic", "Points not north, but toward the realm you have studied least."),
        CollectibleEntity("arcstone", "Arcstone", "epic", "A crystallized story arc — proof a long journey reached its end."),
        CollectibleEntity("crown_of_daybreak", "Crown of Daybreak", "legendary", "Worn only by those whose deeds fill ten levels of ledger."),
    )

    val skills = listOf(
        SkillEntity("scholar_1", "scholar", 1, "Keen Reading", "Highlights key terms in document summaries.", 1, null),
        SkillEntity("scholar_2", "scholar", 2, "Deep Recall", "Semantic search returns 2 extra results.", 2, "scholar_1"),
        SkillEntity("scholar_3", "scholar", 3, "Sage's Synthesis", "Weekly reviews include cross-document insights.", 3, "scholar_2"),
        SkillEntity("explorer_1", "explorer", 1, "Wayfinding", "Knowledge map shows unexplored domains.", 1, null),
        SkillEntity("explorer_2", "explorer", 2, "Trailblazer", "New domains grant a discovery banner.", 2, "explorer_1"),
        SkillEntity("explorer_3", "explorer", 3, "Realm Charter", "Unlock custom domain naming.", 3, "explorer_2"),
        SkillEntity("strategist_1", "strategist", 1, "Quartermaster", "Quest board shows XP forecasts.", 1, null),
        SkillEntity("strategist_2", "strategist", 2, "Campaign Planner", "AI generates quests tuned to your goals.", 2, "strategist_1"),
        SkillEntity("strategist_3", "strategist", 3, "Grand Strategist", "Story arcs get AI chapter outlines.", 3, "strategist_2"),
        SkillEntity("forger_1", "forger", 1, "Kindling", "Streak flames appear on the dashboard.", 1, null),
        SkillEntity("forger_2", "forger", 2, "Steady Flame", "Streak grace: one missed day per 14 kept.", 2, "forger_1"),
        SkillEntity("forger_3", "forger", 3, "Unbreakable", "Best-streak trophies on your character sheet.", 3, "forger_2"),
    )

    /** Difficulty -> XP reward (matches backend DIFFICULTY_XP). */
    val difficultyXp = mapOf(
        "trivial" to 10, "easy" to 25, "normal" to 50, "hard" to 100, "epic" to 250,
    )
}
