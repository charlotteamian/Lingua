package com.lingua.english.data.repository

import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.ai.ChatTurn
import com.lingua.english.data.ai.AiException
import com.lingua.english.data.ai.Prompts
import com.lingua.english.data.local.dao.ChunkDao
import com.lingua.english.data.local.dao.DailyLogDao
import com.lingua.english.data.local.dao.MaterialDao
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.data.local.entity.MaterialEntity
import com.lingua.english.data.prefs.SettingsPreferences
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** AI 转写结果 (未落库), 供随手记页预览后再保存。 */
@Serializable
data class ConvertedMaterial(
    val english: String,
    val chunks: List<ConvertedChunk> = emptyList(),
)

@Serializable
data class ConvertedChunk(
    val text: String,
    val zh: String = "",
    val note: String = "",
)

class MaterialRepository(
    private val materialDao: MaterialDao,
    private val chunkDao: ChunkDao,
    private val dailyLogDao: DailyLogDao,
    private val client: ChatApiClient,
    private val prefs: SettingsPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true }

    val materials: Flow<List<MaterialEntity>> = materialDao.observeAll()

    fun material(id: String): Flow<MaterialEntity?> = materialDao.observeById(id)

    fun chunksOf(materialId: String): Flow<List<ChunkEntity>> = chunkDao.observeByMaterial(materialId)

    /** 随手记 → AI 转写 (不落库, 预览用)。 */
    suspend fun convert(sourceText: String): ConvertedMaterial {
        val prompt = Prompts.materialConversion(sourceText, prefs.learnerLevel.first())
        val raw = client.chat(system = "", turns = listOf(ChatTurn("user", prompt)))
        val parsed = runCatching {
            json.decodeFromString(ConvertedMaterial.serializer(), ChatApiClient.extractJsonObject(raw))
        }.getOrElse { throw AiException("模型返回了无法解析的内容, 重试一次试试。", it) }
        if (parsed.english.isBlank()) throw AiException("模型没有给出英文转写, 重试一次试试。")
        return parsed
    }

    /** 保存转写结果: 语料落库, 选中的词块进复习队列 (今天到期)。 */
    suspend fun save(sourceText: String, converted: ConvertedMaterial, keptChunks: List<ConvertedChunk>) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val materialId = UUID.randomUUID().toString()
        materialDao.insert(
            MaterialEntity(
                id = materialId,
                date = today.toString(),
                sourceText = sourceText.trim(),
                english = converted.english.trim(),
                createdAtEpochMs = now,
            )
        )
        chunkDao.insertAll(
            keptChunks.mapIndexed { index, chunk ->
                ChunkEntity(
                    id = UUID.randomUUID().toString(),
                    materialId = materialId,
                    text = chunk.text.trim(),
                    meaningZh = chunk.zh.trim(),
                    note = chunk.note.trim(),
                    kind = ChunkEntity.KIND_CHUNK,
                    createdAtEpochMs = now + index,
                    dueEpochDay = today.toEpochDay(),
                )
            }
        )
        dailyLogDao.insertIgnore(DailyLogEntity(date = today.toString()))
        dailyLogDao.incrementMaterials(today.toString())
    }

    suspend fun delete(materialId: String) {
        chunkDao.deleteByMaterial(materialId)
        materialDao.deleteById(materialId)
    }

    /** 完整听完一条语料, 计入每日执行。 */
    suspend fun recordListen() {
        val today = LocalDate.now().toString()
        dailyLogDao.insertIgnore(DailyLogEntity(date = today))
        dailyLogDao.incrementListens(today)
    }
}
