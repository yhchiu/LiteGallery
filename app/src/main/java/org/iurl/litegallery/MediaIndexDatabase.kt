package org.iurl.litegallery

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaIndexEntity::class,
        FolderIndexEntity::class,
        MediaSyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MediaIndexDatabase : RoomDatabase() {

    abstract fun mediaIndexDao(): MediaIndexDao

    companion object {
        private const val DATABASE_NAME = "media_index.db"

        @Volatile
        private var INSTANCE: MediaIndexDatabase? = null

        fun getInstance(context: Context): MediaIndexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediaIndexDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
