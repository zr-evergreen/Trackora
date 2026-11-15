package com.evergreen.trackora.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipInputStream

/**
 * Builds ZIP files byte by byte, with a handle on every field, to make the
 * malformed and hostile archives no ordinary ZIP library would write.
 * By default it lays files out exactly as ZipOutputStream does.
 */
class RawZip {

    class Item(val name: String, val data: ByteArray) {
        var nameBytes: ByteArray = name.toByteArray(Charsets.ISO_8859_1)
        var method = 8
        var flags: Int? = null
        var versionNeeded: Int? = null
        var compressed: ByteArray? = null
        var crc: Long? = null
        var size: Long? = null
        var extra = ByteArray(0)
        var localOffset: Long? = null
        var localName: ByteArray? = null
        var localFlags: Int? = null
        var descriptorCrc: Long? = null
        var comment = ByteArray(0)
        var gapAfter = 0
        var madeBy: Int? = null
        var internalAttributes = 0
        var externalAttributes = 0L
        var localVersion: Int? = null
        var localExtra: ByteArray? = null
        /** Whether a deflated file has a data descriptor (as ZipOutputStream writes it) or its sizes up front. */
        var descriptor = true
        /** With a descriptor: also put the real CRC-32 and sizes in the local header, which ZipOutputStream never does. */
        var localSizes = false
    }

    val items = ArrayList<Item>()
    var trailing = ByteArray(0)
    var total: Int? = null
    var encryptFlag = false

    fun add(name: String, data: ByteArray, configure: Item.() -> Unit = {}): RawZip {
        items += Item(name, data).apply(configure)
        return this
    }

    fun build(): ByteArray {
        val out = Out()
        class Laid(val item: Item, val offset: Long, val flags: Int, val needed: Int, val crc: Long, val compressedSize: Long, val size: Long)
        val laid = items.map { item ->
            val compressed = item.compressed ?: if (item.method == 8) deflate(item.data) else item.data
            val descriptor = item.method == 8 && item.descriptor
            val flags = (item.flags ?: if (descriptor) 0x0808 else 0x0800) or (if (encryptFlag) 1 else 0)
            val needed = item.versionNeeded ?: if (item.method == 8) 20 else 10
            val crc = item.crc ?: crc32(item.data)
            val size = item.size ?: item.data.size.toLong()
            val offset = out.size().toLong()
            out.u32(0x04034b50); out.u16(item.localVersion ?: needed); out.u16(item.localFlags ?: flags); out.u16(item.method)
            out.u16(0); out.u16(DOS_DATE_2000)
            if (descriptor && !item.localSizes) { out.u32(0); out.u32(0); out.u32(0) } else { out.u32(crc); out.u32(compressed.size.toLong()); out.u32(size) }
            val localName = item.localName ?: item.nameBytes
            val localExtra = item.localExtra ?: item.extra
            out.u16(localName.size); out.u16(localExtra.size)
            out.write(localName); out.write(localExtra); out.write(compressed)
            if (descriptor) { out.u32(0x08074b50); out.u32(item.descriptorCrc ?: crc); out.u32(compressed.size.toLong()); out.u32(size) }
            out.write(ByteArray(item.gapAfter))
            Laid(item, offset, flags, needed, crc, compressed.size.toLong(), size)
        }
        val centralStart = out.size().toLong()
        for (l in laid) {
            out.u32(0x02014b50); out.u16(l.item.madeBy ?: l.needed); out.u16(l.needed); out.u16(l.flags); out.u16(l.item.method)
            out.u16(0); out.u16(DOS_DATE_2000)
            out.u32(l.crc); out.u32(l.compressedSize); out.u32(l.size)
            out.u16(l.item.nameBytes.size); out.u16(l.item.extra.size); out.u16(l.item.comment.size)
            out.u16(0); out.u16(l.item.internalAttributes); out.u32(l.item.externalAttributes)
            out.u32(l.item.localOffset ?: l.offset)
            out.write(l.item.nameBytes); out.write(l.item.extra); out.write(l.item.comment)
        }
        val centralSize = out.size() - centralStart
        val count = total ?: items.size
        out.u32(0x06054b50); out.u16(0); out.u16(0); out.u16(count); out.u16(count)
        out.u32(centralSize); out.u32(centralStart); out.u16(0)
        out.write(trailing)
        return out.toByteArray()
    }

    private class Out : ByteArrayOutputStream() {
        fun u16(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
        fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        fun u32(v: Int) = u32(v.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        private const val DOS_DATE_2000 = (20 shl 9) or (1 shl 5) or 1

        fun deflate(data: ByteArray, level: Int = Deflater.DEFAULT_COMPRESSION): ByteArray {
            val deflater = Deflater(level, true)
            try {
                deflater.setInput(data)
                deflater.finish()
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (!deflater.finished()) {
                    val n = deflater.deflate(buffer)
                    out.write(buffer, 0, n)
                }
                return out.toByteArray()
            } finally {
                deflater.end()
            }
        }

        fun crc32(data: ByteArray): Long = CRC32().apply { update(data) }.value

        /** The files of a well-formed ZIP, in order. */
        fun unpack(zip: ByteArray): List<Pair<String, ByteArray>> {
            val files = ArrayList<Pair<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(zip)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                    files += entry.name to out.toByteArray()
                }
            }
            return files
        }

        /** A ZIP of [files], laid out as the writer lays them out. */
        fun of(files: List<Pair<String, ByteArray>>, configure: RawZip.() -> Unit = {}): ByteArray =
            RawZip().apply {
                files.forEach { (name, data) -> add(name, data) }
                configure()
            }.build()
    }
}
