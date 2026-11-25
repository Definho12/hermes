package hermes

import com.guilherme.hermes.shared.Photos
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Real check on the internet (build machine only): the X post with a photo he wanted as a sticker. */
class PhotosNetTest {
    @Test fun xPhotoPost() {
        assumeTrue("no internet test", System.getenv("HERMES_NET") == "1")
        val dir = File(System.getProperty("java.io.tmpdir"), "swallow-photos").apply { deleteRecursively(); mkdirs() }
        val files = Photos.download("https://x.com/TsunstoneBC/status/2125460483448963477/photo/1", dir)
        println("TRACE:\n" + Photos.trace)
        println("FILES: " + files.joinToString { "${it.name} ${it.length()}" })
        assertTrue("no picture:\n${Photos.trace}", files.size == 1 && files[0].length() > 5_000)
        val head = files[0].readBytes().take(4).map { it.toInt() and 0xff }
        assertTrue("not an image: $head", head[0] == 0xFF || head[0] == 0x89 || head[0] == 'R'.code)
    }
}
