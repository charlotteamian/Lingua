package com.lingua.english.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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

        fun get(context: Context): LinguaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LinguaDatabase::class.java,
                    "lingua.db",
                ).build().also { instance = it }
            }
    }
}
