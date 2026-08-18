package dev.naominet.empurple.llm

import com.alibaba.fastjson2.JSONObject

data class LlmTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val execute: suspend (JSONObject) -> String,
)
