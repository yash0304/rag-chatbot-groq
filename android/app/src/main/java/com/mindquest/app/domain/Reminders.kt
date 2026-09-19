package com.mindquest.app.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mindquest.app.data.MindQuestDatabase
import com.mindquest.app.data.SettingsStore
import com.mindquest.app.widget.TodayWidget
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Reminder delivery. Scheduling goes through WorkManager so a pending reminder survives app
 * death and reboots, and nothing here touches the network.
 *
 * Two deliberate behaviours beyond a plain notification, both because a notification you
 * swipe away half-asleep has done nothing:
 *  - it can also text you, so the reminder sits in your SMS inbox until it is actually read;
 *  - it repeats until the note is ticked off, rather than firing once and disappearing.
 */
object Reminders {
    const val CHANNEL_ID = "mindquest_reminders"
    const val KEY_TEXT = "note_text"
    const val KEY_NOTE_ID = "note_id"
    const val KEY_ATTEMPT = "attempt"
    private const val TAG_PREFIX = "note-reminder-"
    private const val HABIT_TAG_PREFIX = "habit-reminder-"
    const val KEY_HABIT_ID = "habit_id"

    /** Gap between repeats, and how many times to nag before giving up. */
    private const val REPEAT_MINUTES = 15L
    const val MAX_ATTEMPTS = 8

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Reminders for your inbox notes and errands" }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Schedule (or reschedule) a reminder for a note. Past times are ignored. */
    fun schedule(context: Context, noteId: String, text: String, whenMillis: Long) {
        val delay = whenMillis - System.currentTimeMillis()
        if (delay <= 0) return
        enqueue(context, noteId, text, delay, attempt = 1)
    }

    private fun enqueue(context: Context, noteId: String, text: String, delayMillis: Long, attempt: Int) {
        ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TEXT to text, KEY_NOTE_ID to noteId, KEY_ATTEMPT to attempt))
            .addTag(TAG_PREFIX + noteId)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    /** Queue the next nag for a note that still isn't done. */
    fun scheduleRepeat(context: Context, noteId: String, text: String, nextAttempt: Int) {
        enqueue(context, noteId, text, TimeUnit.MINUTES.toMillis(REPEAT_MINUTES), nextAttempt)
    }

    fun cancel(context: Context, noteId: String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG_PREFIX + noteId)
    }

    // ---------- daily missions ----------

    /**
     * Nudge a daily mission at the same time every day, e.g. 21:00 for "walk 5000 steps".
     *
     * A periodic request rather than a chain of one-shots, so the schedule survives the app
     * being killed and the phone rebooting without anything having to re-arm it. The initial
     * delay lands on the next occurrence of that time; after that WorkManager repeats daily.
     */
    fun scheduleDailyHabit(context: Context, habitId: String, title: String, minuteOfDay: Int) {
        ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<HabitReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNext(minuteOfDay), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_HABIT_ID to habitId, KEY_TEXT to title))
            .addTag(HABIT_TAG_PREFIX + habitId)
            .build()
        // REPLACE so changing the time re-aims the existing schedule instead of stacking a
        // second one on top of it.
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            HABIT_TAG_PREFIX + habitId,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelDailyHabit(context: Context, habitId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(HABIT_TAG_PREFIX + habitId)
    }

    /** Milliseconds from now until the next time the clock reads [minuteOfDay]. */
    private fun millisUntilNext(minuteOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }

    fun formatTimeOfDay(minuteOfDay: Int): String =
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** Send the reminder as a text to the user's own number. No internet involved. */
    fun sendSms(context: Context, number: String, body: String) {
        try {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            // Long notes would be silently truncated by sendTextMessage, so split properly.
            val parts = manager.divideMessage(body)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                manager.sendTextMessage(number, null, body, null, null)
            }
        } catch (e: Exception) {
            // A failed text must never take the notification down with it.
            Log.w("Reminders", "Could not send reminder SMS", e)
        }
    }
}

