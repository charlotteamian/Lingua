package com.lingua.english.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingua.english.data.dict.DictRepository
import com.lingua.english.data.dict.WordDefinition
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.data.tts.TtsProvider
import com.lingua.english.ui.theme.LinguaAmber
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaTertiary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 全 app 共享的「点词查词」入口, 由 MainActivity 提供。 */
val LocalWordLookup = staticCompositionLocalOf<(String) -> Unit> { {} }

data class WordLookupUi(
    val queried: String,
    val loading: Boolean = true,
    val result: WordDefinition? = null,
    val inVocab: Boolean = false,
)

/** 词典底部卡状态机: open → 查离线/在线 → 展示; 加生词本走复习仓库。 */
class WordLookupController(
    private val scope: CoroutineScope,
    private val dict: DictRepository,
    private val review: ReviewRepository,
    private val tts: TtsProvider,
) {
    var ui by mutableStateOf<WordLookupUi?>(null)
        private set

    fun open(word: String) {
        ui = WordLookupUi(queried = word)
        scope.launch {
            val result = dict.lookup(word)
            val inVocab = result?.let { review.hasText(it.word) } ?: false
            ui = ui?.takeIf { it.queried == word }?.copy(
                loading = false, result = result, inVocab = inVocab,
            )
        }
    }

    fun close() {
        ui = null
    }

    fun speak() {
        ui?.result?.let { tts.speak(it.word) }
    }

    fun addToVocab() {
        val current = ui?.result ?: return
        scope.launch {
            val meaning = current.translations.firstOrNull()
                ?: current.definitions.firstOrNull().orEmpty()
            review.addStandalone(current.word, meaning, "", ChunkEntity.KIND_WORD)
            ui = ui?.copy(inVocab = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordLookupSheet(controller: WordLookupController) {
    val ui = controller.ui ?: return
    ModalBottomSheet(
        onDismissRequest = controller::close,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                ui.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("查「${ui.queried}」…", color = LinguaOnSurfaceMuted)
                }

                ui.result == null -> {
                    Text(ui.queried, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "离线词典和在线词典都没查到这个词",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                else -> WordDetail(ui, controller)
            }
        }
    }
}

@Composable
private fun WordDetail(ui: WordLookupUi, controller: WordLookupController) {
    val def = ui.result ?: return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(def.word, style = MaterialTheme.typography.headlineMedium)
            if (def.phonetic.isNotEmpty()) {
                Text(
                    "/${def.phonetic}/  · 英音",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        IconButton(onClick = controller::speak) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "朗读",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (def.tags.isNotEmpty()) {
        Text(
            def.tags.joinToString(" · "),
            color = LinguaAmber,
            style = MaterialTheme.typography.labelMedium,
        )
    }

    def.translations.forEach { line ->
        Text(line, style = MaterialTheme.typography.bodyLarge)
    }

    if (def.definitions.isNotEmpty()) {
        def.definitions.take(4).forEach { line ->
            Text(line, color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (def.forms.isNotEmpty()) {
        Text(
            def.forms.joinToString("  ") { (label, form) -> "$label $form" },
            color = LinguaOnSurfaceMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }

    if (def.fromOnline) {
        Text(
            "在线词典 · dictionaryapi.dev",
            color = LinguaOnSurfaceMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }

    Spacer(Modifier.height(2.dp))
    if (ui.inVocab) {
        Text("✓ 已在生词本", color = LinguaTertiary, style = MaterialTheme.typography.bodyMedium)
    } else {
        OutlinedButton(onClick = controller::addToVocab) {
            Text("加入生词本")
        }
    }
}
