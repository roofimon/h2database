/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.IOException
import java.io.InputStream
import org.h2.engine.Constants
import org.h2.message.DbException
import org.h2.tools.CompressTool
import org.h2.util.Utils

/**
 * An input stream that is backed by a file store.
 */
class FileStoreInputStream(
    private var store: FileStore?,
    compression: Boolean,
    private val alwaysClose: Boolean
) : InputStream() {

    private val page: Data
    private var remainingInBuffer = 0
    private val compress: CompressTool?
    private var endOfFile = false

    init {
        compress = if (compression) {
            CompressTool.getInstance()
        } else {
            null
        }
        page = Data.create(Constants.FILE_BLOCK_SIZE)
        try {
            if (store!!.length() <= FileStore.HEADER_LENGTH.toLong()) {
                close()
            } else {
                fillBuffer()
            }
        } catch (e: IOException) {
            throw DbException.convertIOException(e, store!!.name)
        }
    }

    override fun available(): Int {
        return if (remainingInBuffer <= 0) 0 else remainingInBuffer
    }

    @Throws(IOException::class)
    override fun read(buff: ByteArray): Int {
        return read(buff, 0, buff.size)
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
    private fun readBlock(buff: ByteArray, off: Int, len: Int): Int {
        fillBuffer()
        if (endOfFile) {
            return -1
        }
        val l = Math.min(remainingInBuffer, len)
        page.read(buff, off, l)
        remainingInBuffer -= l
        return l
    }

    @Throws(IOException::class)
    private fun fillBuffer() {
        if (remainingInBuffer > 0 || endOfFile) {
            return
        }
        page.reset()
        store!!.openFile()
        if (store!!.length() == store!!.getFilePointer()) {
            close()
            return
        }
        store!!.readFully(page.getBytes(), 0, Constants.FILE_BLOCK_SIZE)
        page.reset()
        remainingInBuffer = page.readInt()
        if (remainingInBuffer < 0) {
            close()
            return
        }
        page.checkCapacity(remainingInBuffer)
        // get the length to read
        if (compress != null) {
            page.checkCapacity(Integer.BYTES)
            page.readInt()
        }
        page.setPos(page.length() + remainingInBuffer)
        page.fillAligned()
        val len = page.length() - Constants.FILE_BLOCK_SIZE
        page.reset()
        page.readInt()
        store!!.readFully(page.getBytes(), Constants.FILE_BLOCK_SIZE, len)
        page.reset()
        page.readInt()
        if (compress != null) {
            val uncompressed = page.readInt()
            val buff = Utils.newBytes(remainingInBuffer)
            page.read(buff, 0, remainingInBuffer)
            page.reset()
            page.checkCapacity(uncompressed)
            CompressTool.expand(buff, page.getBytes(), 0)
            remainingInBuffer = uncompressed
        }
        if (alwaysClose) {
            store!!.closeFile()
        }
    }

    override fun close() {
        if (store != null) {
            try {
                store!!.close()
                endOfFile = true
            } finally {
                store = null
            }
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        fillBuffer()
        if (endOfFile) {
            return -1
        }
        val i = page.readByte().toInt() and 0xff
        remainingInBuffer--
        return i
    }

}
