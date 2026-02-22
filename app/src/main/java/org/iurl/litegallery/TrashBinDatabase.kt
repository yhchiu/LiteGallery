package org.iurl.litegallery

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TrashBinDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRASH_RECORDS (
                $COLUMN_TRASHED_URI TEXT PRIMARY KEY NOT NULL,
                $COLUMN_ORIGINAL_URI TEXT,
                $COLUMN_ORIGINAL_NAME TEXT NOT NULL,
                $COLUMN_ORIGINAL_PATH_HINT TEXT,
                $COLUMN_TRASHED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRASH_RECORDS")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRASH_RECORDS")
        onCreate(db)
    }

    fun upsertRecord(record: TrashRecord) {
        val values = ContentValues().apply {
            put(COLUMN_TRASHED_URI, record.trashedUri)
            put(COLUMN_ORIGINAL_URI, record.originalUri)
            put(COLUMN_ORIGINAL_NAME, record.originalName)
            put(COLUMN_ORIGINAL_PATH_HINT, record.originalPathHint)
            put(COLUMN_TRASHED_AT, record.trashedAtMs)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_TRASH_RECORDS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertRecords(records: Collection<TrashRecord>) {
        if (records.isEmpty()) return

        val db = writableDatabase
        db.beginTransaction()
        try {
            records.forEach { record ->
                val values = ContentValues().apply {
                    put(COLUMN_TRASHED_URI, record.trashedUri)
                    put(COLUMN_ORIGINAL_URI, record.originalUri)
                    put(COLUMN_ORIGINAL_NAME, record.originalName)
                    put(COLUMN_ORIGINAL_PATH_HINT, record.originalPathHint)
                    put(COLUMN_TRASHED_AT, record.trashedAtMs)
                }
                db.insertWithOnConflict(
                    TABLE_TRASH_RECORDS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllRecords(): List<TrashRecord> {
        val records = mutableListOf<TrashRecord>()
        readableDatabase.query(
            TABLE_TRASH_RECORDS,
            arrayOf(
                COLUMN_TRASHED_URI,
                COLUMN_ORIGINAL_URI,
                COLUMN_ORIGINAL_NAME,
                COLUMN_ORIGINAL_PATH_HINT,
                COLUMN_TRASHED_AT
            ),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                records.add(
                    TrashRecord(
                        trashedUri = cursor.getString(0),
                        originalUri = cursor.getString(1),
                        originalName = cursor.getString(2),
                        originalPathHint = cursor.getString(3),
                        trashedAtMs = cursor.getLong(4)
                    )
                )
            }
        }
        return records
    }

    fun getRecordByTrashedUri(trashedUri: String): TrashRecord? {
        readableDatabase.query(
            TABLE_TRASH_RECORDS,
            arrayOf(
                COLUMN_TRASHED_URI,
                COLUMN_ORIGINAL_URI,
                COLUMN_ORIGINAL_NAME,
                COLUMN_ORIGINAL_PATH_HINT,
                COLUMN_TRASHED_AT
            ),
            "$COLUMN_TRASHED_URI = ?",
            arrayOf(trashedUri),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return TrashRecord(
                    trashedUri = cursor.getString(0),
                    originalUri = cursor.getString(1),
                    originalName = cursor.getString(2),
                    originalPathHint = cursor.getString(3),
                    trashedAtMs = cursor.getLong(4)
                )
            }
        }
        return null
    }

    fun removeByTrashedUris(trashedUris: Collection<String>) {
        if (trashedUris.isEmpty()) return

        val db = writableDatabase
        val chunkSize = 900
        trashedUris.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.delete(
                TABLE_TRASH_RECORDS,
                "$COLUMN_TRASHED_URI IN ($placeholders)",
                chunk.toTypedArray()
            )
        }
    }

    fun clearAllRecords() {
        writableDatabase.delete(TABLE_TRASH_RECORDS, null, null)
    }

    data class TrashRecord(
        val trashedUri: String,
        val originalUri: String?,
        val originalName: String,
        val originalPathHint: String?,
        val trashedAtMs: Long
    )

    companion object {
        private const val DATABASE_NAME = "trash_bin.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_TRASH_RECORDS = "trash_records"
        private const val COLUMN_TRASHED_URI = "trashed_uri"
        private const val COLUMN_ORIGINAL_URI = "original_uri"
        private const val COLUMN_ORIGINAL_NAME = "original_name"
        private const val COLUMN_ORIGINAL_PATH_HINT = "original_path_hint"
        private const val COLUMN_TRASHED_AT = "trashed_at"

        @Volatile
        private var INSTANCE: TrashBinDatabase? = null

        fun getInstance(context: Context): TrashBinDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrashBinDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
