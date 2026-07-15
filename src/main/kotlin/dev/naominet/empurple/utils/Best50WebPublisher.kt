package dev.naominet.empurple.utils

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object Best50WebPublisher {
    private val repositoryFolder = File(Paths.get("").toAbsolutePath().toFile(), "basicWeb")

    @Synchronized
    fun publish(webPage: Best50WebPageRenderer.Result) {
        val paths = listOf(
            "index.html",
            "app.js",
            "styles.css",
            "README.md",
            "vercel.json",
            webPage.relativePath
        ).filter { File(repositoryFolder, it).isFile }

        require(paths.isNotEmpty()) { "No B50 web files found to publish" }
        runGit(listOf("add", "--") + paths)

        val hasChanges = runGit(listOf("diff", "--cached", "--quiet"), allowExitCodes = setOf(0, 1)).exitCode == 1
        if (!hasChanges) return

        runGit(listOf("commit", "-m", "Update Best50 web page ${webPage.id}"))
        runGit(listOf("push", "origin", "master"))
    }

    private fun runGit(arguments: List<String>, allowExitCodes: Set<Int> = setOf(0)): CommandResult {
        val command = listOf("git") + arguments
        println("B50 web publisher: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .directory(repositoryFolder)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()
        if (output.isNotBlank()) println("B50 web publisher: ${output.trim()}")
        require(exitCode in allowExitCodes) {
            "git ${arguments.joinToString(" ")} failed ($exitCode): ${output.trim()}"
        }
        return CommandResult(exitCode, output)
    }

    private data class CommandResult(val exitCode: Int, val output: String)
}
