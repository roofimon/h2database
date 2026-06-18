/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.IOException
import java.io.Reader

import org.h2.util.IOUtils

/**
 * Reader that reads only a specified range from the source reader.
 */
class RangeReader
/**
 * Creates new instance of range reader.
 *
 * @param r
 *            source reader
 * @param offset
 *            offset of the range
 * @param limit
 *            length of the range
 * @throws IOException
 *             on I/O exception during seeking to the specified offset
 */
@Throws(IOException::class)
constructor(private val r: Reader, offset: Long, private var limit: Long) : Reader() {

    init {
        IOUtils.skipFully(r, offset)
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (limit <= 0) {
            return -1
        }
        val c = r.read()
        if (c >= 0) {
            limit--
        }
        return c
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var len = len
        if (limit <= 0) {
            return -1
        }
        if (len > limit) {
            len = limit.toInt()
        }
        val cnt = r.read(cbuf, off, len)
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
        n = r.skip(n)
        limit -= n
        return n
    }

    @Throws(IOException::class)
    override fun ready(): Boolean {
        if (limit > 0) {
            return r.ready()
        }
        return false
    }

    override fun markSupported(): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun mark(readAheadLimit: Int) {
        throw IOException("mark() not supported")
    }

    @Throws(IOException::class)
    override fun reset() {
        throw IOException("reset() not supported")
    }

    @Throws(IOException::class)
    override fun close() {
        r.close()
    }
}
