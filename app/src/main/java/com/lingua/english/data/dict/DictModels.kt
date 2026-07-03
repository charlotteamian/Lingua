package com.lingua.english.data.dict

/** 一次查词的结果, 离线 ECDICT 和在线兜底统一成这个形状。 */
data class WordDefinition(
    val word: String,                       // 命中的词条 (可能是还原后的原形)
    val queried: String,                    // 用户点的原词
    val phonetic: String,                   // 英式音标 (不带斜杠)
    val translations: List<String>,         // 中文释义, 按行
    val definitions: List<String>,          // 英文释义, 按行
    val tags: List<String>,                 // 中考/高考/四六级… 中文标签
    val forms: List<Pair<String, String>>,  // 词形变化: 说明 to 形式
    val fromOnline: Boolean = false,
    val audioUrl: String? = null,           // 在线词典的英音音频 (有就优先播放)
)

object DictTagLabels {
    private val LABELS = mapOf(
        "zk" to "中考", "gk" to "高考", "cet4" to "四级", "cet6" to "六级",
        "ky" to "考研", "toefl" to "托福", "ielts" to "雅思", "gre" to "GRE",
    )

    fun labelsOf(tagField: String): List<String> =
        tagField.split(' ').mapNotNull { LABELS[it.trim()] }
}

/** ECDICT exchange 字段: p:过去式 d:过去分词 i:现在分词 3:三单 r:比较级 t:最高级 s:复数 0:原形 1:变形来源 */
object DictExchange {
    private val LABELS = mapOf(
        "p" to "过去式", "d" to "过去分词", "i" to "现在分词", "3" to "三单",
        "r" to "比较级", "t" to "最高级", "s" to "复数",
    )

    fun parse(exchange: String): List<Pair<String, String>> =
        exchange.split('/').mapNotNull { part ->
            val (key, value) = part.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val label = LABELS[key] ?: return@mapNotNull null
            label to value
        }
}
