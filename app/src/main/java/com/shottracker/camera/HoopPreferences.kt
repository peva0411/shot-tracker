package com.shottracker.camera

import android.content.Context
import android.graphics.RectF
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hoopDataStore: DataStore<Preferences> by preferencesDataStore(name = "hoop_settings")

/**
 * The user-defined hoop region.
 * All values are normalised [0, 1] in frame/display space (same coordinate system as
 * [BallDetection.boundingBox] — see architecture-camera.md for the FILL_CENTER details).
 */
data class HoopRegion(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
) {
    val rect: RectF get() = RectF(
        centerX - width  / 2f,
        centerY - height / 2f,
        centerX + width  / 2f,
        centerY + height / 2f
    )
}

/**
 * Persists the basketball hoop region across sessions using DataStore.
 * Returns null when the user has not yet calibrated the hoop.
 */
@Singleton
class HoopPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyCX = floatPreferencesKey("hoop_cx")
    private val keyCY = floatPreferencesKey("hoop_cy")
    private val keyW  = floatPreferencesKey("hoop_w")
    private val keyH  = floatPreferencesKey("hoop_h")

    val hoopRegion: Flow<HoopRegion?> = context.hoopDataStore.data.map { prefs ->
        val cx = prefs[keyCX] ?: return@map null
        val cy = prefs[keyCY] ?: return@map null
        HoopRegion(
            centerX = cx,
            centerY = cy,
            width   = prefs[keyW] ?: DEFAULT_WIDTH,
            height  = prefs[keyH] ?: DEFAULT_HEIGHT
        )
    }

    suspend fun setHoopRegion(region: HoopRegion) {
        context.hoopDataStore.edit { prefs ->
            prefs[keyCX] = region.centerX.coerceIn(0f, 1f)
            prefs[keyCY] = region.centerY.coerceIn(0f, 1f)
            prefs[keyW]  = region.width.coerceIn(0.01f, 1f)
            prefs[keyH]  = region.height.coerceIn(0.01f, 1f)
        }
    }

    suspend fun clearHoopRegion() {
        context.hoopDataStore.edit { prefs ->
            prefs.remove(keyCX); prefs.remove(keyCY)
            prefs.remove(keyW);  prefs.remove(keyH)
        }
    }

    companion object {
        const val DEFAULT_WIDTH  = 0.12f   // ~12% of frame width
        const val DEFAULT_HEIGHT = 0.06f   // ~6% of frame height
    }
}
