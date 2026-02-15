package com.shottracker.camera.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FeedbackManager"

/**
 * Manages audio and haptic feedback for shot detection.
 */
@Singleton
class FeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var soundPool: SoundPool? = null
    private var shotSoundId: Int = -1
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    
    init {
        initializeSoundPool()
    }
    
    private fun initializeSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
        
        // TODO: Add shot detection sound file to res/raw/
        // shotSoundId = soundPool?.load(context, R.raw.shot_detected, 1) ?: -1
        
        Log.d(TAG, "FeedbackManager initialized")
    }
    
    /**
     * Play sound feedback for shot detection.
     */
    fun playShotSound() {
        if (shotSoundId != -1) {
            soundPool?.play(shotSoundId, 1f, 1f, 1, 0, 1f)
            Log.d(TAG, "Shot sound played")
        }
    }
    
    /**
     * Trigger haptic feedback for shot detection.
     */
    fun vibrate() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                it.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(100)
            }
            Log.d(TAG, "Haptic feedback triggered")
        }
    }
    
    /**
     * Provide combined audio and haptic feedback.
     */
    fun provideShotFeedback() {
        playShotSound()
        vibrate()
    }
    
    /**
     * Release resources.
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        Log.d(TAG, "FeedbackManager released")
    }
}
