package com.lingua.english.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

private val WORD_REGEX = Regex("[A-Za-z][A-Za-z'’-]*")

/**
 * 每个英文单词都可点的文本 — 全 app 查词入口。
 * 单词不加下划线不变色, 保持纯文本观感; 点了弹词典底部卡。
 */
@Composable
fun TappableText(
    text: String,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val annotated = remember(text, onWordTap) { annotateWords(text, onWordTap) }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}

fun annotateWords(text: String, onWordTap: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        for (match in WORD_REGEX.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val word = match.value
            withLink(
                LinkAnnotation.Clickable(
                    tag = word,
                    linkInteractionListener = { onWordTap(word) },
                )
            ) {
                append(word)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
