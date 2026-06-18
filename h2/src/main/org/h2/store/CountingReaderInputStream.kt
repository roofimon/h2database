/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

import org.h2.engine.Constants

/**
 * An input stream that reads the data from a reader and limits the number of
 * bytes that can be read.
 */
class CountingReaderInputStream(
    private val reader: Reader,
    maxLength: Long
) : InputStream() {

    private val charBuffer: CharBuffer =
        CharBuffer.allocate(Constants.IO_BUFFER_SIZE)

    private val encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private var byteBuffer: ByteBuffer? = ByteBuffer.allocate(0)
    private var length: Long = 0
    private var remaining: Long = maxLength

    @Throws(IOException::class)
    override fun read(buff: ByteArray, offset: Int, len: Int): Int {
        var len = len
        if (!fetch()) {
            return -1
        }
        len = Math.min(len, byteBuffer!!.remaining())
        byteBuffer!!.get(buff, offset, len)
        return len
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (!fetch()) {
            return -1
        }
        return byteBuffer!!.get().toInt() and 255
    }

    @Throws(IOException::class)
    private fun fetch(): Boolean {
        if (byteBuffer != null && byteBuffer!!.remaining() == 0) {
            fillBuffer()
        }
        return byteBuffer != null
    }

    @Throws(IOException::class)
    private fun fillBuffer() {
        var len = Math.min(
            (charBuffer.capacity() - charBuffer.position()).toLong(),
            remaining
        ).toInt()
        if (len > 0) {
            len = reader.read(charBuffer.array(), charBuffer.position(), len)
        }
        if (len > 0) {
            remaining -= len.toLong()
        } else {
            len = 0
            remaining = 0
        }
        length += len.toLong()
        charBuffer.limit(charBuffer.position() + len)
        charBuffer.rewind()
        byteBuffer = ByteBuffer.allocate(Constants.IO_BUFFER_SIZE)
        val end = remaining == 0L
        encoder.encode(charBuffer, byteBuffer, end)
        if (end && byteBuffer!!.position() == 0) {
            // EOF
            byteBuffer = null
            return
        }
        byteBuffer!!.flip()
        charBuffer.compact()
        charBuffer.flip()
        charBuffer.position(charBuffer.limit())
    }

    /**
     * The number of characters read so far (but there might still be some bytes
     * in the buffer).
     *
     * @return the number of characters
     */
    fun getLength(): Long {
        return length
    }

    @Throws(IOException::class)
    override fun close() {
        reader.close()
    }

}
