package dev.naominet.empurple.maimai.request

import com.alibaba.fastjson2.annotation.JSONField
import dev.naominet.empurple.maimai.IApiRequest

data class UserLoginRequest(
    @JSONField(name = "userId")
    val userId: Long,
    @JSONField(name = "accessCode")
    val accessCode: String = "",
    @JSONField(name = "regionId")
    val regionId: Int,
    @JSONField(name = "placeId")
    val placeId: Int,
    @JSONField(name = "clientId")
    val clientId: String,
    @JSONField(name = "dateTime")
    val dateTime: Long,
    @JSONField(name = "loginDateTime")
    val loginDateTime: Long,
    @JSONField(name = "isContinue")
    val isContinue: Boolean = false,
    @JSONField(name = "genericFlag")
    val genericFlag: Int = 0,
    @JSONField(name = "token")
    val token: String
) : IApiRequest
