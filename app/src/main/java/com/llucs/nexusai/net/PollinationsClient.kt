package com.llucs.nexusai.net

import com.llucs.nexusai.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class PollinationsClient {

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val url = "https://text.pollinations.ai/openai/chat/completions"
    private val mediaType = "application/json".toMediaType()

    @Volatile
    private var activeCall: Call? = null

    fun cancelActive() {
        activeCall?.cancel()
    }

    suspend fun complete(history: List<UiMessage>): String = withContext(Dispatchers.IO) {
        val body = buildBody(history, stream = false)
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .build()

        val respText = executeWithRetry(request)
        val obj = JSONObject(respText)
        if (obj.has("error")) {
            val msg = obj.getJSONObject("error").optString("message", "Erro da API")
            throw IOException(msg)
        }
        val choices = obj.getJSONArray("choices")
        val msgObj = choices.getJSONObject(0).getJSONObject("message")
        msgObj.getString("content")
    }

    suspend fun stream(history: List<UiMessage>, onChunk: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        val body = buildBody(history, stream = true)
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        executeStreamWithRetry(request, onChunk)
    }

    private fun buildBody(history: List<UiMessage>, stream: Boolean): String {
        val obj = JSONObject()
        obj.put("model", "openai")
        obj.put("temperature", 0.8)
        obj.put("top_p", 0.9)
        obj.put("stream", stream)

        val arr = JSONArray()
        history.forEach { m ->
            arr.put(JSONObject().apply {
                put("role", m.role)
                put("content", m.content)
            })
        }
        obj.put("messages", arr)
        return obj.toString()
    }

    private fun executeWithRetry(request: Request): String {
        var last: Exception? = null
        for (attempt in 1..3) {
            val call = client.newCall(request)
            activeCall = call
            try {
                call.execute().use { resp ->
                    val txt = resp.body?.string().orEmpty()
                    if (txt.startsWith("502 Bad Gateway")) throw IOException("502 Bad Gateway")
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    if (txt.isBlank()) throw IOException("Resposta vazia")
                    return txt
                }
            } catch (e: Exception) {
                last = e
                try {
                    Thread.sleep((attempt * 800).toLong())
                } catch (_: InterruptedException) {
                }
            } finally {
                if (activeCall === call) activeCall = null
            }
        }
        throw (last ?: IOException("Falha na requisição"))
    }

    private suspend fun executeStreamWithRetry(request: Request, onChunk: suspend (String) -> Unit) {
        var last: Exception? = null
        val parentJob = currentCoroutineContext().job

        for (attempt in 1..3) {
            val call = client.newCall(request)
            activeCall = call
            var handle: DisposableHandle? = null

            try {
                handle = parentJob.invokeOnCompletion { call.cancel() }

                call.execute().use { resp ->
                    val body = resp.body ?: throw IOException("Sem corpo")
                    val reader = BufferedReader(InputStreamReader(body.byteStream()))

                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue
                        if (payload == "[DONE]") break
                        if (payload.startsWith("502 Bad Gateway")) throw IOException("502 Bad Gateway")

                        val obj = try {
                            JSONObject(payload)
                        } catch (_: Exception) {
                            null
                        } ?: continue

                        if (obj.has("error")) {
                            val msg = obj.getJSONObject("error").optString("message", "Erro da API")
                            throw IOException(msg)
                        }

                        val choices = obj.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue
                        val c0 = choices.optJSONObject(0) ?: continue
                        val delta = c0.optJSONObject("delta") ?: continue

                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            onChunk(content)
                        }
                    }
                }
                return
            } catch (e: Exception) {
                last = e
                withContext(Dispatchers.IO) {
                    try {
                        Thread.sleep((attempt * 900).toLong())
                    } catch (_: InterruptedException) {
                    }
                }
            } finally {
                handle?.dispose()
                if (activeCall === call) activeCall = null
            }
        }

        throw (last ?: IOException("Falha no streaming"))
    }
}
