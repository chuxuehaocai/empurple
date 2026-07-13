package dev.naominet.empurple.script

import dev.naominet.empurple.command.CommandContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class InstalledScriptRuntime(
    val scriptId: UUID,
    val version: Int,
    val command: ScriptCommand
)

object UserScriptRegistry {
    private val state = AtomicReference<Map<Long, Map<String, InstalledScriptRuntime>>>(emptyMap())

    suspend fun execute(userId: Long, commandName: String, context: CommandContext): Boolean {
        val runtime = state.get()[userId]?.get(commandName) ?: return false
        runtime.command.exec(context)
        return true
    }

    @Synchronized
    fun install(userId: Long, runtime: InstalledScriptRuntime) {
        val current = state.get()
        val userCommands = current[userId].orEmpty().toMutableMap()
        val conflict = userCommands[runtime.command.name]
        require(conflict == null || conflict.scriptId == runtime.scriptId) {
            "You already installed another script command named '${runtime.command.name}'"
        }
        userCommands[runtime.command.name] = runtime
        state.set(current + (userId to userCommands.toMap()))
    }

    @Synchronized
    fun replaceEveryInstallation(runtime: InstalledScriptRuntime) {
        state.set(
            state.get().mapValues { (_, commands) ->
                commands.mapValues { (_, installed) ->
                    if (installed.scriptId == runtime.scriptId) runtime else installed
                }
            }
        )
    }

    @Synchronized
    fun clear() {
        state.set(emptyMap())
    }
}
