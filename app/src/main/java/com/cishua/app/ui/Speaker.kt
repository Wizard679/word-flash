package com.cishua.app.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** 单词发音（系统 TTS，离线可用） */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    try {
                        tts?.language = Locale.US
                        tts?.setSpeechRate(0.92f)
                    } catch (e: Exception) {
                        Log.w(TAG, "set language failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS init failed", e)
        }
    }

    fun speak(text: String) {
        if (!ready) return
        try {
            tts?.stop()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cishua_word")
        } catch (e: Exception) {
            Log.w(TAG, "speak failed", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "shutdown failed", e)
        }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "CiShuaTts"
    }
}
