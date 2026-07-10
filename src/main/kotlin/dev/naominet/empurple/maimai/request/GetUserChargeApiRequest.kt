package dev.naominet.empurple.maimai.request

import com.alibaba.fastjson2.annotation.JSONField
import dev.naominet.empurple.maimai.IApiRequest

data class GetUserChargeApiRequest(
    @JSONField(name = "userId")
    val userId: Long
) : IApiRequest
