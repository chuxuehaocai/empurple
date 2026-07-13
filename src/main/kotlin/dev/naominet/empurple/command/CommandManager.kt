package dev.naominet.empurple.command

import dev.naominet.empurple.command.internal.CommandB50
import dev.naominet.empurple.command.internal.CommandScript
import dev.naominet.empurple.command.internal.CommandTicket
import dev.naominet.empurple.command.internal.CommandWhoami
import dev.naominet.empurple.script.UserScriptRegistry
import dev.naominet.purple.framework.beans.TextMessageBean
import java.util.concurrent.ConcurrentHashMap

object CommandManager {
    private val commands = ConcurrentHashMap<String, ICommand>()
    val prefix = "/"

    init {
        register(CommandWhoami())
        register(CommandTicket())
        register(CommandScript())
        register(CommandB50())
    }

    fun register(command: ICommand) {
        val name = command.name.trim().lowercase()
        require(name.isNotBlank()) { "Command name cannot be blank" }
        require(commands.putIfAbsent(name, command) == null) {
            "Command '$name' is already registered"
        }
    }

    fun isBuiltIn(name: String): Boolean = commands.containsKey(name.lowercase())

    suspend fun process(message: TextMessageBean) {
        val commandString = message.raw_message
        if (!commandString.startsWith(prefix)) return

        val input = commandString.removePrefix(prefix).trimStart()
        val commandName = input.substringBefore(" ").lowercase()
        val args = input.substringAfter(" ", "")
        val context = CommandContext(message, args)

        val builtIn = commands[commandName]
        if (builtIn != null) {
            builtIn.exec(context)
            return
        }
        UserScriptRegistry.execute(context.senderId, commandName, context)
    }
}
