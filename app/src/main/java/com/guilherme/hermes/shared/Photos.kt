package com.guilherme.hermes.shared

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/*
 * Pictures and GIFs of a post. yt-dlp only knows video and audio, so a post with only photos
 * (an X post with a picture, for example) fails there; X's GIFs also have ".../photo/1" links.
 * This finds the post's media (photos in full size, GIFs/videos as the best MP4) and downloads it.
 * Shared file for file by the phone app and the Windows app.
 */
object Photos {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"

    /** What happened in the last search, step by step (shown in "Detalhes" when it fails). */
    val trace = StringBuilder()
    private fun log(s: String) { if (trace.length < 6000) trace.appendLine(s) }

    private val tweetRx = Regex("^https?://(?:www\\.|mobile\\.)?(?:x|twitter|fxtwitter|vxtwitter|fixupx)\\.com/([^/?#]+)/status(?:es)?/(\\d+)(?:/photo/(\\d+))?", RegexOption.IGNORE_CASE)

    data class Tweet(val user: String, val id: String, val photo: Int?)

    fun tweet(url: String): Tweet? = tweetRx.find(url.trim())?.let {
        Tweet(it.groupValues[1], it.groupValues[2], it.groupValues[3].toIntOrNull())
    }

    /** A link that is plainly about one photo (x.com/…/status/…/photo/1): skip yt-dlp and go straight here. */
    fun isPhotoLink(url: String): Boolean = tweet(url)?.photo != null

    /** yt-dlp's error says there was nothing to play: worth trying the pictures. */
    fun worthTrying(error: String): Boolean {
        val e = error.lowercase()
        return "no video" in e || "no media" in e || "unsupported url" in e || "no formats" in e ||
            "requested format is not available" in e || "there is no video" in e || "image" in e
    }

    // ---------- X / Twitter ----------

    /**
     * The token X's own embed widget sends with each request: (id / 1e15 * π) written in base 36,
     * without zeros and the dot. Same arithmetic as JavaScript's Number.toString(36).
     */
    fun xToken(id: String): String = toRadix36(id.toDouble() / 1e15 * Math.PI).replace(Regex("0+|\\."), "")

