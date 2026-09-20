package com.bunko.reader.engine.mobi

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * High-performance byte buffer adapted from Librera Reader (com.foobnix.mobi.parser.ByteArrayBuffer).
 */
class ByteArrayBuffer(size: Int = 32) : OutputStream() {
    private var buf: ByteArray = ByteArray(if (size <= 0) 32 else size)
    private var count: Int = 0

    constructor(data: ByteArray) : this(data.size) {
        System.arraycopy(data, 0, this.buf, 0, data.size)
        this.count = data.size
    }

    override fun write(b: Int) {
        val newcount = count + 1
        ensureCapacity(newcount)
        buf[count] = b.toByte()
        count = newcount
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val newcount = count + len
        ensureCapacity(newcount)
        System.arraycopy(b, off, buf, count, len)
        count = newcount
    }

    private fun ensureCapacity(newcount: Int) {
        if (newcount > buf.size) {
            val newbuf = ByteArray(maxOf(buf.size shl 1, newcount))
            System.arraycopy(buf, 0, newbuf, 0, count)
            buf = newbuf
        }
    }

    fun reset() {
        count = 0
    }

    fun size(): Int = count

    fun getRawData(): ByteArray = buf

    fun newInputStream(): InputStream = ByteArrayInputStream(buf, 0, count)

    fun toByteArray(): ByteArray {
        val result = ByteArray(count)
        System.arraycopy(buf, 0, result, 0, count)
        return result
    }

    override fun toString(): String = String(buf, 0, count)
}
