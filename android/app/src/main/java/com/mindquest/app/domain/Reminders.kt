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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mindquest.app.data.HabitEntity
import com.mindquest.app.data.MindQuestDatabase
import com.mindquest.app.data.SettingsStore
import com.mindquest.app.widget.TodayWidget
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Reminder delivery. A note reminder uses an exact alarm when the user allows them (see
 * ExactAlarms) and WorkManager otherwise; either way it survives app death and reboots, and
 * nothing here touches the network.
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

    /**
     * Schedule (or reschedule) a reminder for a note. Past times are ignored. On time to the
     * minute when exact alarms are allowed; otherwise through WorkManager, which may run a few
     * minutes late but needs no permission at all.
     */
    fun schedule(context: Context, noteId: String, text: String, whenMillis: Long) {
        val delay = whenMillis - System.currentTimeMillis()
        if (delay <= 0) return
        ensureChannel(context)
        if (ExactAlarms.canSchedule(context) && ExactAlarms.set(context, noteId, whenMillis)) return
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

    /** Cancel both routes: a reminder may be waiting on either, depending on when it was set. */
    fun cancel(context: Context, noteId: String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG_PREFIX + noteId)
        ExactAlarms.cancel(context, noteId)
    }

    // ---------- recurring missions ----------

    /**
     * Nudge a mission when its next period comes round — every evening, every Monday, or the
     * 1st of every month, quarter, half-year or year.
     *
     * A chain of one-shots rather than one periodic request, because WorkManager's periods
     * are a fixed number of milliseconds and a month is not. Thirty days repeated would slip
     * off the 1st within a quarter and be a week adrift by the autumn. Each firing works out
     * the next calendar date itself and arms the following one, and WorkManager persists a
     * pending one-shot across app death and reboots exactly as it does a periodic request.
     */
    fun scheduleHabit(context: Context, habitId: String, minuteOfDay: Int, cadence: String) {
        cancelHabitReminder(context, habitId)
        armHabit(context, habitId, Cadences.millisUntilNextFire(cadence, minuteOfDay))
    }

    /**
     * Arm the next firing from inside the worker that just fired. It skips the cancel that
     * [scheduleHabit] does, since a worker cancelling its own tag would be cancelling itself.
     */
    fun rearmHabit(context: Context, habitId: String, minuteOfDay: Int, cadence: String) {
        // Ask for the fire after this one: the current period's slot is now in the past, so
        // nextFireAt naturally rolls forward, but a nudge set for 00:00 could still resolve
        // to the moment that just passed if the worker ran early.
        val delay = Cadences.millisUntilNextFire(cadence, minuteOfDay)
            .coerceAtLeast(TimeUnit.MINUTES.toMillis(5))
        armHabit(context, habitId, delay)
    }

    private fun armHabit(context: Context, habitId: String, delayMillis: Long) {
        ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_HABIT_ID to habitId))
            .addTag(HABIT_TAG_PREFIX + habitId)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun cancelHabitReminder(context: Context, habitId: String) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelAllWorkByTag(HABIT_TAG_PREFIX + habitId)
        // Older builds scheduled these as unique periodic work under the same name; cancel
        // that too so upgrading doesn't leave a second, daily nudge running unseen.
        manager.cancelUniqueWork(HABIT_TAG_PREFIX + habitId)
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
        NoteReminderDelivery.deliver(
            context = applicationContext,
            noteId = inputData.getString(Reminders.KEY_NOTE_ID).orEmpty(),
            fallbackText = inputData.getString(Reminders.KEY_TEXT).orEmpty(),
            attempt = inputData.getInt(Reminders.KEY_ATTEMPT, 1),
        )
        return Result.success()
    }
}

/**
 * Fires one note reminder: notification, optional text, and the next nag if still undone.
 *
 * Shared by the WorkManager path and the exact-alarm path so a reminder looks and behaves
 * the same however it was woken — same wording, same Done and Snooze buttons, same repeats.
 */
object NoteReminderDelivery {

