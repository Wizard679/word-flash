package com.cishua.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cishua.app.engine.WordState

/** 学习进度存储（SQLite）。每个单词在每个词库下一条记录。 */
class ProgressStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "cishua.db"
        const val DB_VERSION = 1
        const val TABLE = "progress"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                bank TEXT NOT NULL,
                word TEXT NOT NULL,
                stage INTEGER NOT NULL DEFAULT 0,
                weight INTEGER NOT NULL DEFAULT 10,
                correct INTEGER NOT NULL DEFAULT 0,
                wrong INTEGER NOT NULL DEFAULT 0,
                mastered INTEGER NOT NULL DEFAULT 0,
                level INTEGER NOT NULL DEFAULT 0,
                due_at INTEGER NOT NULL DEFAULT 0,
                seen INTEGER NOT NULL DEFAULT 0,
                first_day TEXT NOT NULL DEFAULT '',
                last_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (bank, word)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 词库/结构升级时保底处理：重建表，进度重新开始
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun loadAll(bank: String): MutableMap<String, WordState> {
        val out = HashMap<String, WordState>(2048)
        readableDatabase.rawQuery(
            "SELECT word, stage, weight, correct, wrong, mastered, level, due_at, seen, first_day, last_at FROM $TABLE WHERE bank = ?",
            arrayOf(bank)
        ).use { c ->
            while (c.moveToNext()) {
                out[c.getString(0)] = WordState(
                    stage = c.getInt(1),
                    weight = c.getInt(2),
                    correct = c.getInt(3),
                    wrong = c.getInt(4),
                    mastered = c.getInt(5) != 0,
                    level = c.getInt(6),
                    dueAt = c.getLong(7),
                    seen = c.getInt(8),
                    firstDay = c.getString(9) ?: "",
                    lastAt = c.getLong(10)
                )
            }
        }
        return out
    }

    fun save(bank: String, word: String, s: WordState) {
        val cv = ContentValues().apply {
            put("bank", bank)
            put("word", word)
            put("stage", s.stage)
            put("weight", s.weight)
            put("correct", s.correct)
            put("wrong", s.wrong)
            put("mastered", if (s.mastered) 1 else 0)
            put("level", s.level)
            put("due_at", s.dueAt)
            put("seen", s.seen)
            put("first_day", s.firstDay)
            put("last_at", s.lastAt)
        }
        writableDatabase.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun masteredCount(bank: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE bank = ? AND mastered = 1", arrayOf(bank)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun masteredByBank(): Map<String, Int> {
        val out = HashMap<String, Int>()
        readableDatabase.rawQuery(
            "SELECT bank, COUNT(*) FROM $TABLE WHERE mastered = 1 GROUP BY bank", null
        ).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getInt(1)
        }
        return out
    }

    fun countNewToday(bank: String, day: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE bank = ? AND first_day = ?", arrayOf(bank, day)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun seenCount(bank: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE bank = ? AND seen > 0", arrayOf(bank)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun resetBank(bank: String) {
        writableDatabase.delete(TABLE, "bank = ?", arrayOf(bank))
    }

    fun resetAll() {
        writableDatabase.delete(TABLE, null, null)
    }
}
