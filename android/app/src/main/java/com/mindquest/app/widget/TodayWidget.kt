package com.mindquest.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.mindquest.app.R
import com.mindquest.app.data.AgendaItem
import com.mindquest.app.data.MindQuestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget listing what is due today.
 *
 * The point of this one is that there is nothing to dismiss: a notification is gone the
 * moment it is swiped, whereas this sits on the home screen and keeps showing the same
 * unfinished list until the list actually changes.
 *
 * Rows are plain TextViews rather than a collection widget. A RemoteViewsService would
 * scroll, but for the handful of things due in a day a fixed set of lines is far less
 * machinery for the same result.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // onUpdate runs on the main thread, and the agenda comes from the database, so the
        // broadcast is held open while that query runs.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val agenda = MindQuestRepository(context.applicationContext).todayAgenda(MAX_ROWS)
                ids.forEach { id -> manager.updateAppWidget(id, render(context, agenda)) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context, agenda: List<AgendaItem>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        views.setTextViewText(
            R.id.widget_title,
            if (agenda.isEmpty()) "Nothing due today" else "Due today (${agenda.size})",
        )
        views.setViewVisibility(R.id.widget_empty, if (agenda.isEmpty()) View.VISIBLE else View.GONE)

        ROW_IDS.forEachIndexed { index, rowId ->
            val item = agenda.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(rowId, View.GONE)
            } else {
                views.setViewVisibility(rowId, View.VISIBLE)
                val time = timeFmt.format(Date(item.dueAt))
                val prefix = if (item.overdue) "⚠ $time" else time
                views.setTextViewText(rowId, "${item.icon}  $prefix   ${item.title.take(60)}")
            }
        }

        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            views.setOnClickPendingIntent(
                R.id.widget_root,
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
