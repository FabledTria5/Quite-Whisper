package fabled.quitewhisper.compose.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

sealed interface HotkeyEvent {
    data object Pressed : HotkeyEvent
    data object Released : HotkeyEvent
    data class Error(val message: String) : HotkeyEvent
}

interface HotkeyConnection : Closeable {
    val events: SharedFlow<HotkeyEvent>

    suspend fun start(hotkey: String)
}

class HotkeyClient(
    private val helperPath: Path = defaultHotkeyHelperPath(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : HotkeyConnection {
    private val _events = MutableSharedFlow<HotkeyEvent>(extraBufferCapacity = 64)
    private var process: Process? = null

    override val events: SharedFlow<HotkeyEvent> = _events

    override suspend fun start(hotkey: String) = withContext(Dispatchers.IO) {
        close()
        require(helperPath.exists()) {
            missingHotkeyHelperMessage(helperPath)
        }

        val nextProcess = ProcessBuilder(helperPath.toString(), hotkey)
            .directory(File("."))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        process = nextProcess
        scope.launch { readEvents(nextProcess) }
        Unit
    }

    override fun close() {
        process?.destroy()
        process = null
    }

    private suspend fun readEvents(activeProcess: Process) = withContext(Dispatchers.IO) {
        activeProcess.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                runCatching { decodeHotkeyEvent(line) }
                    .onSuccess { _events.tryEmit(it) }
                    .onFailure { _events.tryEmit(HotkeyEvent.Error(it.message ?: "Invalid hotkey helper event")) }
            }
        }
    }
}

fun decodeHotkeyEvent(line: String): HotkeyEvent {
    val root = EngineJson.json.parseToJsonElement(line).jsonObject
    return when (root.getValue("type").jsonPrimitive.contentOrNull) {
        "hotkey" -> when (root.getValue("state").jsonPrimitive.contentOrNull) {
            "pressed" -> HotkeyEvent.Pressed
            "released" -> HotkeyEvent.Released
            else -> HotkeyEvent.Error("Unknown hotkey state: ${root["state"]}")
        }
        "error" -> HotkeyEvent.Error(root["message"]?.jsonPrimitive?.contentOrNull ?: "Hotkey helper failed")
        else -> HotkeyEvent.Error("Unknown hotkey helper event type: ${root["type"]}")
    }
}

fun defaultHotkeyHelperPath(
    startDirectory: Path = Path(System.getProperty("user.dir")).toAbsolutePath(),
): Path {
    System.getenv("QUITEWHISPER_HOTKEY_HELPER_PATH")?.takeIf(String::isNotBlank)?.let {
        return Path(it)
    }

    val executableName = hotkeyHelperExecutableName()
    System.getProperty("compose.application.resources.dir")?.takeIf(String::isNotBlank)?.let { resourcesDir ->
        val bundledHelper = Path(resourcesDir).resolve(executableName)
        if (bundledHelper.exists()) {
            return bundledHelper
        }
    }

    val searchRoots = generateSequence(startDirectory.toAbsolutePath()) { it.parent }.toList()
    val candidates = searchRoots.flatMap { root ->
        listOf(
            root.resolve(Path("engine", "target", "debug", executableName)),
            root.resolve(Path("engine", "target", "release", executableName)),
            root.resolve(executableName),
        )
    }

    val repoRoot = searchRoots.firstOrNull { it.resolve("engine").isDirectory() }
        ?: startDirectory.toAbsolutePath()
    return candidates.firstOrNull { it.exists() }
        ?: repoRoot.resolve(Path("engine", "target", "debug", executableName))
}

fun hotkeyHelperExecutableName(): String = sidecarExecutableName("hotkey-helper")

fun missingHotkeyHelperMessage(helperPath: Path): String =
    "Hotkey helper binary was not found at $helperPath. " +
        "Run `./gradlew :composeApp:run` from the repo root to build and bundle the helper. " +
        "To use a custom binary, set QUITEWHISPER_HOTKEY_HELPER_PATH."
