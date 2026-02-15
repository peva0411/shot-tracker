package com.shottracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ShotTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide components here
    }
}
