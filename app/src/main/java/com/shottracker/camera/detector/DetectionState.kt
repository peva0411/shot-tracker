package com.shottracker.camera.detector

/** States emitted by the ball detector. */
enum class DetectionState {
    IDLE,        // No ball visible
    DETECTING,   // Ball detected below shot-confidence threshold
    CONFIRMED    // Ball detected above shot-confidence threshold
}
