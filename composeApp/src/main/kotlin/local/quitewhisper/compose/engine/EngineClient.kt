package local.quitewhisper.compose.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

interface EngineConnection : Closeable {
    val messages: SharedFlow<EngineMessage>

    suspend fun start()

    suspend fun send(
        request: EngineRequest,
        timeoutMillis: Long = 30_000,
    ): EngineMessage.Result
}

class EngineClient(
    private val enginePath: Path = defaultEnginePath(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : EngineConnection {
    private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<EngineMessage.Result>>()
    private val _messages = MutableSharedFlow<EngineMessage>(extraBufferCapacity = 64)

    private var process: Process? = null
    private var writer: BufferedWriter? = null

    override val messages: SharedFlow<EngineMessage> = _messages

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (process != null) return@withContext
        require(enginePath.exists()) {
            "Rust engine binary was not found at $enginePath. Run `gradle :composeApp:run` or `.\\scripts\\windows-dev.ps1 cargo build --bin quite-whisper-engine` from the repo root, or set QUITEWHISPER_ENGINE_PATH."
        }

        val nextProcess = ProcessBuilder(enginePath.toString())
            .directory(File("."))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        process = nextProcess
        writer = BufferedWriter(OutputStreamWriter(nextProcess.outputStream, Charsets.UTF_8))
        scope.launch { readMessages(nextProcess) }
    }

    override suspend fun send(request: EngineRequest, timeoutMillis: Long): EngineMessage.Result {
        val deferred = CompletableDeferred<EngineMessage.Result>()
        pendingResults[request.id] = deferred
        withContext(Dispatchers.IO) {
            val activeWriter = requireNotNull(writer) { "Engine process is not running" }
            activeWriter.write(EngineJson.encodeRequestLine(request))
            activeWriter.flush()
        }

        return try {
            withTimeout(timeoutMillis) { deferred.await() }
        } finally {
            pendingResults.remove(request.id)
        }
    }

    override fun close() {
        runCatching {
            val id = newCommandId()
            val activeWriter = writer ?: return@runCatching
            activeWriter.write(EngineJson.encodeRequestLine(EngineRequest.Shutdown(id)))
            activeWriter.flush()
        }
        writer = null
        process?.destroy()
        process = null
    }

    private suspend fun readMessages(activeProcess: Process) = withContext(Dispatchers.IO) {
        activeProcess.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val message = EngineJson.decodeMessage(line)
                if (message is EngineMessage.Result) {
                    pendingResults.remove(message.id)?.complete(message)
                }
                _messages.tryEmit(message)
            }
        }
    }
}

fun newCommandId(): String = UUID.randomUUID().toString()

fun defaultEnginePath(
    startDirectory: Path = Path(System.getProperty("user.dir")).toAbsolutePath(),
): Path {
    System.getenv("QUITEWHISPER_ENGINE_PATH")?.takeIf(String::isNotBlank)?.let {
        return Path(it)
    }

    val executableName = engineExecutableName()
    System.getProperty("compose.application.resources.dir")?.takeIf(String::isNotBlank)?.let { resourcesDir ->
        val bundledEngine = Path(resourcesDir).resolve(executableName)
        if (bundledEngine.exists()) {
            return bundledEngine
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

fun engineExecutableName(): String =
    if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "quite-whisper-engine.exe"
    } else {
        "quite-whisper-engine"
    }
