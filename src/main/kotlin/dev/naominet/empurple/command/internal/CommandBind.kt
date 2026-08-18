package dev.naominet.empurple.command.internal

import com.alibaba.fastjson2.JSON
import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.maimai.MaimaiApiClient
import dev.naominet.empurple.maimai.request.UserPreviewRequest
import dev.naominet.empurple.maimai.dto.UserPreviewData
import dev.naominet.empurple.command.ICommand
import dev.naominet.empurple.maimai.MaiCallbackData
import dev.naominet.empurple.maimai.request.QrAuthRequest
import dev.naominet.empurple.utils.QQUserManager
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime

class CommandBind : ICommand {
    override val name = "bind"

    override suspend fun exec(context: CommandContext) {
        CallbackManager.addCallback(
            MaiCallbackData(
                context.senderId,
                context.groupId,
                context.message.message_id,
                ::privateMsgHandler,
                LocalDateTime.now()
            ),
            replaceExisting = true
        )

        Bot.sendGroupMessage(
            context.groupId,
            MessageBuilder()
                .replyGroup(context.message.message_id, "请在 1 分钟内私聊本账号发送二维码解析出来的 SGWCMAID 字符串。")
                .build()
        )
    }

    suspend fun privateMsgHandler(content: TextMessageBean, callbackData: MaiCallbackData) {
        val qqId = callbackData.userId

        try {
            val msg = content.raw_message.trim()
            if (!msg.startsWith("SGWCMAID")) {
                Bot.sendPrivateMessage(
                    qqId,
                    MessageBuilder().reply(content.message_id).append("不是有效的二维码字符串。").build()
                )
                return
            }

            // 请求 Aime Server，把二维码 token 解析成 Mai UID（userId）
            // first 是 userId/错误码，second 是 token
            val qrAuthRequest = QrAuthRequest(msg).execute()
            if (qrAuthRequest.first < 10_000_000) {
                Bot.sendPrivateMessage(
                    qqId,
                    MessageBuilder().reply(content.message_id)
                        .append("在请求 Aime Server 时出现异常。错误码: ${qrAuthRequest.first}").build()
                )
                return
            }

            val maiUid = qrAuthRequest.first
            val preview = JSON.parseObject(
                MaimaiApiClient.call(
                    UserPreviewRequest(maiUid, "", qrAuthRequest.second, EmpurplePlugin.config.clientId).toJson(),
                    "GetUserPreviewApi",
                    maiUid
                ),
                UserPreviewData::class.java
            )
            QQUserManager.bind(qqId, maiUid, preview.userName, preview.iconId)

            Bot.sendPrivateMessage(
                qqId,
                MessageBuilder()
                    .reply(content.message_id)
                    .append("绑定成功！你的 Mai UID 是 $maiUid。")
                    .build()
            )
            Bot.sendGroupMessage(
                callbackData.sourceGroupId,
                MessageBuilder()
                    .replyGroup(callbackData.originMsgId, "绑定成功，Mai UID 已保存。")
                    .build()
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val errorText = "绑定失败：${error.message ?: error::class.simpleName}"
            runCatching { Bot.sendPrivateMessage(qqId, errorText) }
            runCatching {
                Bot.sendGroupMessage(
                    callbackData.sourceGroupId,
                    MessageBuilder().replyGroup(callbackData.originMsgId, errorText).build()
                )
            }
        }
    }
}