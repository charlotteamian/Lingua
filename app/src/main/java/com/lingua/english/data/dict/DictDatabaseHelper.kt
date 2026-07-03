package com.lingua.english.data.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 离线词典: assets/dict/ecdict.db (构建期由 tools/build_dict.py 生成) 首次使用时
 * 拷到 filesDir 再只读打开。词典只读不写, 不走 Room, 避免 schema 校验的额外负担。
 */
class DictDatabaseHelper(private val context: Context) {

    private val lock = Any()
    private var db: SQLiteDatabase? = null

    private fun open(): SQLiteDatabase {
        db?.takeIf { it.isOpen }?.let { return it }
        synchronized(lock) {
            db?.takeIf { it.isOpen }?.let { return it }
            val target = File(context.filesDir, "dict/ecdict-$ASSET_VERSION.db")
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                // 换版本时清掉旧库
                target.parentFile?.listFiles()?.forEach { it.delete() }
                context.assets.open("dict/ecdict.db").use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
            return SQLiteDatabase.openDatabase(
                target.path, null, SQLiteDatabase.OPEN_READONLY,
            ).also { db = it }
        }
    }

    /** 精确查词 (COLLATE NOCASE)。 */
    fun findWord(word: String): WordDefinition? {
        val database = open()
        database.rawQuery(
            "SELECT word, phonetic, translation, definition, tag, exchange FROM word WHERE word = ? COLLATE NOCASE LIMIT 1",
            arrayOf(word),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return WordDefinition(
                word = cursor.getString(0),
                queried = word,
                phonetic = normalizePhonetic(cursor.getString(1)),
                translations = splitLines(cursor.getString(2)),
                definitions = splitLines(cursor.getString(3)),
                tags = DictTagLabels.labelsOf(cursor.getString(4)),
                forms = DictExchange.parse(cursor.getString(5)),
            )
        }
    }

    /** 词形还原表: went → go。 */
    fun findLemma(form: String): String? {
        val database = open()
        database.rawQuery(
            "SELECT lemma FROM lemma WHERE form = ? COLLATE NOCASE LIMIT 1",
            arrayOf(form),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun wordCount(): Int {
        val database = open()
        database.rawQuery("SELECT value FROM meta WHERE key = 'words'", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() ?: 0 else 0
        }
    }

    private fun splitLines(raw: String?): List<String> =
        raw.orEmpty().split("\\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** ECDICT 音标用了几个非标准字符, 显示前替换成 IPA。 */
    private fun normalizePhonetic(raw: String?): String =
        raw.orEmpty()
            .replace('ә', 'ə')
            .replace('ε', 'e')
            .replace("ʧ", "tʃ")
            .replace("ʤ", "dʒ")

    companion object {
        /** 词典资产版本, 重新生成 ecdict.db 时 +1, 触发重拷。 */
        private const val ASSET_VERSION = 1
    }
}
