package com.shottracker.camera.detector

/**
 * A simple 2D Kalman filter for ball position tracking.
 *
 * State vector: [x, y, vx, vy] (position and velocity in normalized [0,1] coords).
 * Measurement: [x, y] (detected ball center).
 *
 * On detection frames, call [correct] with measured position.
 * On skipped frames, call [predict] to get an estimated position.
 */
class BallKalmanFilter(
    private val processNoise: Float = 1e-3f,
    private val measurementNoise: Float = 1e-2f
) {
    // State: [x, y, vx, vy]
    private var x = floatArrayOf(0f, 0f, 0f, 0f)

    // Diagonal covariance (simplified — no off-diagonal terms)
    private var p = floatArrayOf(1f, 1f, 1f, 1f)

    private var initialized = false

    /** Whether the filter has received at least one measurement. */
    val isInitialized: Boolean get() = initialized

    /** Current estimated position (x, y) in normalized coords. */
    val estimatedX: Float get() = x[0]
    val estimatedY: Float get() = x[1]
    val estimatedVx: Float get() = x[2]
    val estimatedVy: Float get() = x[3]

    /**
     * Predict the next state given a time step.
     * Call this on every frame (including skipped ones).
     *
     * @param dt time step in seconds (e.g., 1/30 for 30fps)
     * @return estimated (x, y) after prediction
     */
    fun predict(dt: Float = 1f / 30f): Pair<Float, Float> {
        if (!initialized) return Pair(0.5f, 0.5f)

        // State transition: x' = x + vx*dt, y' = y + vy*dt
        x[0] += x[2] * dt
        x[1] += x[3] * dt

        // Covariance grows
        p[0] += p[2] * dt * dt + processNoise
        p[1] += p[3] * dt * dt + processNoise
        p[2] += processNoise
        p[3] += processNoise

        return Pair(x[0], x[1])
    }

    /**
     * Correct the state with an actual measurement from the detector.
     * Call this on frames where the ball is detected.
     *
     * @param measuredX detected ball center X (normalized [0,1])
     * @param measuredY detected ball center Y (normalized [0,1])
     */
    fun correct(measuredX: Float, measuredY: Float) {
        if (!initialized) {
            x[0] = measuredX
            x[1] = measuredY
            x[2] = 0f
            x[3] = 0f
            p = floatArrayOf(measurementNoise, measurementNoise, 1f, 1f)
            initialized = true
            return
        }

        // Kalman gain (simplified diagonal)
        val kx = p[0] / (p[0] + measurementNoise)
        val ky = p[1] / (p[1] + measurementNoise)

        // Innovation (measurement residual)
        val ix = measuredX - x[0]
        val iy = measuredY - x[1]

        // Update state
        x[0] += kx * ix
        x[1] += ky * iy
        // Update velocity estimate from position correction
        x[2] += kx * ix * 0.5f
        x[3] += ky * iy * 0.5f

        // Update covariance
        p[0] *= (1f - kx)
        p[1] *= (1f - ky)
    }

    /** Reset the filter state. */
    fun reset() {
        x = floatArrayOf(0f, 0f, 0f, 0f)
        p = floatArrayOf(1f, 1f, 1f, 1f)
        initialized = false
    }
}
