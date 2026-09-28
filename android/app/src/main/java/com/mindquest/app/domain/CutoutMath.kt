package com.mindquest.app.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The arithmetic behind the scrapbook, kept apart from Android so it can be tested: where a
 * cutout's pixels are, how see-through its edge is, and whether a tap landed on a turned
 * picture.
 */
object CutoutMath {

    /**
     * The smallest box around every pixel the segmenter is sure about, as
     * [left, top, right, bottom] inclusive, or null when nothing was selected.
     */
    fun bounds(mask: FloatArray, w: Int, h: Int, threshold: Float = 0.5f): IntArray? {
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (mask[row + x] > threshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    /**
     * Opacity from the segmenter's confidence. A soft ramp rather than a hard cut at 0.5, so
     * hair and fur keep a feathered edge instead of a jagged staircase.
     */
    fun alpha(confidence: Float): Int =
        (((confidence - 0.3f) / 0.4f).coerceIn(0f, 1f) * 255f).roundToInt()

    /** Nearest-neighbour resize of a mask to the picture's size, for when the two differ. */
    fun resample(mask: FloatArray, mw: Int, mh: Int, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val sy = (y * mh / h).coerceIn(0, mh - 1)
            for (x in 0 until w) {
                val sx = (x * mw / w).coerceIn(0, mw - 1)
                out[y * w + x] = mask[sy * mw + sx]
            }
        }
        return out
    }

    /**
     * Whether ([px], [py]) falls inside a [w] × [h] box centred on ([cx], [cy]) and turned
     * [degrees] clockwise — i.e. whether a tap hit a picture on the page.
     */
    fun hits(px: Float, py: Float, cx: Float, cy: Float, w: Float, h: Float, degrees: Float): Boolean {
        val r = Math.toRadians(-degrees.toDouble())
        val dx = px - cx
        val dy = py - cy
        val rx = dx * cos(r) - dy * sin(r)
        val ry = dx * sin(r) + dy * cos(r)
        return abs(rx) <= w / 2 && abs(ry) <= h / 2
    }
}
