package dev.naominet.empurple.web

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.naominet.empurple.config.EmpurpleConfig
import dev.naominet.empurple.utils.ResourceHelper
import dev.naominet.purple.framework.core.PurpleFramework
import dev.naominet.purple.framework.core.plugin.PluginManager
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors

object WebUiPanel {
    private val lock = Any()
    private var server: HttpServer? = null
    private var executor = newExecutor()
    private var token = ""
    private var startedAt = 0L

    fun start(config: EmpurpleConfig) = synchronized(lock) {
        if (server != null) return@synchronized
        validate(config)
        token = config.webUiToken
        startedAt = System.currentTimeMillis()
        if (executor.isShutdown) executor = newExecutor()

        val created = HttpServer.create(InetSocketAddress(config.webUiHost, config.webUiPort), 0)
        created.executor = executor
        created.createContext("/") { exchange -> handle(exchange) }
        created.start()
        server = created
        println("Empurple WebUI: http://${config.webUiHost}:${created.address.port}/")
        if (token.isBlank()) System.err.println("Empurple WebUI: no token configured; access is loopback-only.")
    }

    fun stop() = synchronized(lock) {
        server?.stop(1)
        server = null
        executor.shutdownNow()
    }

    private fun newExecutor() = Executors.newFixedThreadPool(4) { task ->
        Thread(task, "empurple-webui").apply { isDaemon = true }
    }

