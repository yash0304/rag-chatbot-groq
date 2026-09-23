package com.mindquest.app.domain

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mindquest.app.data.GoalEntity
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.cadence
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The monthly check-in for a target goal: "🎯 Reach 80 kg — by Mar 2027 · 6 months to go ·
 * last 86 kg · lose 0.86 kg a month". Reply to it with this month's number and it is logged
 * without opening the app — the lowest-effort check-in is the one that actually happens.
 *
 * Scheduled the same way as mission nudges: a chain of one-shots on calendar dates, so the
 * 1st of the month stays the 1st. It stays quiet in a month you have already logged.
 */
object GoalCheckins {

    private const val TAG_PREFIX = "goal-checkin-"
    const val KEY_GOAL_ID = "goal_id"

    fun schedule(context: Context, goalId: String, minuteOfDay: Int, cadence: String) {
        cancel(context, goalId)
        arm(context, goalId, Cadences.millisUntilNextFire(cadence, minuteOfDay))
    }

    /** From inside the worker: no cancel, which would cancel the worker itself. */
    fun rearm(context: Context, goalId: String, minuteOfDay: Int, cadence: String) {
        arm(context, goalId, Cadences.millisUntilNextFire(cadence, minuteOfDay).coerceAtLeast(TimeUnit.MINUTES.toMillis(5)))
    }

    private fun arm(context: Context, goalId: String, delayMillis: Long) {
        Reminders.ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<GoalCheckinWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_GOAL_ID to goalId))
            .addTag(TAG_PREFIX + goalId)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun cancel(context: Context, goalId: String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG_PREFIX + goalId)
    }

    /** One notification per goal; a reply replaces it with the result. */
    fun notificationId(goalId: String): Int = ("goal:$goalId").hashCode()

    private val dayFmt = SimpleDateFormat("d MMM", Locale.getDefault())

    /** The lines a check-in shows: deadline, last reading, and the pace from here. */
    fun summary(goal: GoalEntity, readings: List<Pair<Double, Long>>, today: LocalDate = LocalDate.now()): String {
        val unit = goal.unit ?: ""
        val deadline = goal.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return buildString {
            deadline?.let { d ->
                append("by ${Cadences.formatTarget(d.toString())}")
                Cadences.timeLeft("monthly", d.toString(), today)?.let { append(" · $it") }
            }
            val last = readings.lastOrNull()
            if (last != null) {
                append("\nLast: ${GoalParse.format(last.first, unit)} on ${dayFmt.format(Date(last.second))}")
            } else {
                append("\nNo reading yet — reply with where you are now.")
            }
            if (deadline != null) {
                val s = GoalMath.status(goal.targetValue, unit, deadline, readings.map { it.first }, today)
                s.perMonth?.let { append("\nPace needed: ${GoalMath.describePace(it, unit)}") }
            }
        }
    }

    /** Post the check-in, with a reply box for the number and a tap that opens the app. */
    fun post(context: Context, goal: GoalEntity, readings: List<Pair<Double, Long>>, heading: String? = null) {
        if (!Reminders.hasPermission(context)) return
        Reminders.ensureChannel(context)
        val body = summary(goal, readings)
        val unit = goal.unit ?: ""

        val reply = RemoteInput.Builder(GoalActionReceiver.KEY_VALUE)
            .setLabel(if (unit == "₹") "e.g. 12 lakh" else "e.g. 84.5")
            .build()
        // A reply has to be written into the intent, so this one PendingIntent must be mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val replyIntent = PendingIntent.getBroadcast(
            context, notificationId(goal.id),
            Intent(context, GoalActionReceiver::class.java)
                .setAction(GoalActionReceiver.ACTION_LOG)
                .putExtra(KEY_GOAL_ID, goal.id),
            mutable or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?.let {
                PendingIntent.getActivity(
                    context, notificationId(goal.id), it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(heading ?: "🎯 ${goal.title}")
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { open?.let { setContentIntent(it) } }
            .addAction(
                NotificationCompat.Action.Builder(0, "Log this month's number", replyIntent)
                    .addRemoteInput(reply)
                    .build(),
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(goal.id), notification)
        } catch (e: SecurityException) {
            // permission revoked since scheduling
        }
    }

    /** Replace the check-in with a plain result line once a reply has been handled. */
    fun postResult(context: Context, goalId: String, title: String, text: String) {
        if (!Reminders.hasPermission(context)) return
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(goalId), notification)
        } catch (e: SecurityException) {
            // permission revoked
        }
    }
}

class GoalCheckinWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val goalId = inputData.getString(GoalCheckins.KEY_GOAL_ID).orEmpty()
        if (goalId.isEmpty()) return Result.success()
        val repo = MindQuestRepository(applicationContext)
        val goal = runCatching { repo.goal(goalId) }.getOrNull() ?: return Result.success()
        val minute = goal.checkinMinuteOfDay
        if (goal.status != "active" || minute == null) return Result.success() // chain ends

        val readings = repo.goalProgress(goalId).map { it.value to it.createdAt }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val lastDay = readings.lastOrNull()?.let { Instant.ofEpochMilli(it.second).atZone(zone).toLocalDate() }
        val loggedThisPeriod = lastDay != null &&
            Cadences.periodIndex(goal.cadence, lastDay) == Cadences.periodIndex(goal.cadence, today)
        if (!loggedThisPeriod) GoalCheckins.post(applicationContext, goal, readings)

        GoalCheckins.rearm(applicationContext, goalId, minute, goal.cadence)
        return Result.success()
    }
}

/**
 * Takes the number typed into a check-in's reply box and logs it, then answers in the same
 * notification: "✓ 84.5 kg logged · 4.5 kg to go", or "🎉 Goal reached". A reply that isn't
 * a number puts the check-in back with a note saying so, rather than dropping it.
 */
class GoalActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG) return
        val goalId = intent.getStringExtra(GoalCheckins.KEY_GOAL_ID) ?: return
        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_VALUE)?.toString().orEmpty()
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = MindQuestRepository(app)
                val goal = repo.goal(goalId) ?: return@launch
                val unit = goal.unit ?: ""
                val value = GoalParse.parseValue(typed, unit)
                if (value == null) {
                    val readings = repo.goalProgress(goalId).map { it.value to it.createdAt }
                    GoalCheckins.post(app, goal, readings, heading = "Couldn't read a number from “$typed” — try again")
                    return@launch
                }
                val r = repo.logGoalProgress(goalId, value)
                val logged = GoalParse.format(value, unit)
                if (r.reached) {
                    GoalCheckins.postResult(app, goalId, "🎉 ${goal.title} — reached!", "$logged logged. +${r.xpAwarded} XP")
                } else {
                    val left = r.status?.remaining?.let { " · ${GoalMath.describeRemaining(it, unit)}" } ?: ""
                    GoalCheckins.postResult(app, goalId, "✓ $logged logged$left", goal.title)
                }
            } catch (e: Exception) {
                Log.w("GoalCheckin", "Couldn't log reply", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG = "com.mindquest.app.action.GOAL_LOG"
        const val KEY_VALUE = "goal_value"
    }
}
