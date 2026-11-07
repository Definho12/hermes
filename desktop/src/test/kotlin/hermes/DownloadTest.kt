package hermes

import com.guilherme.hermes.shared.AudioFormat
import com.guilherme.hermes.shared.Choice
import com.guilherme.hermes.shared.Mode
import com.guilherme.hermes.shared.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Real downloads through yt-dlp + ffmpeg, from a small video served by the build machine
 * (HERMES_TEST_URL, see the workflow). Checks the whole path: run, read progress, move the file.
 */
class DownloadTest {
    @Test fun videoAndAudio() {
        val url = System.getenv("HERMES_TEST_URL")
        assumeTrue("no test server", !url.isNullOrBlank())
        val out = File(System.getProperty("java.io.tmpdir"), "hermes-test-out").apply { deleteRecursively(); mkdirs() }
        Settings.folder = out
        Engine.init()
        assertEquals(3, Engine.enqueue("$url\n", Choice(Mode.Video, Quality.P720)) +
            Engine.enqueue(url!!, Choice(Mode.Audio, audio = AudioFormat.Mp3)) +
            Engine.enqueue("nada aqui https://localhost:1/nao-existe.mp4", Choice()))
        val end = System.currentTimeMillis() + 180_000
        while (Engine.items.any { it.active } && System.currentTimeMillis() < end) Thread.sleep(300)

        val (bad, audio, video) = Engine.items.toList()  // newest first
        println(Engine.items.joinToString("\n") { "${it.state} ${it.message} ${it.files} ${it.detail.takeLast(400)}" })
        assertEquals(State.Done, video.state)
        assertTrue(video.files.single().name.endsWith(".mp4"))
        assertTrue(video.files.single().length() > 10_000)
        assertEquals(State.Done, audio.state)
        assertTrue(audio.files.single().name.endsWith(".mp3"))
        assertEquals(State.Failed, bad.state)
        assertEquals("Sem internet ou o site não respondeu", bad.message)
        // the same title twice in the same folder gets a new name, never replacing
        assertTrue(out.listFiles()!!.size == 2)
    }
}
