import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val isWindowsHost = System.getProperty("os.name").contains("Windows", ignoreCase = true)
val isMacHost = System.getProperty("os.name").contains("Mac", ignoreCase = true)

val engineExecutableName = if (isWindowsHost) "quite-whisper-engine.exe" else "quite-whisper-engine"
val engineResourcesRoot = layout.buildDirectory.dir("engineResources")

val engineResourcesOsDir = when {
    isWindowsHost -> "windows"
    isMacHost -> "macos"
    else -> "linux"
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
        add("cargo")
        add("build")
        add("--manifest-path")
        add("engine/Cargo.toml")
        if (release) add("--release")
        add("--bin")
        add("quite-whisper-engine")
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
        commandLine(cargoArgs)
    }
}

val buildRustEngineRelease = tasks.register<Exec>("buildRustEngineRelease") {
    configureRustEngineBuild(release = true)
}

val syncRustEngineRelease = tasks.register<Copy>("syncRustEngineRelease") {
    dependsOn(buildRustEngineRelease)
    from(rootProject.layout.projectDirectory.file("engine/target/release/$engineExecutableName"))
    into(engineResourcesRoot.map { it.dir(engineResourcesOsDir) })
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(syncRustEngineRelease)
}

tasks.matching { it.name == "run" || it.name == "runDistributable" }.configureEach {
    dependsOn(syncRustEngineRelease)
}

compose.desktop {
    application {
        mainClass = "fabled.quitewhisper.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "fabled.quitewhisper.compose"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(engineResourcesRoot)
        }
    }
}
