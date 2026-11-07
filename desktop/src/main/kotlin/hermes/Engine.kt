package hermes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.guilherme.hermes.shared.Choice
import com.guilherme.hermes.shared.Formats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

enum class State { Queued, Running, Done, Failed, Canceled }

class Item(val id: Long, val source: String, val choice: Choice, val time: Long = System.currentTimeMillis()) {
    var state by mutableStateOf(State.Queued)
    var title by mutableStateOf("")
    var pct by mutableStateOf(0f)
    var message by mutableStateOf("Na fila")
    var detail by mutableStateOf("")
    var files by mutableStateOf<List<File>>(emptyList())

    val site: String get() = Formats.site(source)
    val active: Boolean get() = state == State.Queued || state == State.Running
    val shownTitle: String get() = title.ifBlank {
        if (source.startsWith("ytsearch")) "“${source.substringAfter(':')}”" else source
    }
}

/**
 * Downloads one thing at a time with yt-dlp. Each download is made in a temporary folder
 * and then moved into the chosen folder, never replacing a file that is already there.
 */
object Engine {
    val items = mutableStateListOf<Item>()
    var version by mutableStateOf<String?>(null); private set
    var updateStatus by mutableStateOf<String?>(null); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Item>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Unit>()
    private var nextId = System.currentTimeMillis()
    @Volatile private var current: Process? = null
    @Volatile private var currentItem: Item? = null

    private val exe = if (isWindows) ".exe" else ""
    private val bundledDir: File? get() = System.getProperty("compose.application.resources.dir")?.let { File(it) }?.takeIf { it.isDirectory }

    /** Our own copy of yt-dlp in AppData, so it can update itself (Program Files is read-only). */
    private val ytdlp: File get() = File(AppDirs.bin, "yt-dlp$exe")

    /** The yt-dlp to run: ours, or one installed on the computer (the build machine uses that). */
    private fun ytdlpCmd(): String? = when {
        ytdlp.isFile -> ytdlp.path
        else -> onPath("yt-dlp")
    }

    private fun ffmpegDir(): String? {
        bundledDir?.let { if (File(it, "ffmpeg$exe").isFile) return it.path }
        return onPath("ffmpeg")?.let { File(it).parent }
    }

    private fun onPath(name: String): String? =
        System.getenv("PATH")?.split(File.pathSeparator)?.map { File(it, name + exe) }?.firstOrNull { it.isFile }?.path

    fun init() {
        loadHistory()
        scope.launch {
            prepare()
            ready.complete(Unit)
            for (item in queue) {
                if (item.state != State.Queued) continue
                try {
                    run(item)
                } catch (e: Throwable) {
                    Log.error("baixar ${item.source}", e)
                    item.state = State.Failed; item.message = "Erro inesperado: ${e.message ?: e}"; item.detail = e.stackTraceToString().take(3000)
                }
                current = null; currentItem = null
                saveHistory()
            }
        }
    }

    /** Copies the bundled yt-dlp to AppData the first time, then updates it at most every 12 hours. */
    private fun prepare() {
        try {
            val bundled = bundledDir?.let { File(it, "yt-dlp$exe") }
            if (!ytdlp.exists() && bundled != null && bundled.isFile) {
                Files.copy(bundled.toPath(), ytdlp.toPath(), StandardCopyOption.REPLACE_EXISTING)
                ytdlp.setExecutable(true)
                Log.w("yt-dlp copiado para ${ytdlp.path}")
            }
            val stamp = File(AppDirs.bin, "updated")
            if (ytdlp.exists() && System.currentTimeMillis() - stamp.lastModified() > 12 * 3600_000L) {
                update(quiet = true)
                stamp.writeText(System.currentTimeMillis().toString())
            }
            version = ytdlpCmd()?.let { cmd(30, it, "--version")?.trim()?.lines()?.lastOrNull() }
            Log.w("yt-dlp: ${ytdlpCmd()} versão $version · ffmpeg: ${ffmpegDir()}")
        } catch (e: Exception) {
            Log.error("preparar o yt-dlp", e)
        }
    }

    fun updateYtDlp() {
        scope.launch {
            ready.await()
            update(quiet = false)
            File(AppDirs.bin, "updated").writeText(System.currentTimeMillis().toString())
        }
    }

