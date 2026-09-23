package com.mindquest.app.domain

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The buttons on a reminder: Done and Snooze on a note, Mark done on a mission.
 *
 * The repeat-until-done nag only works if "done" is cheap to say. Before this, the only way
 * to stop it was to open the app, find the note and tick it — so the eighth nag arrived for
 * something finished an hour ago. Now the notification that nags is also where you answer.
 *
 * Each button goes through the same repository call the app uses, so ticking from the shade
 * is indistinguishable from ticking in the Inbox: same XP, same streak, same widget refresh.
 * Not exported — only this app's own notifications can reach it.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val app = context.applicationContext
        // Database work can't run on the main thread, and a receiver is torn down the moment
        // onReceive returns; goAsync keeps it alive until the write lands.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = MindQuestRepository(app)
                when (intent.action) {
                    ACTION_NOTE_DONE -> repo.setNoteDone(id, true)
                    ACTION_NOTE_SNOOZE -> repo.snoozeNote(id, SNOOZE_MINUTES)
                    ACTION_HABIT_DONE -> repo.checkin(id)
                }
                NotificationManagerCompat.from(app).cancel(id.hashCode())
                TodayWidget.refresh(app)
            } catch (e: Exception) {
                // Leave the notification up: if the tap didn't take, it should still be there
                // to try again, not vanish as though it had.
                Log.w("ReminderAction", "Notification action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NOTE_DONE = "com.mindquest.app.action.NOTE_DONE"
        const val ACTION_NOTE_SNOOZE = "com.mindquest.app.action.NOTE_SNOOZE"
        const val ACTION_HABIT_DONE = "com.mindquest.app.action.HABIT_DONE"
        private const val EXTRA_ID = "id"

        /** An hour: long enough to finish what you're in the middle of, short enough to matter. */
        const val SNOOZE_MINUTES = 60L

        fun pendingIntent(context: Context, action: String, id: String): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, id)
            // The request code folds in the action as well as the id, so a note's Done and
            // Snooze buttons get distinct PendingIntents instead of one overwriting the other.
            return PendingIntent.getBroadcast(
                context,
                (action + id).hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
