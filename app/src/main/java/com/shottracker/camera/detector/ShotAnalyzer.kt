package com.shottracker.camera.detector

import android.graphics.RectF
import android.util.Log
import com.shottracker.camera.HoopRegion

private const val TAG = "ShotAnalyzer"
private const val COOLDOWN_MS = 1500L
private const val HOOP_OVERLAP_THRESHOLD = 0.25f
private const val ARC_LOOKBACK_MS = 1500L  // how far back to look for a shooting arc

/**
 * A detected shot attempt with kinematic metadata captured at the moment of detection.
 *
 * @param hadProperArc  True if the ball was above the hoop top at some point before entry
 *   (indicates a proper shooting arc rather than a horizontal pass or roll).
 * @param initialConfidence  0–1 score based on overlap, arc quality, and entry velocity.
 *   Used for logging and for the outcome resolution step in [SessionViewModel].
 */
data class ShotEvent(
    val positions: List<BallPosition>,
    val entryOverlap: Float = 0f,
    val entryVelocity: Float = 0f,
    val hadProperArc: Boolean = false,
    val initialConfidence: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Detects shots by checking whether the ball enters the hoop zone while moving downward.
 *
 * Step 4b — arc check: the ball should have been above the hoop top at some point in the
 * recent trajectory, confirming a proper shooting arc rather than incidental contact.
 *
 * Step 4c — confidence scoring: [ShotEvent.initialConfidence] combines overlap fraction,
 * arc quality, and entry velocity into a 0–1 score used for outcome resolution.
 */
class ShotAnalyzer(private val trajectoryTracker: TrajectoryTracker) {

    private var lastShotMs = 0L

    /**
     * Analyse current trajectory against [hoopRegion].
     * Returns a [ShotEvent] on detection, null otherwise.
     */
    fun analyze(hoopRegion: HoopRegion?): ShotEvent? {
        if (hoopRegion == null) return null

        val now = System.currentTimeMillis()
        if (now - lastShotMs < COOLDOWN_MS) return null

        val recent = trajectoryTracker.recentPositions(300L)
        if (recent.isEmpty()) return null

        val latest = recent.last()
        val overlap = overlapFraction(latest.boundingBox, hoopRegion.rect)
        if (overlap < HOOP_OVERLAP_THRESHOLD) return null

        val velocity = trajectoryTracker.verticalVelocity() ?: return null
        if (velocity <= 0f) return null  // must be moving downward into hoop

        val snapshot = trajectoryTracker.snapshot()

        // Step 4b: proper shooting arc check — was the ball above the hoop before entry?
        val hoopTop = hoopRegion.rect.top
        val arcHistory = trajectoryTracker.recentPositions(ARC_LOOKBACK_MS)
        val hadProperArc = arcHistory.any { it.centerY < hoopTop }

        // Step 4c: confidence scoring
        var confidence = 0.40f
        confidence += (overlap - HOOP_OVERLAP_THRESHOLD) * 0.80f  // more overlap = more confident
        if (hadProperArc) confidence += 0.25f                     // proper arc is a strong signal
        if (velocity in 0.2f..3.0f) confidence += 0.10f           // reasonable entry speed
        confidence = confidence.coerceIn(0f, 1f)

        lastShotMs = now
        Log.d(TAG, "Shot detected! overlap=%.2f vel=%.2f arc=$hadProperArc conf=%.2f"
            .format(overlap, velocity, confidence))

        return ShotEvent(
            positions         = snapshot,
            entryOverlap      = overlap,
            entryVelocity     = velocity,
            hadProperArc      = hadProperArc,
            initialConfidence = confidence,
        )
    }

    fun reset() { lastShotMs = 0L }

    private fun overlapFraction(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left,   b.left)
        val interTop    = maxOf(a.top,    b.top)
        val interRight  = minOf(a.right,  b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        if (interRight <= interLeft || interBottom <= interTop) return 0f
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val minArea = minOf(a.width() * a.height(), b.width() * b.height())
        return if (minArea <= 0f) 0f else interArea / minArea
    }
}

