package dev.naominet.empurple.command.internal

import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand
import dev.naominet.empurple.script.ScriptService
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import java.util.UUID

class CommandScript : ICommand {
    override val name = "script"

    override suspend fun exec(context: CommandContext) {
        val parts = context.args.trim().split(Regex("\\s+"), limit = 2)
        val action = parts.firstOrNull().orEmpty().lowercase()
        val value = parts.getOrNull(1).orEmpty().trim()

        try {
            when (action) {
                "create" -> {
                    require(value.isNotEmpty()) { "Usage: /script create <name>" }
                    ScriptService.create(value, context.senderId, context.groupId, context.message.message_id)
                }
                "publish" -> {
                    val id = parseUuid(value, "Usage: /script publish <uuid>")
                    val script = ScriptService.publish(id, context.senderId)
                    reply(context, "脚本已发布。UUID: ${script.id}，命令: /${script.name}，版本: ${script.version}")
                }
                "add" -> {
                    val id = parseUuid(value, "Usage: /script add <uuid>")
                    val script = ScriptService.add(id, context.senderId)
                    reply(
                        context,
                        "脚本已为当前用户安装。命令: /${script.name}，版本: ${script.version}\n" +
                            "警告：该 Lua 脚本具有 Bot/JVM 高权限，只安装可信来源。作者: ${script.ownerId}"
                    )
                }
                "edit" -> {
                    val id = parseUuid(value, "Usage: /script edit <uuid>")
                    ScriptService.beginEdit(id, context.senderId, context.groupId, context.message.message_id)
                }
                else -> reply(context, "用法: /script create <name> | publish <uuid> | add <uuid> | edit <uuid>")
            }
        } catch (error: Exception) {
            reply(context, error.message ?: "Script operation failed")
        }
    }

    private fun parseUuid(value: String, usage: String): UUID {
        require(value.isNotEmpty()) { usage }
        return try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID")
        }
    }

    private fun reply(context: CommandContext, message: String) {
        Bot.sendGroupMessage(
            context.groupId,
            MessageBuilder().replyGroup(context.message.message_id, message).build()
        )
    }
}
