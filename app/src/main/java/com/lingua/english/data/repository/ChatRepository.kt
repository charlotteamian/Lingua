package com.lingua.english.data.repository

import android.util.Base64
import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.ai.ChatTurn
import com.lingua.english.data.ai.Prompts
import com.lingua.english.data.local.dao.ChatDao
import com.lingua.english.data.local.dao.DailyLogDao
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.data.prefs.SettingsPreferences
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(
    private val chatDao: ChatDao,
    private val dailyLogDao: DailyLogDao,
    private val client: ChatApiClient,
    private val prefs: SettingsPreferences,
    private val audioDir: File,
) {

    val messages: Flow<List<ChatMessageEntity>> = chatDao.observeAll()

    suspend fun isConfigured(): Boolean = client.isConfigured()

    /**
     * 发送一条用户消息并等助手回复。用户消息先落库 (失败也保留),
     * 助手回复成功后落库。上下文 = 最后一个「新话题」分界之后的消息, 上限 20 条。
     */
    suspend fun send(text: String, newTopic: Boolean): String {
        val now = System.currentTimeMillis()
        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text.trim(),
            createdAtEpochMs = now,
            contextBreak = newTopic,
        )
        chatDao.insert(userMessage)
        bumpDailyChat()

        val context = buildContext()
        val system = Prompts.tutorSystem(
            profile = prefs.learnerProfile.first(),
            levelId = prefs.learnerLevel.first(),
        )
        val reply = client.chat(system, context)
        chatDao.insert(
            ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = reply.trim(),
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
        return reply
    }

    /**
     * 发送语音消息: 录音归档到 audioDir → 音频直发 Gemini → 模型转写回填到
     * 用户气泡 (点词查词照常用) + 回复落库。历史上下文用转写文字, 不重复传音频。
     */
    suspend fun sendVoice(recorded: File, newTopic: Boolean): String {
        val system = Prompts.tutorSystem(
            profile = prefs.learnerProfile.first(),
            levelId = prefs.learnerLevel.first(),
        )
        val context = if (newTopic) emptyList() else buildContext()

        audioDir.mkdirs()
        val stored = File(audioDir, "${UUID.randomUUID()}.aac")
        recorded.copyTo(stored, overwrite = true)
        recorded.delete()

        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = "",
            createdAtEpochMs = System.currentTimeMillis(),
            contextBreak = newTopic,
            audioPath = stored.path,
        )
        chatDao.insert(userMessage)
        bumpDailyChat()

        val audioB64 = Base64.encodeToString(stored.readBytes(), Base64.NO_WRAP)
        val result = client.voiceChatGemini(system, context, audioB64)
        chatDao.updateContent(userMessage.id, result.transcript.trim().ifEmpty { "(语音消息)" })
        chatDao.insert(
            ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = result.reply.trim(),
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
        return result.reply
    }

    suspend fun clearAll() {
        chatDao.clearAll()
        runCatching { audioDir.deleteRecursively() }
    }

    private suspend fun buildContext(): List<ChatTurn> {
        val recent = chatDao.latest(MAX_CONTEXT).reversed()
        val afterBreak = recent.indexOfLast { it.contextBreak }
            .let { if (it >= 0) recent.subList(it, recent.size) else recent }
        return afterBreak
            // 语音消息转写失败时 content 为空, 跳过
            .filter { it.content.isNotBlank() }
            // 合并相邻同角色消息 (上一条发送失败后重发会连出两条 user, Anthropic 协议要求角色交替)
            .fold(mutableListOf()) { acc, message ->
                val last = acc.lastOrNull()
                if (last != null && last.role == message.role) {
                    acc[acc.lastIndex] = last.copy(content = "${last.content}\n\n${message.content}")
                } else {
                    acc.add(ChatTurn(message.role, message.content))
                }
                acc
            }
    }

    private suspend fun bumpDailyChat() {
        val today = LocalDate.now().toString()
        dailyLogDao.insertIgnore(DailyLogEntity(date = today))
        dailyLogDao.incrementChatTurns(today)
    }

    companion object {
        private const val MAX_CONTEXT = 20
    }
}
