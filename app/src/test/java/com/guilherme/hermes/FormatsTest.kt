package com.guilherme.hermes

import com.guilherme.hermes.shared.AudioFormat
import com.guilherme.hermes.shared.Choice
import com.guilherme.hermes.shared.Formats
import com.guilherme.hermes.shared.Mode
import com.guilherme.hermes.shared.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    @Test fun videoAsksForMp4UpToTheHeight() {
        val a = Formats.args(Choice(Mode.Video, Quality.P720))
        assertTrue(a.contains("res:720,vcodec:h264,acodec:aac,ext:mp4:m4a"))
        assertEquals("mp4", a[a.indexOf("--merge-output-format") + 1])
        assertTrue(a.contains("--no-playlist"))
        assertFalse(a.contains("-x"))
    }

    @Test fun bestVideoHasNoLimit() {
        val a = Formats.args(Choice(Mode.Video, Quality.Best))
        assertTrue(a.contains("res,vcodec:h264,acodec:aac,ext:mp4:m4a"))
    }

    @Test fun audioMp3AndOriginal() {
        val mp3 = Formats.args(Choice(Mode.Audio, audio = AudioFormat.Mp3))
        assertTrue(mp3.contains("-x"))
        assertEquals("mp3", mp3[mp3.indexOf("--audio-format") + 1])
        val orig = Formats.args(Choice(Mode.Audio, audio = AudioFormat.Original, playlist = true))
        assertFalse(orig.contains("--audio-format"))
        assertTrue(orig.contains("--yes-playlist"))
    }

    @Test fun choiceSurvivesSaving() {
        val c = Choice(Mode.Audio, Quality.P480, AudioFormat.Original, true)
        assertEquals(c, Choice.decode(c.encode()))
        assertEquals(Choice(), Choice.decode("lixo"))
    }

    @Test fun linksAndSearches() {
        assertEquals(
            listOf("https://youtu.be/abc", "https://www.instagram.com/reel/xyz/"),
            Formats.split("https://youtu.be/abc\n\n  https://www.instagram.com/reel/xyz/  \nhttps://youtu.be/abc"),
        )
        // text shared by apps: "Watch this! https://vm.tiktok.com/ZM123/"
        assertEquals(listOf("https://vm.tiktok.com/ZM123/"), Formats.split("Olha isso! https://vm.tiktok.com/ZM123/"))
        assertEquals(listOf("lofi hip hop"), Formats.split(" lofi   hip hop "))
        assertEquals("ytsearch1:lofi hip hop", Formats.source("lofi hip hop"))
        assertTrue(Formats.split("   ").isEmpty())
    }

    @Test fun siteNames() {
        assertEquals("YouTube", Formats.site("https://m.youtube.com/watch?v=1"))
        assertEquals("YouTube", Formats.site("https://youtu.be/1"))
        assertEquals("TikTok", Formats.site("https://vm.tiktok.com/x"))
        assertEquals("X", Formats.site("https://x.com/a/status/1"))
        assertEquals("Busca no YouTube", Formats.site("ytsearch1:abc"))
        assertEquals("example.org", Formats.site("https://www.example.org/v.mp4"))
    }

    @Test fun readsProgressAndTitles() {
        assertEquals(42.5f, Formats.progress("[download]  42.5% of   10.00MiB at  2.00MiB/s ETA 00:03")!!, 0.01f)
        assertNull(Formats.progress("[youtube] abc: Downloading webpage"))
        assertEquals("Meu Vídeo", Formats.titleFrom("[download] Destination: /data/dl/7/Meu Vídeo.f137.mp4"))
        assertEquals("Song", Formats.titleFrom("[ExtractAudio] Destination: C:\\Users\\x\\Song.mp3"))
        assertEquals("Clip", Formats.titleFrom("[Merger] Merging formats into \"/tmp/Clip.mp4\""))
        assertEquals(3 to 12, Formats.playlistPos("[download] Downloading item 3 of 12"))
    }

    @Test fun friendlyErrors() {
        assertEquals("Esse site não é suportado", Formats.friendly("ERROR: Unsupported URL: https://a.b"))
        assertTrue(Formats.friendly("ERROR: [youtube] x: Sign in to confirm you’re not a bot").startsWith("O site bloqueou"))
        assertEquals("Esse site pediu login para esse conteúdo",
            Formats.friendly("ERROR: [Instagram] x: Requested content is not available, rate-limit reached or login required"))
        assertEquals("Falhou: algo estranho", Formats.friendly("bla\nERROR: [generic] algo estranho"))
    }
}
