package dev.naominet.empurple

import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandManager
import dev.naominet.empurple.config.EmpurpleConfig
import dev.naominet.empurple.llm.LLMService
import dev.naominet.empurple.llm.LlmMentionHandler
import dev.naominet.empurple.utils.MinecraftMOTDHelper
import dev.naominet.empurple.utils.ResourceHelper
import dev.naominet.empurple.web.WebUiPanel
import dev.naominet.purple.framework.config.ConfigManager
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.core.plugin.IPlugin
import dev.naominet.purple.framework.core.plugin.PluginInfomation
import dev.naominet.purple.framework.event.EventManager
import dev.naominet.purple.framework.utils.MessageBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object EmpurplePlugin : IPlugin {
    override val info = PluginInfomation("empurple")
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
        System.err.println("Empurple coroutine failed: ${error.stackTraceToString()}")
    }
    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler)

    private var scope = newScope()
    private var groupListener: AutoCloseable? = null
    private var privateListener: AutoCloseable? = null
    lateinit var config: EmpurpleConfig

    override fun start(): IPlugin {
        scope = newScope()
        ResourceHelper.initialize()
        config = ConfigManager.register(EmpurpleConfig())
        LLMService.initialize(config)
        if (config.webUiEnabled) {
            WebUiPanel.start(config)
        }

        groupListener = EventManager.groupMessageEvent.listen { msg ->
            scope.launch {
                if (LlmMentionHandler.process(msg, config)) return@launch
                CommandManager.process(msg)
            }
        }
        privateListener = EventManager.privateMessageEvent.listen { msg ->
            scope.launch {
                try {
                    CallbackManager.process(msg)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    System.err.println("Private interaction failed: ${error.stackTraceToString()}")
                }
            }
        }
        return this
    }

    override fun stop() {
        WebUiPanel.stop()
        groupListener?.close()
        groupListener = null
        privateListener?.close()
        privateListener = null
        scope.cancel()
    }
}