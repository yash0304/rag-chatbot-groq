package com.mindquest.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mindquest.app.R

/**
 * A single mic on the home screen. Press, speak, done — the note is filed with its date
 * before the phone is back in your pocket.
 *
 * Separate from the today widget on purpose: that one is a page you read and tick off, this
 * one is a button you hit without reading anything. Putting a mic on the page would have
 * meant a tap target competing with the rows.
 */
class CaptureWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_capture)
        // NEW_TASK only, and the activity has its own affinity in the manifest: pressing the
        // mic must not disturb the app if it happens to be open behind the home screen.
        val intent = Intent(context, VoiceCaptureActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.capture_root, pending)
        views.setOnClickPendingIntent(R.id.capture_mic, pending)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
