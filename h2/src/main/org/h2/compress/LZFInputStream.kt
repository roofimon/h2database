/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.compress

import java.io.IOException
import java.io.InputStream
import org.h2.mvstore.DataUtils
import org.h2.util.Utils

/**
 * An input stream to read from an LZF stream.
 * The data is automatically expanded.
 */
class LZFInputStream @Throws(IOException::class) constructor(private val `in`: InputStream) : InputStream() {

    private var decompress: CompressLZF? = CompressLZF()
    private var pos = 0
    private var bufferLength = 0
    private var inBuffer: ByteArray? = null
    private var buffer: ByteArray? = null

    init {
        if (readInt() != LZFOutputStream.MAGIC) {
            throw IOException("Not an LZFInputStream")
        }
    }

    @Throws(IOException::class)
    private fun fillBuffer() {
        if (buffer != null && pos < bufferLength) {
            return
        }
        var len = readInt()
        if (decompress == null) {
            // EOF
            this.bufferLength = 0
        } else if (len < 0) {
            len = -len
            buffer = ensureSize(buffer, len)
            readFully(buffer!!, len)
            this.bufferLength = len
        } else {
            inBuffer = ensureSize(inBuffer, len)
            val size = readInt()
            readFully(inBuffer!!, len)
            buffer = ensureSize(buffer, size)
            try {
                decompress!!.expand(inBuffer!!, 0, len, buffer!!, 0, size)
            } catch (e: ArrayIndexOutOfBoundsException) {
                throw DataUtils.convertToIOException(e)
            }
            this.bufferLength = size
        }
        pos = 0
    }

    @Throws(IOException::class)
    private fun readFully(buff: ByteArray, len: Int) {
        var len = len
        var off = 0
        while (len > 0) {
            val l = `in`.read(buff, off, len)
            len -= l
            off += l
        }
    }

    @Throws(IOException::class)
    private fun readInt(): Int {
        var x = `in`.read()
        if (x < 0) {
            decompress = null
            return 0
        }
        x = (x shl 24) + (`in`.read() shl 16) + (`in`.read() shl 8) + `in`.read()
        return x
    }

    @Throws(IOException::class)
    override fun read(): Int {
        fillBuffer()
        if (pos >= bufferLength) {
            return -1
        }
        return buffer!![pos++].toInt() and 255
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        var off = off
        var len = len
        if (len == 0) {
            return 0
        }
        var read = 0
        while (len > 0) {
            val r = readBlock(b, off, len)
            if (r < 0) {
                break
            }
            read += r
            off += r
            len -= r
        }
        return if (read == 0) -1 else read
    }

    @Throws(IOException::class)
    private fun readBlock(b: ByteArray, off: Int, len: Int): Int {
        fillBuffer()
        if (pos >= bufferLength) {
            return -1
        }
        var max = Math.min(len, bufferLength - pos)
        max = Math.min(max, b.size - off)
        System.arraycopy(buffer!!, pos, b, off, max)
        pos += max
        return max
    }

    @Throws(IOException::class)
    override fun close() {
        `in`.close()
    }

    companion object {
        private fun ensureSize(buff: ByteArray?, len: Int): ByteArray {
            return if (buff == null || buff.size < len) Utils.newBytes(len) else buff
        }
    }
}
