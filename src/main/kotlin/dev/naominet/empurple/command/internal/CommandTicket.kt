package dev.naominet.empurple.command.internal

import com.alibaba.fastjson2.JSON
import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand
import dev.naominet.empurple.maimai.MaiCallbackData
import dev.naominet.empurple.maimai.MaimaiApiClient
import dev.naominet.empurple.maimai.dto.UserChargeData
import dev.naominet.empurple.maimai.dto.UserLoginData
import dev.naominet.empurple.maimai.dto.UserPreviewData
import dev.naominet.empurple.maimai.request.GetUserChargeApiRequest
import dev.naominet.empurple.maimai.request.QrAuthRequest
import dev.naominet.empurple.maimai.request.UserChargeLogData
import dev.naominet.empurple.maimai.request.UserChargePacketData
import dev.naominet.empurple.maimai.request.UserChargePacketItem
import dev.naominet.empurple.maimai.request.UserLoginRequest
import dev.naominet.empurple.maimai.request.UserLogoutRequest
import dev.naominet.empurple.maimai.request.UserPreviewRequest
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CommandTicket: ICommand {
    override val name: String
        get() = "ticket"

    override suspend fun exec(context: CommandContext) {
        val msgBuilder = MessageBuilder()

        Bot.sendGroupMessage(
            context.groupId,
            msgBuilder.replyGroup(context.message.message_id, "请私聊本账号发送你的二维码解析出来的字符串...").build()
        )
        if(context.args.isEmpty()) {
            CallbackManager.addCallback(
                MaiCallbackData(
                    context.senderId,
                    context.groupId,
                    context.message.message_id,
                    ::privateMsgHandler_getCount,
                    LocalDateTime.now()
                )
            )
        }

        if(context.args.isNotEmpty() && (context.groupId == 1063238023L || context.groupId == 960156363L)) {
            val num = context.args.toIntOrNull()
            if (num != null && num in 1..5) {
                CallbackManager.addCallback(
                    MaiCallbackData(
                        context.senderId,
                        context.groupId,
                        context.message.message_id,
                        ::privateMsgHandler_sendTicket,
                        LocalDateTime.now(),
                        num
                    )
                )
            }else{
                Bot.sendGroupMessage(
                    context.groupId,
                    msgBuilder.replyGroup(context.message.message_id, "无效的Ticket ID。").build()
                )
            }
        }
    }

    suspend fun privateMsgHandler_sendTicket(content: TextMessageBean, callbackData: MaiCallbackData) {
        val msg = content.raw_message
        val msgBuilder = MessageBuilder()
        if (!msg.startsWith("SGWCMAID")) {
            Bot.sendPrivateMessage(
                content.sender.user_id,
                msgBuilder.reply(content.message_id).append("不是有效的二维码字符串。").build()
            )
            return
        }

        //request aimedb to get userid & token
        //first is userId/error code, second is token
        val qrAuthRequest = QrAuthRequest(msg).execute()

        if (qrAuthRequest.first < 10000000) {
            Bot.sendPrivateMessage(
                content.user_id,
                msgBuilder.reply(content.message_id)
                    .append("在请求Aime Server时出现异常。错误码: ${qrAuthRequest.first}").build()
            )
            return
        }

        val targetUserId = qrAuthRequest.first
        val token = qrAuthRequest.second

        //GetUserChargeApi
        val getUserChargeApiRequest = GetUserChargeApiRequest(targetUserId)
        val chargeDataBean  = JSON.parseObject(
            MaimaiApiClient.call(getUserChargeApiRequest.toJson(), "GetUserChargeApi", targetUserId),
            UserChargeData::class.java
        )

        msgBuilder.reply(callbackData.originMsgId)
        for (chargeData in chargeDataBean.userChargeList!!){
            if(chargeData.chargeId == callbackData.ticketId){
                if(chargeData.stock != 0){
                    Bot.sendPrivateMessage(
                        content.user_id,
                        msgBuilder.reply(content.message_id)
                            .append("在尝试发票时出现了一个异常。当前Ticket ID对应的功能票数量不为0.出于安全考虑，不会继续发票。").build()
                    )
                    return
                }
            }
        }

        //getPreview to get needed data
        val userPreviewData = UserPreviewRequest(
            targetUserId,
            token = token,
            clientId = EmpurplePlugin.config.clientId
        )
        val shits = JSON.parseObject(
            MaimaiApiClient.call(userPreviewData.toJson(), "GetUserPreviewApi", targetUserId),
            UserPreviewData::class.java
        )

        val loginDateTime = System.currentTimeMillis() / 1000
        val loginRequest = UserLoginRequest(
            targetUserId,
            "",
            EmpurplePlugin.config.regionId,
            EmpurplePlugin.config.placeId,
            EmpurplePlugin.config.clientId,
            loginDateTime - 600,
            loginDateTime,
            false,
            0,
            token
        )
        val loginResponseData = MaimaiApiClient.callWithCookie(loginRequest.toJson(), "UserLoginApi", targetUserId)
        val cookie = loginResponseData.cookieHeader
        val loginResult = JSON.parseObject(loginResponseData.body, UserLoginData::class.java)

        if (loginResult.returnCode != 1) {
            Bot.sendPrivateMessage(
                content.user_id,
                msgBuilder.reply(content.message_id)
                    .append("在尝试登陆时出现异常。Stage: UserLoginApi, errorCode:${loginResult.returnCode}").build()
            )
            return
        }

        Bot.sendPrivateMessage(
            content.user_id,
            msgBuilder.reply(content.message_id).append("登录成功。出于一些原因，需要等待60s再下发功能票。").build()
        )
        delay(60_000)

        val now = LocalDateTime.now()
        val validDate = now
            .toLocalDate()
            .atTime(4, 0, 0)
            .plusDays(90)
        val validDateStr = validDate.format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
        val ticketPacket = UserChargePacketData(
            targetUserId,
            UserChargeLogData(
                callbackData.ticketId,
                0,
                formatDateTime(LocalDateTime.now()),
                1,
                shits.playerRating,
                EmpurplePlugin.config.placeId,
                EmpurplePlugin.config.regionId,
                EmpurplePlugin.config.clientId,
            ),
            UserChargePacketItem(
                callbackData.ticketId,
                1,
                formatDateTime(LocalDateTime.now()),
                validDateStr,
            ),
            loginDateTime
        )
        MaimaiApiClient.call(ticketPacket.toJson(), "UpsertUserChargelogApi", targetUserId, cookie)
        Bot.sendPrivateMessage(content.user_id, msgBuilder.reply(content.message_id).append("下发完成。").build())

        //logout now
        val userLogoutRequest = UserLogoutRequest(
            userId = targetUserId,
            placeId = EmpurplePlugin.config.placeId,
            regionId = EmpurplePlugin.config.regionId,
            clientId = EmpurplePlugin.config.clientId,
            loginDateTime = loginDateTime
        )
        try {
            MaimaiApiClient.call(userLogoutRequest.toJson(), "UserLogoutApi", targetUserId, cookie)
            Bot.sendPrivateMessage(content.user_id, msgBuilder.reply(content.message_id).append("账号已登出。").build())
        } catch (_: Exception) {
        }

        Bot.sendGroupMessage(callbackData.sourceGroupId, msgBuilder.toString())
    }

    private val dateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun formatDateTime(dateTime: LocalDateTime): String {
        return "${dateTime.format(dateTimeFormatter)}.0"
    }

    suspend fun privateMsgHandler_getCount(content: TextMessageBean, callbackData: MaiCallbackData) {
        val msg = content.raw_message
        val msgBuilder = MessageBuilder()
        if (!msg.startsWith("SGWCMAID")) {
            Bot.sendPrivateMessage(
                content.sender.user_id,
                msgBuilder.reply(content.message_id).append("不是有效的二维码字符串。").build()
            )
            return
        }

        //request aimedb to get userid & token
        //first is userId/error code, second is token
        val qrAuthRequest = QrAuthRequest(msg).execute()

        if (qrAuthRequest.first < 10000000) {
            Bot.sendPrivateMessage(
                content.user_id,
                msgBuilder.reply(content.message_id)
                    .append("在请求Aime Server时出现异常。错误码: ${qrAuthRequest.first}").build()
            )
            return
        }

        val targetUserId = qrAuthRequest.first

        //GetUserChargeApi
        val getUserChargeApiRequest = GetUserChargeApiRequest(targetUserId)
        val chargeDataBean  = JSON.parseObject(
            MaimaiApiClient.call(getUserChargeApiRequest.toJson(), "GetUserChargeApi", targetUserId),
            UserChargeData::class.java
        )

        msgBuilder.reply(callbackData.originMsgId)
        for (chargeData in chargeDataBean.userChargeList!!){
            msgBuilder.append(
                "功能票ID:${chargeData.chargeId}, 数量:${chargeData.stock}, 有效日期至:${chargeData.validDate}\n"
            )
        }
        msgBuilder.append("·汇报完毕。")

        Bot.sendGroupMessage(callbackData.sourceGroupId, msgBuilder.toString())
    }
}