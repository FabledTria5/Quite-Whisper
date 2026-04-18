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
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.compose.native.tray)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
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
        mainClass = "local.quitewhisper.compose.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "QuiteWhisper"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(engineResourcesRoot)
        }
    }
}
