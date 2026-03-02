package com.shottracker.data

import android.content.Context
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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "detection_settings")

@Singleton
class DetectionPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyConfidence = floatPreferencesKey("confidence_threshold")

    val confidenceThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[keyConfidence] ?: DEFAULT_CONFIDENCE
    }

    suspend fun setConfidenceThreshold(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[keyConfidence] = value.coerceIn(0.05f, 0.95f)
        }
    }

    companion object {
        const val DEFAULT_CONFIDENCE = 0.3f
    }
}
