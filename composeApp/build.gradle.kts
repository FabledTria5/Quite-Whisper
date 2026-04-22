import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

val isWindowsHost = System.getProperty("os.name").contains("Windows", ignoreCase = true)
val isMacHost = System.getProperty("os.name").contains("Mac", ignoreCase = true)

val rustSidecarBinaryNames = listOf("quite-whisper-engine", "hotkey-helper")
fun rustSidecarExecutableName(binaryName: String) = if (isWindowsHost) "$binaryName.exe" else binaryName

val engineExecutableName = rustSidecarExecutableName("quite-whisper-engine")
val rustSidecarExecutableNames = rustSidecarBinaryNames.map(::rustSidecarExecutableName)
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
).filter(String::isNotBlank).joinToString(":")
val rustEngineCargoExecutable = if (isWindowsHost) {
    "cargo"
} else {
    File("${System.getProperty("user.home")}/.cargo/bin/cargo")
        .takeIf { it.exists() }
        ?.absolutePath
        ?: "cargo"
}

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designSystem)
            implementation(projects.core.presentation)

            // Core
            implementation(dependencyNotation =  libs.kotlinx.serialization.json)
            implementation(dependencyNotation = libs.kotlinx.coroutines.core)
            implementation(dependencyNotation = libs.koin.core)
            implementation(dependencyNotation = libs.koin.core.viewmodel)
            implementation(dependencyNotation = libs.koin.compose.viewmodel)

            // Lifecycle
            implementation(dependencyNotation = libs.androidx.lifecycle.viewmodelCompose)
            implementation(dependencyNotation = libs.androidx.lifecycle.runtimeCompose)
            implementation(dependencyNotation =  "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

            // Compose
            implementation(dependencyNotation = libs.compose.runtime)
            implementation(dependencyNotation = libs.compose.foundation)
            implementation(dependencyNotation = libs.compose.material3)
            implementation(dependencyNotation = libs.compose.ui)
            implementation(dependencyNotation = libs.compose.components.resources)
            implementation(dependencyNotation = libs.compose.uiToolingPreview)
            implementation(dependencyNotation = libs.compose.native.tray)
        }

        commonTest.dependencies {
            implementation(dependencyNotation = libs.kotlin.test)
            implementation(dependencyNotation = libs.kotlinx.coroutines.test)
            implementation(dependencyNotation = libs.koin.test)
        }

        jvmMain.dependencies {
            implementation(dependencyNotation = compose.desktop.currentOs)
            implementation(dependencyNotation = libs.kotlinx.coroutines.swing)
        }
    }
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
            "clang" to "clang",
        ).filterNot { (_, command) -> commandExists(command) }
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
                    "$installHint",
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

abstract class MarkExecutableTask : DefaultTask() {
    @get:Internal
    abstract val directory: DirectoryProperty

    @get:Input
    abstract val executableNames: ListProperty<String>

    @TaskAction
    fun markExecutable() {
        executableNames.get().forEach { executableName ->
            directory.get().file(executableName).asFile.setExecutable(true, false)
        }
    }
}

abstract class MarkBundledExecutablesTask : DefaultTask() {
    @get:Internal
    abstract val binariesDirectory: DirectoryProperty

    @get:Input
    abstract val executableNames: ListProperty<String>

    @TaskAction
    fun markExecutables() {
        val names = executableNames.get().toSet()
        binariesDirectory.get().asFile
            .walkTopDown()
            .filter { it.isFile && it.name in names }
            .forEach { it.setExecutable(true, false) }
    }
}

val checkRustEngineToolchain = tasks.register<CheckRustEngineToolchainTask>("checkRustEngineToolchain") {
    enabled = !isWindowsHost
    macHost.set(isMacHost)
    cargoExecutable.set(rustEngineCargoExecutable)
    toolchainPath.set(rustEngineToolchainPath)
}

val buildRustEngineRelease = tasks.register<Exec>("buildRustEngineRelease") {
    dependsOn(checkRustEngineToolchain)
    configureRustEngineBuild(release = true)
}

val syncRustEngineRelease = tasks.register<Copy>("syncRustEngineRelease") {
    dependsOn(buildRustEngineRelease)
    from(rustSidecarExecutableNames.map {
        rootProject.layout.projectDirectory.file("engine/target/release/$it")
    })
    into(engineResourcesRoot.map { it.dir(engineResourcesOsDir) })
}

val markRustEngineExecutable = tasks.register<MarkExecutableTask>("markRustEngineExecutable") {
    enabled = !isWindowsHost
    dependsOn(syncRustEngineRelease)
    directory.set(layout.buildDirectory.dir("engineResources/$engineResourcesOsDir"))
    executableNames.set(rustSidecarExecutableNames)
}

val markBundledRustEngineExecutables = tasks.register<MarkBundledExecutablesTask>("markBundledRustEngineExecutables") {
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

tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
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

compose.desktop {
    application {
        mainClass = "fabled.quitewhisper.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "fabled.quitewhisper.app"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(engineResourcesRoot)
        }
    }
}
