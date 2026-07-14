package dev.naominet.empurple.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object LLMConnecter {
    private val apiKey: String
        get() = "cpa_free"

    private val endPointUrl: String
        get() = System.getenv("OPENAI_BASE_URL")?.takeIf { it.isNotBlank() }
            ?: "https://cpa.epstein.motorcycles/"

    private val modelName: String
        get() = System.getenv("OPENAI_MODEL")?.takeIf { it.isNotBlank() }
            ?: "gpt-5.5"

    private val defaultSystemPrompt: String?
        get() = System.getenv("OPENAI_SYSTEM_PROMPT")?.takeIf { it.isNotBlank() }

    private const val DEFAULT_TIMEOUT_MS = 60_000

    data class Message(
        val role: String,
        val content: String
    )

    /**
     * 使用 OpenAI Chat Completions 协议发送单轮对话，并返回 choices[0].message.content。
     *
     * @param systemPrompt 可选 system prompt；不传时会读取 OPENAI_SYSTEM_PROMPT 环境变量。
     */
    @Throws(Exception::class)
    suspend fun chat(prompt: String, systemPrompt: String? = defaultSystemPrompt): String =
        chat(listOf(Message("user", prompt)), systemPrompt)

    /**
     * 使用 OpenAI Chat Completions 协议发送多轮对话，并返回 choices[0].message.content。
     *
     * @param systemPrompt 可选 system prompt；不传时会读取 OPENAI_SYSTEM_PROMPT 环境变量。
     */
    @Throws(Exception::class)
    suspend fun chat(messages: List<Message>, systemPrompt: String? = defaultSystemPrompt): String {
        return withContext(Dispatchers.IO) {
            executeChat(messages, systemPrompt)
        }
    }

    private fun executeChat(messages: List<Message>, systemPrompt: String?): String {
        if (apiKey.isBlank()) {
            throw IllegalStateException("OPENAI_API_KEY is not set")
        }
        if (messages.isEmpty()) {
            throw IllegalArgumentException("messages must not be empty")
        }

        val requestMessages = buildList {
            systemPrompt?.takeIf { it.isNotBlank() }?.let {
                add(Message("system", it))
            }
            addAll(messages)
        }

        val request = JSONObject()
        request["model"] = modelName
        request["messages"] = JSONArray(requestMessages.map { message ->
            JSONObject().apply {
                this["role"] = message.role
                this["content"] = message.content
            }
        })

        val responseBody = postJson(chatCompletionsUrl(), request.toJSONString())
        val response = JSON.parseObject(responseBody)
        val choices = response.getJSONArray("choices")
            ?: throw IOException("OpenAI response missing choices: $responseBody")
        if (choices.isEmpty()) {
            throw IOException("OpenAI response choices is empty: $responseBody")
        }

        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.getJSONObject("message")
            ?: throw IOException("OpenAI response missing choices[0].message: $responseBody")
        return message.getString("content")
            ?: throw IOException("OpenAI response missing choices[0].message.content: $responseBody")
    }

    private fun chatCompletionsUrl(): String {
        val baseUrl = endPointUrl.trimEnd('/')
        return if (baseUrl.endsWith("/v1")) {
            "$baseUrl/chat/completions"
        } else {
            "$baseUrl/v1/chat/completions"
        }
    }

    private fun postJson(url: String, body: String): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = DEFAULT_TIMEOUT_MS
                readTimeout = DEFAULT_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            conn.outputStream.use { outputStream ->
                outputStream.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val responseBody = readAll(if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            if (conn.responseCode !in 200..299) {
                throw IOException("OpenAI chat request failed: HTTP ${conn.responseCode}\n$responseBody")
            }
            return responseBody
        } finally {
            conn?.disconnect()
        }
    }

    private fun readAll(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        return inputStream.use { stream ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(4096)
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
        }
    }
}
