/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/**
 * This class is backed by an input stream and supports reading values and
 * variable size data.
 */
class DataReader(
    private val `in`: InputStream
) : Reader() {

    /**
     * Read a byte.
     *
     * @return the byte
     * @throws IOException on failure
     */
    @Throws(IOException::class)
    fun readByte(): Byte {
        val x = `in`.read()
        if (x < 0) {
            throw FastEOFException()
        }
        return x.toByte()
    }

    /**
     * Read a variable size integer.
     *
     * @return the value
     * @throws IOException on failure
     */
    @Throws(IOException::class)
    fun readVarInt(): Int {
        var b = readByte().toInt()
        if (b >= 0) {
            return b
        }
        var x = b and 0x7f
        b = readByte().toInt()
        if (b >= 0) {
            return x or (b shl 7)
        }
        x = x or ((b and 0x7f) shl 7)
        b = readByte().toInt()
        if (b >= 0) {
            return x or (b shl 14)
        }
        x = x or ((b and 0x7f) shl 14)
        b = readByte().toInt()
        if (b >= 0) {
            return x or (b shl 21)
        }
        return x or ((b and 0x7f) shl 21) or (readByte().toInt() shl 28)
    }

    /**
     * Read one character from the input stream.
     *
     * @return the character
     */
    @Throws(IOException::class)
    private fun readChar(): Char {
        val x = readByte().toInt() and 0xff
        return if (x < 0x80) {
            x.toChar()
        } else if (x >= 0xe0) {
            (((x and 0xf) shl 12) +
                    ((readByte().toInt() and 0x3f) shl 6) +
                    (readByte().toInt() and 0x3f)).toChar()
        } else {
            (((x and 0x1f) shl 6) +
                    (readByte().toInt() and 0x3f)).toChar()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        // ignore
    }

    @Throws(IOException::class)
    override fun read(buff: CharArray, off: Int, len: Int): Int {
        if (len == 0) {
            return 0
        }
        var i = 0
        try {
            while (i < len) {
                buff[off + i] = readChar()
                i++
            }
            return len
        } catch (e: EOFException) {
            if (i == 0) {
                return -1
            }
            return i
        }
    }

    /**
     * Constructing such an EOF exception is fast, because the stack trace is
     * not filled in. If used in a static context, this will also avoid
     * classloader memory leaks.
     */
    internal class FastEOFException : EOFException() {

        @Synchronized
        override fun fillInStackTrace(): Throwable? {
            return null
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }

}