    /** Port of V8's DoubleToRadixCString (what JavaScript does for Number.toString(radix)). */
    fun toRadix36(v: Double): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyz"; val radix = 36
        var integer = Math.floor(v); var fraction = v - integer
        var delta = maxOf(0.5 * (Math.nextUp(v) - v), Math.nextUp(0.0))
        val frac = StringBuilder()
        if (fraction >= delta) {
            do {
                fraction *= radix; delta *= radix
                val digit = fraction.toInt(); frac.append(chars[digit]); fraction -= digit
                if (fraction > 0.5 || (fraction == 0.5 && (digit and 1) == 1)) {
                    if (fraction + delta > 1) {
                        // round up, carrying into the digits already written
                        while (true) {
                            if (frac.isEmpty()) { integer += 1; break }
                            val c = frac.last(); frac.setLength(frac.length - 1)
                            val d = chars.indexOf(c)
                            if (d + 1 < radix) { frac.append(chars[d + 1]); break }
                        }
                        break
                    }
                }
            } while (fraction >= delta)
        }
        val int = StringBuilder()
        do {
            val r = integer % radix; int.insert(0, chars[r.toInt()]); integer = (integer - r) / radix
        } while (integer > 0)
        return if (frac.isEmpty()) int.toString() else "$int.$frac"
    }

    /** pbs.twimg.com/media/NAME.jpg → the original, full-size file. */
    fun original(url: String): String {
        val m = Regex("^(https://pbs\\.twimg\\.com/media/[^.?/]+)\\.(\\w+)").find(url) ?: return url
        return "${m.groupValues[1]}?format=${m.groupValues[2]}&name=orig"
    }

    /** The best MP4 among X's video variants. */
    private fun bestMp4(variants: org.json.JSONArray?): String? {
        variants ?: return null
        var best: String? = null; var rate = -1
        for (k in 0 until variants.length()) variants.getJSONObject(k).let { v ->
            val type = v.optString("content_type", v.optString("type"))
            val u = v.optString("url", v.optString("src"))
            if (u.isNotBlank() && ("mp4" in type || u.contains(".mp4"))) {
                val r = v.optInt("bitrate", 0); if (r > rate) { rate = r; best = u }
            }
        }
        return best
    }

    /** Media addresses in X's embed answer (photos, GIFs, videos), in the post's order. */
    fun photosFromSyndication(json: String): List<String> {
        val o = JSONObject(json); val out = ArrayList<String>()
        o.optJSONArray("mediaDetails")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { m ->
                when (m.optString("type")) {
                    "photo" -> m.optString("media_url_https").takeIf { it.isNotBlank() }?.let { out += original(it) }
                    "animated_gif", "video" -> bestMp4(m.optJSONObject("video_info")?.optJSONArray("variants"))?.let { out += it }
                }
            }
        }
        if (out.isEmpty()) o.optJSONArray("photos")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).optString("url").takeIf { it.isNotBlank() }?.let { out += original(it) }
        }
        if (out.isEmpty()) o.optJSONObject("video")?.optJSONArray("variants")?.let { v -> bestMp4(v)?.let { out += it } }
        return out.distinct()
    }

    /** fxtwitter / vxtwitter answers. */
    fun photosFromMirror(json: String): List<String> {
        val o = JSONObject(json); val out = ArrayList<String>()
        val media = o.optJSONObject("tweet")?.optJSONObject("media")
        media?.optJSONArray("all")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).optString("url").takeIf { it.isNotBlank() }?.let { out += it }
        }
        if (out.isEmpty()) for (key in listOf("photos", "videos")) media?.optJSONArray(key)?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).optString("url").takeIf { it.isNotBlank() }?.let { out += it }
        }
        if (out.isEmpty()) o.optJSONArray("media_extended")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).optString("url").takeIf { it.isNotBlank() }?.let { out += it }
        }
        if (out.isEmpty()) o.optJSONArray("mediaURLs")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { out += it } }
        return out.map(::original).distinct()
    }

    private fun xPhotos(t: Tweet): List<String> {
        val tries = listOf(
            "https://cdn.syndication.twimg.com/tweet-result?id=${t.id}&lang=en&token=${xToken(t.id)}" to ::photosFromSyndication,
            "https://api.fxtwitter.com/${t.user}/status/${t.id}" to ::photosFromMirror,
            "https://api.vxtwitter.com/${t.user}/status/${t.id}" to ::photosFromMirror,
        )
        for ((url, parse) in tries) {
            try {
                val body = text(url)
                val found = parse(body)
                log("${url.substringBefore('?')}: ${found.size} foto(s)")
                if (found.isEmpty()) {
                    val flat = body.replace(Regex("\\s+"), " ")
                    val at = listOf("mediaDetails", "\"media\"", "video").map { flat.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
                    log("  resposta: " + flat.substring(at).take(900))
                }
                if (found.isNotEmpty()) return found
            } catch (e: Exception) {
                log("${url.substringBefore('?')}: ${e.message}")
            }
        }
        return emptyList()
    }

    // ---------- any other page ----------

    private val ogRx = Regex("<meta[^>]+(?:property|name)=[\"'](?:og:image(?::secure_url|:url)?|twitter:image(?::src)?)[\"'][^>]*>", RegexOption.IGNORE_CASE)
    private val contentRx = Regex("content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    fun photosFromPage(html: String): List<String> =
        ogRx.findAll(html).mapNotNull { contentRx.find(it.value)?.groupValues?.get(1) }
            .map { it.replace("&amp;", "&") }.filter { it.startsWith("http") }.distinct().take(1).toList()

    // ---------- the whole thing ----------

    /** The pictures in a post or page (full size), or an empty list. */
    fun find(url: String): List<String> {
        trace.setLength(0)
        tweet(url)?.let { t ->
            val all = xPhotos(t)
            // ".../photo/2" means just that one
            val pick = t.photo?.let { n -> all.getOrNull(n - 1)?.let { listOf(it) } } ?: all
            return pick
        }
        if (url.startsWith("ytsearch")) return emptyList()
        return try {
            val found = photosFromPage(text(url)); log("página: ${found.size} imagem(ns)"); found
        } catch (e: Exception) {
            log("página: ${e.message}"); emptyList()
        }
    }

    /**
     * Downloads the pictures into [dir], named after the post (e.g. "TsunstoneBC-2125460483448963477.jpg").
     * Returns the files, or an empty list.
     */
    fun download(url: String, dir: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<File> {
        val links = find(url)
        if (links.isEmpty()) return emptyList()
        val t = tweet(url)
        val base = (t?.let { "${it.user}-${it.id}" } ?: (url.substringAfter("://").substringBefore('?').trimEnd('/')
            .replace(Regex("[^A-Za-z0-9._-]+"), "-").takeLast(80))).ifBlank { "imagem" }
        val out = ArrayList<File>()
        links.forEachIndexed { i, link ->
            onProgress(i + 1, links.size)
            try {
                val c = open(link)
                val type = c.contentType.orEmpty().lowercase()
                val ext = when {
                    "mp4" in type -> "mp4"; "png" in type -> "png"; "webp" in type -> "webp"; "gif" in type -> "gif"; "avif" in type -> "avif"
                    "jpeg" in type || "jpg" in type -> "jpg"
                    else -> Regex("[.?&](?:format=)?(jpe?g|png|webp|gif|mp4)\\b").find(link)?.groupValues?.get(1)?.replace("jpeg", "jpg") ?: "jpg"
                }
                val name = if (links.size == 1) "$base.$ext" else "$base-${i + 1}.$ext"
                val f = File(dir, name)
                c.inputStream.use { inp -> f.outputStream().use { inp.copyTo(it) } }
                if (f.length() > 0) out += f else f.delete()
                log("baixou $name (${f.length() / 1024} KB)")
            } catch (e: Exception) {
                log("baixar $link: ${e.message}")
            }
        }
        return out
    }

    private fun open(url: String): HttpURLConnection {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            val code = c.responseCode
            if (code in 300..399) {
                u = URL(URL(u), c.getHeaderField("Location") ?: error("redirecionou sem destino")).toString(); c.disconnect()
                return@repeat
            }
            if (code !in 200..299) { c.disconnect(); error("HTTP $code") }
            return c
        }
        error("redirecionou demais")
    }

    private fun text(url: String): String = open(url).let { c -> c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } }
}
