package com.llucs.nexusai.ui.chat

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null

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

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
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
        startAudioMeter()
    }

    fun stop() {
        if (!state.listening) return

        audioJob?.cancel()
        audioJob = null

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {}
        try {
            audioRecord?.release()
        } catch (_: Throwable) {}
        audioRecord = null

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

    private fun startAudioMeter() {
        val sampleRate = 16000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, encoding).let { if (it > 0) it else sampleRate }
        val bufSize = max(minBuf, sampleRate)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channel,
            encoding,
            bufSize
        )

        audioRecord = rec

        try {
            rec.startRecording()
        } catch (_: Throwable) {
            return
        }

        val chunk = ShortArray(512)

        audioJob = scope.launch(Dispatchers.Default) {
            var levels = state.levels.toMutableList()
            while (isActive && state.listening) {
                val read = try { rec.read(chunk, 0, chunk.size) } catch (_: Throwable) { 0 }
                var sum = 0.0
                for (i in 0 until max(0, read)) {
                    val v = chunk[i].toInt()
                    sum += (v * v).toDouble()
                }
                val rms = if (read > 0) kotlin.math.sqrt(sum / read) else 0.0
                val norm = min(1.0, rms / 9000.0).toFloat()

                val eased = min(1f, max(0f, norm))
                levels.add(0, eased)
                if (levels.size > 24) levels = levels.subList(0, 24)

                push { it.copy(levels = levels.toList()) }
                delay(50)
            }
        }
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
