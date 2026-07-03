package com.lingua.english.data.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 查词入口: 归一化 → 离线精确 → 词形还原 → 常见后缀剥离 → 在线兜底。
 * 在线结果做进程内缓存, 反复点同一个词不重复请求。
 */
class DictRepository(
    private val local: DictDatabaseHelper,
    private val online: OnlineDictClient,
) {

    private val onlineCache = HashMap<String, WordDefinition?>()

    suspend fun lookup(rawWord: String): WordDefinition? = withContext(Dispatchers.IO) {
        val word = normalize(rawWord) ?: return@withContext null

        local.findWord(word)?.let { return@withContext it.copy(queried = rawWord) }

        // 词形还原: went→go, cities→city
        local.findLemma(word)?.let { lemma ->
            local.findWord(lemma)?.let { return@withContext it.copy(queried = rawWord) }
        }

        // 常见后缀剥离兜底 (词典和 lemma 表都没有时)
        for (candidate in suffixCandidates(word)) {
            local.findWord(candidate)?.let { return@withContext it.copy(queried = rawWord) }
        }

        val cached = onlineCache[word]
        if (onlineCache.containsKey(word)) {
            return@withContext cached?.copy(queried = rawWord)
        }
        val fetched = online.lookup(word)
        onlineCache[word] = fetched
        fetched?.copy(queried = rawWord)
    }

    fun offlineWordCount(): Int = local.wordCount()

    /** 去掉两端非字母字符和所有格, 全小写。纯数字/空串返回 null。 */
    private fun normalize(raw: String): String? {
        var word = raw.trim().trim { !it.isLetter() && it != '\'' && it != '’' }
        word = word.removeSuffix("'s").removeSuffix("’s").trimEnd('\'', '’')
        word = word.lowercase()
        return word.takeIf { it.isNotEmpty() && it.any { ch -> ch.isLetter() } }
    }

    private fun suffixCandidates(word: String): List<String> {
        val candidates = mutableListOf<String>()
        fun addIfLong(base: String) {
            if (base.length >= 2) candidates.add(base)
        }
        when {
            word.endsWith("ies") -> addIfLong(word.dropLast(3) + "y")
            word.endsWith("es") -> {
                addIfLong(word.dropLast(2))
                addIfLong(word.dropLast(1))
            }
            word.endsWith("s") && !word.endsWith("ss") -> addIfLong(word.dropLast(1))
        }
        if (word.endsWith("ing")) {
            addIfLong(word.dropLast(3))
            addIfLong(word.dropLast(3) + "e")
        }
        if (word.endsWith("ed")) {
            addIfLong(word.dropLast(2))
            addIfLong(word.dropLast(1))
        }
        if (word.endsWith("er") || word.endsWith("est")) {
            val cut = if (word.endsWith("er")) 2 else 3
            addIfLong(word.dropLast(cut))
            addIfLong(word.dropLast(cut) + "e")
        }
        return candidates
    }
}
