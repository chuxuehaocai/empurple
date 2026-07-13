package dev.naominet.empurple.script

import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand
import dev.naominet.empurple.maimai.MaiCallbackData
import dev.naominet.empurple.maimai.MaimaiApiClient
import dev.naominet.empurple.utils.ResourceHelper
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import kotlinx.coroutines.runBlocking
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.io.File
import java.time.LocalDateTime
import javax.imageio.ImageIO

class ScriptCommand(
    override val name: String,
    val globals: Globals,
    private val command: LuaTable,
    val source: String = name
) : ICommand {
    private val execute = command.get("exec").also {
        require(it.isfunction()) { "Lua command '$source' must define an exec(context) function" }
    }

    override suspend fun exec(context: CommandContext) {
        try {
            execute.call(createContext(context))
        } catch (error: LuaError) {
            System.err.println("Failed to execute Lua command '$name' from '$source': ${error.message}")
        }
    }

    private fun createContext(context: CommandContext): LuaTable = LuaTable().apply {
        set("args", LuaValue.valueOf(context.args))
        set("rawMessage", LuaValue.valueOf(context.rawMessage))
        set("groupId", LuaValue.valueOf(context.groupId.toDouble()))
        set("senderId", LuaValue.valueOf(context.senderId.toDouble()))
        set("message", CoerceJavaToLua.coerce(context.message))
        set("javaContext", CoerceJavaToLua.coerce(context))
        set("api", CoerceJavaToLua.coerce(LuaCommandApi(context, source)))
    }
}

class LuaCommandApi(
    private val context: CommandContext,
    private val source: String
) {
    @JvmOverloads
    fun sendGroupMessage(message: String, autoEscaped: Boolean = false) {
        Bot.sendGroupMessage(context.groupId, message, autoEscaped)
    }

    @JvmOverloads
    fun sendGroupMessage(groupId: Long, message: String, autoEscaped: Boolean = false) {
        Bot.sendGroupMessage(groupId, message, autoEscaped)
    }

    @JvmOverloads
    fun sendPrivateMessage(userId: Long, message: String, autoEscaped: Boolean = false) {
        Bot.sendPrivateMessage(userId, message, autoEscaped)
    }

    fun reply(message: String) {
        Bot.sendGroupMessage(
            context.groupId,
            MessageBuilder().replyGroup(context.message.message_id, message).build()
        )
    }

    fun deleteMessage(messageId: Long) {
        Bot.deleteMessage(messageId)
    }

    fun sendLike(userId: Long, count: Int) {
        Bot.sendLike(userId, count)
    }

    fun newMessageBuilder(): MessageBuilder = MessageBuilder()

    fun config() = EmpurplePlugin.config

    fun resourceHelper() = ResourceHelper

    fun newFile(path: String): File = File(path)

    fun readImage(file: File) = ImageIO.read(file)

    fun writeImage(image: java.awt.image.RenderedImage, format: String, file: File): Boolean =
        ImageIO.write(image, format, file)

    fun delay(milliseconds: Long) {
        runBlocking { kotlinx.coroutines.delay(milliseconds) }
    }

    fun coroutineRoundTrip(value: String, milliseconds: Long = 1): String = runBlocking {
        kotlinx.coroutines.delay(milliseconds)
        value
    }

    @JvmOverloads
    fun callMaimaiApi(data: String, api: String, userId: Long, cookie: String? = null): String =
        runBlocking { MaimaiApiClient.call(data, api, userId, cookie) }

    fun callMaimaiApiWithCookie(data: String, api: String, userId: Long): MaimaiApiClient.Response =
        runBlocking { MaimaiApiClient.callWithCookie(data, api, userId) }

    @JvmOverloads
    fun awaitPrivateMessage(ticketId: Int = 0, callback: LuaValue) {
        require(callback.isfunction()) { "Lua command '$source' callback must be a function" }
        require(
            CallbackManager.addCallback(
                MaiCallbackData(
                    context.senderId,
                    context.groupId,
                    context.message.message_id,
                    { message, callbackData -> invokeCallback(callback, message, callbackData) },
                    LocalDateTime.now(),
                    ticketId
                )
            )
        ) { "User already has a pending private-message interaction" }
    }

    private suspend fun invokeCallback(
        callback: LuaValue,
        message: TextMessageBean,
        callbackData: MaiCallbackData
    ) {
        try {
            val callbackContext = LuaTable().apply {
                set("message", CoerceJavaToLua.coerce(message))
                set("rawMessage", LuaValue.valueOf(message.raw_message))
                set("senderId", LuaValue.valueOf(message.sender.user_id.toDouble()))
                set("userId", LuaValue.valueOf(callbackData.userId.toDouble()))
                set("sourceGroupId", LuaValue.valueOf(callbackData.sourceGroupId.toDouble()))
                set("originMsgId", LuaValue.valueOf(callbackData.originMsgId.toDouble()))
                set("ticketId", LuaValue.valueOf(callbackData.ticketId))
                set("callbackData", CoerceJavaToLua.coerce(callbackData))
                set("api", CoerceJavaToLua.coerce(this@LuaCommandApi))
            }
            callback.call(callbackContext)
        } catch (error: LuaError) {
            System.err.println("Failed to execute callback for Lua command '$source': ${error.message}")
        }
    }
}
