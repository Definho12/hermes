package com.guilherme.hermes

import com.guilherme.hermes.shared.Photos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotosTest {
    @Test fun tokenMatchesJavaScript() {
        // values computed with node: ((Number(id)/1e15)*Math.PI).toString(36).replace(/(0+|\.)/g,'')
        val expected = mapOf(
            "2125460483448963477" to "55hbx1kynq", "1" to "bhi2ay3f28n", "20" to "6dq1a2xwd93",
            "1585341984679469056" to "3uchycv2wqc", "1234567890123456789" to "2zqic77uqyk",
            "999999999999999999" to "2f9lc2ug9mm", "2000000000000000000" to "4uj6o5owj98",
            "1866000000000000001" to "4iu7mmiovn", "1500000000000000000" to "3mwe49oefy", "46" to "eod2xzkd8v8",
        )
        for ((id, tok) in expected) assertEquals(id, tok, Photos.xToken(id))
    }

    @Test fun recognisesXLinks() {
        val t = Photos.tweet("https://x.com/TsunstoneBC/status/2125460483448963477/photo/1")!!
        assertEquals("TsunstoneBC", t.user); assertEquals("2125460483448963477", t.id); assertEquals(1, t.photo)
        assertTrue(Photos.isPhotoLink("https://x.com/TsunstoneBC/status/2125460483448963477/photo/1"))
        assertFalse(Photos.isPhotoLink("https://twitter.com/a/status/123?s=20"))
        assertEquals("123", Photos.tweet("https://twitter.com/a/status/123?s=20")!!.id)
        assertNull(Photos.tweet("https://www.youtube.com/watch?v=1"))
    }

    @Test fun readsAnswers() {
        val syn = """{"mediaDetails":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/AbC_1.jpg"},
            {"type":"video","media_url_https":"https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/x.jpg"},
            {"type":"photo","media_url_https":"https://pbs.twimg.com/media/DeF-2.png"}]}"""
        assertEquals(listOf("https://pbs.twimg.com/media/AbC_1?format=jpg&name=orig", "https://pbs.twimg.com/media/DeF-2?format=png&name=orig"),
            Photos.photosFromSyndication(syn))
        assertEquals(listOf("https://pbs.twimg.com/media/X1?format=jpg&name=orig"),
            Photos.photosFromSyndication("""{"photos":[{"url":"https://pbs.twimg.com/media/X1.jpg"}]}"""))
        assertEquals(listOf("https://pbs.twimg.com/media/Q?format=jpg&name=orig"),
            Photos.photosFromMirror("""{"code":200,"tweet":{"media":{"photos":[{"url":"https://pbs.twimg.com/media/Q.jpg"}]}}}"""))
        assertEquals(listOf("https://pbs.twimg.com/media/V?format=jpg&name=orig"),
            Photos.photosFromMirror("""{"media_extended":[{"type":"image","url":"https://pbs.twimg.com/media/V.jpg"}]}"""))
        assertEquals(listOf("https://i.example.com/a.jpg?x=1&y=2"),
            Photos.photosFromPage("""<head><meta property="og:image" content="https://i.example.com/a.jpg?x=1&amp;y=2"><meta name="twitter:image" content="https://other/b.jpg"></head>"""))
    }

    @Test fun readsGifs() {
        val syn = """{"mediaDetails":[{"type":"animated_gif","media_url_https":"https://pbs.twimg.com/tweet_video_thumb/G.jpg",
            "video_info":{"variants":[{"bitrate":0,"content_type":"video/mp4","url":"https://video.twimg.com/tweet_video/G.mp4"}]}},
            {"type":"video","video_info":{"variants":[{"content_type":"application/x-mpegURL","url":"https://v/p.m3u8"},
            {"bitrate":256000,"content_type":"video/mp4","url":"https://v/low.mp4"},{"bitrate":2176000,"content_type":"video/mp4","url":"https://v/high.mp4"}]}}]}"""
        assertEquals(listOf("https://video.twimg.com/tweet_video/G.mp4", "https://v/high.mp4"), Photos.photosFromSyndication(syn))
        assertEquals(listOf("https://video.twimg.com/tweet_video/G.mp4"),
            Photos.photosFromMirror("""{"tweet":{"media":{"all":[{"type":"gif","url":"https://video.twimg.com/tweet_video/G.mp4"}]}}}"""))
    }

    @Test fun whenToTryPictures() {
        assertTrue(Photos.worthTrying("ERROR: [twitter] 2125: No video could be found in this tweet"))
        assertFalse(Photos.worthTrying("ERROR: Unable to download webpage: timed out"))
    }
}
