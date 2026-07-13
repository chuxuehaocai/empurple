package dev.naominet.empurple.command.internal

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.toJSONString
import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand
import dev.naominet.empurple.maimai.MaiCallbackData
import dev.naominet.empurple.maimai.MaimaiApiClient
import dev.naominet.empurple.maimai.dto.UserAllData
import dev.naominet.empurple.maimai.dto.UserLoginData
import dev.naominet.empurple.maimai.dto.UserPreviewData
import dev.naominet.empurple.maimai.request.QrAuthRequest
import dev.naominet.empurple.maimai.request.UserDataRequest
import dev.naominet.empurple.maimai.request.UserLoginRequest
import dev.naominet.empurple.maimai.request.UserLogoutRequest
import dev.naominet.empurple.maimai.request.UserPreviewRequest
import dev.naominet.empurple.utils.ResourceHelper
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.io.File
import java.time.LocalDateTime
import javax.imageio.ImageIO
import kotlin.random.Random

class CommandWhoami : ICommand {
    override val name = "whoami"

    override suspend fun exec(context: CommandContext) {
        val msgBuilder = MessageBuilder()

        Bot.sendGroupMessage(
            context.groupId,
            msgBuilder.replyGroup(context.message.message_id, "请私聊本账号发送你的二维码解析出来的字符串...").build()
        )
        CallbackManager.addCallback(
            MaiCallbackData(
                context.senderId,
                context.groupId,
                context.message.message_id,
                ::privateMsgHandler,
                LocalDateTime.now()
            )
        )
    }

    suspend fun privateMsgHandler(content: TextMessageBean, callbackData: MaiCallbackData) {
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
        Bot.sendPrivateMessage(content.user_id, msgBuilder.reply(content.message_id).append("正在尝试登录...").build())
        val previewRequest = UserPreviewRequest(targetUserId, "", token, EmpurplePlugin.config.clientId)
        val responseData = MaimaiApiClient.call(previewRequest.toJSONString(), "GetUserPreviewApi", targetUserId, null)
        val responseBean = JSON.parseObject(responseData, UserPreviewData::class.java)

        if (responseBean.isLogin == 1) {
            Bot.sendPrivateMessage(
                content.user_id,
                msgBuilder.reply(content.message_id).append("在尝试登陆时出现异常。因为用户已经登录/位于小黑屋。").build()
            )
            return
        }

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
            msgBuilder.reply(content.message_id).append("登录成功。出于一些原因，需要等待60s再拉取数据。").build()
        )
        delay(60_000)
        val userDataAllRequest = UserDataRequest(targetUserId)
        val userDataAllResponseData =
            MaimaiApiClient.call(userDataAllRequest.toJson(), "GetUserDataApi", targetUserId, cookie)
        val userDataResponseBean = JSON.parseObject(userDataAllResponseData, UserAllData::class.java)
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


        val baseImage = ImageIO.read(
            CommandWhoami::class.java.getResourceAsStream("/base.png")
        )
        val iconImage = ResourceHelper.iconImage(responseBean.iconId.toString())
        val outFile = File(ResourceHelper.dataCacheFolder, content.sender.user_id.toString() + "-whoami.png")

        //use awt to draw image.
        val g2d = baseImage.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        }

        g2d.drawImage(iconImage, 45, 363, 480, 480, null)

        g2d.color = Color.black
        g2d.font = Font("Sans-serif", 0, 48)
        g2d.drawString(userDataResponseBean.userData.userName, 699, 380)
        g2d.drawString(userDataResponseBean.userData.firstPlayDate, 800, 560)
        g2d.drawString(calculateIdNumber(userDataResponseBean).toString(), 550, 710)
        g2d.dispose()
        withContext(Dispatchers.IO) {
            ImageIO.write(
                baseImage,
                "png",
                outFile
            )
        }

        //send to user
        Bot.sendGroupMessage(
            callbackData.sourceGroupId,
            msgBuilder.reply(callbackData.originMsgId).image(outFile).build()
        )
    }


    fun pad2(num: Int): String = num.toString().padStart(2, '0')

    fun calculateIdNumber(userDataBean: UserAllData): Long {
        val regionName = userDataBean.userData.lastRegionName

        val provinceCode = when (regionName) {
            "北京" -> "11"
            "天津" -> "12"
            "河北" -> "13"
            "山西" -> "14"
            "内蒙古" -> "15"

            "辽宁" -> "21"
            "吉林" -> "22"
            "黑龙江" -> "23"

            "上海" -> "31"
            "江苏" -> "32"
            "浙江" -> "33"
            "安徽" -> "34"
            "福建" -> "35"
            "江西" -> "36"
            "山东" -> "37"

            "河南" -> "41"
            "湖北" -> "42"
            "湖南" -> "43"
            "广东" -> "44"
            "广西" -> "45"
            "海南" -> "46"

            "重庆" -> "50"
            "四川" -> "51"
            "贵州" -> "52"
            "云南" -> "53"
            "西藏" -> "54"

            "陕西" -> "61"
            "甘肃" -> "62"
            "青海" -> "63"
            "宁夏" -> "64"
            "新疆" -> "65"

            "台湾" -> "71"
            "香港" -> "81"
            "澳门" -> "82"

            else -> "00"
        }

        val id = StringBuilder()
        id.append(provinceCode)
        id.append(pad2(Random(userDataBean.userId).nextInt(0, 15))) // 地级市
        id.append(pad2(Random(userDataBean.userId).nextInt(0, 10))) // 区县
        id.append(userDataBean.userData.firstPlayDate.take(10).replace("-", ""))
        id.append(userDataBean.userData.playerRating.toString().padStart(4, '0').takeLast(4))

        return id.toString().toLong()
    }
}