package dev.naominet.empurple.command.internal

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand
import dev.naominet.empurple.maimai.MaimaiApiClient
import dev.naominet.empurple.maimai.request.QrAuthRequest
import dev.naominet.empurple.maimai.request.UserPreviewRequest
import dev.naominet.empurple.maimai.request.UserRatingRequest
import dev.naominet.empurple.utils.Best50ImageRenderer
import dev.naominet.empurple.utils.Best50WebPageRenderer
import dev.naominet.empurple.utils.Best50WebPublisher
import dev.naominet.empurple.utils.QQUserManager
import dev.naominet.empurple.utils.ResourceHelper
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.time.Duration

class CommandB50 : ICommand {
    override val name = "b50"

    override suspend fun exec(context: CommandContext) {
        if (context.args.isNotBlank()) {
            replyGroup(context.groupId, context.message.message_id, "仅支持 /b50 请不要添加参数")
            return
        }
        val boundUid = QQUserManager.getMaiUID(context.senderId)
        if (boundUid != null) {
            updateFromBoundUid(context.senderId, boundUid, context.groupId, context.message.message_id)
            return
        }
        val registered = CallbackManager.add(context.senderId, "b50", Duration.ofMinutes(2)) { message ->
            handleQr(message, context.senderId, context.groupId, context.message.message_id)
        }
        if (!registered) {
            replyGroup(context.groupId, context.message.message_id, "你已有等待中的私聊操作")
            return
        }
        replyGroup(context.groupId, context.message.message_id, "请在 2 分钟内私聊发送二维码解析出来的 SGWCMAID 字符串")
    }

    private suspend fun updateFromBoundUid(qqUserId: Long, maiUid: Long, groupId: Long, originMessageId: Long) {
        try {
            val data = fetchRating(maiUid, QQUserManager.getUserName(qqUserId), QQUserManager.getIconId(qqUserId))
            finishUpdate(qqUserId, groupId, originMessageId, data, false)
        } catch (error: Exception) {
            replyGroup(groupId, originMessageId, "Best50 生成失败 ${error.message ?: error::class.simpleName}")
        }
    }

    private suspend fun handleQr(message: TextMessageBean, qqUserId: Long, groupId: Long, originMessageId: Long) {
        try {
            val qr = message.raw_message
            require(qr.startsWith("SGWCMAID")) { "不是有效的二维码字符串 请重新执行 /b50" }
            val auth = QrAuthRequest(qr).execute()
            require(auth.first >= 10_000_000) { "二维码认证失败 错误码 ${auth.first}" }
            val preview = JSON.parseObject(
                MaimaiApiClient.call(
                    UserPreviewRequest(auth.first, "", auth.second, EmpurplePlugin.config.clientId).toJson(),
                    "GetUserPreviewApi",
                    auth.first
                )
            )
            val data = fetchRating(auth.first, preview.getString("userName"), preview.getIntValue("iconId"), preview.getIntValue("playerRating"))
            finishUpdate(qqUserId, groupId, originMessageId, data, true)
        } catch (error: Exception) {
            val text = "Best50 生成失败 ${error.message ?: error::class.simpleName}"
            Bot.sendPrivateMessage(qqUserId, text)
            replyGroup(groupId, originMessageId, text)
        }
    }

    private suspend fun fetchRating(targetUserId: Long, userName: String? = null, iconId: Int? = null, playerRating: Int? = null): JSONObject {
        val rating = JSON.parseObject(MaimaiApiClient.call(UserRatingRequest(targetUserId).toJson(), "GetUserRatingApi", targetUserId))
        return JSONObject().apply {
            put("userId", targetUserId)
            userName?.takeIf { it.isNotBlank() }?.let { put("userName", it) }
            iconId?.takeIf { it > 0 }?.let { put("iconId", it) }
            playerRating?.takeIf { it > 0 }?.let { put("playerRating", it) }
            put("userRating", rating.getJSONObject("userRating") ?: JSONObject())
        }
    }

    private suspend fun finishUpdate(qqUserId: Long, groupId: Long, originMessageId: Long, data: JSONObject, notifyPrivate: Boolean) {
        if (notifyPrivate) Bot.sendPrivateMessage(qqUserId, "数据获取完成 正在生成图片和发布网页")
        replyGroup(groupId, originMessageId, "数据获取完成 正在生成图片和发布网页")
        sendImage(qqUserId, groupId, originMessageId, data)
        if (notifyPrivate) Bot.sendPrivateMessage(qqUserId, "Best50 图片已发送到群聊")
    }

    private suspend fun sendImage(userId: Long, groupId: Long, messageId: Long, data: JSONObject) {
        val webIdFile = cacheWebIdFile(userId)
        val cachedWebId = webIdFile.takeIf(File::isFile)?.readText()?.trim()
        val webPage = Best50WebPageRenderer.render(data, cachedWebId).also { webIdFile.writeText(it.id) }
        val webUrl = "https://b50.naominet.dev/${webPage.relativePath}"
        val output = outputPngFile(userId)
        coroutineScope {
            val publishJob = async(Dispatchers.IO) { runCatching { Best50WebPublisher.publish(webPage) } }
            val renderJob = async(Dispatchers.IO) { Best50ImageRenderer.render(data, output, webUrl) }
            renderJob.await()
            Bot.sendGroupMessage(groupId, MessageBuilder().reply(messageId).image(output).append("\nBest50 网页: $webUrl").build())
            publishJob.await()
        }
    }

    private fun replyGroup(groupId: Long, messageId: Long, message: String) {
        Bot.sendGroupMessage(groupId, MessageBuilder().replyGroup(messageId, message).build())
    }

    companion object {
        fun outputPngFile(userId: Long) = File(ResourceHelper.dataCacheFolder, "${userId}_best50.png")
        fun cacheWebIdFile(userId: Long) = File(ResourceHelper.dataCacheFolder, "${userId}_best50_web_id.txt")
    }
}