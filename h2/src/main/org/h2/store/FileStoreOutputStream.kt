/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.OutputStream
import java.util.Arrays

import org.h2.engine.Constants
import org.h2.tools.CompressTool

/**
 * An output stream that is backed by a file store.
 */
class FileStoreOutputStream(
    store: FileStore,
    compressionAlgorithm: String?
) : OutputStream() {
    private var store: FileStore?
    private val page: Data
    private val compressionAlgorithm: String?
    private val compress: CompressTool?
    private val buffer = byteArrayOf(0)

    init {
        this.store = store
        if (compressionAlgorithm != null) {
            this.compress = CompressTool.getInstance()
            this.compressionAlgorithm = compressionAlgorithm
        } else {
            this.compress = null
            this.compressionAlgorithm = null
        }
        page = Data.create(Constants.FILE_BLOCK_SIZE)
    }

    override fun write(b: Int) {
        buffer[0] = b.toByte()
        write(buffer)
    }

    override fun write(buff: ByteArray) {
        write(buff, 0, buff.size)
    }

    override fun write(buff: ByteArray, off: Int, len: Int) {
        var buff = buff
        var off = off
        var len = len
        if (len > 0) {
            page.reset()
            if (compress != null) {
                if (off != 0 || len != buff.size) {
                    buff = Arrays.copyOfRange(buff, off, off + len)
                    off = 0
                }
                val uncompressed = len
                buff = compress.compress(buff, compressionAlgorithm)
                len = buff.size
                page.checkCapacity(2 * Integer.BYTES + len)
                page.writeInt(len)
                page.writeInt(uncompressed)
                page.write(buff, off, len)
            } else {
                page.checkCapacity(Integer.BYTES + len)
                page.writeInt(len)
                page.write(buff, off, len)
            }
            page.fillAligned()
            store!!.write(page.getBytes(), 0, page.length())
        }
    }

    override fun close() {
        if (store != null) {
            try {
                store!!.close()
            } finally {
                store = null
            }
        }
    }

}
