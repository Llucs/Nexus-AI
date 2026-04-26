package com.llucs.nexusai.net

import com.llucs.nexusai.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ApiClient {

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val url =
        "https://devtoolbox-api.devtoolbox-api.workers.dev/ai/generate"

    private val mediaType = "application/json".toMediaType()

    private val activeCall = AtomicReference<Call?>()

    fun cancelActive() {
        activeCall.getAndSet(null)?.cancel()
    }

    suspend fun complete(history: List<UiMessage>): String =
        withContext(Dispatchers.IO) {

            val prompt = buildPrompt(history)

            val bodyJson = JSONObject().apply {
                put("prompt", prompt)
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(mediaType))
                .header("Content-Type", "application/json")
                .build()

            val respText = executeWithRetry(request)

            val obj = JSONObject(respText)

            if (obj.has("error")) {
                throw IOException(obj.getString("error"))
            }

            obj.getString("response")
        }

    private fun buildPrompt(history: List<UiMessage>): String {
        return history.joinToString("\n") {
            val role = if (it.role == "user") "Usuário" else "IA"
            "$role: ${it.content}"
        } + "\nIA:"
    }

    private suspend fun executeWithRetry(request: Request): String =
        withContext(Dispatchers.IO) {

            var lastException: Exception? = null

            for (attempt in 1..3) {
                val call = client.newCall(request)
                activeCall.set(call)

                val handle = coroutineContext.job.invokeOnCompletion {
                    call.cancel()
                }

                try {
                    return@withContext call.execute().use { resp ->
                        val txt = resp.body?.string().orEmpty()

                        if (!resp.isSuccessful) {
                            throw IOException("HTTP ${resp.code}: $txt")
                        }

                        if (txt.isBlank()) {
                            throw IOException("Resposta vazia")
                        }

                        txt
                    }
                } catch (e: Exception) {
                    lastException = e
                    delay(attempt * 800L)
                } finally {
                    handle.dispose()
                    activeCall.compareAndSet(call, null)
                }
            }

            throw lastException ?: IOException("Falha na requisição")
        }
}