package com.llucs.nexusai.net

import com.llucs.nexusai.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
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
import java.util.concurrent.atomic.AtomicReference

class ApiClient {

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val url = "https://text.pollinations.ai/openai/chat/completions"
    private val mediaType = "application/json".toMediaType()

    private val activeCall = AtomicReference<Call?>(null)

    fun cancelActive() {
        activeCall.getAndSet(null)?.cancel()
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

        obj.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
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
        obj.put("temperature", 0.2)
        obj.put("top_p", 0.8)
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

    private suspend fun executeWithRetry(request: Request): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 1..3) {
            val call = client.newCall(request)
            activeCall.set(call)
            val handle = coroutineContext.job.invokeOnCompletion { call.cancel() }

            try {
                return@withContext call.execute().use { resp ->
                    val txt = resp.body?.string().orEmpty()
                    if (txt.startsWith("502 Bad Gateway")) throw IOException("502 Bad Gateway")
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $txt")
                    if (txt.isBlank()) throw IOException("Resposta vazia")
                    txt
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                delay(attempt * 800L)
            } finally {
                handle.dispose()
                activeCall.compareAndSet(call, null)
            }
        }
        throw (lastException ?: IOException("Falha na requisição"))
    }

    private suspend fun executeStreamWithRetry(request: Request, onChunk: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 1..3) {
            val call = client.newCall(request)
            activeCall.set(call)
            val handle = coroutineContext.job.invokeOnCompletion { call.cancel() }

            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string().orEmpty()
                        throw IOException("HTTP ${resp.code}: $errorBody")
                    }

                    val body = resp.body ?: throw IOException("Sem corpo na resposta")
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
                            continue
                        }

                        if (obj.has("error")) {
                            val msg = obj.getJSONObject("error").optString("message", "Erro da API")
                            throw IOException(msg)
                        }

                        val choices = obj.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue

                        val content = choices.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                        if (!content.isNullOrEmpty()) {
                            onChunk(content)
                        }
                    }
                }
                return@withContext
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                delay(attempt * 900L)
            } finally {
                handle.dispose()
                activeCall.compareAndSet(call, null)
            }
        }
        throw (lastException ?: IOException("Falha no streaming"))
    }
}