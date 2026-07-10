package dev.naominet.empurple.command

import dev.naominet.empurple.command.impl.CommandTicket
import dev.naominet.empurple.command.impl.CommandWhoami
import dev.naominet.purple.framework.beans.TextMessageBean

object CommandManager {
    val commands = mutableListOf<ICommand>()
    val prefix = "/"

    init {
        register(CommandWhoami())
        register(CommandTicket())
    }

    fun register(command: ICommand) {
        commands.add(command)
    }

    suspend fun process(message: TextMessageBean) {
        val commandString = message.raw_message
        if (!commandString.startsWith(prefix)) return

        val input = commandString.removePrefix(prefix).trimStart()
        val commandName = input.substringBefore(" ")
        val args = input.substringAfter(" ", "")
        val context = CommandContext(message, args)

        commands.firstOrNull { it.name == commandName }?.exec(context)
    }
}