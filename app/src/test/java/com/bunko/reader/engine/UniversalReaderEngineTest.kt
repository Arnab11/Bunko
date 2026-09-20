package com.bunko.reader.engine

import com.bunko.reader.engine.archive.UniversalArchiveReader
import com.bunko.reader.engine.model.ArchiveType
import com.bunko.reader.engine.model.ReflowSpine
import com.bunko.reader.engine.model.TocItem
import com.bunko.reader.engine.txt.TxtDocumentReader
import com.bunko.reader.offline.LocalBookFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UniversalReaderEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testArchiveTypeDetection() {
        assertEquals(ArchiveType.ZIP, UniversalArchiveReader.detectArchiveType(File("test.cbz")))
        assertEquals(ArchiveType.ZIP, UniversalArchiveReader.detectArchiveType(File("test.zip")))
        assertEquals(ArchiveType.RAR, UniversalArchiveReader.detectArchiveType(File("test.cbr")))
        assertEquals(ArchiveType.RAR, UniversalArchiveReader.detectArchiveType(File("test.rar")))
        assertEquals(ArchiveType.SEVEN_ZIP, UniversalArchiveReader.detectArchiveType(File("test.cb7")))
        assertEquals(ArchiveType.SEVEN_ZIP, UniversalArchiveReader.detectArchiveType(File("test.7z")))
        assertEquals(ArchiveType.TAR, UniversalArchiveReader.detectArchiveType(File("test.cbt")))
        assertEquals(ArchiveType.TAR, UniversalArchiveReader.detectArchiveType(File("test.tar")))
    }

    @Test
    fun testNaturalFilenameComparison() {
        val list = listOf("page_10.jpg", "page_1.jpg", "page_2.jpg", "page_100.jpg", "page_20.jpg")
        val sorted = list.sortedWith { a, b -> UniversalArchiveReader.compareNaturalFileNames(a, b) }
        assertEquals(listOf("page_1.jpg", "page_2.jpg", "page_10.jpg", "page_20.jpg", "page_100.jpg"), sorted)
    }

    @Test
    fun testZipArchiveListing() = runBlocking {
        val zipFile = tempFolder.newFile("sample.cbz")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("02.png"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("01.jpg"))
            zos.write(byteArrayOf(4, 5, 6))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("Text file".toByteArray())
            zos.closeEntry()
        }

        val entries = UniversalArchiveReader.listImageEntries(zipFile)
        assertEquals(2, entries.size)
        assertEquals("01.jpg", entries[0])
        assertEquals("02.png", entries[1])

        val bytes = UniversalArchiveReader.extractEntryBytes(zipFile, "01.jpg")
        assertNotNull(bytes)
        assertEquals(3, bytes!!.size)
    }

    @Test
    fun testMisnamedArchiveFallback() = runBlocking {
        // A ZIP file named .cbz.cbr
        val misnamedFile = tempFolder.newFile("Solo Leveling v01 (2021) (Digital) (LuCaZ).cbz.cbr")
        ZipOutputStream(FileOutputStream(misnamedFile)).use { zos ->
            zos.putNextEntry(ZipEntry("001.jpg"))
            zos.write(byteArrayOf(10, 20, 30))
            zos.closeEntry()
        }

        // Magic signature detects it as ZIP or falls back to ZIP extractor
        val entries = UniversalArchiveReader.listImageEntries(misnamedFile)
        assertEquals(1, entries.size)
        assertEquals("001.jpg", entries[0])

        val bytes = UniversalArchiveReader.extractEntryBytes(misnamedFile, "001.jpg")
        assertNotNull(bytes)
        assertEquals(3, bytes!!.size)
    }

    @Test
    fun testTxtDocumentReaderWithChapters() = runBlocking {
        val txtFile = tempFolder.newFile("story.txt")
        val content = """
            Chapter 1
            Once upon a time in a digital library.
            
            Chapter 2
            The reader engine became universal and lightweight.
        """.trimIndent()
        txtFile.writeText(content)

        val parsed = TxtDocumentReader.parseTxt(file = txtFile)
        assertEquals("story", parsed.title)
        assertEquals(2, parsed.spines.size)
        assertEquals("Chapter 1", parsed.spines[0].title)
        assertEquals("Chapter 2", parsed.spines[1].title)
        assertTrue(parsed.spines[0].rawHtml.contains("digital library"))
    }

    @Test
    fun testByteArrayBufferAndLz77() {
        val buffer = com.bunko.reader.engine.mobi.ByteArrayBuffer(16)
        buffer.write("Hello ".toByteArray())
        buffer.write("World".toByteArray())
        assertEquals("Hello World", buffer.toString())
        assertEquals(11, buffer.size())

        // Test LZ77 literal byte decompression
        val rawBytes = byteArrayOf(
            5, 'B'.code.toByte(), 'u'.code.toByte(), 'n'.code.toByte(), 'k'.code.toByte(), 'o'.code.toByte(),
            ' '.code.toByte(),
            (0xC0 or 'R'.code).toByte() // Space followed by char
        )
        val decompressed = com.bunko.reader.engine.mobi.LibreraMobiParser.lz77(rawBytes)
        val resultStr = String(decompressed)
        assertEquals("Bunko  R", resultStr)
    }

    @Test
    fun testRealMobiFile() {
        val file = File("src/test/resources/sample.mobi")
        if (file.exists()) {
            val parser = com.bunko.reader.engine.mobi.LibreraMobiParser(file)
            println("MOBI Title: ${parser.getTitle()}")
            println("MOBI Author: ${parser.getAuthor()}")
            println("MOBI Records: ${parser.recordsCount}")
            println("MOBI firstImageIndex: ${parser.firstImageIndex}")
            println("MOBI lastContentIndex: ${parser.lastContentIndex}")
            val text = parser.getTextContent()
            println("MOBI text length: ${text.length}")
            val cover = parser.getCoverOrThumb()
            println("MOBI cover: ${cover?.size} bytes")
            assertTrue(text.isNotEmpty())
        }
    }
}