    private fun validate(config: EmpurpleConfig) {
        require(config.webUiHost.isNotBlank()) { "WebUI host cannot be blank" }
        require(config.webUiPort in 0..65535) { "WebUI port must be between 0 and 65535" }
        val address = InetAddress.getByName(config.webUiHost)
        require(address.isLoopbackAddress || config.webUiToken.isNotBlank()) {
            "WebUI requires a token when binding outside loopback"
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            addSecurityHeaders(exchange)
            if (exchange.requestMethod != "GET") {
                exchange.responseHeaders.set("Allow", "GET")
                sendJson(exchange, 405, jsonOf("error" to "method_not_allowed"))
                return
            }
            if (token.isBlank() && exchange.remoteAddress.address?.isLoopbackAddress != true) {
                sendJson(exchange, 403, jsonOf("error" to "loopback_only"))
                return
            }
            if (!isAuthorized(exchange)) {
                exchange.responseHeaders.set("WWW-Authenticate", "Bearer")
                sendJson(exchange, 401, jsonOf("error" to "unauthorized"))
                return
            }

            when (exchange.requestURI.path) {
                "/" -> sendHtml(exchange, PANEL_HTML)
                "/api/health" -> sendJson(exchange, 200, health())
                "/api/status" -> sendJson(exchange, 200, status())
                "/api/plugins" -> sendJson(exchange, 200, plugins())
                "/api/cache" -> sendJson(exchange, 200, cache())
                else -> sendJson(exchange, 404, jsonOf("error" to "not_found"))
            }
        } catch (error: Exception) {
            runCatching { sendJson(exchange, 500, jsonOf("error" to "internal_error")) }
            System.err.println("Empurple WebUI request failed: ${error.message}")
        } finally {
            exchange.close()
        }
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        if (token.isBlank()) return true
        val authorization = exchange.requestHeaders.getFirst("Authorization")
        val candidate = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?: return false
        return MessageDigest.isEqual(
            candidate.toByteArray(StandardCharsets.UTF_8),
            token.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun health(): JSONObject = jsonOf(
        "status" to "ok",
        "time" to Instant.now().toString(),
        "uptimeMillis" to (System.currentTimeMillis() - startedAt)
    )

    private fun status(): JSONObject = jsonOf(
        "frameworkVersion" to PurpleFramework.version,
        "protocol" to PurpleFramework.configuration.protocol,
        "address" to PurpleFramework.configuration.address,
        "port" to PurpleFramework.configuration.port,
        "path" to PurpleFramework.configuration.path,
        "transportReady" to runCatching { PurpleFramework.transport }.isSuccess,
        "pluginCount" to PluginManager.plugins.size,
        "webUi" to jsonOf("readOnly" to true, "tokenProtected" to token.isNotBlank())
    )

    private fun plugins(): JSONObject = jsonOf(
        "plugins" to PluginManager.plugins.map { jsonOf("id" to it.info.id) }
    )

    private fun cache(): JSONObject {
        val files = ResourceHelper.dataCacheFolder.listFiles()?.filter(File::isFile).orEmpty()
        return jsonOf(
            "fileCount" to files.size,
            "totalBytes" to files.sumOf { it.length() },
            "files" to files.sortedByDescending { it.lastModified() }.take(50).map { file ->
                jsonOf(
                    "name" to file.name,
                    "bytes" to file.length(),
                    "modifiedAt" to Instant.ofEpochMilli(file.lastModified()).toString()
                )
            }
        )
    }

    private fun addSecurityHeaders(exchange: HttpExchange) {
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
        exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.responseHeaders.set("X-Frame-Options", "DENY")
    }

    private fun sendHtml(exchange: HttpExchange, body: String) =
        send(exchange, 200, "text/html; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun sendJson(exchange: HttpExchange, status: Int, body: JSONObject) =
        send(exchange, status, "application/json; charset=utf-8", JSON.toJSONBytes(body))

    private fun send(exchange: HttpExchange, status: Int, contentType: String, body: ByteArray) {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun jsonOf(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value) }
    }

    private val PANEL_HTML = """
        <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Empurple Panel</title>
        <style>
        :root{color-scheme:dark;--ink:#f5f2ff;--muted:#aaa2be;--violet:#a98bff;--lilac:#d9ccff;--panel:#171320;--line:#352d46;--good:#7ee0b5;--bad:#ff8b9d}*{box-sizing:border-box}body{margin:0;background:#0e0b13;color:var(--ink);font-family:"Segoe UI Variable","Microsoft YaHei UI",sans-serif;min-height:100vh}body:before{content:"";position:fixed;inset:0;pointer-events:none;background:repeating-linear-gradient(90deg,transparent 0,transparent calc(25% - 1px),rgba(169,139,255,.06) 25%);mask-image:linear-gradient(to bottom,#000,transparent 70%)}main{width:min(1120px,calc(100% - 32px));margin:auto;padding:54px 0 72px}.mast{display:grid;grid-template-columns:1.5fr 1fr;gap:40px;align-items:end;border-bottom:1px solid var(--line);padding-bottom:28px}.kicker,.label{font:600 11px/1.4 Consolas,monospace;letter-spacing:.18em;text-transform:uppercase;color:var(--violet)}h1{font-size:clamp(50px,9vw,112px);line-height:.82;letter-spacing:-.08em;margin:14px 0 0;font-weight:760}.mast p{color:var(--muted);line-height:1.7;margin:0}.pulse{display:inline-block;width:9px;height:9px;border-radius:50%;background:var(--good);box-shadow:0 0 0 5px rgba(126,224,181,.1);margin-right:9px}.grid{display:grid;grid-template-columns:repeat(12,1fr);gap:16px;margin-top:24px}.card{grid-column:span 4;background:linear-gradient(145deg,rgba(35,28,48,.94),rgba(20,16,29,.94));border:1px solid var(--line);border-radius:4px;padding:22px;min-height:190px}.wide{grid-column:span 8}.value{font:650 34px/1 Consolas,monospace;margin:20px 0 8px}.muted{color:var(--muted)}ul{list-style:none;padding:0;margin:18px 0 0}.row{display:grid;grid-template-columns:1fr auto;gap:16px;padding:11px 0;border-top:1px solid var(--line);align-items:center}.row code{color:var(--lilac)}.badge{font:600 11px Consolas,monospace;padding:5px 8px;border:1px solid var(--line);color:var(--muted)}.error{color:var(--bad)}footer{margin-top:22px;color:var(--muted);font:12px Consolas,monospace}@media(max-width:760px){main{padding-top:32px}.mast{grid-template-columns:1fr}.card,.wide{grid-column:1/-1}h1{font-size:60px}}
        </style></head><body><main><header class="mast"><div><div class="kicker">Local observatory / read only</div><h1>em<br>purple</h1></div><p><span class="pulse"></span><strong id="health">正在连接</strong><br>插件、脚本与缓存的本地观测面板。这里不执行命令，也不修改运行状态。</p></header><section class="grid"><article class="card"><div class="label">Framework</div><div class="value" id="version">—</div><div class="muted" id="transport">等待状态</div></article><article class="card"><div class="label">Plugins</div><div class="value" id="pluginCount">—</div><ul id="plugins"></ul></article><article class="card"><div class="label">Cache</div><div class="value" id="cacheSize">—</div><div class="muted" id="cacheFiles">等待状态</div></article><article class="card"><div class="label">Panel contract</div><div class="value">GET</div><div class="muted">只读接口 · 不缓存 · 默认仅回环访问</div></article></section><footer id="updated">Empurple Panel</footer></main>
        <script>const api=p=>fetch(p).then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.json()}),esc=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),bytes=n=>n<1024?n+' B':n<1048576?(n/1024).toFixed(1)+' KiB':(n/1048576).toFixed(1)+' MiB';Promise.all([api('/api/health'),api('/api/status'),api('/api/plugins'),api('/api/cache')]).then(([h,s,p,c])=>{health.textContent='运行正常 · '+Math.floor(h.uptimeMillis/1000)+'s';version.textContent=s.frameworkVersion;transport.textContent=s.protocol+' · '+s.address+':'+s.port+s.path;pluginCount.textContent=p.plugins.length;plugins.innerHTML=p.plugins.map(v=>'<li class="row"><code>'+esc(v.id)+'</code><span class="badge">loaded</span></li>').join('')||'<li class="muted">没有已加载插件</li>';cacheSize.textContent=bytes(c.totalBytes);cacheFiles.textContent=c.fileCount+' 个数据文件';updated.textContent='最后读取 '+new Date().toLocaleString()}).catch(e=>{health.textContent='读取失败';health.className='error';updated.textContent=e.message+' · 检查访问令牌'})</script></body></html>
    """.trimIndent()
}