    suspend fun deliver(context: Context, noteId: String, fallbackText: String, attempt: Int) {
        val app = context.applicationContext
        // Read the note back rather than trusting the text captured at scheduling time — it
        // may have been edited, completed or deleted in the meantime.
        val note = runCatching { MindQuestDatabase.get(app).noteDao().get(noteId) }.getOrNull()

        if (noteId.isNotEmpty() && note == null) return // deleted
        if (note?.done == true) return // already handled

        val text = note?.text?.takeIf { it.isNotBlank() } ?: fallbackText.ifBlank { "Reminder" }
        val settings = SettingsStore(app)

        post(app, noteId, text, attempt)

        val number = settings.reminderPhone()
        if (settings.smsRemindersEnabled() && number != null && Reminders.hasSmsPermission(app)) {
            Reminders.sendSms(app, number, "MindQuest reminder: $text")
        }

        // Keep nagging while it is still outstanding, but stop eventually — an alarm that
        // never gives up gets silenced at the OS level, which would be worse than useless.
        if (settings.repeatUntilDone() && note != null && attempt < Reminders.MAX_ATTEMPTS) {
            Reminders.scheduleRepeat(app, noteId, text, attempt + 1)
        }

        TodayWidget.refresh(app)
    }

    private fun post(context: Context, noteId: String, text: String, attempt: Int) {
        if (!Reminders.hasPermission(context)) return
        Reminders.ensureChannel(context)

        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = launch?.let {
            PendingIntent.getActivity(
                context, noteId.hashCode(), it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (attempt > 1) "Still waiting ($attempt)" else "MindQuest reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { pending?.let { setContentIntent(it) } }
            .apply {
                // A reminder with no id is a legacy one-off with no note behind it; there is
                // nothing for the buttons to act on, so it gets none.
                if (noteId.isNotEmpty()) {
                    addAction(
                        0, "✓ Done",
                        ReminderActionReceiver.pendingIntent(
                            context, ReminderActionReceiver.ACTION_NOTE_DONE, noteId,
                        ),
                    )
                    addAction(
                        0, "⏰ Snooze 1h",
                        ReminderActionReceiver.pendingIntent(
                            context, ReminderActionReceiver.ACTION_NOTE_SNOOZE, noteId,
                        ),
                    )
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(noteId.hashCode(), notification)
        } catch (e: SecurityException) {
            // permission revoked between scheduling and firing
        }
    }
}


/**
 * The nudge for a recurring mission — "walk 5000 steps" every evening, "weigh in" on the 1st
 * of each month.
 *
 * It stays silent when the mission is already done for the current period, because the point
 * is to catch the ones that would otherwise slip, not to announce itself regardless. When
 * the mission is working towards something it says so: a goal of 80 kg by March 2027 is
 * worth being reminded of precisely when you are deciding whether to bother this month.
 *
 * Whatever it decides, it arms the next firing before it returns — that chain is the whole
 * schedule, so dropping it would silently end the reminder.
 */
class HabitReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getString(Reminders.KEY_HABIT_ID).orEmpty()
        if (habitId.isEmpty()) return Result.success()

        val habit = runCatching {
            MindQuestDatabase.get(applicationContext).habitDao().get(habitId)
        }.getOrNull() ?: return Result.success() // deleted, so the chain ends here

        val minute = habit.remindMinuteOfDay
            ?: return Result.success() // nudge switched off since this was armed

        val today = LocalDate.now()
        val thisPeriod = Cadences.periodIndex(habit.cadence, today)
        val lastPeriod = habit.lastCheckinDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { Cadences.periodIndex(habit.cadence, it) }

        if (lastPeriod != thisPeriod) post(habitId, habit)

        Reminders.rearmHabit(applicationContext, habitId, minute, habit.cadence)
        return Result.success()
    }

    private fun post(habitId: String, habit: HabitEntity) {
        val cadence = Cadences.of(habit.cadence)
        val body = buildString {
            append(habit.title)
            habit.targetNote?.takeIf { it.isNotBlank() }?.let { target ->
                append("\n🎯 $target")
                habit.targetDate?.let { append(" by ${Cadences.formatTarget(it)}") }
                habit.targetDate?.let { date ->
                    Cadences.timeLeft(habit.cadence, date)?.let { append(" · $it") }
                }
            }
            if (habit.streak > 0) append("\n🔥 ${habit.streak}-${cadence.unit} streak on the line")
        }

        if (Reminders.hasPermission(applicationContext)) {
            Reminders.ensureChannel(applicationContext)
            val notification = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("${cadence.label.replaceFirstChar { it.uppercase() }} mission")
                .setContentText(habit.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                // Checking in from here pays the same XP and keeps the same streak as the
                // button in the app — it is the same call.
                .addAction(
                    0, "✓ Mark done",
                    ReminderActionReceiver.pendingIntent(
                        applicationContext, ReminderActionReceiver.ACTION_HABIT_DONE, habitId,
                    ),
                )
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
            Reminders.sendSms(applicationContext, number, "MindQuest: ${body.replace('\n', ' ')}")
        }
    }
}
