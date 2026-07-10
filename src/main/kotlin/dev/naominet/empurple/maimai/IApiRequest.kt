package dev.naominet.empurple.maimai

import com.alibaba.fastjson2.JSON

interface IApiRequest {
    fun toJson(): String = JSON.toJSONString(this)
}
