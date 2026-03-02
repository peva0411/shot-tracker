package com.shottracker.media

import android.util.Log
import com.shottracker.camera.HoopRegion
import com.shottracker.camera.detector.BallPosition
import com.shottracker.camera.detector.ShotEvent
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ShotRepository"

@Singleton
class ShotRepository @Inject constructor(private val shotDao: ShotDao) {

    fun getAllShots() = shotDao.getAllShots()

    /**
     * Persist a detected shot.  Trajectory and hoop region are serialised to JSON so they
     * can be inspected offline and used for future model training.
     */
    suspend fun insertShot(
        event: ShotEvent,
        outcome: ShotOutcome,
        hoopRegion: HoopRegion?,
        madeConfidence: Float = 0f
    ): Long {
        val entity = ShotEntity(
            capturedAt      = event.timestampMs,
            outcome         = outcome,
            madeConfidence  = madeConfidence,
            trajectoryJson  = trajectoryToJson(event.positions),
            hoopRegionJson  = hoopToJson(hoopRegion)
        )
        return shotDao.insert(entity).also {
            Log.d(TAG, "Shot saved id=$it outcome=$outcome positions=${event.positions.size}")
        }
    }

    suspend fun deleteShot(id: Long) = shotDao.deleteById(id)

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun trajectoryToJson(positions: List<BallPosition>): String {
        val array = JSONArray()
        positions.forEach { p ->
            array.put(JSONObject().apply {
                put("cx", p.centerX.toDouble())
                put("cy", p.centerY.toDouble())
                put("conf", p.confidence.toDouble())
                put("ts", p.timestampMs)
                put("l", p.boundingBox.left.toDouble())
                put("t", p.boundingBox.top.toDouble())
                put("r", p.boundingBox.right.toDouble())
                put("b", p.boundingBox.bottom.toDouble())
            })
        }
        return array.toString()
    }

    private fun hoopToJson(region: HoopRegion?): String {
        if (region == null) return "{}"
        return JSONObject().apply {
            put("cx", region.centerX.toDouble())
            put("cy", region.centerY.toDouble())
            put("w",  region.width.toDouble())
            put("h",  region.height.toDouble())
        }.toString()
    }
}
