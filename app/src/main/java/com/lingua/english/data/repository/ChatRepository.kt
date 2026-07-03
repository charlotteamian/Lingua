package com.lingua.english.data.repository

import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.ai.ChatTurn
import com.lingua.english.data.ai.Prompts
import com.lingua.english.data.local.dao.ChatDao
import com.lingua.english.data.local.dao.DailyLogDao
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.data.prefs.SettingsPreferences
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(
    private val chatDao: ChatDao,
    private val dailyLogDao: DailyLogDao,
    private val client: ChatApiClient,
    private val prefs: SettingsPreferences,
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

    suspend fun clearAll() = chatDao.clearAll()

    private suspend fun buildContext(): List<ChatTurn> {
        val recent = chatDao.latest(MAX_CONTEXT).reversed()
        val afterBreak = recent.indexOfLast { it.contextBreak }
            .let { if (it >= 0) recent.subList(it, recent.size) else recent }
        // 合并相邻同角色消息 (上一条发送失败后重发会连出两条 user, Anthropic 协议要求角色交替)
        return afterBreak.fold(mutableListOf()) { acc, message ->
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
