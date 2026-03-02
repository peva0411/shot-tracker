package com.shottracker.media

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ShotOutcome { ATTEMPTED, MADE, MISSED, AMBIGUOUS }

/**
 * Persisted record of a detected shot attempt, including a trajectory snapshot
 * for offline analysis and future model improvement.
 *
 * [trajectoryJson]  – JSON array of [BallPosition] snapshots at shot time.
 * [hoopRegionJson]  – JSON of the [HoopRegion] active at shot time (useful for replay analysis).
 */
@Entity(tableName = "shots")
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val outcome: ShotOutcome,
    val madeConfidence: Float,
    val trajectoryJson: String,
    val hoopRegionJson: String
)
