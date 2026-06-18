/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0, and the
 * EPL 1.0 (https://h2database.com/html/license.html). Initial Developer: H2
 * Group
 */
package org.h2.store

import java.io.IOException
import java.io.InputStream
import org.h2.engine.SessionRemote
import org.h2.message.DbException
import org.h2.mvstore.DataUtils

/**
 * An input stream used by the client side of a tcp connection to fetch LOB data
 * on demand from the server.
 */
class LobStorageRemoteInputStream(
    private val sessionRemote: SessionRemote?,
    /**
     * The lob id.
     */
    private val lobId: Long,
    private val hmac: ByteArray?
) : InputStream() {

    /**
     * The position.
     */
    private var pos: Long = 0

    @Throws(IOException::class)
    override fun read(): Int {
        val buff = ByteArray(1)
        val len = read(buff, 0, 1)
        return if (len < 0) len else (buff[0].toInt() and 255)
    }

    @Throws(IOException::class)
    override fun read(buff: ByteArray): Int {
        return read(buff, 0, buff.size)
    }

    @Throws(IOException::class)
    override fun read(buff: ByteArray, off: Int, length: Int): Int {
        assert(length >= 0)
        if (length == 0) {
            return 0
        }
        var len: Int
        try {
            len = sessionRemote!!.readLob(lobId, hmac, pos, buff, off, length)
        } catch (e: DbException) {
            throw DataUtils.convertToIOException(e)
        }
        if (len == 0) {
            return -1
        }
        pos += len.toLong()
        return len
    }
}
