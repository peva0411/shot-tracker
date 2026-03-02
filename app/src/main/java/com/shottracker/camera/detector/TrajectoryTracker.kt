package com.shottracker.camera.detector

import android.graphics.RectF

/**
 * A single ball position sample captured from a detected frame.
 * Coordinates are in normalised [0, 1] frame space (same as [BallDetection.boundingBox]).
 */
data class BallPosition(
    val centerX: Float,
    val centerY: Float,
    val boundingBox: RectF,
    val confidence: Float,
    val timestampMs: Long
)

/**
 * Maintains a fixed-size ring buffer of recent [BallPosition] samples and provides
 * simple kinematic helpers for shot analysis.
 *
 * Not thread-safe on its own — callers are expected to access it from a single
 * coroutine context (the main dispatcher via [SessionViewModel]).
 */
class TrajectoryTracker(private val maxPositions: Int = 30) {

    private val buffer = ArrayDeque<BallPosition>(maxPositions + 1)

    /** Snapshot of all buffered positions, oldest first. */
    val positions: List<BallPosition> get() = buffer.toList()

    fun add(detection: BallDetection) {
        val box = detection.boundingBox
        val pos = BallPosition(
            centerX    = (box.left + box.right) / 2f,
            centerY    = (box.top + box.bottom) / 2f,
            boundingBox = RectF(box),
            confidence = detection.confidence,
            timestampMs = System.currentTimeMillis()
        )
        if (buffer.size >= maxPositions) buffer.removeFirst()
        buffer.addLast(pos)
    }

    fun clear() { buffer.clear() }

    fun snapshot(): List<BallPosition> = buffer.toList()

    fun recentPositions(withinMs: Long): List<BallPosition> {
        val cutoff = System.currentTimeMillis() - withinMs
        return buffer.filter { it.timestampMs >= cutoff }
    }

    /**
     * Positive value means the ball is moving **downward** (Y increases toward the bottom
     * of the frame).  Returns null when there are fewer than 3 recent samples.
     *
     * Units: normalised [0,1] units per second.
     */
    fun verticalVelocity(): Float? {
        val recent = recentPositions(500L)
        if (recent.size < 3) return null
        val first = recent.first()
        val last  = recent.last()
        val dt = (last.timestampMs - first.timestampMs).toFloat()
        if (dt <= 0f) return null
        return (last.centerY - first.centerY) / dt * 1000f
    }
}
