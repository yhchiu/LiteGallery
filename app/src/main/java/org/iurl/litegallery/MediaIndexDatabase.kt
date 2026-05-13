package org.iurl.litegallery

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaIndexEntity::class,
        FolderIndexEntity::class,
        MediaSyncStateEntity::class
    ],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_name` ON `media_items` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_sizeBytes` ON `media_items` (`sizeBytes`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_items_mediaType_dateModifiedMs` " +
                        "ON `media_items` (`mediaType`, `dateModifiedMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_items_mediaType_name` " +
                        "ON `media_items` (`mediaType`, `name`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_items_mediaType_sizeBytes` " +
                        "ON `media_items` (`mediaType`, `sizeBytes`)"
                )
            }
        }
    }
}
