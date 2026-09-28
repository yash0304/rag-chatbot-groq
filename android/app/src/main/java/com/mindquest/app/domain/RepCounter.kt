package com.mindquest.app.domain

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Counts exercise repetitions from body landmarks — the 33 points MediaPipe's pose model
 * finds in each camera frame. Each exercise is one joint angle moving between a resting
 * position and a working one; a rep is out to the working side and back. Two thresholds with
 * a gap between them, not one, so a knee hovering at the line doesn't count five times.
 */
class RepCounter(val exercise: Exercise) {

    enum class Exercise(val label: String, val emoji: String) {
        Squats("Squats", "🏋️"),
        PushUps("Push-ups", "💪"),
        Curls("Bicep curls", "🦾"),
        JumpingJacks("Jumping jacks", "🤸"),
    }

    /** A landmark: position as fractions of the frame, and how sure the model is it's visible. */
    data class Point(val x: Float, val y: Float, val visibility: Float = 1f)

    var count = 0
        private set

    /** The angle being watched on the last good frame, for the screen to show. */
    var lastAngle: Float? = null
        private set

    private var working = false
    private var lastRepAt = Long.MIN_VALUE / 2

    /** One camera frame's landmarks; returns true when this frame completed a rep. */
    fun update(landmarks: List<Point>, timeMs: Long): Boolean = feed(measure(landmarks), timeMs)

    /** The state machine on its own, for testing: one angle per frame, or null if unseen. */
    internal fun feed(angle: Float?, timeMs: Long): Boolean {
        angle ?: return false
        lastAngle = angle
        val (rest, work, restHigh) = zones(exercise)
        val inWork = if (restHigh) angle < work else angle > work
        val inRest = if (restHigh) angle > rest else angle < rest
        if (!working && inWork) {
            working = true
        } else if (working && inRest) {
            working = false
            if (timeMs - lastRepAt >= MIN_REP_MS) {
                lastRepAt = timeMs
                count++
                return true
            }
        }
        return false
    }

    fun reset() {
        count = 0
        working = false
        lastAngle = null
    }

    private fun measure(p: List<Point>): Float? {
        if (p.size < 33) return null
        fun side(a: Int, b: Int, c: Int): Float? =
            if (listOf(a, b, c).all { p[it].visibility >= MIN_VISIBILITY }) angle(p[a], p[b], p[c]) else null
        fun both(l: Triple<Int, Int, Int>, r: Triple<Int, Int, Int>): Float? {
            val sides = listOfNotNull(side(l.first, l.second, l.third), side(r.first, r.second, r.third))
            return if (sides.isEmpty()) null else sides.average().toFloat()
        }
        return when (exercise) {
            Exercise.Squats -> both(Triple(L_HIP, L_KNEE, L_ANKLE), Triple(R_HIP, R_KNEE, R_ANKLE))
            Exercise.PushUps, Exercise.Curls -> both(Triple(L_SHOULDER, L_ELBOW, L_WRIST), Triple(R_SHOULDER, R_ELBOW, R_WRIST))
            // Arms from the sides to overhead: the angle at the shoulder between hip and wrist.
            Exercise.JumpingJacks -> both(Triple(L_HIP, L_SHOULDER, L_WRIST), Triple(R_HIP, R_SHOULDER, R_WRIST))
        }
    }

    companion object {
        const val MIN_VISIBILITY = 0.5f
        /** Faster than this isn't a rep, it's a jitter. */
        const val MIN_REP_MS = 350L

        const val L_SHOULDER = 11; const val R_SHOULDER = 12
        const val L_ELBOW = 13; const val R_ELBOW = 14
        const val L_WRIST = 15; const val R_WRIST = 16
        const val L_HIP = 23; const val R_HIP = 24
        const val L_KNEE = 25; const val R_KNEE = 26
        const val L_ANKLE = 27; const val R_ANKLE = 28

        /** Joints joined by lines on screen: arms, shoulders, trunk, legs. */
        val BONES = listOf(
            L_SHOULDER to R_SHOULDER, L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST, R_SHOULDER to R_ELBOW,
            R_ELBOW to R_WRIST, L_SHOULDER to L_HIP, R_SHOULDER to R_HIP, L_HIP to R_HIP,
            L_HIP to L_KNEE, L_KNEE to L_ANKLE, R_HIP to R_KNEE, R_KNEE to R_ANKLE,
        )

        /**
         * (rest threshold, work threshold, rest is the high angle). Squat: standing ~175°,
         * down under 100°. Push-up: arms straight ~170°, down under 95°. Curl: arm straight
         * ~165°, curled under 60°. Jumping jack: arms down ~20°, overhead past 130°.
         */
        fun zones(e: Exercise): Triple<Float, Float, Boolean> = when (e) {
            Exercise.Squats -> Triple(155f, 100f, true)
            Exercise.PushUps -> Triple(150f, 95f, true)
            Exercise.Curls -> Triple(140f, 60f, true)
            Exercise.JumpingJacks -> Triple(50f, 130f, false)
        }

        /** The angle at [b] between [a] and [c], 0–180°. */
        fun angle(a: Point, b: Point, c: Point): Float {
            val r = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) -
                atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
            var deg = abs(Math.toDegrees(r)).toFloat()
            if (deg > 180f) deg = 360f - deg
            return deg
        }
    }
}
