package dev.naominet.empurple.utils

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

object QQUserManager {
    private val bindings = ConcurrentHashMap<Long, Long>()
    private val userNames = ConcurrentHashMap<Long, String>()
    private val iconIds = ConcurrentHashMap<Long, Int>()
    private val lock = Any()
    @Volatile private var loaded = false

    fun bind(qqID: Long, maiUID: Long, userName: String? = null, iconId: Int? = null) {
        require(qqID > 0 && maiUID > 0)
        synchronized(lock) {
            loadLocked()
            bindings[qqID] = maiUID
            userName?.trim()?.takeIf { it.isNotEmpty() }?.let { userNames[qqID] = it }
            iconId?.takeIf { it > 0 }?.let { iconIds[qqID] = it }
            saveLocked()
        }
    }

    fun getMaiUID(qqID: Long): Long? { ensureLoaded(); return bindings[qqID] }
    fun getUserName(qqID: Long): String? { ensureLoaded(); return userNames[qqID] }
    fun getIconId(qqID: Long): Int? { ensureLoaded(); return iconIds[qqID] }
    fun getQQ(maiUID: Long): Long? { ensureLoaded(); return bindings.entries.firstOrNull { it.value == maiUID }?.key }
    fun getAll(): Map<Long, Long> { ensureLoaded(); return bindings.toMap() }

    fun unbind(qqID: Long): Boolean = synchronized(lock) {
        loadLocked()
        val removed = bindings.remove(qqID) != null
        userNames.remove(qqID)
        iconIds.remove(qqID)
        if (removed) saveLocked()
        removed
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) { loadLocked() }
    }

    private fun loadLocked() {
        if (loaded) return
        val file = storageFile()
        if (file.isFile) runCatching {
            file.readLines().forEach { line ->
                val parts = line.substringBefore('#').trim().split('=', limit = 4)
                if (parts.size < 2) return@forEach
                val qq = parts[0].toLongOrNull() ?: return@forEach
                val mai = parts[1].toLongOrNull() ?: return@forEach
                if (qq <= 0 || mai <= 0) return@forEach
                bindings[qq] = mai
                parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let {
                    runCatching { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }
                        .getOrNull()?.takeIf { name -> name.isNotBlank() }?.let { name -> userNames[qq] = name }
                }
                parts.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 }?.let { iconIds[qq] = it }
            }
        }.onFailure { System.err.println("QQUserManager load failed: ${it.message}") }
        loaded = true
    }

    private fun saveLocked() {
        val target = storageFile().absoluteFile
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(bindings.entries.joinToString("\n") { (qq, mai) ->
            val name = userNames[qq]?.let { Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8)) }
            val icon = iconIds[qq]
            when {
                name != null && icon != null -> "$qq=$mai=$name=$icon"
                name != null -> "$qq=$mai=$name"
                icon != null -> "$qq=$mai==$icon"
                else -> "$qq=$mai"
            }
        })
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun storageFile() = File(FileManager.userDataFile)
}
