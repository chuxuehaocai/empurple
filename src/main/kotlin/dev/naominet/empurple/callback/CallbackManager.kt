package dev.naominet.empurple.callback

import dev.naominet.empurple.maimai.MaiCallbackData
import dev.naominet.purple.framework.beans.TextMessageBean
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

data class PendingInteraction(
    val userId: Long,
    val type: String,
    val expiresAt: LocalDateTime,
    val handler: suspend (TextMessageBean) -> Unit
)

object CallbackManager {
    private val pending = ConcurrentHashMap<Long, PendingInteraction>()

    fun add(
        userId: Long,
        type: String,
        timeout: Duration,
        handler: suspend (TextMessageBean) -> Unit
    ): Boolean {
        removeExpiredCallbacks()
        return pending.putIfAbsent(
            userId,
            PendingInteraction(userId, type, LocalDateTime.now().plus(timeout), handler)
        ) == null
    }

    fun addCallback(callbackData: MaiCallbackData, replaceExisting: Boolean = false): Boolean {
        val interaction = PendingInteraction(
            callbackData.userId,
            "maimai",
            LocalDateTime.now().plus(Duration.ofMinutes(1))
        ) { message ->
            callbackData.function(message, callbackData)
        }
        removeExpiredCallbacks()
        if (replaceExisting) {
            pending[callbackData.userId] = interaction
            return true
        }
        return pending.putIfAbsent(callbackData.userId, interaction) == null
    }

    suspend fun process(message: TextMessageBean): Boolean {
        removeExpiredCallbacks()
        // 不同私聊事件实现对 user_id 的填充并不一致；注册时使用的是 sender.user_id。
        // 先保持对顶层 user_id 的兼容，找不到时再用实际发送者 ID，避免直接 return false。
        val topLevelUserId = message.user_id.takeIf { it != 0L }
        val senderId = message.sender.user_id
        val interaction = topLevelUserId?.let(pending::remove)
            ?: senderId.takeIf { it != 0L && it != topLevelUserId }?.let(pending::remove)
            ?: return false
        interaction.handler(message)
        return true
    }

    fun pendingType(userId: Long): String? {
        removeExpiredCallbacks()
        return pending[userId]?.type
    }

    private fun removeExpiredCallbacks() {
        val now = LocalDateTime.now()
        pending.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }
}
