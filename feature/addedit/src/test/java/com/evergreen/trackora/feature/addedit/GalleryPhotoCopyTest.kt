package com.evergreen.trackora.feature.addedit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime

/**
 * The copy behind gallery photos. It replaces storing the picker's URI, which
 * stopped loading after an app restart; what matters is that the copy is
 * exact and that a failed one never leaves an entry pointing at half a file.
 */
class GalleryPhotoCopyTest {

    @get:Rule
    val dir = TemporaryFolder()

    @Test
    fun `the photo is copied byte for byte`() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }

        val copy = copyPhoto({ ByteArrayInputStream(bytes) }, dir.root, "IMG_a_gallery.jpg")

        assertEquals("IMG_a_gallery.jpg", copy?.name)
        assertArrayEquals(bytes, copy?.readBytes())
    }

    @Test
    fun `a source that cannot be opened yields no file`() {
        val copy = copyPhoto({ null }, dir.root, "IMG_b_gallery.jpg")

        assertNull(copy)
        assertFalse(dir.root.resolve("IMG_b_gallery.jpg").exists())
    }

    @Test
    fun `a read that fails part way leaves no partial file behind`() {
        val failing = object : InputStream() {
            private var served = 0
            override fun read(): Int {
                if (served++ > 1_000) throw IOException("provider went away")
                return 7
            }
        }

        val copy = copyPhoto({ failing }, dir.root, "IMG_c_gallery.jpg")

        assertNull(copy)
        assertFalse(dir.root.resolve("IMG_c_gallery.jpg").exists())
    }

    @Test
    fun `a revoked permission is treated as a failed copy, not a crash`() {
        val copy = copyPhoto({ throw SecurityException("no access") }, dir.root, "IMG_d_gallery.jpg")

        assertNull(copy)
    }

    @Test
    fun `names match camera photos with a gallery suffix and keep the extension`() {
        val name = galleryPhotoName(LocalDateTime.of(2026, 10, 27, 17, 41, 42, 123_000_000), "png")

        assertEquals("IMG_20261027_174142_123_gallery.png", name)
    }
}
