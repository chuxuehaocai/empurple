package dev.naominet.empurple.maimai.request

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.annotation.JSONField
import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.maimai.IApiRequest
import moe.cuteyuki.kanadebot.utils.HttpClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.HashMap

/**
 * Aime QR 码认证请求。
 *
 * 通过 ai.sys-allnet.cn 将 QR token 解析为用户 ID + 登录 token。
 *
 * 参考: reverseMai/config.py qr_api
 */
class QrAuthRequest
@JvmOverloads constructor(
    qrCodeToken: String
) : IApiRequest {

    val chimeSalt: String = EmpurplePlugin.config.aimeSalt

    @JSONField(name = "chipID")
    val chipId: String = EmpurplePlugin.config.keychipId

    @JSONField(name = "openGameID")
    val openGameId: String = "MAID"

    @JSONField(name = "key")
    val key: String

    @JSONField(name = "qrCode")
    val qrCode: String

    @JSONField(name = "timestamp")
    val timestamp: String = LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
        .format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))

    @JSONField(serialize = false)
    private var userIdOrError: Long = -1

    init {
        val rawKey = chipId + timestamp + chimeSalt
        key = sha256(rawKey).uppercase()

        qrCode = if (qrCodeToken.length > 64) {
            qrCodeToken.substring(qrCodeToken.length - 64)
        } else {
            qrCodeToken
        }
    }

    override fun toJson(): String {
        val obj = JSONObject()
        obj["chipID"] = chipId
        obj["openGameID"] = openGameId
        obj["key"] = key
        obj["qrCode"] = qrCode
        obj["timestamp"] = timestamp
        return obj.toJSONString()
    }

    fun execute(): Pair<Long, String> {
        val headers: MutableMap<String?, String?> = HashMap()
        headers["Contention"] = "Keep-Alive"
        headers["Host"] = "ai.sys-allnet.cn"
        headers["User-Agent"] = "WC_AIME_LIB"
        headers["Content-Type"] = "application/json"

        val url = EmpurplePlugin.config.aimeUrl
        val bodyBytes = toJson().toByteArray(StandardCharsets.UTF_8)

        val result = HttpClient.post(url, headers, bodyBytes, 15.0)

        val responseBody = String(result.body ?: ByteArray(0), StandardCharsets.UTF_8)
        val obj = JSON.parseObject(responseBody)

        val errorID = obj.getIntValue("errorID")
        val userID = obj.getLongValue("userID")
        val token = obj.getString("token")

        userIdOrError = if (errorID == 0) userID else errorID.toLong()

        println(responseBody)
        return userIdOrError to token
    }

    companion object {
        private fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            return hexString.toString()
        }
    }
}
