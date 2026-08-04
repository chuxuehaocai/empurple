package dev.naominet.empurple.command.internal

import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand

class CommandJrrp : ICommand {
    override val name = "jrrp"

    override suspend fun exec(context: CommandContext) {
        
    }
}