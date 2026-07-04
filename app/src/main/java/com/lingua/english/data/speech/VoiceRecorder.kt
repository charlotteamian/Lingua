package com.lingua.english.data.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * 语音消息录音器 (对话页用)。和听写不同, 什么时候说完由用户决定,
 * 停顿不会被引擎掐断。AAC-ADTS 16kHz, 直接可发 Gemini / MediaPlayer 回放。
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    /** 失败会抛异常, 调用方 runCatching。 */
    fun start() {
        cancel()
        val target = File(context.cacheDir, "voice_take.aac")
        target.delete()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(48_000)
        r.setOutputFile(target.path)
        r.prepare()
        r.start()
        recorder = r
        file = target
    }

    /** 停止并返回录音文件; 太短 (没录到) 返回 null。 */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        return file?.takeIf { it.exists() && it.length() > 1_500 }
    }

    fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        file?.delete()
        file = null
    }
}
