package com.evergreen.trackora.backup

import com.evergreen.trackora.backup.PhotoLinks.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PhotoLinksTest {

    @Test
    fun theAppsOwnLinksPointToTheirFiles() {
        assertEquals(
            Link.AppFile("IMG_20261103_101500.jpg"),
            PhotoLinks.parse("content://com.evergreen.trackora.fileprovider/external_files/IMG_20261103_101500.jpg"),
        )
        assertEquals(
            Link.AppFile("IMG_20261104_120000_123_gallery.png"),
            PhotoLinks.parse("content://com.evergreen.trackora.debug.fileprovider/external_files/IMG_20261104_120000_123_gallery.png"),
        )
    }

    @Test
    fun restoredPhotosInSubfoldersAreTheAppsToo() {
        val link = PhotoLinks.parse("content://com.evergreen.trackora.fileprovider/external_files/restored/r1/IMG_1.jpg")
        assertEquals(Link.AppFile("restored/r1/IMG_1.jpg"), link)
        assertEquals("IMG_1.jpg", (link as Link.AppFile).fileName)
    }

    @Test
    fun percentEscapesAreDecoded() {
        assertEquals(
            Link.AppFile("IMG_1.jpg"),
            PhotoLinks.parse("content://com.evergreen.trackora.fileprovider/external_files/IMG%5F1%2ejpg"),
        )
    }

    @Test
    fun everythingElseIsSomeoneElses() {
        listOf(
            "",
            "IMG_1.jpg",
            "file:///sdcard/IMG_1.jpg",
            "content://com.android.providers.media.documents/document/image%3A1234",
            "content://media/external/images/media/12",
            "content://com.evil.fileprovider/external_files/IMG_1.jpg",
            "content://com.evergreen.trackora.fileprovider.evil/external_files/IMG_1.jpg",
            "content://com.evergreen.trackora/external_files/IMG_1.jpg",
            "CONTENT://com.evergreen.trackora.fileprovider/external_files/IMG_1.jpg",
            "content://com.evergreen.trackora.fileprovider",
            "content://com.evergreen.trackora.fileprovider/external_files",
            "content://com.evergreen.trackora.fileprovider/external_files/",
            "content://com.evergreen.trackora.fileprovider/my_images/IMG_1.jpg",
            "content://com.evergreen.trackora.fileprovider/my_cache/IMG_1.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/IMG_1.jpg?x=1",
            "content://com.evergreen.trackora.fileprovider/external_files/IMG_1.jpg#x",
            "content://com.evergreen.trackora.fileprovider/external_files/../databases/trackora_database",
            "content://com.evergreen.trackora.fileprovider/external_files/a/./IMG_1.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/a//IMG_1.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/%2e%2e/x",
            "content://com.evergreen.trackora.fileprovider/external_files/a%2Fb.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/IMG%G1.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/IMG%4",
            "content://com.evergreen.trackora.fileprovider/external_files/IMG%٣٣.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/عکس.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/IMG 1.jpg",
            "content://com.evergreen.trackora.fileprovider/external_files/" + List(9) { "d" }.joinToString("/") + "/IMG_1.jpg",
        ).forEach { assertEquals(it, Link.Other, PhotoLinks.parse(it)) }
    }

    @Test
    fun theAcceptedAppsCanBeNarrowed() {
        val debug = "content://com.evergreen.trackora.debug.fileprovider/external_files/IMG_1.jpg"
        assertEquals(Link.Other, PhotoLinks.parse(debug, setOf("com.evergreen.trackora")))
        assertEquals(Link.AppFile("IMG_1.jpg"), PhotoLinks.parse(debug, setOf("com.evergreen.trackora.debug")))
    }

    @Test
    fun linksAreBuiltAsTheFileProviderBuildsThem() {
        assertEquals(
            "content://com.evergreen.trackora.fileprovider/external_files/IMG_20261103_101500.jpg",
            PhotoLinks.uriOf("com.evergreen.trackora", "IMG_20261103_101500.jpg"),
        )
        val restored = PhotoLinks.uriOf("com.evergreen.trackora.debug", "restored/r1/IMG_1.jpg")
        assertEquals("content://com.evergreen.trackora.debug.fileprovider/external_files/restored/r1/IMG_1.jpg", restored)
        assertEquals(Link.AppFile("restored/r1/IMG_1.jpg"), PhotoLinks.parse(restored))
    }

    @Test
    fun linksAreNotBuiltForUnsafePathsOrAppIds() {
        listOf("", "../x.jpg", "a//b.jpg", "/abs.jpg", "a b.jpg", "x/../y.jpg").forEach { path ->
            try {
                PhotoLinks.uriOf("com.evergreen.trackora", path)
                fail("built a link for $path")
            } catch (expected: IllegalArgumentException) {
            }
        }
        listOf("", "trackora", "com.evil/x", "com..x", "com.x/").forEach { id ->
            try {
                PhotoLinks.uriOf(id, "IMG_1.jpg")
                fail("built a link for app $id")
            } catch (expected: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun aTakenNameGetsTheFirstFreeNumber() {
        assertEquals("IMG_1.jpg", PhotoLinks.uniqueName("IMG_1.jpg", emptySet()))
        assertEquals("IMG_1-2.jpg", PhotoLinks.uniqueName("IMG_1.jpg", setOf("IMG_1.jpg")))
        assertEquals("IMG_1-2.jpg", PhotoLinks.uniqueName("IMG_1.jpg", setOf("img_1.JPG")))
        assertEquals("IMG_1-4.jpg", PhotoLinks.uniqueName("IMG_1.jpg", setOf("IMG_1.jpg", "IMG_1-2.jpg", "img_1-3.jpg")))
        assertEquals("noext-2", PhotoLinks.uniqueName("noext", setOf("noext")))
    }

    @Test
    fun aUniqueNameStaysAValidName() {
        val long = "A".repeat(124) + ".jpg"
        val unique = PhotoLinks.uniqueName(long, setOf(long))
        assertEquals(128, unique.length)
        assertTrue(unique.endsWith("-2.jpg"))
        assertTrue(PhotoLinks.isValidName(unique))
        val longExtension = "a." + "x".repeat(100)
        val other = PhotoLinks.uniqueName(longExtension, setOf(longExtension))
        assertTrue(other, PhotoLinks.isValidName(other))
        assertFalse(other == longExtension)
    }
}
