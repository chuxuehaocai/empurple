package dev.naominet.empurple.script

import dev.naominet.empurple.callback.CallbackManager
import dev.naominet.empurple.command.CommandManager
import dev.naominet.purple.framework.beans.TextMessageBean
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import java.time.Duration
import java.util.UUID

object ScriptService {
    private const val maxSourceBytes = 64 * 1024
    private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")

    suspend fun initialize() {
        ScriptRepository.initialize()
        UserScriptRegistry.clear()
        restoreInstallations()
    }

    suspend fun create(nameInput: String, ownerId: Long, groupId: Long, messageId: Long): ScriptRecord {
        val name = normalizeName(nameInput)
        require(!CommandManager.isBuiltIn(name)) { "Command name '$name' is reserved" }
        val record = ScriptRepository.create(name, ownerId)
        val registered = CallbackManager.add(
            ownerId,
            "script-create",
            Duration.ofMinutes(10)
        ) { message -> receiveCreateCode(record, message) }
        require(registered) { "You already have a pending private-message interaction" }
        replyGroup(groupId, messageId, "脚本 UUID: ${record.id}\n请在 10 分钟内私聊发送完整 Lua 源码。")
        return record
    }

    suspend fun publish(id: UUID, ownerId: Long): ScriptRecord {
        val current = requireScript(id)
        require(current.ownerId == ownerId) { "Only the original author can publish this script" }
        compile(current, current.draftCode)
        val published = ScriptRepository.publish(id, ownerId)
        if (published.publishedCode != null) {
            val runtime = runtime(published)
            UserScriptRegistry.replaceEveryInstallation(runtime)
        }
        return published
    }

    suspend fun add(id: UUID, userId: Long): ScriptRecord {
        val script = requireScript(id)
        require(script.published && script.publishedCode != null) { "Script is not published" }
        require(!CommandManager.isBuiltIn(script.name)) { "Command name '${script.name}' is reserved" }
        val runtime = runtime(script)
        UserScriptRegistry.install(userId, runtime)
        ScriptRepository.install(userId, script)
        return script
    }

    suspend fun beginEdit(id: UUID, ownerId: Long, groupId: Long, messageId: Long) {
        val current = requireScript(id)
        require(current.ownerId == ownerId) { "Only the original author can edit this script" }
        val registered = CallbackManager.add(
            ownerId,
            "script-edit",
            Duration.ofMinutes(10)
        ) { message -> receiveEditCode(id, ownerId, current.version, message) }
        require(registered) { "You already have a pending private-message interaction" }
        replyGroup(groupId, messageId, "请在 10 分钟内私聊发送脚本 $id 的完整新源码。")
    }

    private suspend fun receiveCreateCode(record: ScriptRecord, message: TextMessageBean) {
        try {
            val code = validateSource(message.raw_message)
            compile(record, code)
            ScriptRepository.updateDraft(record.uuid, record.ownerId, code, record.version)
            replyPrivate(record.ownerId, "Lua 草稿保存成功。UUID: ${record.id}\n使用 /script publish ${record.id} 发布。")
        } catch (error: Exception) {
            replyPrivate(record.ownerId, "Lua 草稿保存失败: ${error.message}")
        }
    }

    private suspend fun receiveEditCode(id: UUID, ownerId: Long, expectedVersion: Int, message: TextMessageBean) {
        try {
            val code = validateSource(message.raw_message)
            val current = requireScript(id)
            compile(current, code)
            val updated = ScriptRepository.edit(id, ownerId, code, expectedVersion)
            if (updated.published) UserScriptRegistry.replaceEveryInstallation(runtime(updated))
            replyPrivate(ownerId, "Lua 脚本更新成功。UUID: $id，版本: ${updated.version}")
        } catch (error: Exception) {
            replyPrivate(ownerId, "Lua 脚本更新失败，旧版本保持不变: ${error.message}")
        }
    }

    private suspend fun restoreInstallations() {
        val scripts = ScriptRepository.scripts().associateBy { it.id }
        ScriptRepository.installations().forEach { installation ->
            val script = scripts[installation.scriptId] ?: return@forEach
            if (!script.published || script.publishedCode == null || CommandManager.isBuiltIn(script.name)) return@forEach
            try {
                UserScriptRegistry.install(installation.userId, runtime(script))
            } catch (error: Exception) {
                System.err.println("Failed to restore script '${script.id}' for ${installation.userId}: ${error.message}")
            }
        }
    }

    private fun runtime(script: ScriptRecord): InstalledScriptRuntime {
        val code = requireNotNull(script.publishedCode) { "Script is not published" }
        return InstalledScriptRuntime(script.uuid, script.version, compile(script, code))
    }

    private fun compile(script: ScriptRecord, code: String): ScriptCommand =
        ScriptCommandLoader.loadSource(code, "user-script:${script.id}", script.name)

    private suspend fun requireScript(id: UUID): ScriptRecord =
        requireNotNull(ScriptRepository.get(id)) { "Script does not exist" }

    private fun normalizeName(input: String): String {
        val name = input.trim().lowercase()
        require(namePattern.matches(name)) { "Name must match ${namePattern.pattern}" }
        return name
    }

    private fun validateSource(source: String): String {
        require(source.isNotBlank()) { "Lua source cannot be blank" }
        require(source.toByteArray().size <= maxSourceBytes) { "Lua source exceeds 64 KiB" }
        return source
    }

    private fun replyGroup(groupId: Long, messageId: Long, message: String) {
        Bot.sendGroupMessage(groupId, MessageBuilder().replyGroup(messageId, message).build())
    }

    private fun replyPrivate(userId: Long, message: String) {
        Bot.sendPrivateMessage(userId, message)
    }
}
