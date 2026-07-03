package com.lingua.english.data.dict

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 在线词典兜底: dictionaryapi.dev (免费, 无 key)。离线 ECDICT 查不到才用,
 * 只有英文释义; 音频优先挑英音 (-uk.mp3)。
 */
class OnlineDictClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 查不到或网络失败返回 null, 由调用方决定文案。 */
    fun lookup(word: String): WordDefinition? {
        val request = Request.Builder()
            .url("https://api.dictionaryapi.dev/api/v2/entries/en/${word.trim()}")
            .build()
        val body = runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull() ?: return null

        return runCatching { parse(word, body) }.getOrNull()
    }

    private fun parse(queried: String, body: String): WordDefinition? {
        val entries = json.parseToJsonElement(body).jsonArray
        if (entries.isEmpty()) return null
        val entry = entries[0].jsonObject

        val phonetics = entry["phonetics"]?.jsonArray.orEmpty().map { it.jsonObject }
        val ukPhonetic = phonetics.firstOrNull {
            it["audio"]?.jsonPrimitive?.content.orEmpty().contains("-uk.")
        }
        val chosen = ukPhonetic ?: phonetics.firstOrNull {
            !it["text"]?.jsonPrimitive?.content.isNullOrBlank()
        }
        val phoneticText = (chosen?.get("text")?.jsonPrimitive?.content
            ?: entry["phonetic"]?.jsonPrimitive?.content).orEmpty().trim('/')
        val audio = ukPhonetic?.get("audio")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        val definitions = entry["meanings"]?.jsonArray.orEmpty().flatMap { meaning ->
            val pos = meaning.jsonObject["partOfSpeech"]?.jsonPrimitive?.content.orEmpty()
            meaning.jsonObject["definitions"]?.jsonArray.orEmpty().take(2).map { def ->
                val text = def.jsonObject["definition"]?.jsonPrimitive?.content.orEmpty()
                if (pos.isNotEmpty()) "$pos. $text" else text
            }
        }.filter { it.isNotBlank() }.take(6)
        if (definitions.isEmpty()) return null

        return WordDefinition(
            word = entry["word"]?.jsonPrimitive?.content ?: queried,
            queried = queried,
            phonetic = phoneticText,
            translations = emptyList(),
            definitions = definitions,
            tags = emptyList(),
            forms = emptyList(),
            fromOnline = true,
            audioUrl = audio,
        )
    }
}
