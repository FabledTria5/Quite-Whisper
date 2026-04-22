package fabled.quitewhisper.compose.engine

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HotkeyClientTest {
    @Test
    fun decodesPressedAndReleasedEvents() {
        assertEquals(
            HotkeyEvent.Pressed,
            decodeHotkeyEvent("""{"type":"hotkey","state":"pressed"}"""),
        )
        assertEquals(
            HotkeyEvent.Released,
            decodeHotkeyEvent("""{"type":"hotkey","state":"released"}"""),
        )
    }

    @Test
    fun decodesHelperErrorEvent() {
        val event = decodeHotkeyEvent("""{"type":"error","message":"Shortcut is already registered"}""")

        assertIs<HotkeyEvent.Error>(event)
        assertEquals("Shortcut is already registered", event.message)
    }

    @Test
    fun defaultHotkeyHelperPathFindsHelperBundledInComposeResources() {
        val resourcesDir = Files.createTempDirectory("quite-whisper-resources")
        val helperPath = resourcesDir.resolve(hotkeyHelperExecutableName())
        Files.createFile(helperPath)

        val previousResourcesDir = System.getProperty("compose.application.resources.dir")
        System.setProperty("compose.application.resources.dir", resourcesDir.toString())
        try {
            assertEquals(
                helperPath,
                defaultHotkeyHelperPath(startDirectory = Files.createTempDirectory("quite-whisper-install")),
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
    fun defaultHotkeyHelperPathFallbackPointsAtRepoRootWhenHelperIsMissing() {
        val repoRoot = Files.createTempDirectory("quite-whisper-repo")
        val composeDir = repoRoot.resolve("composeApp")
        Files.createDirectories(composeDir)
        Files.createDirectories(repoRoot.resolve("engine"))

        assertEquals(
            repoRoot.resolve(Path("engine", "target", "debug", hotkeyHelperExecutableName())),
            defaultHotkeyHelperPath(startDirectory = composeDir),
        )
    }
}
