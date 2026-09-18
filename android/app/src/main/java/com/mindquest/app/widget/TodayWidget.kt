package com.mindquest.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import com.mindquest.app.R
import com.mindquest.app.data.AgendaItem
import com.mindquest.app.data.MindQuestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget listing what is due today, as a typed checklist.
 *
 * The point of this one is that there is nothing to dismiss: a notification is gone the
 * moment it is swiped, whereas this sits on the home screen showing the same unfinished
 * list until the list actually changes.
 *
 * Tapping a row ticks it off in place — no need to open the app. The row is struck through
 * first and only disappears a beat later, because crossing a line off a list is the part
 * that feels like finishing something; watching it vanish instantly feels like a glitch.
 *
 * Rows are plain TextViews rather than a collection widget. A RemoteViewsService would
 * scroll, but for the handful of things due in a day this is far less machinery.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                drawAll(context, manager, struckId = null, awardedXp = 0)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TICK) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val isQuest = intent.getBooleanExtra(EXTRA_IS_QUEST, false)
        if (id.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = AppWidgetManager.getInstance(context)
                // Strike first, while the item is still in the list…
                drawAll(context, manager, struckId = id, awardedXp = 0)
                delay(STRIKE_LINGER_MS)
                // …then commit it and redraw without it.
                val xp = MindQuestRepository(context.applicationContext).completeAgendaItem(id, isQuest)
                drawAll(context, manager, struckId = null, awardedXp = xp)
                if (xp > 0) {
                    // Let the XP land visibly before the header settles back.
                    delay(XP_LINGER_MS)
                    drawAll(context, manager, struckId = null, awardedXp = 0)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun drawAll(
        context: Context,
        manager: AppWidgetManager,
        struckId: String?,
        awardedXp: Int,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
        if (ids.isEmpty()) return
        val agenda = MindQuestRepository(context.applicationContext).todayAgenda(MAX_ROWS)
        val views = render(context, agenda, struckId, awardedXp)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun render(
        context: Context,
        agenda: List<AgendaItem>,
        struckId: String?,
        awardedXp: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        views.setTextViewText(
            R.id.widget_title,
            when {
                awardedXp > 0 -> "+$awardedXp XP"
                agenda.isEmpty() -> "NOTHING DUE TODAY"
                else -> "DUE TODAY (${agenda.size})"
            },
        )
        views.setViewVisibility(R.id.widget_empty, if (agenda.isEmpty()) View.VISIBLE else View.GONE)

        ROW_IDS.forEachIndexed { index, rowId ->
            val item = agenda.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(rowId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(rowId, View.VISIBLE)

            val struck = item.id == struckId
            val box = if (struck) "[x]" else "[ ]"
            val time = timeFmt.format(Date(item.dueAt))
            val marker = if (item.overdue && !struck) "!" else " "
            val line = "$box $marker$time  ${item.icon} ${item.title.take(48)}"

            if (struck) {
                // A real strikethrough span, so it reads as a line drawn across the words
                // rather than as hyphens typed over them.
                val spanned = SpannableString(line)
                spanned.setSpan(StrikethroughSpan(), 0, spanned.length, 0)
                views.setTextViewText(rowId, spanned)
            } else {
                views.setTextViewText(rowId, line)
            }

            views.setOnClickPendingIntent(
                rowId,
                PendingIntent.getBroadcast(
                    context,
                    item.id.hashCode(),
                    Intent(context, TodayWidget::class.java).apply {
                        action = ACTION_TICK
                        putExtra(EXTRA_ID, item.id)
                        putExtra(EXTRA_IS_QUEST, item.isQuest)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }

        // The header opens the app; the rows are reserved for ticking things off.
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(
                    context, 0, launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        return views
    }

    companion object {
        private const val MAX_ROWS = 5
        private const val STRIKE_LINGER_MS = 700L
        private const val XP_LINGER_MS = 1200L
        private const val ACTION_TICK = "com.mindquest.app.widget.TICK"
        private const val EXTRA_ID = "item_id"
        private const val EXTRA_IS_QUEST = "is_quest"

        private val ROW_IDS = listOf(
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
            R.id.widget_row_4, R.id.widget_row_5,
        )
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        /**
         * Redraw every placed widget. Safe to call from anywhere that changes a note or
         * quest — if no widget is on the home screen this is a no-op.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TodayWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
