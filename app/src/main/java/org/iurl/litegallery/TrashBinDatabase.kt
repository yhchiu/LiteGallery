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
                $COLUMN_PATH TEXT PRIMARY KEY NOT NULL,
                $COLUMN_ORIGINAL_NAME TEXT NOT NULL,
                $COLUMN_TRASHED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrade path yet.
    }

    fun upsertRecord(path: String, originalName: String, trashedAtMs: Long) {
        val values = ContentValues().apply {
            put(COLUMN_PATH, path)
            put(COLUMN_ORIGINAL_NAME, originalName)
            put(COLUMN_TRASHED_AT, trashedAtMs)
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
                    put(COLUMN_PATH, record.path)
                    put(COLUMN_ORIGINAL_NAME, record.originalName)
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

    fun getAllPaths(): Set<String> {
        val paths = mutableSetOf<String>()
        readableDatabase.query(
            TABLE_TRASH_RECORDS,
            arrayOf(COLUMN_PATH),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                paths.add(cursor.getString(0))
            }
        }
        return paths
    }

    fun getAllRecords(): List<TrashRecord> {
        val records = mutableListOf<TrashRecord>()
        readableDatabase.query(
            TABLE_TRASH_RECORDS,
            arrayOf(COLUMN_PATH, COLUMN_ORIGINAL_NAME, COLUMN_TRASHED_AT),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                records.add(
                    TrashRecord(
                        path = cursor.getString(0),
                        originalName = cursor.getString(1),
                        trashedAtMs = cursor.getLong(2)
                    )
                )
            }
        }
        return records
    }

    fun getOriginalName(path: String): String? {
        readableDatabase.query(
            TABLE_TRASH_RECORDS,
            arrayOf(COLUMN_ORIGINAL_NAME),
            "$COLUMN_PATH = ?",
            arrayOf(path),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    fun removePaths(paths: Collection<String>) {
        if (paths.isEmpty()) return

        val db = writableDatabase
        val chunkSize = 900
        paths.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.delete(
                TABLE_TRASH_RECORDS,
                "$COLUMN_PATH IN ($placeholders)",
                chunk.toTypedArray()
            )
        }
    }

    fun updateTrashedAt(paths: Collection<String>, trashedAtMs: Long) {
        if (paths.isEmpty()) return

        val values = ContentValues().apply {
            put(COLUMN_TRASHED_AT, trashedAtMs)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            paths.forEach { path ->
                db.update(
                    TABLE_TRASH_RECORDS,
                    values,
                    "$COLUMN_PATH = ?",
                    arrayOf(path)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    data class TrashRecord(
        val path: String,
        val originalName: String,
        val trashedAtMs: Long
    )

    companion object {
        private const val DATABASE_NAME = "trash_bin.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_TRASH_RECORDS = "trash_records"
        private const val COLUMN_PATH = "path"
        private const val COLUMN_ORIGINAL_NAME = "original_name"
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
