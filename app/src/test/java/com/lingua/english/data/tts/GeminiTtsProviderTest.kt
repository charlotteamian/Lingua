package com.lingua.english.data.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTtsProviderTest {

    @Test
    fun enablesStreamingForGemini31AndNewer() {
        assertTrue(GeminiTtsProvider.supportsStreaming("gemini-3.1-flash-tts-preview"))
        assertTrue(GeminiTtsProvider.supportsStreaming("gemini-3.5-flash-tts"))
        assertTrue(GeminiTtsProvider.supportsStreaming("gemini-4-flash-tts"))
    }

    @Test
    fun keepsLegacyAndUnknownModelsOnNonStreamingPath() {
        assertFalse(GeminiTtsProvider.supportsStreaming("gemini-2.5-flash-preview-tts"))
        assertFalse(GeminiTtsProvider.supportsStreaming("custom-tts-model"))
    }
}
