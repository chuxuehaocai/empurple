package dev.naominet.empurple.command

interface ICommand {
    val name: String
    suspend fun exec(context: CommandContext)
}