package dev.naominet.empurple.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object MusicDataProvider {
    private const val musicDataUrl = "https://www.diving-fish.com/api/maimaidxprober/music_data"
    private val client = HttpClient(CIO)
    private val dsCache = ConcurrentHashMap<Int, Map<Int, Double>>()
    private val titleCache = ConcurrentHashMap<Int, String>()
    @Volatile private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val cacheFile = File(ResourceHelper.dataCacheFolder, "music_data.json")
            val content = runCatching {
                if (cacheFile.exists()) cacheFile.readText() else download().also { cacheFile.writeText(it) }
            }.getOrElse {
                System.err.println("Unable to load B50 music metadata: ${it.message}")
                "[]"
            }
            runCatching { parse(JSON.parseArray(content)) }
                .onFailure { System.err.println("Unable to parse B50 music metadata: ${it.message}") }
            loaded = true
        }
    }

    fun getDs(musicId: Int, level: Int): Double {
        load()
        return dsCache[musicId]?.get(level) ?: 0.0
    }

    fun getTitle(musicId: Int): String {
        load()
        return titleCache[musicId] ?: "Unknown"
    }

    private fun download(): String = runBlocking {
        val response = client.get(musicDataUrl)
        require(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        response.body<String>()
    }

    private fun parse(array: JSONArray) {
        dsCache.clear()
        titleCache.clear()
        for (index in 0 until array.size) {
            val item = array.getJSONObject(index)
            val id = item.getIntValue("id")
            titleCache[id] = item.getString("title") ?: "Unknown"
            val ds = item.getJSONArray("ds") ?: continue
            dsCache[id] = (0 until ds.size).associateWith { ds.getDoubleValue(it) }
        }
    }
}
