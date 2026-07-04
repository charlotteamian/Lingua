package com.lingua.english.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.data.local.entity.MaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_message ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_message ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("UPDATE chat_message SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query("DELETE FROM chat_message")
    suspend fun clearAll()
}

@Dao
interface MaterialDao {
    @Query("SELECT * FROM material_entry ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM material_entry WHERE id = :id")
    fun observeById(id: String): Flow<MaterialEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MaterialEntity)

    @Query("DELETE FROM material_entry WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChunkDao {
    @Query("SELECT * FROM chunk ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<ChunkEntity>>

    @Query("SELECT * FROM chunk WHERE materialId = :materialId ORDER BY createdAtEpochMs ASC")
    fun observeByMaterial(materialId: String): Flow<List<ChunkEntity>>

    @Query("SELECT * FROM chunk WHERE dueEpochDay <= :today ORDER BY dueEpochDay ASC, createdAtEpochMs ASC")
    fun observeDue(today: Long): Flow<List<ChunkEntity>>

    @Query("SELECT COUNT(*) FROM chunk WHERE dueEpochDay <= :today")
    fun observeDueCount(today: Long): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM chunk WHERE text = :text COLLATE NOCASE)")
    suspend fun hasText(text: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Update
    suspend fun update(chunk: ChunkEntity)

    @Query("UPDATE chunk SET meaningZh = :meaningZh WHERE id = :id")
    suspend fun updateMeaning(id: String, meaningZh: String)

    @Query("DELETE FROM chunk WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chunk WHERE materialId = :materialId")
    suspend fun deleteByMaterial(materialId: String)
}

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_log WHERE date = :date")
    fun observe(date: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_log ORDER BY date DESC")
    suspend fun all(): List<DailyLogEntity>

    @Query("SELECT * FROM daily_log ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyLogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(log: DailyLogEntity)

    @Query("UPDATE daily_log SET reviewsDone = reviewsDone + 1 WHERE date = :date")
    suspend fun incrementReviews(date: String)

    @Query("UPDATE daily_log SET listensDone = listensDone + 1 WHERE date = :date")
    suspend fun incrementListens(date: String)

    @Query("UPDATE daily_log SET chatTurns = chatTurns + 1 WHERE date = :date")
    suspend fun incrementChatTurns(date: String)

    @Query("UPDATE daily_log SET materialsAdded = materialsAdded + 1 WHERE date = :date")
    suspend fun incrementMaterials(date: String)
}
