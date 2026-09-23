package com.mindquest.app.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.mindquest.app.data.MindQuestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reminders at the minute they were set for.
 *
 * WorkManager — the fallback, and how reminders worked until now — is allowed to batch work
 * to save battery, so "buy sugar at 5pm" could arrive at 5:07. An exact alarm fires at 5:00
 * even with the phone asleep. It needs one special permission, "Alarms & reminders", which
 * Android 14 and later leave off until you allow it; without it every reminder simply goes
 * through WorkManager as before, so nothing breaks either way.
 *
 * Only the first firing of a note reminder is exact. The repeat nags every fifteen minutes
 * after it stay on WorkManager — a nag a minute or two late is fine, and asking the phone to
 * wake exactly eight times per errand would be a poor use of the battery.
 */
object ExactAlarms {

    const val ACTION_FIRE = "com.mindquest.app.action.REMINDER_FIRE"
    private const val EXTRA_NOTE_ID = "note_id"

    private fun manager(context: Context): AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    /** Whether exact alarms are available right now. Before Android 12 they always are. */
    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return manager(context)?.canScheduleExactAlarms() == true
    }

    /** The system page where the user can allow exact alarms for this app. */
    fun settingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    /** Arm an exact alarm for a note. Returns false if it couldn't, so the caller falls back. */
    fun set(context: Context, noteId: String, atMillis: Long): Boolean = try {
        val am = manager(context) ?: throw IllegalStateException("No alarm service")
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(context, noteId, create = true)!!)
        true
    } catch (e: SecurityException) {
        // Permission withdrawn between the check and the call.
        Log.w("ExactAlarms", "Exact alarm refused; falling back", e)
        false
    } catch (e: Exception) {
        Log.w("ExactAlarms", "Couldn't set exact alarm; falling back", e)
        false
    }

    fun cancel(context: Context, noteId: String) {
        pendingIntent(context, noteId, create = false)?.let { pi ->
            manager(context)?.cancel(pi)
            pi.cancel()
        }
    }

    private fun pendingIntent(context: Context, noteId: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // The note id is also in the data URI, so each note's alarm is a distinct
            // PendingIntent even if two ids happened to share a hash code.
            .setData(Uri.parse("mindquest://reminder/$noteId"))
            .putExtra(EXTRA_NOTE_ID, noteId)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, noteId.hashCode(), intent, flags)
    }

    internal fun noteIdOf(intent: Intent): String? = intent.getStringExtra(EXTRA_NOTE_ID)
}

/**
 * Fires exact reminders, and puts them back when the phone forgets them.
 *
 * Android drops every app's alarms on reboot, and may on an app update; WorkManager rebuilds
 * its own work, but exact alarms are this app's to rebuild. The same happens the moment the
 * user allows exact alarms, so reminders already waiting on WorkManager move over at once.
 * Not exported: these are system broadcasts and our own alarms, nothing else.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ExactAlarms.ACTION_FIRE -> ExactAlarms.noteIdOf(intent)?.let {
                        NoteReminderDelivery.deliver(app, it, fallbackText = "", attempt = 1)
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                    -> MindQuestRepository(app).rearmNoteReminders()
                }
            } catch (e: Exception) {
                Log.w("ReminderAlarm", "Couldn't handle ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
