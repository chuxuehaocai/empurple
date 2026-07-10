package dev.naominet.empurple.command

import dev.naominet.purple.framework.beans.TextMessageBean

class CommandContext(
    val message: TextMessageBean,
    val args: String
) {
    val rawMessage: String = message.raw_message
    val groupId: Long = message.group_id
    val senderId: Long = message.sender.user_id
}
