package com.guilherme.hermes

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.guilherme.hermes.shared.Choice
import com.guilherme.hermes.shared.Formats
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** A saved file: where it is (to open or share it) and what kind it is. */
data class Saved(val uri: String, val name: String, val mime: String)

enum class State { Queued, Running, Done, Failed, Canceled }

data class Item(
    val id: Long,
    val source: String,
    val choice: Choice,
    val state: State = State.Queued,
    val title: String = "",
    val pct: Float = 0f,
    val message: String = "Na fila",
    /** Full error text from yt-dlp, shown when the person taps a failed download. */
    val detail: String = "",
    val files: List<Saved> = emptyList(),
    val time: Long = System.currentTimeMillis(),
) {
    val site: String get() = Formats.site(source)
    val active: Boolean get() = state == State.Queued || state == State.Running
    val shownTitle: String get() = title.ifBlank {
        if (source.startsWith("ytsearch")) "“${source.substringAfter(':')}”" else source
    }
}

/**
 * Downloads one thing at a time with yt-dlp and puts the result where the phone's other apps
 * see it: videos in Filmes/Hermes (gallery), audio in Música/Hermes.
 */
object Engine {
    private const val TAG = "Hermes"
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Boolean>()
    private val updated = CompletableDeferred<Unit>()
    private var initError = ""
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val ids = AtomicLong(System.currentTimeMillis())
    @Volatile private var runningId = -1L
    private val canceled: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet())

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version
    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus

    private val prefs by lazy { app.getSharedPreferences("hermes", Context.MODE_PRIVATE) }

    var lastChoice: Choice
        get() = Choice.decode(prefs.getString("choice", null))
        set(v) { prefs.edit().putString("choice", v.encode()).apply() }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        _items.value = loadHistory()
        scope.launch {
            val ok = try {
                YoutubeDL.init(app)
                FFmpeg.init(app)
                _version.value = YoutubeDL.version(app)
                true
            } catch (e: Throwable) {
                Log.e(TAG, "init failed", e)
                initError = e.stackTraceToString().take(3000)
                _updateStatus.value = "O motor de download não iniciou"
                false
            }
            ready.complete(ok)
            // sites change all the time: keep yt-dlp fresh (at most every 12 hours)
            if (ok && System.currentTimeMillis() - prefs.getLong("lastUpdate", 0L) > 12 * 3600_000L) {
                if (doUpdate()) prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
            }
            updated.complete(Unit)
        }
        scope.launch {
            updated.await()
            for (id in queue) {
                if (id in canceled) continue
                runningId = id
                try {
                    run(id)
                } catch (e: Throwable) {
                    Log.e(TAG, "download crashed", e)
                    set(id) { it.copy(state = State.Failed, message = "Erro inesperado: ${e.message ?: e}", detail = e.stackTraceToString().take(3000)) }
                }
                runningId = -1L
                saveHistory()
            }
        }
    }

    /** Adds one download per link (or one search) in the text. Returns how many were added. */
    fun enqueue(text: String, choice: Choice): Int {
        val entries = Formats.split(text)
        for (e in entries) {
            val src = Formats.source(e) ?: continue
            val id = ids.incrementAndGet()
            _items.update { listOf(Item(id, src, choice)) + it }
            queue.trySend(id)
        }
        if (entries.isNotEmpty()) {
            lastChoice = choice
            DownloadService.start(app)
        }
        return entries.size
    }

    fun retry(id: Long) {
        canceled.remove(id)
        set(id) { it.copy(state = State.Queued, pct = 0f, message = "Na fila", detail = "") }
        queue.trySend(id)
        DownloadService.start(app)
    }

    fun cancel(id: Long) {
        canceled.add(id)
        set(id) { it.copy(state = State.Canceled, message = "Cancelado", pct = 0f) }
        if (runningId == id) try { YoutubeDL.destroyProcessById("dl-$id") } catch (_: Throwable) {}
        saveHistory()
    }

    fun remove(id: Long) {
        _items.update { l -> l.filterNot { it.id == id && !it.active } }
        saveHistory()
    }

    fun clearFinished() {
        _items.update { l -> l.filter { it.active } }
        saveHistory()
    }

    fun updateYtDlp() {
        scope.launch {
            if (!ready.await()) { _updateStatus.value = "O motor de download não iniciou"; return@launch }
            if (doUpdate()) prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
        }
    }

    private fun doUpdate(): Boolean {
        _updateStatus.value = "Atualizando…"
        return try {
            val r = YoutubeDL.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            _version.value = YoutubeDL.version(app)
            _updateStatus.value = if (r == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) "Já está atualizado" else "Atualizado!"
            true
        } catch (e: Throwable) {
            Log.e(TAG, "update failed", e)
            _updateStatus.value = "Não consegui atualizar agora"
            false
        }
    }

    // ---------- internals ----------

    private fun set(id: Long, change: (Item) -> Item) {
        _items.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun get(id: Long) = _items.value.firstOrNull { it.id == id }

    private suspend fun run(id: Long) {
        val item = get(id) ?: return
        set(id) { it.copy(state = State.Running, pct = 0f, message = "Preparando…") }
        if (!ready.await()) {
            set(id) { it.copy(state = State.Failed, message = "O motor de download não iniciou", detail = initError) }
            return
        }
        val dir = File(app.cacheDir, "dl/$id").apply { deleteRecursively(); mkdirs() }
        val req = YoutubeDLRequest(item.source)
        req.addCommands(Formats.args(item.choice))
        req.addOption("-o", File(dir, Formats.TEMPLATE).absolutePath)
        if (item.source.startsWith("ytsearch")) req.addOption("--playlist-items", "1")

        var error = ""
        var lastUi = 0L
        var pos: Pair<Int, Int>? = null
        try {
            YoutubeDL.execute(req, "dl-$id", false) { progress, _, line ->
                if (id in canceled) return@execute
                Formats.titleFrom(line)?.let { t -> set(id) { it.copy(title = t) } }
                Formats.step(line)?.let { s -> set(id) { it.copy(message = s, pct = 100f) } }
                Formats.playlistPos(line)?.let { pos = it }
                val now = System.currentTimeMillis()
                if (progress >= 0f && now - lastUi > 250) {
                    lastUi = now
                    val p = progress.coerceIn(0f, 100f)
                    val where = pos?.let { "${it.first} de ${it.second} · " } ?: ""
                    set(id) { it.copy(pct = p, message = "${where}Baixando · ${p.toInt()}%") }
                }
            }
        } catch (e: Throwable) {
            error = e.message ?: e.toString()
            Log.w(TAG, "yt-dlp: $error")
        }
        if (id in canceled) { dir.deleteRecursively(); return }

        val made = dir.listFiles().orEmpty().filter { f ->
            f.isFile && f.length() > 0 && f.extension.lowercase() !in Formats.leftovers && !f.name.contains(".temp.") &&
                !Regex("\\.f\\d+[-\\w]*\\.\\w+$").containsMatchIn(f.name)
        }.sortedBy { it.lastModified() }
        if (made.isEmpty()) {
            set(id) { it.copy(state = State.Failed, message = Formats.friendly(error), detail = error.take(4000)) }
            dir.deleteRecursively()
            return
        }

        set(id) { it.copy(message = "Salvando…", pct = 100f) }
        val saved = ArrayList<Saved>()
        var saveError = ""
        for (f in made) {
            try {
                saved += save(f)
            } catch (e: Throwable) {
                Log.e(TAG, "save failed", e)
                saveError = e.message ?: e.toString()
            }
        }
        dir.deleteRecursively()
        if (saved.isEmpty()) {
            set(id) { it.copy(state = State.Failed, message = "Não consegui salvar o arquivo", detail = saveError) }
            return
        }
        val title = if (saved.size == 1) saved[0].name.substringBeforeLast('.') else get(id)?.title.orEmpty()
        val msg = if (saved.size == 1) "Pronto" else "Pronto · ${saved.size} arquivos"
        set(id) { it.copy(state = State.Done, message = msg, pct = 100f, files = saved, title = title.ifBlank { it.title }, detail = "") }
        DownloadService.notifyDone(app, get(id)!!)
    }

    fun folderFor(mime: String): String = when {
        mime.startsWith("video") -> Environment.DIRECTORY_MOVIES + "/Hermes"
        mime.startsWith("audio") -> Environment.DIRECTORY_MUSIC + "/Hermes"
        else -> Environment.DIRECTORY_DOWNLOADS + "/Hermes"
    }

    private fun mimeOf(f: File): String {
        val ext = f.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "opus", "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }

    /** Copies a finished file into the shared storage (never replacing anything already there). */
    private fun save(f: File): Saved {
        val mime = mimeOf(f)
        val collection = when {
            mime.startsWith("video") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mime.startsWith("audio") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folderFor(mime))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val r = app.contentResolver
        val uri: Uri = r.insert(collection, values) ?: error("o Android não deixou criar o arquivo")
        try {
            r.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out, 256 * 1024) } }
            r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            r.delete(uri, null, null)
            throw e
        }
        // the name Android actually used (it adds " (1)" when the name is taken)
        val name = r.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: f.name
        return Saved(uri.toString(), name, mime)
    }

    // ---------- history (finished downloads survive closing the app) ----------

    private fun saveHistory() {
        val arr = JSONArray()
        for (it in _items.value.filter { !it.active }.take(150)) {
            arr.put(JSONObject().apply {
                put("id", it.id); put("source", it.source); put("choice", it.choice.encode())
                put("state", it.state.name); put("title", it.title); put("message", it.message)
                put("detail", it.detail.take(2000)); put("time", it.time)
                put("files", JSONArray().apply {
                    it.files.forEach { s -> put(JSONObject().put("uri", s.uri).put("name", s.name).put("mime", s.mime)) }
                })
            })
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<Item> = try {
        val arr = JSONArray(prefs.getString("history", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val fs = o.optJSONArray("files") ?: JSONArray()
            Item(
                id = o.getLong("id"), source = o.getString("source"), choice = Choice.decode(o.optString("choice")),
                state = runCatching { State.valueOf(o.getString("state")) }.getOrDefault(State.Failed),
                title = o.optString("title"), pct = 100f, message = o.optString("message"),
                detail = o.optString("detail"), time = o.optLong("time"),
                files = (0 until fs.length()).map { k ->
                    val s = fs.getJSONObject(k); Saved(s.getString("uri"), s.getString("name"), s.getString("mime"))
                },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
