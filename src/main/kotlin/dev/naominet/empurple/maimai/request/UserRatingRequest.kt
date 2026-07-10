package dev.naominet.empurple.maimai.request

import com.alibaba.fastjson2.annotation.JSONField
import dev.naominet.empurple.maimai.IApiRequest

data class UserRatingRequest(
    @JSONField(name = "userId")
    val uid: Long
) : IApiRequest