    private fun update(quiet: Boolean) {
        if (!ytdlp.exists()) { if (!quiet) updateStatus = "Só dá para atualizar a cópia do Swallow"; return }
        updateStatus = "Atualizando…"
        val out = cmd(180, ytdlp.path, "-U") ?: ""
        version = cmd(30, ytdlp.path, "--version")?.trim()?.lines()?.lastOrNull()
        updateStatus = when {
            "up to date" in out.lowercase() -> if (quiet) null else "Já está atualizado"
            "updated yt-dlp" in out.lowercase() -> "Atualizado!"
            else -> if (quiet) null else "Não consegui atualizar agora"
        }
        Log.w("atualizar yt-dlp: ${out.trim().takeLast(300)}")
    }

    /** Runs a command and returns what it printed (null if it couldn't run). */
    private fun cmd(timeoutSec: Long, vararg args: String): String? = try {
        val p = ProcessBuilder(*args).redirectErrorStream(true).apply { environment().putAll(pyEnv) }.start()
        val text = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        p.waitFor(timeoutSec, TimeUnit.SECONDS)
        text
    } catch (e: Exception) {
        Log.error("rodar ${args.firstOrNull()}", e); null
    }

    private val pyEnv = mapOf("PYTHONIOENCODING" to "utf-8", "PYTHONUTF8" to "1")

    /** Adds one download per link (or one search) in the text. Returns how many were added. */
    fun enqueue(text: String, choice: Choice): Int {
        val entries = Formats.split(text)
        for (e in entries) {
            val src = Formats.source(e) ?: continue
            val item = Item(nextId++, src, choice)
            items.add(0, item)
            queue.trySend(item)
        }
        if (entries.isNotEmpty()) { Settings.choice = choice; Settings.save() }
        return entries.size
    }

    fun retry(item: Item) {
        item.state = State.Queued; item.message = "Na fila"; item.detail = ""; item.pct = 0f
        queue.trySend(item)
    }

    fun cancel(item: Item) {
        val was = item.state
        item.state = State.Canceled; item.message = "Cancelado"; item.pct = 0f
        if (was == State.Running && currentItem === item) stop(current)
        saveHistory()
    }

    fun cancelAll() { items.filter { it.active }.forEach { cancel(it) } }

    private fun stop(p: Process?) {
        p ?: return
        try {
            p.descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
        } catch (_: Exception) {}
    }

    fun remove(item: Item) { if (!item.active) { items.remove(item); saveHistory() } }
    fun clearFinished() { items.removeAll { !it.active }; saveHistory() }

    private fun run(item: Item) {
        item.state = State.Running; item.message = "Preparando…"; item.pct = 0f
        currentItem = item
        val yt = ytdlpCmd()
        if (yt == null) {
            item.state = State.Failed; item.message = "O motor de download (yt-dlp) não foi encontrado"
            item.detail = "Procurei em ${ytdlp.path}. Reinstale o Swallow."
            return
        }
        val tmp = File(AppDirs.tmp, item.id.toString()).apply { deleteRecursively(); mkdirs() }
        val args = arrayListOf(yt, item.source)
        args += Formats.args(item.choice)
        args += listOf("-o", File(tmp, Formats.TEMPLATE).path)
        if (item.source.startsWith("ytsearch")) args += listOf("--playlist-items", "1")
        ffmpegDir()?.let { args += listOf("--ffmpeg-location", it) }
        // optional: a cookies.txt next to the settings lets yt-dlp see things that need login
        File(AppDirs.data, "cookies.txt").takeIf { it.isFile }?.let { args += listOf("--cookies", it.path) }

        val out = StringBuilder()
        var pos: Pair<Int, Int>? = null
        var lastUi = 0L
        val code = try {
            val p = ProcessBuilder(args).directory(tmp).redirectErrorStream(true).apply { environment().putAll(pyEnv) }.start()
            current = p
            p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                if (out.length < 200_000) out.appendLine(line)
                if (item.state == State.Canceled) return@forEachLine
                Formats.titleFrom(line)?.let { item.title = it }
                Formats.playlistPos(line)?.let { pos = it }
                Formats.step(line)?.let { item.message = it; item.pct = 100f }
                val pct = Formats.progress(line)
                val now = System.currentTimeMillis()
                if (pct != null && now - lastUi > 200) {
                    lastUi = now
                    item.pct = pct.coerceIn(0f, 100f)
                    val where = pos?.let { "${it.first} de ${it.second} · " } ?: ""
                    item.message = "${where}Baixando · ${pct.toInt()}%"
                }
            }
            p.waitFor()
        } catch (e: Exception) {
            out.appendLine("ERROR: ${e.message}"); -1
        }
        if (item.state == State.Canceled) { tmp.deleteRecursively(); return }

