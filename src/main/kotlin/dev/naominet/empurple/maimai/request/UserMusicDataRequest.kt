package dev.naominet.empurple.maimai.request

import com.alibaba.fastjson2.annotation.JSONField
import dev.naominet.empurple.maimai.IApiRequest

data class UserMusicDataRequest(
    @JSONField(name = "userId")
    val userId: Long,
    @JSONField(name = "nextIndex")
    val nextIndex: Long = 0,
    @JSONField(name = "maxCount")
    val maxCount: Int = 50,
) : IApiRequest
