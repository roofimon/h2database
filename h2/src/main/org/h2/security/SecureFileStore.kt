/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.security

import org.h2.util.Bits.LONG_VH_BE
import org.h2.engine.Constants
import org.h2.store.DataHandler
import org.h2.store.FileStore
import org.h2.util.MathUtils

/**
 * A file store that encrypts all data before writing, and decrypts all data
 * after reading. Areas that were never written to (for example after calling
 * setLength to enlarge the file) are not encrypted (contains 0 bytes).
 */
class SecureFileStore(
    handler: DataHandler, name: String, mode: String,
    cipher: String, key: ByteArray, keyIterations: Int
) : FileStore(handler, name, mode) {

    private var key: ByteArray = key
    private val cipher: BlockCipher = CipherFactory.getBlockCipher(cipher)
    private val cipherForInitVector: BlockCipher = CipherFactory.getBlockCipher(cipher)
    private var buffer = ByteArray(4)
    private var pos: Long = 0
    private val bufferForInitVector: ByteArray = ByteArray(Constants.FILE_BLOCK_SIZE)
    private val keyIterations: Int = keyIterations

    override protected fun generateSalt(): ByteArray {
        return MathUtils.secureRandomBytes(Constants.FILE_BLOCK_SIZE)
    }

    override protected fun initKey(salt: ByteArray) {
        key = SHA256.getHashWithSalt(key, salt)
        for (i in 0 until keyIterations) {
            key = SHA256.getHash(key, true)
        }
        cipher.setKey(key)
        key = SHA256.getHash(key, true)
        cipherForInitVector.setKey(key)
    }

    override protected fun writeDirect(b: ByteArray, off: Int, len: Int) {
        super.write(b, off, len)
        pos += len
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (buffer.size < b.size) {
            buffer = ByteArray(len)
        }
        System.arraycopy(b, off, buffer, 0, len)
        xorInitVector(buffer, 0, len, pos)
        cipher.encrypt(buffer, 0, len)
        super.write(buffer, 0, len)
        pos += len
    }

    override fun readFullyDirect(b: ByteArray, off: Int, len: Int) {
        super.readFully(b, off, len)
        pos += len
    }

    override fun readFully(b: ByteArray, off: Int, len: Int) {
        super.readFully(b, off, len)
        for (i in 0 until len) {
            if (b[i].toInt() != 0) {
                cipher.decrypt(b, off, len)
                xorInitVector(b, off, len, pos)
                break
            }
        }
        pos += len
    }

    override fun seek(x: Long) {
        this.pos = x
        super.seek(x)
    }

    private fun xorInitVector(b: ByteArray, off: Int, len: Int, p: Long) {
        var off = off
        var len = len
        var p = p
        val iv = bufferForInitVector
        while (len > 0) {
            var i = 0
            while (i < Constants.FILE_BLOCK_SIZE) {
                LONG_VH_BE.set(iv, i, (p + i) ushr 3)
                i += 8
            }
            cipherForInitVector.encrypt(iv, 0, Constants.FILE_BLOCK_SIZE)
            for (j in 0 until Constants.FILE_BLOCK_SIZE) {
                b[off + j] = (b[off + j].toInt() xor iv[j].toInt()).toByte()
            }
            p += Constants.FILE_BLOCK_SIZE
            off += Constants.FILE_BLOCK_SIZE
            len -= Constants.FILE_BLOCK_SIZE
        }
    }
}