        val made = tmp.listFiles().orEmpty().filter { f ->
            f.isFile && f.length() > 0 && f.extension.lowercase() !in Formats.leftovers && !f.name.contains(".temp.") &&
                !Regex("\\.f\\d+[-\\w]*\\.\\w+$").containsMatchIn(f.name)
        }.sortedBy { it.lastModified() }
        if (made.isEmpty()) {
            val text = out.toString()
            item.state = State.Failed
            item.message = Formats.friendly(if (code == 0) "" else text)
            item.detail = text.lines().filter { it.isNotBlank() }.takeLast(25).joinToString("\n")
            Log.w("falhou (${item.source}): ${Formats.errorLine(text)}")
            tmp.deleteRecursively()
            return
        }

        item.message = "Salvando…"
        val dest = Settings.folder.apply { mkdirs() }
        val moved = made.map { f ->
            var d = File(dest, f.name)
            var k = 2
            while (d.exists()) { d = File(dest, "${f.nameWithoutExtension} ($k).${f.extension}"); k++ }
            try {
                Files.move(f.toPath(), d.toPath())
            } catch (_: Exception) {
                Files.copy(f.toPath(), d.toPath())
            }
            d
        }
        tmp.deleteRecursively()
        item.files = moved
        if (moved.size == 1) item.title = moved[0].nameWithoutExtension
        item.state = State.Done; item.pct = 100f
        item.message = if (moved.size == 1) "Pronto" else "Pronto · ${moved.size} arquivos"
        Log.w("pronto: ${moved.joinToString { it.name }}")
    }

    // ---------- history ----------

    private val historyFile get() = File(AppDirs.data, "history.json")

    @Synchronized
    fun saveHistory() {
        try {
            val arr = JSONArray()
            for (it in items.toList().filter { !it.active }.take(200)) {
                arr.put(JSONObject().apply {
                    put("id", it.id); put("source", it.source); put("choice", it.choice.encode()); put("time", it.time)
                    put("state", it.state.name); put("title", it.title); put("message", it.message); put("detail", it.detail.take(2000))
                    put("files", JSONArray(it.files.map { f -> f.path }))
                })
            }
            historyFile.writeText(arr.toString())
        } catch (e: Exception) { Log.error("salvar history.json", e) }
    }

    private fun loadHistory() {
        try {
            if (!historyFile.exists()) return
            val arr = JSONArray(historyFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val it = Item(o.getLong("id"), o.getString("source"), Choice.decode(o.optString("choice")), o.optLong("time"))
                it.state = runCatching { State.valueOf(o.getString("state")) }.getOrDefault(State.Failed)
                it.title = o.optString("title"); it.message = o.optString("message"); it.detail = o.optString("detail"); it.pct = 100f
                val fs = o.optJSONArray("files") ?: JSONArray()
                it.files = (0 until fs.length()).map { k -> File(fs.getString(k)) }
                items.add(it)
            }
            nextId = maxOf(nextId, (items.maxOfOrNull { it.id } ?: 0) + 1)
        } catch (e: Exception) { Log.error("ler history.json", e) }
    }

    /** For the build machine's screenshots: a few made-up downloads in every state. */
    fun demo() {
        items.clear()
        fun add(src: String, c: Choice, st: State, title: String, msg: String, pct: Float = 100f, detail: String = "") =
            Item(nextId++, src, c).also { it.state = st; it.title = title; it.message = msg; it.pct = pct; it.detail = detail; items.add(it) }
        add("https://www.youtube.com/watch?v=demo1", Choice(), State.Running, "Pôr do sol em Ouro Preto (4K)", "Baixando · 62%", 62f)
        add("https://www.instagram.com/reel/demo/", Choice(com.guilherme.hermes.shared.Mode.Audio), State.Queued, "", "Na fila", 0f)
        add("https://www.tiktok.com/@a/video/1", Choice(quality = com.guilherme.hermes.shared.Quality.P720), State.Done, "Receita de pão de queijo", "Pronto")
        add("https://x.com/a/status/1", Choice(), State.Failed, "", "Esse conteúdo é privado", detail = "ERROR: [twitter] 1: This post is from a private account")
        add("ytsearch1:lofi para estudar", Choice(com.guilherme.hermes.shared.Mode.Audio), State.Done, "Lofi para estudar - 1 hora", "Pronto")
        version = "2026.10.22"
    }
}
