import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val engineResourcesRoot = layout.buildDirectory.dir("engineResources")

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("fabled.quitewhisper.rust-engine")
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

compose.desktop {
    application {
        mainClass = "fabled.quitewhisper.app.presentation.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "fabled.quitewhisper.app"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(engineResourcesRoot)
        }
    }
}
