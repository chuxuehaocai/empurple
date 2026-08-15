package dev.naominet.empurple.config

import dev.naominet.purple.framework.config.IConfig


data class EmpurpleConfig(
    override val configId: String = "empurple-config",
    val titleServerUrl: String = "",
    val aesKey: String = "",
    val aesIv: String = "",
    val aimeUrl: String = "",
    val keychipId: String = "",
    val obfuscateParam: String = "",
    val apiVersion: String = "",
    val clientId: String = "",
    val aimeSalt: String = "",
    val regionId: Int = 0,
    val regionName: String = "",
    val placeId: Int = 0,
    val placeName: String = "",
    val webUiEnabled: Boolean = false,
    val webUiHost: String = "127.0.0.1",
    val webUiPort: Int = 8080,
    val webUiToken: String = "",
    val llmEnabled: Boolean = true,
    val llmMentionPrefix: String = "[CQ:at,qq=3081375261]",
    val llmBaseUrl: String = "https://api.deepseek.com/anthropic",
    val llmApiKey: String = "",
    val llmModelName: String = "deepseek-v4-flash",
    val llmTemperature: Double = 1.0,
    val llmSystemPrompt: String = "",
    val llmMaxTokens: Int = 4096,
    val llmMaxToolIterations: Int = 8,
    /** Max completed user/assistant turns kept per user. 0 disables history. */
    val llmMaxHistoryTurns: Int = 20,
    /** Second-pass moderation with the same model but a different system prompt. */
    val llmModerationEnabled: Boolean = true,
    val llmModerationSystemPrompt: String = DEFAULT_LLM_MODERATION_SYSTEM_PROMPT,
    /** Shown to the user when moderation blocks the draft reply. */
    val llmModerationBlockedReply: String = "抱歉，这条回复未通过安全审核，我换个说法吧。",
): IConfig {
    companion object {
        val DEFAULT_LLM_MODERATION_SYSTEM_PROMPT: String =
            """
            You are a strict content safety reviewer for a Chinese QQ group chatbot.
            Review ONLY the draft assistant reply that follows.
            Block any content that is sensitive, illegal, or high-risk, including but not limited to:
            - political content involving Chinese politics, government leaders, protests, or historically sensitive events
            - pornography, sexual content involving minors, or extreme sexual violence
            - self-harm / suicide encouragement
            - terrorism, violent extremism, or detailed violent crime guidance
            - scams, malware, phishing, or clear criminal assistance
            - doxxing / leaking private personal data
            - hate speech targeting protected groups
            - any other content that would be unsafe to post publicly in a Chinese online community

            Reply with exactly one line and nothing else:
            - ALLOW
            - BLOCK: <short reason in Chinese>

            If unsure, choose BLOCK.
            """.trimIndent()
    }
}
