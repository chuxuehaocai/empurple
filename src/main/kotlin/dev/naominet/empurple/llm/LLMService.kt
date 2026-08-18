package dev.naominet.empurple.llm

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import dev.naominet.empurple.config.EmpurpleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

fun interface LlmTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: String): String
}

private object JavaHttpLlmTransport : LlmTransport {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    override suspend fun post(url: String, headers: Map<String, String>, body: String): String =
        withContext(Dispatchers.IO) {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(body))
            headers.forEach(builder::header)

            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw IllegalStateException("LLM API returned HTTP ${response.statusCode()}: ${response.body()}")
            }
            response.body()
        }
}

/**
 * In-memory, per-user chat history.
 * Each completed turn stores plain user text + final assistant text (tool loop internals are not persisted).
 */
class LlmChatHistoryStore(
    private val maxTurns: Int,
) {
    private data class Session(
        val mutex: Mutex = Mutex(),
        val messages: MutableList<JSONObject> = mutableListOf(),
    )

    private val sessions = ConcurrentHashMap<Long, Session>()

    suspend fun <T> withUserLock(userId: Long, block: suspend () -> T): T {
        val session = sessions.getOrPut(userId) { Session() }
        return session.mutex.withLock { block() }
    }

    fun snapshot(userId: Long): JSONArray {
        val session = sessions[userId] ?: return JSONArray()
        return JSONArray(session.messages.map { copyMessage(it) })
    }

    fun appendTurn(userId: Long, userContent: String, assistantContent: String) {
        if (maxTurns <= 0) return
        val session = sessions.getOrPut(userId) { Session() }
        session.messages.add(JSONObject.of("role", "user", "content", userContent))
        session.messages.add(JSONObject.of("role", "assistant", "content", assistantContent))
        trim(session)
    }

    fun clear(userId: Long) {
        sessions.remove(userId)
    }

    fun turnCount(userId: Long): Int = (sessions[userId]?.messages?.size ?: 0) / 2

    private fun trim(session: Session) {
        val maxMessages = maxTurns.coerceAtLeast(0) * 2
        while (session.messages.size > maxMessages) {
            session.messages.removeAt(0)
        }
    }

    companion object {
        internal fun copyMessage(message: JSONObject): JSONObject =
            JSON.parseObject(JSON.toJSONString(message))
                ?: JSONObject(message)
    }
}

enum class LlmModerationVerdict {
    ALLOW,
    BLOCK,
}

data class LlmModerationResult(
    val verdict: LlmModerationVerdict,
    val reason: String = "",
    val raw: String = "",
)

