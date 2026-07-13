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

    fun addCallback(callbackData: MaiCallbackData): Boolean = add(
        callbackData.userId,
        "maimai",
        Duration.ofMinutes(1)
    ) { message ->
        callbackData.function(message, callbackData)
    }

    suspend fun process(message: TextMessageBean): Boolean {
        removeExpiredCallbacks()
        val userId = if (message.user_id != 0L) message.user_id else message.sender.user_id
        val interaction = pending.remove(userId) ?: return false
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
