package fabled.quitewhisper.data.engine

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class EnginePathTest {
    @Test
    fun defaultEnginePathFindsEngineBundledInComposeResources() {
        val resourcesDir = Files.createTempDirectory("quite-whisper-resources")
        val enginePath = resourcesDir.resolve(engineExecutableName())
        Files.createFile(enginePath)

        val previousResourcesDir = System.getProperty("compose.application.resources.dir")
        System.setProperty("compose.application.resources.dir", resourcesDir.toString())
        try {
            assertEquals(
                enginePath,
                defaultEnginePath(startDirectory = Files.createTempDirectory("quite-whisper-install")),
            )
        } finally {
            if (previousResourcesDir == null) {
                System.clearProperty("compose.application.resources.dir")
            } else {
                System.setProperty("compose.application.resources.dir", previousResourcesDir)
            }
        }
    }

    @Test
    fun defaultEnginePathFindsEngineWhenStartedFromComposeModuleDirectory() {
        val repoRoot = Files.createTempDirectory("quite-whisper-repo")
        val composeDir = repoRoot.resolve("composeApp")
        val enginePath = repoRoot
            .resolve(Path("engine", "target", "debug", engineExecutableName()))
        Files.createDirectories(composeDir)
        Files.createDirectories(enginePath.parent)
        Files.createFile(enginePath)

        assertEquals(
            enginePath,
            defaultEnginePath(startDirectory = composeDir),
        )
    }

    @Test
    fun defaultEnginePathFallbackPointsAtRepoRootWhenEngineIsMissing() {
        val repoRoot = Files.createTempDirectory("quite-whisper-repo")
        val composeDir = repoRoot.resolve("composeApp")
        Files.createDirectories(composeDir)
        Files.createDirectories(repoRoot.resolve("engine"))

        assertEquals(
            repoRoot.resolve(Path("engine", "target", "debug", engineExecutableName())),
            defaultEnginePath(startDirectory = composeDir),
        )
    }

    @Test
    fun missingEngineMessageMentionsUnixAndWindowsBuildCommands() {
        val message = missingEngineMessage(Path("/missing/quite-whisper-engine"))

        assertContains(message, "./gradlew :composeApp:run")
        assertContains(message, "cargo build --manifest-path engine/Cargo.toml --bin quite-whisper-engine")
        assertContains(message, ".\\scripts\\windows-dev.ps1 cargo build --bin quite-whisper-engine")
        assertContains(message, "QUITEWHISPER_ENGINE_PATH")
    }
}