class CloudLlmService(
    private val config: EmpurpleConfig,
    private val transport: LlmTransport = JavaHttpLlmTransport,
    initialTools: Collection<LlmTool> = defaultTools(),
    private val historyStore: LlmChatHistoryStore = LlmChatHistoryStore(config.llmMaxHistoryTurns),
) {
    private val tools = ConcurrentHashMap<String, LlmTool>().apply {
        initialTools.forEach { put(it.name, it) }
    }

    fun registerTool(tool: LlmTool) {
        require(tool.name.isNotBlank()) { "Tool name cannot be blank" }
        tools[tool.name] = tool
    }

    fun unregisterTool(name: String) {
        tools.remove(name)
    }

    fun clearHistory(userId: Long) {
        historyStore.clear(userId)
    }

    fun historyTurnCount(userId: Long): Int = historyStore.turnCount(userId)

    suspend fun request(
        userId: Long,
        prompt: String,
        systemPrompt: String? = null,
        useHistory: Boolean = true
    ): String {
        require(userId != 0L) { "userId cannot be 0" }
        require(prompt.isNotBlank()) { "Prompt cannot be blank" }
        require(config.llmApiKey.isNotBlank()) { "llmApiKey is not configured" }

        if (!useHistory) {
            val messages = JSONArray().apply {
                add(JSONObject.of("role", "user", "content", prompt))
            }
            val draft = runToolLoop(messages, systemPrompt)
            if (containsEndConversation(draft)) {
                return stripEndConversation(draft)
            }
            return moderateIfNeeded(userPrompt = prompt, draftReply = draft)
        }

        return historyStore.withUserLock(userId) {
            val messages = historyStore.snapshot(userId).apply {
                add(JSONObject.of("role", "user", "content", prompt))
            }

            val draft = runToolLoop(messages, systemPrompt)
            if (containsEndConversation(draft)) {
                // Model declined / ended chat: drop this user's context entirely.
                historyStore.clear(userId)
                return@withUserLock stripEndConversation(draft)
            }

            val reply = moderateIfNeeded(userPrompt = prompt, draftReply = draft)
            if (reply != draft) {
                // Blocked: do not poison history with the rejected draft.
                return@withUserLock reply
            }

            historyStore.appendTurn(userId, prompt, reply)
            reply
        }
    }

    private suspend fun moderateIfNeeded(userPrompt: String, draftReply: String): String {
        if (!config.llmModerationEnabled) return draftReply
        if (draftReply.isBlank()) return draftReply

        val result = runCatching { moderate(userPrompt = userPrompt, draftReply = draftReply) }
            .getOrElse { error ->
                System.err.println("LLM moderation failed (fail-closed): ${error.stackTraceToString()}")
                return config.llmModerationBlockedReply
            }

        return if (result.verdict == LlmModerationVerdict.ALLOW) {
            draftReply
        } else {
            System.err.println(
                "LLM moderation blocked reply" +
                    (if (result.reason.isNotBlank()) ": ${result.reason}" else "") +
                    " | raw=${result.raw}"
            )
            config.llmModerationBlockedReply
        }
    }

    /**
     * Second-pass review with the same model/endpoint, different system prompt, no tools, no chat history.
     */
    internal suspend fun moderate(userPrompt: String, draftReply: String): LlmModerationResult {
        val reviewUserMessage = buildString {
            appendLine("User message:")
            appendLine(userPrompt)
            appendLine()
            appendLine("Draft assistant reply to review:")
            appendLine(draftReply)
        }
        val messages = JSONArray().apply {
            add(JSONObject.of("role", "user", "content", reviewUserMessage))
        }
        val response = send(
            messages = messages,
            system = effectiveModerationSystemPrompt(),
            includeTools = false,
            temperature = 0.0,
            maxTokens = 256,
        )
        val text = extractText(response)
            .ifEmpty { throw IllegalStateException("Moderation API returned no text content") }
        return parseModerationResult(text)
    }

    private suspend fun runToolLoop(messages: JSONArray, systemPrompt: String? = null): String {
        repeat(config.llmMaxToolIterations.coerceAtLeast(1)) {
            val response = send(
                messages = messages,
                system = effectiveSystemPrompt(systemPrompt),
                includeTools = true,
            )
            val content = response.getJSONArray("content") ?: JSONArray()
            if (response.getString("stop_reason") != "tool_use") {
                return extractText(response)
                    .ifEmpty { throw IllegalStateException("LLM API returned no text content") }
            }

            messages.add(JSONObject.of("role", "assistant", "content", content))
            val results = JSONArray()
            content.forEach { rawBlock ->
                val block = rawBlock as? JSONObject ?: return@forEach
                if (block.getString("type") != "tool_use") return@forEach

                val name = block.getString("name")
                val tool = tools[name]
                val result = runCatching {
                    requireNotNull(tool) { "Unknown tool: $name" }
                    tool.execute(block.getJSONObject("input") ?: JSONObject())
                }
                results.add(JSONObject.of(
                    "type", "tool_result",
                    "tool_use_id", block.getString("id"),
                    "content", result.getOrElse { "Tool execution failed: ${it.message}" },
                    "is_error", result.isFailure,
                ))
            }
            if (results.isEmpty()) {
                throw IllegalStateException("LLM requested tool use without any tool_use blocks")
            }
            messages.add(JSONObject.of("role", "user", "content", results))
        }
        throw IllegalStateException("LLM exceeded ${config.llmMaxToolIterations} tool iterations")
    }

    private fun effectiveSystemPrompt(override: String? = null): String {
        val base = override?.trim()?.takeIf(String::isNotEmpty) ?: config.llmSystemPrompt.trim()
        return if (base.isEmpty()) END_CONVERSATION_INSTRUCTION
        else "$base\n\n$END_CONVERSATION_INSTRUCTION"
    }

    private fun effectiveModerationSystemPrompt(): String {
        val custom = config.llmModerationSystemPrompt.trim()
        return custom.ifEmpty { EmpurpleConfig.DEFAULT_LLM_MODERATION_SYSTEM_PROMPT }
    }

    private suspend fun send(
        messages: JSONArray,
        system: String,
        includeTools: Boolean,
        temperature: Double = config.llmTemperature,
        maxTokens: Int = config.llmMaxTokens,
    ): JSONObject {
        val payload = JSONObject().apply {
            put("model", config.llmModelName)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("messages", messages)
            put("system", system)
            if (includeTools && tools.isNotEmpty()) {
                put("tools", JSONArray(tools.values.sortedBy { it.name }.map { tool ->
                    JSONObject.of(
                        "name", tool.name,
                        "description", tool.description,
                        "input_schema", tool.inputSchema,
                    )
                }))
            }
        }
        val response = transport.post(
            messagesUrl(config.llmBaseUrl),
            mapOf(
                "Content-Type" to "application/json",
                "x-api-key" to config.llmApiKey,
                "anthropic-version" to "2023-06-01",
            ),
            JSON.toJSONString(payload),
        )
        return JSON.parseObject(response)
            ?: throw IllegalStateException("LLM API returned invalid JSON")
    }

    companion object {
        const val END_CONVERSATION_TAG = "<end_conversation>"

        private val END_CONVERSATION_INSTRUCTION =
            "If you do not want to answer, refuse the request, or want to end this conversation, " +
                "reply with $END_CONVERSATION_TAG (optionally with a short farewell before the tag). " +
                "That clears the conversation context."

        private val END_CONVERSATION_REGEX =
            Regex("""<\s*end_conversation\s*/?\s*>""", RegexOption.IGNORE_CASE)

        private val MODERATION_ALLOW_REGEX =
            Regex("""^\s*ALLOW\b""", RegexOption.IGNORE_CASE)
        private val MODERATION_BLOCK_REGEX =
            Regex("""^\s*BLOCK(?:\s*[:：]\s*(.*))?$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        internal fun containsEndConversation(text: String): Boolean =
            END_CONVERSATION_REGEX.containsMatchIn(text)

        internal fun stripEndConversation(text: String): String =
            END_CONVERSATION_REGEX.replace(text, "")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()

        internal fun extractText(response: JSONObject): String {
            val content = response.getJSONArray("content") ?: JSONArray()
            return content
                .mapNotNull { (it as? JSONObject)?.takeIf { block -> block.getString("type") == "text" }?.getString("text") }
                .joinToString("\n")
                .trim()
        }

        internal fun parseModerationResult(raw: String): LlmModerationResult {
            val text = raw.trim()
            if (text.isEmpty()) {
                return LlmModerationResult(LlmModerationVerdict.BLOCK, reason = "empty moderation response", raw = raw)
            }
            // Prefer an explicit ALLOW line when present.
            if (MODERATION_ALLOW_REGEX.containsMatchIn(text) && !MODERATION_BLOCK_REGEX.containsMatchIn(text)) {
                return LlmModerationResult(LlmModerationVerdict.ALLOW, raw = raw)
            }
            MODERATION_BLOCK_REGEX.find(text)?.let { match ->
                return LlmModerationResult(
                    verdict = LlmModerationVerdict.BLOCK,
                    reason = match.groupValues.getOrNull(1)?.trim().orEmpty(),
                    raw = raw,
                )
            }
            // Ambiguous / non-conforming response: fail closed.
            return LlmModerationResult(
                verdict = LlmModerationVerdict.BLOCK,
                reason = "unrecognized moderation response",
                raw = raw,
            )
        }

        internal fun messagesUrl(baseUrl: String): String =
            "${baseUrl.trimEnd('/')}/v1/messages"

        fun defaultTools(): List<LlmTool> = listOf(
            LlmTool(
                name = "get_current_time",
                description = "Get the current server date, time, and time zone. Call this when the user asks for the current time or date.",
                inputSchema = JSONObject.of(
                    "type", "object",
                    "properties", JSONObject(),
                    "additionalProperties", false,
                ),
            ) {
                ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
            },
        )
    }
}

object LLMService {
    @Volatile
    private var service: CloudLlmService? = null

    fun initialize(config: EmpurpleConfig) {
        service = CloudLlmService(config)
    }

    fun registerTool(tool: LlmTool) {
        checkNotNull(service) { "LLMService is not initialized" }.registerTool(tool)
    }

    fun clearHistory(userId: Long) {
        checkNotNull(service) { "LLMService is not initialized" }.clearHistory(userId)
    }

    suspend fun request(
        userId: Long,
        prompt: String,
        systemPrompt: String? = null,
        useHistory: Boolean = true
    ): String =
        checkNotNull(service) { "LLMService is not initialized" }.request(userId, prompt, systemPrompt, useHistory)
}
