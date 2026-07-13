package dev.naominet.empurple.script

import java.util.UUID

data class ScriptRecord(
    val id: String,
    val name: String,
    val ownerId: Long,
    val draftCode: String = "",
    val publishedCode: String? = null,
    val version: Int = 0,
    val published: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val uuid: UUID get() = UUID.fromString(id)
}

data class ScriptInstallation(
    val userId: Long,
    val scriptId: String,
    val version: Int,
    val installedAt: Long = System.currentTimeMillis()
)

data class ScriptStore(
    val scripts: MutableList<ScriptRecord> = mutableListOf(),
    val installations: MutableList<ScriptInstallation> = mutableListOf()
)
