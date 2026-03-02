package com.shottracker.media

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class CaptureConverters {
    @TypeConverter fun fromCaptureType(value: CaptureType): String = value.name
    @TypeConverter fun toCaptureType(value: String): CaptureType = CaptureType.valueOf(value)
    @TypeConverter fun fromUploadStatus(value: UploadStatus): String = value.name
    @TypeConverter fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}

class ShotConverters {
    @TypeConverter fun fromShotOutcome(value: ShotOutcome): String = value.name
    @TypeConverter fun toShotOutcome(value: String): ShotOutcome = ShotOutcome.valueOf(value)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS shots (
                id              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                capturedAt      INTEGER NOT NULL,
                outcome         TEXT NOT NULL,
                madeConfidence  REAL NOT NULL,
                trajectoryJson  TEXT NOT NULL,
                hoopRegionJson  TEXT NOT NULL
            )"""
        )
    }
}

@Database(entities = [CaptureEntity::class, ShotEntity::class], version = 2, exportSchema = false)
@TypeConverters(CaptureConverters::class, ShotConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun captureDao(): CaptureDao
    abstract fun shotDao(): ShotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shot_tracker.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
    }
}
