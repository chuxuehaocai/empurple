package dev.naominet.empurple.maimai

import dev.naominet.empurple.EmpurplePlugin
import dev.naominet.empurple.utils.CipherAES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.cuteyuki.kanadebot.utils.HttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 舞萌 DX 称号服 API 客户端。
 *
 * 封装称号服的 AES + Zlib 加密通信协议，提供挂起版 API 供协程调用。
 */
object MaimaiApiClient {

    data class Response(
        val body: String,
        /** Cookie 请求头值（如 "JSESSIONID=abc123"），仅在登录请求时返回。 */
        val cookieHeader: String?
    )

    private val titleServerUri: String
        get() = EmpurplePlugin.config.titleServerUrl

    private val OBFUSCATE_PARAM: String
        get() = EmpurplePlugin.config.obfuscateParam

    private val API_VERSION: String
        get() = EmpurplePlugin.config.apiVersion

    // ---- 公开 API ----

    /** 调用称号服 API 并返回响应体。 */
    @Throws(Exception::class)
    suspend fun call(
        data: String, useApi: String?, userId: Long,
        cookie: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            execute(data, useApi, userId, cookie).body
        }
    }

    /** 调用称号服 API 并返回完整响应（含 cookie header），用于登录请求。 */
    @Throws(Exception::class)
    suspend fun callWithCookie(
        data: String, useApi: String?, userId: Long
    ): Response {
        return withContext(Dispatchers.IO) {
            execute(data, useApi, userId, cookie = null, captureCookie = true)
        }
    }

    // ---- 内部实现 ----

    private fun execute(
        data: String, useApi: String?, userId: Long,
        cookie: String? = null,
        captureCookie: Boolean = false
    ): Response {
        val api: String = useApi!!
        val hashApi = obfuscateApi(api)

        val plainBytes = data.toByteArray(StandardCharsets.UTF_8)
        val compressed = zlibCompress(plainBytes)
        val encrypted: ByteArray = CipherAES.encrypt(compressed)

        val uaSuffix: String = if (userId != 0L) userId.toString() else EmpurplePlugin.config.clientId
        val headers: MutableMap<String?, String?> = LinkedHashMap()
        headers["Content-Type"] = "application/json"
        headers["User-Agent"] = "$hashApi#$uaSuffix"
        headers["charset"] = "UTF-8"
        headers["Mai-Encoding"] = API_VERSION
        if (encrypted.isNotEmpty()) {
            headers["Content-Encoding"] = "deflate"
        }
        headers["number"] = "0"
        if (!cookie.isNullOrBlank()) {
            headers["Cookie"] = cookie
        }

        val url = titleServerUri + "/" + hashApi

        val maxRetries = 2
        var lastException: Exception? = null

        for (attempt in 0..<maxRetries) {
            try {
                val httpResult: HttpClient.HttpResult = HttpClient.post(url, headers, encrypted, 15.0)

                if (httpResult.statusCode != 200) {
                    val bodyText =
                        if (httpResult.body != null) String(httpResult.body, StandardCharsets.UTF_8) else ""
                    lastException = Exception("Response error: " + httpResult.statusCode + "\n" + bodyText)
                    if (attempt < maxRetries - 1) {
                        Thread.sleep(1000)
                        continue
                    }
                    throw lastException
                }

                val respBytes: ByteArray? = httpResult.body
                if (respBytes == null || respBytes.isEmpty()) {
                    lastException = Exception("Empty response body")
                    if (attempt < maxRetries - 1) {
                        Thread.sleep(1000)
                        continue
                    }
                    throw lastException
                }

                val decrypted: ByteArray?
                try {
                    decrypted = CipherAES.decrypt(respBytes)
                } catch (e: Exception) {
                    throw Exception("AES decrypt failed: " + e.message, e)
                }

                val decompressed: ByteArray?
                try {
                    decompressed = zlibDecompress(decrypted)
                } catch (e: Exception) {
                    throw Exception("Zlib decompression failed: " + e.message, e)
                }

                val body = String(decompressed, StandardCharsets.UTF_8)
                val cookieHeader = if (captureCookie) httpResult.cookieHeader() else null
                return Response(body, cookieHeader)

            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Thread.sleep(2000)
                    continue
                }
            } catch (e: UnknownHostException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Thread.sleep(2000)
                    continue
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Thread.sleep(1000)
                    continue
                }
            }
        }

        throw if (lastException != null) lastException else Exception("API call failed after retries: $useApi")
    }

    private fun obfuscateApi(api: String?): String {
        try {
            val combined = api + "MaimaiChn" + OBFUSCATE_PARAM
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(combined.toByteArray(StandardCharsets.UTF_8))
            val sb = StringBuilder()
            for (b in digest) sb.append(String.format("%02x", b.toInt() and 0xFF))
            return sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("MD5 algorithm not available", e)
        }
    }

    @Throws(IOException::class)
    private fun zlibCompress(input: ByteArray?): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(1024)
        val chunks = ArrayList<ByteArray>()
        var totalLen = 0
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            chunks.add(buffer.copyOf(count))
            totalLen += count
        }
        val output = ByteArray(totalLen)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, output, offset, chunk.size)
            offset += chunk.size
        }
        return output
    }

    @Throws(IOException::class)
    private fun zlibDecompress(input: ByteArray?): ByteArray {
        val inflater = Inflater(false)
        inflater.setInput(input)
        val buffer = ByteArray(1024)
        val chunks = ArrayList<ByteArray>()
        var totalLen = 0
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                chunks.add(buffer.copyOf(count))
                totalLen += count
            }
        } catch (e: Exception) {
            throw IOException("ZLIB 解压失败", e)
        }
        val output = ByteArray(totalLen)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, output, offset, chunk.size)
            offset += chunk.size
        }
        return output
    }
}
