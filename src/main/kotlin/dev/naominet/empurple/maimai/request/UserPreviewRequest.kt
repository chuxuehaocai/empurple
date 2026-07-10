package dev.naominet.empurple.maimai.request

import com.alibaba.fastjson2.annotation.JSONField
import dev.naominet.empurple.maimai.IApiRequest

data class UserPreviewRequest(
    @JSONField(name = "userId")
    val uid: Long,
    @JSONField(name = "segaIdAuthKey")
    val segaIdAuthKey: String = "",
    @JSONField(name = "token")
    val token: String,
    @JSONField(name = "clientId")
    val clientId: String
) : IApiRequest
