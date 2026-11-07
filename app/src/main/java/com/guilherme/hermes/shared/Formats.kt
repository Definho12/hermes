package com.guilherme.hermes.shared

/*
 * The rules of Swallow, shared file for file by the phone app and the Windows app:
 * what to ask yt-dlp for (video or audio, which quality), how to read its progress,
 * and how to turn its errors into plain Portuguese.
 */

enum class Mode { Video, Audio }

enum class Quality(val label: String, val height: Int?) {
    Best("Melhor", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
}

enum class AudioFormat(val label: String) {
    Mp3("MP3"),
    Original("Original"),
}

data class Choice(
    val mode: Mode = Mode.Video,
    val quality: Quality = Quality.Best,
    val audio: AudioFormat = AudioFormat.Mp3,
    val playlist: Boolean = false,
) {
    /** Short text for the list, e.g. "Vídeo · 720p" or "Áudio · MP3". */
    val label: String get() = when (mode) {
        Mode.Video -> "Vídeo · ${quality.label}"
        Mode.Audio -> "Áudio · ${audio.label}"
    } + if (playlist) " · playlist" else ""

    fun encode(): String = "${mode.name}|${quality.name}|${audio.name}|$playlist"

    companion object {
        fun decode(s: String?): Choice {
            val p = s?.split('|') ?: return Choice()
            return try {
                Choice(Mode.valueOf(p[0]), Quality.valueOf(p[1]), AudioFormat.valueOf(p[2]), p.getOrNull(3) == "true")
            } catch (_: Exception) {
                Choice()
            }
        }
    }
}

object Formats {
    /** Extensions yt-dlp leaves behind while working (not the final file). */
    val leftovers = setOf("part", "ytdl", "temp", "json", "jpg", "jpeg", "png", "webp", "vtt", "srt", "description")

    /**
     * The yt-dlp options for a choice. The output folder/template is added by each app.
     * Video: best picture up to the chosen height, preferring H.264 + AAC so it plays everywhere,
     * joined into one MP4. Audio: MP3 at the best quality, or the original stream with no re-encode.
     */
    fun args(c: Choice): List<String> {
        val a = ArrayList<String>()
        when (c.mode) {
            Mode.Video -> {
                val res = c.quality.height?.let { "res:$it" } ?: "res"
                a += listOf("-f", "bv*+ba/b")
                a += listOf("-S", "$res,vcodec:h264,acodec:aac,ext:mp4:m4a")
                a += listOf("--merge-output-format", "mp4")
                a += "--embed-metadata"
            }
            Mode.Audio -> {
                a += listOf("-f", "ba/b", "-x")
                if (c.audio == AudioFormat.Mp3) a += listOf("--audio-format", "mp3", "--audio-quality", "0")
                a += listOf("--embed-thumbnail", "--convert-thumbnails", "jpg", "--embed-metadata")
            }
        }
        a += if (c.playlist) "--yes-playlist" else "--no-playlist"
        a += listOf("--no-mtime", "--windows-filenames", "--retries", "5", "--fragment-retries", "5", "--newline")
        return a
    }

    /** File name template: the title, cut so it never gets too long for the phone or Windows. */
    const val TEMPLATE = "%(title).120B.%(ext)s"

    private val linkRx = Regex("https?://[^\\s<>\"]+")

    fun linkIn(text: String): String? = linkRx.find(text)?.value?.trimEnd('.', ',', ')', ']')

    /**
     * What to download for one line typed or pasted by the person: the link in it, or,
     * when there is no link, a YouTube search for the words (first result).
     */
    fun source(line: String): String? {
        val t = line.trim()
        if (t.isEmpty()) return null
        return linkIn(t) ?: "ytsearch1:$t"
    }

    /** One entry per line (several links can be pasted at once). A shared text with a link counts once. */
    fun split(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val withLinks = lines.filter { linkIn(it) != null }
        return when {
            withLinks.isNotEmpty() -> withLinks.map { linkIn(it)!! }.distinct()
            // no links: a single search with all the words
            else -> listOfNotNull(text.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() })
        }
    }

