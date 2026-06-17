/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.compress

import java.io.IOException
import java.io.OutputStream
import org.h2.engine.Constants

/**
 * An output stream to write an LZF stream.
 * The data is automatically compressed.
 */
class LZFOutputStream @Throws(IOException::class) constructor(private val out: OutputStream) : OutputStream() {

    private val compress = CompressLZF()
    private val buffer: ByteArray
    private var pos = 0
    private var outBuffer: ByteArray? = null

    init {
        val len = Constants.IO_BUFFER_SIZE_COMPRESS
        buffer = ByteArray(len)
        ensureOutput(len)
        writeInt(MAGIC)
    }

    private fun ensureOutput(len: Int) {
        // TODO calculate the maximum overhead (worst case) for the output
        // buffer
        val outputLen = (if (len < 100) len + 100 else len) * 2
        if (outBuffer == null || outBuffer!!.size < outputLen) {
            outBuffer = ByteArray(outputLen)
        }
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        if (pos >= buffer.size) {
            flush()
        }
        buffer[pos++] = b.toByte()
    }

    @Throws(IOException::class)
    private fun compressAndWrite(buff: ByteArray, len: Int) {
        if (len > 0) {
            ensureOutput(len)
            val compressed = compress.compress(buff, 0, len, outBuffer!!, 0)
            if (compressed > len) {
                writeInt(-len)
                out.write(buff, 0, len)
            } else {
                writeInt(compressed)
                writeInt(len)
                out.write(outBuffer!!, 0, compressed)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeInt(x: Int) {
        out.write((x shr 24).toByte().toInt())
        out.write((x shr 16).toByte().toInt())
        out.write((x shr 8).toByte().toInt())
        out.write(x.toByte().toInt())
    }

    @Throws(IOException::class)
    override fun write(buff: ByteArray, off: Int, len: Int) {
        var off = off
        var len = len
        while (len > 0) {
            val copy = Math.min(buffer.size - pos, len)
            System.arraycopy(buff, off, buffer, pos, copy)
            pos += copy
            if (pos >= buffer.size) {
                flush()
            }
            off += copy
            len -= copy
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        compressAndWrite(buffer, pos)
        pos = 0
    }

    @Throws(IOException::class)
    override fun close() {
        flush()
        out.close()
    }

    companion object {
        /**
         * The file header of a LZF file.
         */
        const val MAGIC: Int = ('H'.code shl 24) or ('2'.code shl 16) or ('I'.code shl 8) or 'S'.code
    }
}
