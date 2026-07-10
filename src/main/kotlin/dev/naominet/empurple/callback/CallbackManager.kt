package dev.naominet.empurple.callback

import dev.naominet.empurple.maimai.MaiCallbackData
import dev.naominet.purple.framework.beans.TextMessageBean
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

object CallbackManager {
    private val callbacks = CopyOnWriteArrayList<MaiCallbackData>()

    fun addCallback(callbackData: MaiCallbackData) {
        removeExpiredCallbacks()
        callbacks.removeIf { it.userId == callbackData.userId }
        callbacks.add(callbackData)
    }

    suspend fun process(message: TextMessageBean): Boolean {
        removeExpiredCallbacks()
        val callback = callbacks.firstOrNull { it.userId == message.user_id } ?: return false
        callbacks.remove(callback)
        callback.function(message, callback)
        return true
    }

    private fun removeExpiredCallbacks() {
        val expiredBefore = LocalDateTime.now().minusMinutes(1)
        callbacks.removeIf { it.time.isBefore(expiredBefore) }
    }
}