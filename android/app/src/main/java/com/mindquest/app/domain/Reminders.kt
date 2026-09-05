package com.mindquest.app.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Reminder notifications for inbox notes. Scheduling goes through WorkManager so a pending
 * reminder survives app death and device reboots. Everything is local — no network.
 */
object Reminders {
    const val CHANNEL_ID = "mindquest_reminders"
    const val KEY_TEXT = "note_text"
    const val KEY_NOTE_ID = "note_id"
    private const val TAG_PREFIX = "note-reminder-"

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

    /** Schedule (or reschedule) a reminder for a note. Past times are ignored. */
    fun schedule(context: Context, noteId: String, text: String, whenMillis: Long) {
        val delay = whenMillis - System.currentTimeMillis()
        if (delay <= 0) return
        ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TEXT to text, KEY_NOTE_ID to noteId))
            .addTag(TAG_PREFIX + noteId)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun cancel(context: Context, noteId: String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG_PREFIX + noteId)
    }
}

/** Posts the reminder notification when its scheduled time arrives. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val text = inputData.getString(Reminders.KEY_TEXT).orEmpty().ifBlank { "Reminder" }
        val noteId = inputData.getString(Reminders.KEY_NOTE_ID).orEmpty()
        if (!Reminders.hasPermission(applicationContext)) return Result.success()
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
            .setContentTitle("MindQuest reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext)
                .notify(noteId.hashCode(), notification)
            Result.success()
        } catch (e: SecurityException) {
            Result.success() // permission revoked between scheduling and firing
        }
    }
}
