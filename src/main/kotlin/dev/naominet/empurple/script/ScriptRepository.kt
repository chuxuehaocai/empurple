package dev.naominet.empurple.script

import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

object ScriptRepository {
    private val mutex = Mutex()
    private val directory = File("resource/scripts")
    private val metadataFile = File(directory, "metadata.json")
    private var store = ScriptStore()

    suspend fun initialize() = mutex.withLock {
        if (!directory.exists()) directory.mkdirs()
        store = if (metadataFile.exists()) {
            JSON.parseObject(metadataFile.readText(), ScriptStore::class.java) ?: ScriptStore()
        } else {
            ScriptStore()
        }
    }

    suspend fun create(name: String, ownerId: Long): ScriptRecord = mutex.withLock {
        val record = ScriptRecord(UUID.randomUUID().toString(), name, ownerId)
        store.scripts.add(record)
        save()
        record
    }

    suspend fun get(id: UUID): ScriptRecord? = mutex.withLock {
        store.scripts.firstOrNull { it.id == id.toString() }
    }

    suspend fun updateDraft(id: UUID, ownerId: Long, code: String, expectedVersion: Int? = null): ScriptRecord = mutex.withLock {
        val index = store.scripts.indexOfFirst { it.id == id.toString() }
        require(index >= 0) { "Script does not exist" }
        val current = store.scripts[index]
        require(current.ownerId == ownerId) { "Only the original author can edit this script" }
        if (expectedVersion != null) require(current.version == expectedVersion) { "Script changed while waiting for code" }
        val updated = current.copy(draftCode = code, updatedAt = System.currentTimeMillis())
        store.scripts[index] = updated
        save()
        updated
    }

    suspend fun edit(id: UUID, ownerId: Long, code: String, expectedVersion: Int): ScriptRecord = mutex.withLock {
        val index = store.scripts.indexOfFirst { it.id == id.toString() }
        require(index >= 0) { "Script does not exist" }
        val current = store.scripts[index]
        require(current.ownerId == ownerId) { "Only the original author can edit this script" }
        require(current.version == expectedVersion) { "Script changed while waiting for code" }
        val updated = if (current.published) {
            current.copy(
                draftCode = code,
                publishedCode = code,
                version = current.version + 1,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            current.copy(draftCode = code, updatedAt = System.currentTimeMillis())
        }
        store.scripts[index] = updated
        if (updated.published) {
            store.installations.replaceAll {
                if (it.scriptId == updated.id) it.copy(version = updated.version) else it
            }
        }
        save()
        updated
    }

    suspend fun publish(id: UUID, ownerId: Long): ScriptRecord = mutex.withLock {
        val index = store.scripts.indexOfFirst { it.id == id.toString() }
        require(index >= 0) { "Script does not exist" }
        val current = store.scripts[index]
        require(current.ownerId == ownerId) { "Only the original author can publish this script" }
        require(current.draftCode.isNotBlank()) { "Script has no valid draft" }
        if (current.published && current.publishedCode == current.draftCode) return@withLock current
        val updated = current.copy(
            publishedCode = current.draftCode,
            version = current.version + 1,
            published = true,
            updatedAt = System.currentTimeMillis()
        )
        store.scripts[index] = updated
        store.installations.replaceAll {
            if (it.scriptId == updated.id) it.copy(version = updated.version) else it
        }
        save()
        updated
    }

    suspend fun install(userId: Long, script: ScriptRecord): ScriptInstallation = mutex.withLock {
        val existingIndex = store.installations.indexOfFirst { it.userId == userId && it.scriptId == script.id }
        val installation = ScriptInstallation(userId, script.id, script.version)
        if (existingIndex >= 0) store.installations[existingIndex] = installation else store.installations.add(installation)
        save()
        installation
    }

    suspend fun installations(): List<ScriptInstallation> = mutex.withLock { store.installations.toList() }

    suspend fun scripts(): List<ScriptRecord> = mutex.withLock { store.scripts.toList() }

    private fun save() {
        if (!directory.exists()) directory.mkdirs()
        val temp = File(directory, "metadata.json.tmp")
        temp.writeText(JSON.toJSONString(store))
        try {
            Files.move(
                temp.toPath(), metadataFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(temp.toPath(), metadataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
