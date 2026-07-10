package dev.naominet.empurple.utils

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64
import javax.imageio.ImageIO

object ResourceHelper {
    private lateinit var resourceFolder: File
    private lateinit var iconsCacheFolder: File
    lateinit var coverCacheFolder: File
        private set
    lateinit var dataCacheFolder: File
        private set


    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        followRedirects = true
    }

    fun initialize() {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        resourceFolder = File(workingDir, "resource")

        if (resourceFolder.exists()) {
            if(!resourceFolder.isDirectory)
                throw RuntimeException("Resource folder:${resourceFolder.path} must be a directory.")

        } else {
            resourceFolder.mkdir()
        }

        iconsCacheFolder = File(resourceFolder, "icons")

        if (iconsCacheFolder.exists()) {
            if(!iconsCacheFolder.isDirectory)
                throw RuntimeException("Resource folder:${iconsCacheFolder.path} must be a directory.")

        } else {
            iconsCacheFolder.mkdir()
        }

        coverCacheFolder = File(resourceFolder, "CoverCache")

        if (coverCacheFolder.exists()) {
            if(!coverCacheFolder.isDirectory)
                throw RuntimeException("Resource folder:${coverCacheFolder.path} must be a directory.")

        } else {
            coverCacheFolder.mkdir()
        }

        dataCacheFolder = File(resourceFolder, "DataCache")

        if (dataCacheFolder.exists()) {
            if(!dataCacheFolder.isDirectory)
                throw RuntimeException("Resource folder:${coverCacheFolder.path} must be a directory.")

        } else {
            dataCacheFolder.mkdir()
        }
    }



    fun iconImagePath(iconId: String): String?{
        val cacheFile = File(iconsCacheFolder, "$iconId.png")

        // 1. try to get image from local cache
        if (cacheFile.exists()) {
            try {
                if (cacheFile.exists()) return cacheFile.path
            } catch (_: IOException) {
                // corrupted cache file, fall through to re-download
            }
        }

        // 2. download from lxns server and save to cache
        val bytes = downloadBytes(
            "https://assets2.lxns.net/maimai/icon/$iconId.png",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
        ) ?: return null

        return try {
            Files.write(cacheFile.toPath(), bytes)
            cacheFile.path
        } catch (_: IOException) {
            null
        }
    }


    fun iconImage(iconId: String): BufferedImage? {
        val cacheFile = File(iconsCacheFolder, "$iconId.png")

        // 1. try to get image from local cache
        if (cacheFile.exists()) {
            try {
                val cached = ImageIO.read(cacheFile)
                if (cached != null) return cached
            } catch (_: IOException) {
                // corrupted cache file, fall through to re-download
            }
        }

        // 2. download from lxns server and save to cache
        val bytes = downloadBytes("https://assets2.lxns.net/maimai/icon/$iconId.png") ?: return null

        return try {
            Files.write(cacheFile.toPath(), bytes)
            ImageIO.read(cacheFile)
        } catch (_: IOException) {
            null
        }
    }

    fun iconImageBase64(iconId: String): String? {
        val cacheFile = File(iconsCacheFolder, "$iconId.png")

        // 1. try to get image from local cache
        if (cacheFile.exists()) {
            try {
                val bytes = Files.readAllBytes(cacheFile.toPath())
                return Base64.getEncoder().encodeToString(bytes)
            } catch (_: IOException) {
                // corrupted cache file, fall through to re-download
            }
        }

        // 2. download from lxns server and save to cache
        val bytes = downloadBytes("https://assets2.lxns.net/maimai/icon/$iconId.png") ?: return null

        return try {
            Files.write(cacheFile.toPath(), bytes)
            Base64.getEncoder().encodeToString(bytes)
        } catch (_: IOException) {
            null
        }
    }

    fun coverImage(coverId: String): BufferedImage? {
        val cacheFile = File(coverCacheFolder, "$coverId.png")

        // 1. try to get image from local cache
        if (cacheFile.exists()) {
            try {
                val image = ImageIO.read(cacheFile)
                if (image != null) {
                    return image
                }
                // corrupted or unreadable image, fall through to re-download
            } catch (_: IOException) {
                // corrupted cache file, fall through to re-download
            }
        }

        // 2. download from lxns server and save to cache
        val id: Int = if(coverId.toInt() > 10000)
            coverId.toInt() - 10000
        else
            coverId.toInt()
        val bytes = downloadBytes("https://assets2.lxns.net/maimai/jacket/$id.png") ?: return null

        return try {
            // Save to cache
            Files.write(cacheFile.toPath(), bytes)
            // Decode and return the image
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (_: IOException) {
            null
        }
    }

    private fun downloadBytes(url: String, userAgent: String = "KanadeBot/1.0"): ByteArray? = runBlocking {
        try {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, userAgent)
            }

            if (!response.status.isSuccess()) {
                return@runBlocking null
            }

            response.body<ByteArray>().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
