package dev.naominet.empurple.command.internal

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand
import dev.naominet.empurple.maimai.MaimaiApiClient
import dev.naominet.empurple.maimai.dto.UserLoginData
import dev.naominet.empurple.maimai.dto.UserMusicData
import dev.naominet.empurple.maimai.dto.UserMusicDetail
import dev.naominet.empurple.maimai.dto.UserPreviewData
import dev.naominet.empurple.maimai.request.QrAuthRequest
import dev.naominet.empurple.maimai.request.UserLoginRequest
import dev.naominet.empurple.maimai.request.UserLogoutRequest
import dev.naominet.empurple.maimai.request.UserMusicDataRequest
import dev.naominet.empurple.maimai.request.UserPreviewRequest
import dev.naominet.empurple.maimai.request.UserRatingRequest
import dev.naominet.empurple.utils.Best50ImageRenderer
import dev.naominet.empurple.utils.Best50WebPageRenderer
import dev.naominet.empurple.utils.Best50WebPublisher
import dev.naominet.empurple.utils.ResourceHelper
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

class CommandB50 : ICommand {
    override val name = "b50"
    var useFullData = false
    override suspend fun exec(context: CommandContext) {
        val forceUpdate = context.args.trim().equals("u", ignoreCase = true)
        useFullData = context.args.trim().equals("f", ignoreCase = true)
        val cache = cacheJsonFile(context.senderId)
        if (!forceUpdate && cache.exists()) {
            try {
                sendImage(context.senderId, context.groupId, context.message.message_id, JSON.parseObject(cache.readText()), false)
            } catch (error: Exception) {
                replyGroup(context.groupId, context.message.message_id, "缓存生成失败: ${error.message}，请使用 /b50 u 更新。")
            }
            return
        }

        val registered = CallbackManager.add(context.senderId, "b50", Duration.ofMinutes(2)) { message ->
            handleQr(message, context.senderId, context.groupId, context.message.message_id)
        }
        if (!registered) {
            replyGroup(context.groupId, context.message.message_id, "你已有等待中的私聊操作，请先完成后再试。")
            return
        }
        replyGroup(context.groupId, context.message.message_id, "请在 2 分钟内私聊发送二维码解析出来的 SGWCMAID 字符串。")
    }

    private suspend fun handleQr(message: TextMessageBean, qqUserId: Long, groupId: Long, originMessageId: Long) {
        try {
            val qr = message.raw_message
            require(qr.startsWith("SGWCMAID")) { "不是有效的二维码字符串，请重新执行 /b50 u。" }
            Bot.sendPrivateMessage(qqUserId, "已收到二维码，正在更新 Best50 数据，请稍候。")
            replyGroup(groupId, originMessageId, "已收到数据，正在更新 Best50。")
            val auth = QrAuthRequest(qr).execute()
            require(auth.first >= 10_000_000) { "二维码认证失败，错误码: ${auth.first}" }
            val targetUserId = auth.first
            val token = auth.second
            val preview = JSON.parseObject(
                MaimaiApiClient.call(
                    UserPreviewRequest(targetUserId, "", token, EmpurplePlugin.config.clientId).toJson(),
                    "GetUserPreviewApi",
                    targetUserId
                ),
                UserPreviewData::class.java
            )
            lateinit var cache: JSONObject
            if(useFullData) {
                cache = if (preview.isLogin == 1) {
                    replyGroup(groupId, originMessageId, "账号当前已登录，将使用 Rating 数据生成。")
                    fetchLessData(targetUserId, preview)
                } else {
                    fetchFullData(targetUserId, token, preview, qqUserId)
                }
            }else{
                cache = fetchLessData(targetUserId, preview)
            }
            saveCache(qqUserId, cache)
            Bot.sendPrivateMessage(qqUserId, "数据获取完成，正在生成图片和发布网页。")
            replyGroup(groupId, originMessageId, "数据获取完成，正在生成图片和发布网页。")
            sendImage(qqUserId, groupId, originMessageId, cache)
            Bot.sendPrivateMessage(qqUserId, "Best50 图片已发送到群聊。")
        } catch (error: Exception) {
            Bot.sendPrivateMessage(qqUserId, "Best50 生成失败: ${error.message}")
        }
    }

    private suspend fun fetchLessData(targetUserId: Long, preview: UserPreviewData): JSONObject {
        val rating = JSON.parseObject(
            MaimaiApiClient.call(UserRatingRequest(targetUserId).toJson(), "GetUserRatingApi", targetUserId)
        )
        return cacheObject(preview, rating.getJSONObject("userRating") ?: JSONObject())
    }

