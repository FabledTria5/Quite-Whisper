package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class CheckRustEngineToolchainTask : DefaultTask() {

    @get:Input
    abstract val macHost: Property<Boolean>

    @get:Input
    abstract val cargoExecutable: Property<String>

    @get:Input
    abstract val toolchainPath: Property<String>

    @TaskAction
    fun checkToolchain() {
        val missingCommands = listOf(
            "cargo" to cargoExecutable.get(),
            "cmake" to "cmake",
            "clang" to "clang"
        )
            .filterNot { (_, command) -> commandExists(command) }
            .map { (displayName, _) -> displayName }

        if (missingCommands.isNotEmpty()) {
            val installHint = if (macHost.get()) {
                "Install Rust from https://rustup.rs/, CMake with `brew install cmake`, " +
                        "and Xcode Command Line Tools with `xcode-select --install`."
            } else {
                "Install Rust from https://rustup.rs/, CMake, and clang for your Linux distribution."
            }
            throw GradleException(
                "Rust engine toolchain is incomplete. Missing command(s): ${missingCommands.joinToString()}. " +
                        installHint,
            )
        }
    }

    private fun commandExists(command: String): Boolean =
        try {
            ProcessBuilder("sh", "-c", "command -v $command")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .apply { environment()["PATH"] = toolchainPath.get() }
                .start()
                .waitFor() == 0
        } catch (_: Exception) {
            false
        }
}