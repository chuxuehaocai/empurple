package dev.naominet.empurple

import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandManager
import dev.naominet.empurple.config.EmpurpleConfig
import dev.naominet.empurple.llm.LLMService
import dev.naominet.empurple.llm.LlmMentionHandler
import dev.naominet.empurple.script.ScriptCommandLoader
import dev.naominet.empurple.script.ScriptService
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
        kotlinx.coroutines.runBlocking { ScriptService.initialize() }
        ScriptCommandLoader.load()
        if (config.webUiEnabled) {
            WebUiPanel.start(config)
        }

        groupListener = EventManager.groupMessageEvent.listen { msg ->
            scope.launch {
                if (LlmMentionHandler.process(msg, config)) return@launch
                if (msg.raw_message.equals("看看里面")){
                    if(msg.group_id == 981665238L || msg.group_id == 981701956L){
                        val sb = MessageBuilder()
                        val motdInfo = MinecraftMOTDHelper.getMOTD("nbb.rainplay.cn", 14878)

                        sb.reply(msg.message_id)

                        if(motdInfo.players.online != 0){
                            sb.append("有${motdInfo.players.online}个入在服务器里喵。")
                        }else{
                            sb.append("没人在线喵。")
                        }

                        Bot.sendGroupMessage(msg.group_id, sb.build())
                    }
                }
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