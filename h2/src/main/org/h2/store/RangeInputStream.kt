/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

import org.h2.util.IOUtils

/**
 * Input stream that reads only a specified range from the source stream.
 */
class RangeInputStream
/**
 * Creates new instance of range input stream.
 *
 * @param in
 *            source stream
 * @param offset
 *            offset of the range
 * @param limit
 *            length of the range
 * @throws IOException
 *             on I/O exception during seeking to the specified offset
 */
@Throws(IOException::class)
constructor(`in`: InputStream, offset: Long, private var limit: Long) : FilterInputStream(`in`) {

    init {
        IOUtils.skipFully(`in`, offset)
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (limit <= 0) {
            return -1
        }
        val b = `in`.read()
        if (b >= 0) {
            limit--
        }
        return b
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        var len = len
        if (limit <= 0) {
            return -1
        }
        if (len > limit) {
            len = limit.toInt()
        }
        val cnt = `in`.read(b, off, len)
        if (cnt > 0) {
            limit -= cnt.toLong()
        }
        return cnt
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        var n = n
        if (n > limit) {
            n = limit.toInt().toLong()
        }
        n = `in`.skip(n)
        limit -= n
        return n
    }

    @Throws(IOException::class)
    override fun available(): Int {
        val cnt = `in`.available()
        if (cnt > limit) {
            return limit.toInt()
        }
        return cnt
    }

    @Throws(IOException::class)
    override fun close() {
        `in`.close()
    }

    override fun mark(readlimit: Int) {
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        throw IOException("mark/reset not supported")
    }

    override fun markSupported(): Boolean {
        return false
    }
}
