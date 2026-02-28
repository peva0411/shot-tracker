package com.shottracker.media

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context

class CaptureConverters {
    @TypeConverter
    fun fromCaptureType(value: CaptureType): String = value.name
    @TypeConverter
    fun toCaptureType(value: String): CaptureType = CaptureType.valueOf(value)
    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name
    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}

@Database(entities = [CaptureEntity::class], version = 1, exportSchema = false)
@TypeConverters(CaptureConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun captureDao(): CaptureDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shot_tracker.db"
                ).build().also { INSTANCE = it }
            }
    }
}
