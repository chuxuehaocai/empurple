package dev.naominet.empurple

import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandManager
import dev.naominet.empurple.config.EmpurpleConfig
import dev.naominet.empurple.script.ScriptCommandLoader
import dev.naominet.empurple.script.ScriptService
import dev.naominet.empurple.utils.ResourceHelper
import dev.naominet.purple.framework.config.ConfigManager
import dev.naominet.purple.framework.core.plugin.IPlugin
import dev.naominet.purple.framework.core.plugin.PluginInfomation
import dev.naominet.purple.framework.event.EventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object EmpurplePlugin : IPlugin {
    override val info = PluginInfomation("empurple")
    private val scope = CoroutineScope(Dispatchers.Default)
    lateinit var config: EmpurpleConfig

    override fun start(): IPlugin {
        ResourceHelper.initialize()
        config = ConfigManager.register(EmpurpleConfig())
        kotlinx.coroutines.runBlocking { ScriptService.initialize() }
        ScriptCommandLoader.load()

        EventManager.groupMessageEvent.listen { msg ->
            ChatStealer.saveTheChat(msg)
            scope.launch { CommandManager.process(msg) }
        }
        EventManager.privateMessageEvent.listen { msg ->
            scope.launch { CallbackManager.process(msg) }
        }
        return this
    }
}