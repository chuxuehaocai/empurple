package dev.naominet.empurple.llm

import dev.naominet.empurple.config.EmpurpleConfig
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot

object LlmMentionHandler {
    fun extractPrompt(rawMessage: String, prefix: String): String? {
        if (!rawMessage.startsWith(prefix)) return null
        return rawMessage.removePrefix(prefix).trim()
    }

    suspend fun process(message: TextMessageBean, config: EmpurpleConfig): Boolean {
        if (!config.llmEnabled) return false
        val prompt = extractPrompt(message.raw_message, config.llmMentionPrefix) ?: return false
        val userId = message.sender.user_id.takeIf { it != 0L } ?: message.user_id
        val reply = if (prompt.isEmpty()) {
            "请在 @机器人 后输入问题。"
        } else if (userId == 0L) {
            "无法识别发送者，暂时不能回复。"
        } else {
            runCatching { LLMService.request(userId, prompt) }
                .getOrElse {
                    System.err.println("Cloud LLM request failed: ${it.stackTraceToString()}")
                    "云端 LLM 调用失败：${it.message ?: "未知错误"}"
                }
        }
        // Empty reply is valid after <end_conversation> with no farewell text.
        if (reply.isNotBlank()) {
            Bot.sendGroupMessage(message.group_id, reply)
        }
        return true
    }
}