/**
 * Fires one reminder, then decides whether to queue another.
 *
 * A CoroutineWorker rather than a plain Worker because it reads the note back from the
 * database: the whole point of repeating is to stop once the thing is actually done, and
 * that can only be known by looking.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getString(Reminders.KEY_NOTE_ID).orEmpty()
        val attempt = inputData.getInt(Reminders.KEY_ATTEMPT, 1)
        val fallbackText = inputData.getString(Reminders.KEY_TEXT).orEmpty().ifBlank { "Reminder" }

        // Read the note back rather than trusting the text captured at scheduling time — it
        // may have been edited, completed or deleted in the meantime.
        val note = runCatching {
            MindQuestDatabase.get(applicationContext).noteDao().get(noteId)
        }.getOrNull()

        if (noteId.isNotEmpty() && note == null) return Result.success() // deleted
        if (note?.done == true) return Result.success() // already handled

        val text = note?.text?.takeIf { it.isNotBlank() } ?: fallbackText
        val settings = SettingsStore(applicationContext)

        postNotification(noteId, text, attempt)

        val number = settings.reminderPhone()
        if (settings.smsRemindersEnabled() && number != null && Reminders.hasSmsPermission(applicationContext)) {
            Reminders.sendSms(applicationContext, number, "MindQuest reminder: $text")
        }

        // Keep nagging while it is still outstanding, but stop eventually — an alarm that
        // never gives up gets silenced at the OS level, which would be worse than useless.
        if (settings.repeatUntilDone() && note != null && attempt < Reminders.MAX_ATTEMPTS) {
            Reminders.scheduleRepeat(applicationContext, noteId, text, attempt + 1)
        }

        TodayWidget.refresh(applicationContext)
        return Result.success()
    }

    private fun postNotification(noteId: String, text: String, attempt: Int) {
        if (!Reminders.hasPermission(applicationContext)) return
        Reminders.ensureChannel(applicationContext)

        val launch = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = launch?.let {
            PendingIntent.getActivity(
                applicationContext, noteId.hashCode(), it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (attempt > 1) "Still waiting ($attempt)" else "MindQuest reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(noteId.hashCode(), notification)
        } catch (e: SecurityException) {
            // permission revoked between scheduling and firing
        }
    }
}


/**
 * The daily nudge for a mission such as "walk 5000 steps".
 *
 * It checks the streak first and stays silent if the mission is already done today — the
 * point is to catch the days you would otherwise miss, not to announce itself regardless.
 * When it does fire it uses the same repeat-until-done chain as note reminders.
 */
class HabitReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getString(Reminders.KEY_HABIT_ID).orEmpty()
        if (habitId.isEmpty()) return Result.success()

        val habit = runCatching {
            MindQuestDatabase.get(applicationContext).habitDao().get(habitId)
        }.getOrNull() ?: return Result.success() // deleted; the periodic work will be cancelled too

        val today = LocalDate.now().toString()
        if (habit.lastCheckinDate == today) return Result.success() // already done

        if (Reminders.hasPermission(applicationContext)) {
            Reminders.ensureChannel(applicationContext)
            val streakNote = if (habit.streak > 0) " · ${habit.streak}-day streak on the line" else ""
            val notification = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Daily mission")
                .setContentText(habit.title + streakNote)
                .setStyle(NotificationCompat.BigTextStyle().bigText(habit.title + streakNote))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(applicationContext)
                    .notify(habitId.hashCode(), notification)
            } catch (e: SecurityException) {
                // permission revoked since scheduling
            }
        }

        val settings = SettingsStore(applicationContext)
        val number = settings.reminderPhone()
        if (settings.smsRemindersEnabled() && number != null && Reminders.hasSmsPermission(applicationContext)) {
            Reminders.sendSms(applicationContext, number, "MindQuest: ${habit.title}")
        }
        return Result.success()
    }
}
