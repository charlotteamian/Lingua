package com.lingua.english.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.lingua.english.R

/** 底部 tab: 按用户动作划分 — 看今天 / 去聊天 / 查语料 / 做复习。设置从今日页齿轮进。 */
enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    Today("today", R.string.nav_today, Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    Chat("chat", R.string.nav_chat, Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    Corpus("corpus", R.string.nav_corpus, Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks),
    Review("review", R.string.nav_review, Icons.Outlined.Style, Icons.Filled.Style),
}

object Routes {
    const val SETTINGS = "settings"
    const val COMPOSE_ENTRY = "compose_entry"
    const val MATERIAL_DETAIL = "material/{materialId}"
    const val PRACTICE = "practice/{materialId}"
    const val CHAT_HISTORY = "chat_history"
    const val CHAT_SESSION = "chat_session/{anchorId}"
    const val LIBRARY = "library"
    const val MEDIA = "media"
    const val MEDIA_PLAYER = "media_player/{mediaId}"
    const val YT_LOGIN = "yt_login"
    const val STATS = "stats"

    fun materialDetail(id: String) = "material/$id"
    fun practice(id: String) = "practice/$id"
    fun chatSession(anchorId: String) = "chat_session/$anchorId"
    fun mediaPlayer(id: String) = "media_player/$id"
}
