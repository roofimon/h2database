/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.security

import org.h2.util.Bits.INT_VH_BE
import org.h2.util.Bits.LONG_VH_BE

/**
 * A pseudo-encryption algorithm that makes the data appear to be
 * encrypted. This algorithm is cryptographically extremely weak, and should
 * only be used to hide data from reading the plain text using a text editor.
 */
class Fog : BlockCipher {

    private var key = 0

    override fun encrypt(bytes: ByteArray, off: Int, len: Int) {
        var i = off
        while (i < off + len) {
            encryptBlock(bytes, bytes, i)
            i += 16
        }
    }

    override fun decrypt(bytes: ByteArray, off: Int, len: Int) {
        var i = off
        while (i < off + len) {
            decryptBlock(bytes, bytes, i)
            i += 16
        }
    }

    private fun encryptBlock(`in`: ByteArray, out: ByteArray, off: Int) {
        var x0 = INT_VH_BE.get(`in`, off) as Int
        var x1 = INT_VH_BE.get(`in`, off + 4) as Int
        var x2 = INT_VH_BE.get(`in`, off + 8) as Int
        var x3 = INT_VH_BE.get(`in`, off + 12) as Int
        val k = key
        x0 = Integer.rotateLeft(x0 xor k, x1)
        x2 = Integer.rotateLeft(x2 xor k, x1)
        x1 = Integer.rotateLeft(x1 xor k, x0)
        x3 = Integer.rotateLeft(x3 xor k, x0)
        INT_VH_BE.set(out, off, x0)
        INT_VH_BE.set(out, off + 4, x1)
        INT_VH_BE.set(out, off + 8, x2)
        INT_VH_BE.set(out, off + 12, x3)
    }

    private fun decryptBlock(`in`: ByteArray, out: ByteArray, off: Int) {
        var x0 = INT_VH_BE.get(`in`, off) as Int
        var x1 = INT_VH_BE.get(`in`, off + 4) as Int
        var x2 = INT_VH_BE.get(`in`, off + 8) as Int
        var x3 = INT_VH_BE.get(`in`, off + 12) as Int
        val k = key
        x1 = Integer.rotateRight(x1, x0) xor k
        x3 = Integer.rotateRight(x3, x0) xor k
        x0 = Integer.rotateRight(x0, x1) xor k
        x2 = Integer.rotateRight(x2, x1) xor k
        INT_VH_BE.set(out, off, x0)
        INT_VH_BE.set(out, off + 4, x1)
        INT_VH_BE.set(out, off + 8, x2)
        INT_VH_BE.set(out, off + 12, x3)
    }

    override fun getKeyLength(): Int {
        return 16
    }

    override fun setKey(key: ByteArray) {
        this.key = (LONG_VH_BE.get(key, 0) as Long).toInt()
    }
}
