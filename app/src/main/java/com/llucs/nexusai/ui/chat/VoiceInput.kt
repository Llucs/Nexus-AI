package com.llucs.nexusai.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope

data class VoiceCaptureState(
    val listening: Boolean = false,
    val partialText: String = "",
    val finalText: String = "",
    val levels: List<Float> = List(24) { 0f },
    val showText: Boolean = false
)

class VoiceInputController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onState: (VoiceCaptureState) -> Unit
) {

    private var recognizer: SpeechRecognizer? = null

    private var state = VoiceCaptureState()

    private fun push(update: (VoiceCaptureState) -> VoiceCaptureState) {
        state = update(state)
        onState(state)
    }

    fun toggleShowText() {
        push { it.copy(showText = !it.showText) }
    }

    fun start() {
        if (state.listening) return

        // Some devices/ROMs don't ship a Speech Recognition service.
        // In that case, starting the recognizer will just error instantly.
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            push { it.copy(listening = false, finalText = "", partialText = "", levels = List(24) { 0f }) }
            return
        }

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Use the recognizer's RMS callback as the visual mic meter.
                // This avoids starting a second AudioRecord capture, which can
                // conflict with SpeechRecognizer on many devices.
                val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                push { s ->
                    var levels = s.levels.toMutableList()
                    levels.add(0, norm)
                    if (levels.size > 24) levels = levels.subList(0, 24)
                    s.copy(levels = levels.toList())
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                stop()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                push { it.copy(finalText = text, partialText = "") }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                push { it.copy(partialText = text) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        push { it.copy(listening = true, finalText = "", partialText = "", levels = List(24) { 0f }) }

        sr.startListening(intent)
    }

    fun stop() {
        if (!state.listening) return

        try {
            recognizer?.stopListening()
        } catch (_: Throwable) {}
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {}
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {}
        recognizer = null

        push { it.copy(listening = false, levels = List(24) { 0f }) }
    }

    fun resetText() {
        push { it.copy(finalText = "", partialText = "") }
    }

}

fun normalizePtDictation(raw: String): String {
    if (raw.isBlank()) return raw

    var t = raw

    val repl = listOf(
        Regex("(?i)\\b(ponto\\s+final|ponto)\\b") to ".",
        Regex("(?i)\\b(v[ií]rgula)\\b") to ",",
        Regex("(?i)\\b(interroga[cç][aã]o)\\b") to "?",
        Regex("(?i)\\b(exclama[cç][aã]o)\\b") to "!",
        Regex("(?i)\\b(dois\\s+pontos)\\b") to ":",
        Regex("(?i)\\b(ponto\\s+e\\s+v[ií]rgula)\\b") to ";"
    )

    for ((r, v) in repl) {
        t = t.replace(r, v)
    }

    t = t.replace(Regex("\\s+([,\\.!\\?:;])"), "$1")
    t = t.replace(Regex("([,\\.!\\?:;])(\\S)"), "$1 $2")
    t = t.replace(Regex("\\s{2,}"), " ").trim()

    return t
}
