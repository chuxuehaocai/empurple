package dev.naominet.empurple.script

import dev.naominet.empurple.command.CommandManager
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

object ScriptCommandLoader {
    val defaultDirectory = File("scripts/commands")

    fun load(directory: File = defaultDirectory): List<ScriptCommand> {
        if (!directory.exists() && !directory.mkdirs()) {
            System.err.println("Unable to create Lua command directory: ${directory.absolutePath}")
            return emptyList()
        }
        if (!directory.isDirectory) {
            System.err.println("Lua command path is not a directory: ${directory.absolutePath}")
            return emptyList()
        }

        return directory.listFiles { file -> file.isFile && file.extension.equals("lua", true) }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull(::loadAndRegister)
    }

    fun loadSource(source: String, sourceName: String, expectedName: String? = null): ScriptCommand {
        val globals = JsePlatform.standardGlobals()
        val result = globals.load(source, sourceName).call()
        require(result.istable()) { "Lua command '$sourceName' must return a table" }

        val commandTable = result.checktable()
        val nameValue = commandTable.get("name")
        require(nameValue.isstring()) { "Lua command '$sourceName' must define a string name" }

        val name = nameValue.tojstring().trim().lowercase()
        require(name.isNotEmpty()) { "Lua command '$sourceName' has a blank name" }
        if (expectedName != null) require(name == expectedName) {
            "Lua command name '$name' does not match expected name '$expectedName'"
        }
        require(commandTable.get("exec").isfunction()) {
            "Lua command '$sourceName' must define an exec(context) function"
        }

        installCompatibilityGlobals(globals, commandTable)
        return ScriptCommand(name, globals, commandTable, sourceName)
    }

    fun loadFile(file: File): ScriptCommand = loadSource(file.readText(), file.absolutePath)

    private fun loadAndRegister(file: File): ScriptCommand? {
        return try {
            loadFile(file).also(CommandManager::register)
        } catch (error: Exception) {
            System.err.println("Failed to load Lua command '${file.absolutePath}': ${error.message}")
            null
        }
    }

    private fun installCompatibilityGlobals(globals: org.luaj.vm2.Globals, command: LuaTable) {
        globals.set("command", command)
        globals.set("exec", command.get("exec"))
        globals.set("commandName", LuaValue.valueOf(command.get("name").tojstring()))
    }
}
