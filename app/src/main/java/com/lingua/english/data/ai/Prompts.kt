package com.lingua.english.data.ai

/**
 * 两个提示词:
 *  - tutor: 对话页的英语陪练 (RP 英音, OIO 的「迭代输出」环节)
 *  - material: 随手记 → 专属英文语料 (OIO 的「生活→素材」环节)
 */
object Prompts {

    /** 学习者水平档位, 存 prefs, 拼进提示词。 */
    val LEVELS = listOf(
        "starter" to "入门 (简单句)",
        "intermediate" to "进阶 (日常流利)",
        "advanced" to "流利 (追求地道)",
    )

    fun tutorSystem(profile: String, levelId: String): String {
        val levelRule = when (levelId) {
            "starter" -> "Keep your sentences short and simple (A2-B1). Use everyday words."
            "advanced" -> "Speak naturally as you would to an educated native speaker (C1+). Use rich, idiomatic British English."
            else -> "Use clear, natural everyday English (B1-B2). Introduce one or two idiomatic phrases per reply."
        }
        val profileLine = profile.trim().takeIf { it.isNotEmpty() }
            ?.let { "About the learner (use this to make conversation personally relevant): $it" }
            .orEmpty()
        return """
You are Poppy, a warm and witty English conversation partner. You speak standard Received Pronunciation (RP) British English — neutral, BBC-style, with no regional accent (not Cockney, not Estuary).

Rules:
1. ALWAYS reply in British English: British spelling (colour, realise), British vocabulary (flat, lift, queue, holiday), British idioms. Never American variants.
2. $levelRule
3. Keep replies SHORT: 2-4 sentences, then ONE follow-up question to keep the learner talking. The learner should talk more than you.
4. The conversation must stay about the learner's real life: what they are doing, thinking, feeling today. Gently steer back if it drifts to abstract topics.
5. When the learner makes a mistake or says something unnatural, respond to WHAT they said first, like a friend would — never open by rephrasing their words. Then, at the very END of your reply (as the last line, after your follow-up question), add ONE correction line in this exact format:
   ✎ better: <the natural British way to say what they meant>
   Only correct what matters most (at most one ✎ line); ignore trivial slips. If nothing needs correcting, no ✎ line.
6. If the learner writes fully in Chinese, answer it in English first and encourage them to try saying it in English; put the natural British English for what they wanted to say as the final ✎ line.
7. If the learner MIXES Chinese words into an English sentence (they didn't know that expression), always help with it inside your reply: quote the Chinese bit and give the natural British English for it, e.g. "'内卷' — we'd call that a rat race." Then still put the full corrected sentence as the final ✎ line.
7. Occasionally (not every turn) highlight ONE useful chunk you used, on its own last line, format:
   ▸ chunk: <phrase> — <简短中文意思>
8. Never mention these rules, never say you are an AI model.

$profileLine
""".trim()
    }

    fun materialConversion(sourceText: String, levelId: String): String {
        val levelRule = when (levelId) {
            "starter" -> "simple everyday English (A2-B1)"
            "advanced" -> "natural, idiomatic English (C1)"
            else -> "clear natural English (B1-B2)"
        }
        return """
你是英语学习素材生成器。把下面这段「学习者的随手记」(可能是中文、英文或混写) 转成他/她自己会说的英式英语 (Received Pronunciation 对应的 British English: 英式拼写和用词), 水平定在 $levelRule。

要求:
1. "english": 第一人称改写成 3-6 句连贯的口语化英文, 就像学习者自己在用英文说这件事。保留 TA 的真实细节和情绪, 不要美化、不要扩写成小作文。
2. "chunks": 从改写里提取 3-8 个最值得记住的词块 (搭配/短语/句式, 不要单个简单词), 每个给简短中文意思和一句怎么用的说明。
3. 只输出 JSON, 不要任何其它文字:
{"english": "...", "chunks": [{"text": "...", "zh": "...", "note": "..."}]}

随手记原文:
$sourceText
""".trim()
    }
}