    private suspend fun fetchFullData(
        targetUserId: Long,
        token: String,
        preview: UserPreviewData,
        qqUserId: Long
    ): JSONObject {
        val config = EmpurplePlugin.config
        val loginDateTime = System.currentTimeMillis() / 1000
        val loginRequest = UserLoginRequest(
            targetUserId, "", config.regionId, config.placeId, config.clientId,
            loginDateTime - 600, loginDateTime, false, 0, token
        )
        val response = MaimaiApiClient.callWithCookie(loginRequest.toJson(), "UserLoginApi", targetUserId)
        val login = JSON.parseObject(response.body, UserLoginData::class.java)
        require(login.returnCode == 1) { "UserLoginApi failed: ${login.returnCode}" }
        val cookie = requireNotNull(response.cookieHeader) { "UserLoginApi did not return a cookie" }

        try {
            Bot.sendPrivateMessage(qqUserId, "登录成功，等待 60 秒后拉取数据。")
            delay(60_000)
            val rating = JSON.parseObject(
                MaimaiApiClient.call(UserRatingRequest(targetUserId).toJson(), "GetUserRatingApi", targetUserId, cookie)
            )
            val details = fetchMusicDetails(targetUserId, cookie)
            val userRating = rating.getJSONObject("userRating") ?: JSONObject()
            return cacheObject(
                preview,
                JSONObject().apply {
                    put("ratingList", enrich(userRating.getJSONArray("ratingList"), details))
                    put("newRatingList", enrich(userRating.getJSONArray("newRatingList"), details))
                }
            )
        } finally {
            withContext(NonCancellable) {
                delay(5_000)
                runCatching {
                    MaimaiApiClient.call(
                        UserLogoutRequest(
                            userId = targetUserId,
                            regionId = config.regionId,
                            placeId = config.placeId,
                            clientId = config.clientId,
                            loginDateTime = loginDateTime
                        ).toJson(),
                        "UserLogoutApi", targetUserId, cookie
                    )
                }.onFailure { System.err.println("B50 logout failed: ${it.message}") }
            }
        }
    }

    private suspend fun fetchMusicDetails(userId: Long, cookie: String): Map<Pair<Int, Int>, UserMusicDetail> {
        val result = mutableMapOf<Pair<Int, Int>, UserMusicDetail>()
        var nextIndex = 0L
        do {
            val page = JSON.parseObject(
                MaimaiApiClient.call(
                    UserMusicDataRequest(userId, nextIndex, 50).toJson(),
                    "GetUserMusicApi", userId, cookie
                ),
                UserMusicData::class.java
            )
            page.userMusicList.flatMap { it.userMusicDetailList }.forEach { result[it.musicId to it.level] = it }
            val following = page.nextIndex.toLong()
            if (following <= nextIndex || following <= 0) break
            nextIndex = following
        } while (true)
        return result
    }

    private fun enrich(array: JSONArray?, details: Map<Pair<Int, Int>, UserMusicDetail>): JSONArray = JSONArray().apply {
        array?.forEach { raw ->
            val item = raw as JSONObject
            val musicId = item.getIntValue("musicId")
            val level = item.getIntValue("level")
            val detail = details[musicId to level]
            add(JSONObject().apply {
                put("musicId", musicId)
                put("level", level)
                put("achievement", detail?.achievement ?: item.getIntValue("achievement"))
                put("comboStatus", detail?.comboStatus ?: item.getIntValue("comboStatus"))
                put("syncStatus", detail?.syncStatus ?: item.getIntValue("syncStatus"))
            })
        }
    }

    private fun cacheObject(preview: UserPreviewData, userRating: JSONObject): JSONObject = JSONObject().apply {
        put("userId", preview.userId)
        put("userName", preview.userName)
        put("iconId", preview.iconId)
        put("playerRating", preview.playerRating)
        put("userRating", userRating)
    }

    private fun saveCache(userId: Long, cache: JSONObject) {
        val target = cacheJsonFile(userId)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(cache.toJSONString())
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sendImage(
        userId: Long,
        groupId: Long,
        messageId: Long,
        cache: JSONObject,
        refreshWeb: Boolean = true
    ) {
        val webIdFile = cacheWebIdFile(userId)
        val cachedWebId = webIdFile.takeIf(File::isFile)?.readText()?.trim()
        val webPage = (if (refreshWeb) null else Best50WebPageRenderer.find(cachedWebId.orEmpty()))
            ?: Best50WebPageRenderer.render(cache, cachedWebId).also { webIdFile.writeText(it.id) }
        Best50WebPublisher.publish(webPage)
        val webUrl = "https://b50.naominet.dev/${webPage.relativePath}"
        val output = cachePngFile(userId)
        if (refreshWeb || !output.isFile || cachedWebId != webPage.id) {
            Best50ImageRenderer.render(cache, output, webUrl)
        }
        Bot.sendGroupMessage(
            groupId,
            MessageBuilder()
                .reply(messageId)
                .image(output)
                .append("\nBest50 网页: $webUrl")
                .build()
        )
    }

    private fun replyGroup(groupId: Long, messageId: Long, message: String) {
        Bot.sendGroupMessage(groupId, MessageBuilder().replyGroup(messageId, message).build())
    }

    companion object {
        fun cacheJsonFile(userId: Long) = File(ResourceHelper.dataCacheFolder, "${userId}_best50.json")
        fun cachePngFile(userId: Long) = File(ResourceHelper.dataCacheFolder, "${userId}_best50.png")
        fun cacheWebIdFile(userId: Long) = File(ResourceHelper.dataCacheFolder, "${userId}_best50_web_id.txt")
    }
}
