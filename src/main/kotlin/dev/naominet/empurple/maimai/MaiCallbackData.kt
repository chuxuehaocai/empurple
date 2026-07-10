package dev.naominet.empurple.maimai

import dev.naominet.purple.framework.beans.TextMessageBean
import java.time.LocalDateTime

data class MaiCallbackData(
    val userId: Long,
    val sourceGroupId: Long,
    val originMsgId: Long,
    val function: suspend (TextMessageBean, MaiCallbackData) -> Unit,
    val time: LocalDateTime
)