    /** A friendly name for where the link is from. */
    fun site(source: String): String {
        if (source.startsWith("ytsearch")) return "Busca no YouTube"
        val host = Regex("https?://([^/?#]+)").find(source)?.groupValues?.get(1)?.lowercase()?.removePrefix("www.")?.removePrefix("m.")
            ?: return "Link"
        val known = listOf(
            "youtu" to "YouTube", "instagram" to "Instagram", "tiktok" to "TikTok", "twitter" to "X",
            "x.com" to "X", "facebook" to "Facebook", "fb.watch" to "Facebook", "reddit" to "Reddit", "redd.it" to "Reddit",
            "soundcloud" to "SoundCloud", "vimeo" to "Vimeo", "twitch" to "Twitch", "kwai" to "Kwai",
            "pinterest" to "Pinterest", "pin.it" to "Pinterest", "dailymotion" to "Dailymotion", "bandcamp" to "Bandcamp",
            "threads" to "Threads", "bsky" to "Bluesky", "bilibili" to "Bilibili", "globo" to "Globoplay", "spotify" to "Spotify",
        )
        return known.firstOrNull { host.contains(it.first) }?.second ?: host
    }

    private val pctRx = Regex("\\[download]\\s+(\\d+(?:\\.\\d+)?)%")
    private val destRx = Regex("^\\[(?:download|ExtractAudio)] Destination: (.+)$")
    private val mergeRx = Regex("^\\[Merger] Merging formats into \"(.+)\"$")
    private val itemRx = Regex("^\\[download] Downloading (?:item|video) (\\d+) of (\\d+)")

    /** Percentage (0..100) from a yt-dlp progress line. */
    fun progress(line: String): Float? = pctRx.find(line)?.groupValues?.get(1)?.toFloatOrNull()

    /** The title, taken from the file name yt-dlp is about to write. */
    fun titleFrom(line: String): String? {
        val path = (destRx.find(line.trim()) ?: mergeRx.find(line.trim()))?.groupValues?.get(1) ?: return null
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        // "Title.f137.mp4" → "Title"
        return name.replace(Regex("(\\.f\\d+[-\\w]*)?\\.[A-Za-z0-9]{2,5}$"), "").trim().takeIf { it.isNotEmpty() }
    }

    /** "item 3 of 12" while a playlist downloads. */
    fun playlistPos(line: String): Pair<Int, Int>? =
        itemRx.find(line.trim())?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    /** What step yt-dlp is on, in Portuguese, from a line of its output. */
    fun step(line: String): String? = when {
        line.startsWith("[Merger]") -> "Juntando vídeo e som…"
        line.startsWith("[ExtractAudio]") -> "Convertendo o áudio…"
        line.startsWith("[EmbedThumbnail]") -> "Colocando a capa…"
        line.startsWith("[Metadata]") -> "Colocando o nome…"
        line.startsWith("[VideoConvertor]") || line.startsWith("[VideoRemuxer]") -> "Convertendo o vídeo…"
        else -> null
    }

    /** The most useful line of yt-dlp's error output. */
    fun errorLine(full: String): String {
        val lines = full.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull() ?: return ""
        return line.removePrefix("ERROR:").trim().replace(Regex("^\\[[^]]+]\\s*"), "").take(240)
    }

    /** yt-dlp's error, explained in plain Portuguese. */
    fun friendly(full: String): String {
        val f = full.lowercase()
        return when {
            f.isBlank() -> "Não encontrei nada para baixar nesse link"
            "unsupported url" in f -> "Esse site não é suportado"
            "drm" in f -> "Esse conteúdo é protegido (DRM) e não pode ser baixado"
            "sign in to confirm" in f || "http error 429" in f || "too many requests" in f ->
                "O site bloqueou por agora (muitos pedidos). Tente de novo mais tarde"
            "private" in f && ("video" in f || "account" in f || "post" in f) -> "Esse conteúdo é privado"
            "login required" in f || "requires login" in f || "log in" in f || "cookies" in f ->
                "Esse site pediu login para esse conteúdo"
            "age" in f && ("restrict" in f || "confirm your age" in f) -> "Conteúdo com restrição de idade (pede login)"
            "not available in your country" in f || "geo" in f && "restrict" in f -> "Não está disponível no Brasil"
            "video unavailable" in f || "has been removed" in f || "404" in f || "not found" in f -> "Não encontrado ou foi removido"
            "no video formats" in f || "no video could be found" in f -> "Não achei vídeo nesse link"
            "unable to download webpage" in f || "getaddrinfo" in f || "timed out" in f || "network is unreachable" in f ||
                "connection" in f && ("reset" in f || "refused" in f) -> "Sem internet ou o site não respondeu"
            "no space left" in f -> "Sem espaço no armazenamento"
            "is not a valid url" in f -> "Esse link não é válido"
            else -> "Falhou: " + errorLine(full)
        }
    }
}
