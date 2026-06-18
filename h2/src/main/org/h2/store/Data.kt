/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 *
 * The variable size number format code is a port from SQLite,
 * but stored in reverse order (least significant bits in the first byte).
 */
package org.h2.store

import org.h2.util.Bits.INT_VH_BE

import java.io.IOException
import java.io.OutputStream
import java.io.Reader

import org.h2.engine.Constants
import org.h2.util.MathUtils.Companion.roundUpInt
import org.h2.util.Utils.Companion.copyBytes

/**
 * This class represents a byte buffer that contains persistent data of a page.
 *
 * @author Thomas Mueller
 * @author Noel Grandin
 * @author Nicolas Fortin, Atelier SIG, IRSTV FR CNRS 24888
 */
class Data private constructor(
    /**
     * The data itself.
     */
    private var data: ByteArray
) {

    /**
     * The current write or read position.
     */
    private var pos: Int = 0

    /**
     * Write an integer at the current position.
     * The current position is incremented.
     *
     * @param x the value
     */
    fun writeInt(x: Int) {
        INT_VH_BE.set(data, pos, x)
        pos += 4
    }

    /**
     * Read an integer at the current position.
     * The current position is incremented.
     *
     * @return the value
     */
    fun readInt(): Int {
        val x = INT_VH_BE.get(data, pos) as Int
        pos += 4
        return x
    }

    private fun writeStringWithoutLength(chars: CharArray, len: Int) {
        var p = pos
        val buff = data
        for (i in 0 until len) {
            val c = chars[i].code
            if (c < 0x80) {
                buff[p++] = c.toByte()
            } else if (c >= 0x800) {
                buff[p++] = (0xe0 or (c shr 12)).toByte()
                buff[p++] = ((c shr 6) and 0x3f).toByte()
                buff[p++] = (c and 0x3f).toByte()
            } else {
                buff[p++] = (0xc0 or (c shr 6)).toByte()
                buff[p++] = (c and 0x3f).toByte()
            }
        }
        pos = p
    }

    /**
     * Get the current write position of this buffer, which is the current
     * length.
     *
     * @return the length
     */
    fun length(): Int {
        return pos
    }

    /**
     * Get the byte array used for this page.
     *
     * @return the byte array
     */
    fun getBytes(): ByteArray {
        return data
    }

    /**
     * Set the position to 0.
     */
    fun reset() {
        pos = 0
    }

    /**
     * Append a number of bytes to this buffer.
     *
     * @param buff the data
     * @param off the offset in the data
     * @param len the length in bytes
     */
    fun write(buff: ByteArray, off: Int, len: Int) {
        System.arraycopy(buff, off, data, pos, len)
        pos += len
    }

    /**
     * Copy a number of bytes to the given buffer from the current position. The
     * current position is incremented accordingly.
     *
     * @param buff the output buffer
     * @param off the offset in the output buffer
     * @param len the number of bytes to copy
     */
    fun read(buff: ByteArray, off: Int, len: Int) {
        System.arraycopy(data, pos, buff, off, len)
        pos += len
    }

    /**
     * Set the current read / write position.
     *
     * @param pos the new position
     */
    fun setPos(pos: Int) {
        this.pos = pos
    }

    /**
     * Read one single byte.
     *
     * @return the value
     */
    fun readByte(): Byte {
        return data[pos++]
    }

    /**
     * Check if there is still enough capacity in the buffer.
     * This method extends the buffer if required.
     *
     * @param plus the number of additional bytes required
     */
    fun checkCapacity(plus: Int) {
        if (pos + plus >= data.size) {
            // a separate method to simplify inlining
            expand(plus)
        }
    }

    private fun expand(plus: Int) {
        // must copy everything, because pos could be 0 and data may be
        // still required
        data = copyBytes(data, (data.size + plus) * 2)
    }

    /**
     * Fill up the buffer with empty space and an (initially empty) checksum
     * until the size is a multiple of Constants.FILE_BLOCK_SIZE.
     */
    fun fillAligned() {
        // 0..6 > 8, 7..14 > 16, 15..22 > 24, ...
        val len = roundUpInt(pos + 2, Constants.FILE_BLOCK_SIZE)
        pos = len
        if (data.size < len) {
            checkCapacity(len - data.size)
        }
    }

    companion object {
        /**
         * Create a new buffer.
         *
         * @param capacity the initial capacity of the buffer
         * @return the buffer
         */
        @JvmStatic
        fun create(capacity: Int): Data {
            return Data(ByteArray(capacity))
        }

        /**
         * Copy a String from a reader to an output stream.
         *
         * @param source the reader
         * @param target the output stream
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copyString(source: Reader, target: OutputStream) {
            val buff = CharArray(Constants.IO_BUFFER_SIZE)
            val d = Data(ByteArray(3 * Constants.IO_BUFFER_SIZE))
            while (true) {
                val l = source.read(buff)
                if (l < 0) {
                    break
                }
                d.writeStringWithoutLength(buff, l)
                target.write(d.data, 0, d.pos)
                d.reset()
            }
        }
    }
}
