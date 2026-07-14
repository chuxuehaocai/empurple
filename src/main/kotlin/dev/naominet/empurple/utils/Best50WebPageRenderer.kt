package dev.naominet.empurple.utils

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.UUID

object Best50WebPageRenderer {
    data class Result(
        val id: String,
        val file: File,
        val relativePath: String
    )

    fun render(cacheJson: JSONObject): Result {
        MusicDataProvider.load()
        val id = UUID.randomUUID().toString()
        val outputFolder = File(basicWebFolder(), id)
        require(outputFolder.mkdirs() || outputFolder.isDirectory) { "Unable to create B50 web folder: ${outputFolder.path}" }
        val outputFile = File(outputFolder, "index.html")
        outputFile.writeText(buildHtml(cacheJson), StandardCharsets.UTF_8)
        return Result(id, outputFile, "$id/index.html")
    }

    private fun basicWebFolder(): File = File(Paths.get("").toAbsolutePath().toFile(), "basicWeb").apply {
        require(mkdirs() || isDirectory) { "Unable to create basicWeb folder: $path" }
    }

    private fun buildHtml(cacheJson: JSONObject): String {
        val title = escapeHtml(cacheJson.getString("userName") ?: "Unknown")
        val originJson = jsString(cacheJson.toJSONString())
        val musicData = jsString(collectMusicData(cacheJson).toJSONString())
        val imageData = jsString(collectImageData(cacheJson).toJSONString())
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>$title 的 b50</title>
              <link rel="stylesheet" href="../styles.css">
            </head>
            <body class="embedded">
              <main class="shell">
                <header class="hero">
                  <div>
                    <p class="eyebrow">maimai DX</p>
                    <h1>Best 50</h1>
                    <p class="subtitle">$title 的 b50。</p>
                  </div>
                  <button id="themeButton" class="ghost-button" type="button" aria-label="切换主题">🌙</button>
                </header>

                <section class="panel input-panel">
                  <div class="input-actions">
                    <label class="file-button">
                      <input id="fileInput" type="file" accept=".json,application/json,text/json">
                      上传 JSON
                    </label>
                    <button id="parseButton" type="button">解析</button>
                    <button id="clearButton" class="secondary" type="button">清空</button>
                    <button id="sampleButton" class="secondary" type="button">填入示例</button>
                  </div>
                  <textarea id="jsonInput" spellcheck="false" placeholder="在这里粘贴 Best 50 JSON。"></textarea>
                  <p id="status" class="status" role="status">正在读取内嵌 JSON。</p>
                </section>

                <section id="result" class="result hidden" aria-live="polite">
                  <section class="panel profile-card">
                    <div class="avatar" id="avatar">
                      <img id="avatarImage" alt="用户头像">
                      <span id="avatarFallback">♪</span>
                    </div>
                    <div class="profile-main">
                      <p class="eyebrow">Player</p>
                      <h2 id="playerName">Unknown</h2>
                      <p id="playerMeta">Rating: -</p>
                    </div>
                    <div class="score-grid">
                      <div class="score-tile">
                        <span>B35</span>
                        <strong id="b35Score">0</strong>
                      </div>
                      <div class="score-tile">
                        <span>B15</span>
                        <strong id="b15Score">0</strong>
                      </div>
                      <div class="score-tile total">
                        <span>Total</span>
                        <strong id="totalScore">0</strong>
                      </div>
                    </div>
                  </section>

                  <section class="toolbar panel">
                    <label>
                      搜索
                      <input id="searchInput" type="search" placeholder="歌曲名 / ID / 等级">
                    </label>
                    <label>
                      排序
                      <select id="sortSelect">
                        <option value="source">原始顺序</option>
                        <option value="ra">RA 高到低</option>
                        <option value="achievement">达成率高到低</option>
                        <option value="ds">定数高到低</option>
                        <option value="title">标题 A-Z</option>
                      </select>
                    </label>
                  </section>

                  <section class="section-block">
                    <div class="section-title">
                      <h2>Best 35</h2>
                      <span id="b35Count">0 首</span>
                    </div>
                    <div id="b35List" class="card-grid"></div>
                  </section>

                  <section class="section-block">
                    <div class="section-title">
                      <h2>Best 15</h2>
                      <span id="b15Count">0 首</span>
                    </div>
                    <div id="b15List" class="card-grid"></div>
                  </section>
                </section>
              </main>

              <template id="songCardTemplate">
                <article class="song-card">
                  <img class="song-cover" alt="歌曲曲绘" loading="lazy">
                  <div class="song-rank"></div>
                  <div class="song-body">
                    <div class="song-title-row">
                      <h3></h3>
                      <span class="level-pill"></span>
                    </div>
                    <p class="song-subtitle"></p>
                    <div class="song-stats">
                      <span><b class="achievement"></b> 达成率</span>
                      <span><b class="ds"></b> 定数</span>
                      <span><b class="ra"></b> RA</span>
                    </div>
                    <div class="badges"></div>
                  </div>
                </article>
              </template>

              <script>
                window.__BEST50_JSON__ = JSON.parse($originJson);
                window.__BEST50_MUSIC_DATA__ = JSON.parse($musicData);
                window.__BEST50_IMAGE_DATA__ = JSON.parse($imageData);
                window.__BEST50_AUTO_PARSE__ = true;
              </script>
              <script src="../app.js"></script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun collectMusicData(cacheJson: JSONObject): JSONObject {
        val result = JSONObject()
        val userRating = cacheJson.getJSONObject("userRating") ?: return result
        collectMusicIds(userRating.getJSONArray("ratingList"), result)
        collectMusicIds(userRating.getJSONArray("newRatingList"), result)
        return result
    }

    private fun collectImageData(cacheJson: JSONObject): JSONObject = JSONObject().apply {
        cacheJson.getString("iconId")
            ?.let(ResourceHelper::iconImageBase64)
            ?.let { put("icon", "data:image/png;base64,$it") }
        put("covers", JSONObject().also { covers ->
            val userRating = cacheJson.getJSONObject("userRating") ?: return@also
            collectCoverImages(userRating.getJSONArray("ratingList"), covers)
            collectCoverImages(userRating.getJSONArray("newRatingList"), covers)
        })
    }

    private fun collectCoverImages(array: JSONArray?, result: JSONObject) {
        array?.forEach { raw ->
            val item = raw as? JSONObject ?: return@forEach
            val musicId = item.getIntValue("musicId")
            if (musicId <= 0 || result.containsKey(musicId.toString())) return@forEach
            ResourceHelper.coverImageBase64(musicId.toString())
                ?.let { result.put(musicId.toString(), "data:image/png;base64,$it") }
        }
    }

    private fun collectMusicIds(array: JSONArray?, result: JSONObject) {
        array?.forEach { raw ->
            val item = raw as? JSONObject ?: return@forEach
            val musicId = item.getIntValue("musicId")
            if (musicId <= 0 || result.containsKey(musicId.toString())) return@forEach
            result.put(musicId.toString(), JSONObject().apply {
                put("title", MusicDataProvider.getTitle(musicId))
                put("ds", JSONArray().apply {
                    repeat(5) { level -> add(MusicDataProvider.getDs(musicId, level)) }
                })
            })
        }
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> char.toString()
                }
            )
        }
    }

    private fun jsString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when {
                char == '\\' -> append("\\\\")
                char == '"' -> append("\\\"")
                char == '\b' -> append("\\b")
                char.code == 0x0c -> append("\\f")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char == '<' -> append("\\u003C")
                char == '>' -> append("\\u003E")
                char == '&' -> append("\\u0026")
                char.code == 0x2028 -> append("\\u2028")
                char.code == 0x2029 -> append("\\u2029")
                char.code < 0x20 -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('"')
    }
}
