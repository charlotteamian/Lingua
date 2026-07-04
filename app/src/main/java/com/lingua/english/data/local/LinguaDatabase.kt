package com.lingua.english.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lingua.english.data.local.dao.ChatDao
import com.lingua.english.data.local.dao.ChunkDao
import com.lingua.english.data.local.dao.DailyLogDao
import com.lingua.english.data.local.dao.MaterialDao
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.data.local.entity.MaterialEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        MaterialEntity::class,
        ChunkEntity::class,
        DailyLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LinguaDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun materialDao(): MaterialDao
    abstract fun chunkDao(): ChunkDao
    abstract fun dailyLogDao(): DailyLogDao

    companion object {
        @Volatile
        private var instance: LinguaDatabase? = null

        /** v1→v2: 聊天消息加语音文件路径 (语音消息功能)。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_message ADD COLUMN audioPath TEXT")
            }
        }

        fun get(context: Context): LinguaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LinguaDatabase::class.java,
                    "lingua.db",
                ).addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
