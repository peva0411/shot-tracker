package com.shottracker.drive

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// GoogleDriveService is @Singleton and @Inject constructor, so Hilt resolves it automatically.
// This module exists as a placeholder for any future Drive-related bindings.
@Module
@InstallIn(SingletonComponent::class)
object DriveModule
