package com.llucs.nexusai.net

import com.llucs.nexusai.ApiResponse
import com.llucs.nexusai.TokenUsage
import com.llucs.nexusai.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ApiClient {

    private val client = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://opencode.ai/zen/v1/chat/completions"
    private val model = "deepseek-v4-flash-free"
    private val mediaType = "application/json".toMediaType()

    private val activeCall = AtomicReference<Call?>()

    fun cancelActive() {
        activeCall.getAndSet(null)?.cancel()
    }

    suspend fun complete(history: List<UiMessage>): ApiResponse =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()
            for (msg in history) {
                messages.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }

            val bodyJson = JSONObject().apply {
                put("model", model)
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(baseUrl)
                .post(bodyJson.toString().toRequestBody(mediaType))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer public")
                .build()

            val respText = executeWithRetry(request)
            val obj = JSONObject(respText)

            if (obj.has("error")) {
                throw IOException(obj.getString("error"))
            }

            val modelName = obj.optString("model", null)?.ifBlank { null }

            val usageJson = obj.optJSONObject("usage")
            val usage = usageJson?.let {
                TokenUsage(
                    promptTokens = it.optInt("prompt_tokens", 0),
                    completionTokens = it.optInt("completion_tokens", 0),
                    totalTokens = it.optInt("total_tokens", 0)
                )
            }

            val choices = obj.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val msg = choices.getJSONObject(0).optJSONObject("message")
                if (msg != null) {
                    val content = msg.optString("content", "")
                    ApiResponse(content, modelName, usage)
                } else {
                    throw IOException("Resposta inv\u00e1lida: sem message")
                }
            } else {
                throw IOException("Resposta inv\u00e1lida: sem choices")
            }
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
                    if (attempt < 3) {
                        kotlinx.coroutines.delay(attempt * 800L)
                    }
                } finally {
                    handle.dispose()
                    activeCall.compareAndSet(call, null)
                }
            }

            throw lastException ?: IOException("Falha na requisi\u00e7\u00e3o")
        }
}
