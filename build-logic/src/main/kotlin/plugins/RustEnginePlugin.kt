package plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import tasks.CheckRustEngineToolchainTask
import tasks.MarkBundledExecutablesTask
import tasks.MarkExecutableTask
import java.io.File

@Suppress("unused")
class RustEnginePlugin : Plugin<Project> {

    override fun apply(project: Project) = with(receiver = project) {
        val isWindowsHost =
            System.getProperty("os.name").contains(other = "Windows", ignoreCase = true)

        val isMacHost =
            System.getProperty("os.name").contains(other = "Mac", ignoreCase = true)

        val rustSidecarBinaryNames = listOf("quite-whisper-engine", "hotkey-helper")

        val rustSidecarExecutableNames = rustSidecarBinaryNames.map { binaryName ->
            if (isWindowsHost) "$binaryName.exe" else binaryName
        }

        val engineResourcesRoot = layout.buildDirectory.dir("engineResources")
        val engineResourcesOsDir = when {
            isWindowsHost -> "windows"
            isMacHost -> "macos"
            else -> "linux"
        }
        val rustEngineToolchainPath = listOf(
            "${System.getProperty("user.home")}/.cargo/bin",
            "/opt/homebrew/bin",
            "/usr/local/bin",
            System.getenv("PATH").orEmpty(),
        ).filter(predicate = String::isNotBlank).joinToString(separator = ":")

        val rustEngineCargoExecutable = if (isWindowsHost) {
            "cargo"
        } else {
            File("${System.getProperty("user.home")}/.cargo/bin/cargo")
                .takeIf { it.exists() }
                ?.absolutePath
                ?: "cargo"
        }

        fun Exec.configureRustEngineBuild(release: Boolean) {
            workingDir = rootProject.projectDir
            val cargoArgs = buildList {
                add(rustEngineCargoExecutable)
                add("build")
                add("--manifest-path")
                add("engine/Cargo.toml")
                if (release) add("--release")
                rustSidecarBinaryNames.forEach { binaryName ->
                    add("--bin")
                    add(binaryName)
                }
            }
            if (isWindowsHost) {
                commandLine(
                    "powershell.exe",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    rootProject.layout.projectDirectory.file("scripts/windows-dev.ps1").asFile.absolutePath,
                    *cargoArgs.toTypedArray(),
                )
            } else {
                environment("PATH", rustEngineToolchainPath)
                commandLine(cargoArgs)
            }
        }

        val checkRustEngineToolchain = tasks.register(
            "checkRustEngineToolchain",
            CheckRustEngineToolchainTask::class.java,
        ) {
            enabled = !isWindowsHost
            macHost.set(isMacHost)
            cargoExecutable.set(rustEngineCargoExecutable)
            toolchainPath.set(rustEngineToolchainPath)
        }

        val buildRustEngineRelease = tasks.register(
            "buildRustEngineRelease",
            Exec::class.java
        ) {
            dependsOn(checkRustEngineToolchain)
            configureRustEngineBuild(release = true)
        }

        val syncRustEngineRelease = tasks.register(
            "syncRustEngineRelease",
            Copy::class.java
        ) {
            dependsOn(buildRustEngineRelease)
            from(rustSidecarExecutableNames.map {
                rootProject.layout.projectDirectory.file("engine/target/release/$it")
            })
            into(engineResourcesRoot.map { it.dir(engineResourcesOsDir) })
        }

        val markRustEngineExecutable = tasks.register(
            "markRustEngineExecutable",
            MarkExecutableTask::class.java,
        ) {
            enabled = !isWindowsHost
            dependsOn(syncRustEngineRelease)
            directory.set(layout.buildDirectory.dir("engineResources/$engineResourcesOsDir"))
            executableNames.set(rustSidecarExecutableNames)
        }

        val markBundledRustEngineExecutables = tasks.register(
            "markBundledRustEngineExecutables",
            MarkBundledExecutablesTask::class.java,
        ) {
            enabled = !isWindowsHost
            binariesDirectory.set(layout.buildDirectory.dir("compose/binaries"))
            executableNames.set(rustSidecarExecutableNames)
        }

        val rustEngineConsumerTaskNames = setOf(
            "prepareAppResources",
            "run",
            "jvmRun",
            "hotRunJvm",
            "hotRunJvmAsync",
            "hotDevJvm",
            "hotDevJvmAsync",
            "runDistributable",
            "runRelease",
            "runReleaseDistributable",
            "packageDistributionForCurrentOS",
            "packageReleaseDistributionForCurrentOS",
        )

        tasks.matching { it.name in rustEngineConsumerTaskNames }.configureEach {
            dependsOn(if (isWindowsHost) syncRustEngineRelease else markRustEngineExecutable)
        }

        tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }
            .configureEach {
                finalizedBy(markBundledRustEngineExecutables)
            }

        tasks.matching {
            it.name == "packageDmg" ||
                    it.name == "packageReleaseDmg" ||
                    it.name == "packageDistributionForCurrentOS" ||
                    it.name == "packageReleaseDistributionForCurrentOS"
        }.configureEach {
            dependsOn(markBundledRustEngineExecutables)
        }

        markBundledRustEngineExecutables.configure {
            mustRunAfter(tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" })
        }
    }
}
