/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.security

import org.h2.util.Bits.INT_VH_BE
import org.h2.message.DbException

/**
 * An implementation of the XTEA block cipher algorithm.
 *
 * This implementation uses 32 rounds.
 * The best attack reported as of 2009 is 36 rounds (Wikipedia).
 */
class XTEA : BlockCipher {

    private var k0 = 0
    private var k1 = 0
    private var k2 = 0
    private var k3 = 0
    private var k4 = 0
    private var k5 = 0
    private var k6 = 0
    private var k7 = 0
    private var k8 = 0
    private var k9 = 0
    private var k10 = 0
    private var k11 = 0
    private var k12 = 0
    private var k13 = 0
    private var k14 = 0
    private var k15 = 0
    private var k16 = 0
    private var k17 = 0
    private var k18 = 0
    private var k19 = 0
    private var k20 = 0
    private var k21 = 0
    private var k22 = 0
    private var k23 = 0
    private var k24 = 0
    private var k25 = 0
    private var k26 = 0
    private var k27 = 0
    private var k28 = 0
    private var k29 = 0
    private var k30 = 0
    private var k31 = 0

    override fun setKey(b: ByteArray) {
        val key = kotlin.IntArray(4)
        var i = 0
        while (i < 16) {
            key[i / 4] = INT_VH_BE.get(b, i) as Int
            i += 4
        }
        val r = kotlin.IntArray(32)
        run {
            var j = 0
            var sum = 0
            while (j < 32) {
                r[j++] = sum + key[sum and 3]
                sum += DELTA
                r[j++] = sum + key[(sum ushr 11) and 3]
            }
        }
        k0 = r[0]; k1 = r[1]; k2 = r[2]; k3 = r[3]
        k4 = r[4]; k5 = r[5]; k6 = r[6]; k7 = r[7]
        k8 = r[8]; k9 = r[9]; k10 = r[10]; k11 = r[11]
        k12 = r[12]; k13 = r[13]; k14 = r[14]; k15 = r[15]
        k16 = r[16]; k17 = r[17]; k18 = r[18]; k19 = r[19]
        k20 = r[20]; k21 = r[21]; k22 = r[22]; k23 = r[23]
        k24 = r[24]; k25 = r[25]; k26 = r[26]; k27 = r[27]
        k28 = r[28]; k29 = r[29]; k30 = r[30]; k31 = r[31]
    }

    override fun encrypt(bytes: ByteArray, off: Int, len: Int) {
        if (len % BlockCipher.ALIGN != 0) {
            throw DbException.getInternalError("unaligned len $len")
        }
        var i = off
        while (i < off + len) {
            encryptBlock(bytes, bytes, i)
            i += 8
        }
    }

    override fun decrypt(bytes: ByteArray, off: Int, len: Int) {
        if (len % BlockCipher.ALIGN != 0) {
            throw DbException.getInternalError("unaligned len $len")
        }
        var i = off
        while (i < off + len) {
            decryptBlock(bytes, bytes, i)
            i += 8
        }
    }

    private fun encryptBlock(`in`: ByteArray, out: ByteArray, off: Int) {
        var y = INT_VH_BE.get(`in`, off) as Int
        var z = INT_VH_BE.get(`in`, off + 4) as Int
        y += (((z shl 4) xor (z ushr 5)) + z) xor k0
        z += (((y ushr 5) xor (y shl 4)) + y) xor k1
        y += (((z shl 4) xor (z ushr 5)) + z) xor k2
        z += (((y ushr 5) xor (y shl 4)) + y) xor k3
        y += (((z shl 4) xor (z ushr 5)) + z) xor k4
        z += (((y ushr 5) xor (y shl 4)) + y) xor k5
        y += (((z shl 4) xor (z ushr 5)) + z) xor k6
        z += (((y ushr 5) xor (y shl 4)) + y) xor k7
        y += (((z shl 4) xor (z ushr 5)) + z) xor k8
        z += (((y ushr 5) xor (y shl 4)) + y) xor k9
        y += (((z shl 4) xor (z ushr 5)) + z) xor k10
        z += (((y ushr 5) xor (y shl 4)) + y) xor k11
        y += (((z shl 4) xor (z ushr 5)) + z) xor k12
        z += (((y ushr 5) xor (y shl 4)) + y) xor k13
        y += (((z shl 4) xor (z ushr 5)) + z) xor k14
        z += (((y ushr 5) xor (y shl 4)) + y) xor k15
        y += (((z shl 4) xor (z ushr 5)) + z) xor k16
        z += (((y ushr 5) xor (y shl 4)) + y) xor k17
        y += (((z shl 4) xor (z ushr 5)) + z) xor k18
        z += (((y ushr 5) xor (y shl 4)) + y) xor k19
        y += (((z shl 4) xor (z ushr 5)) + z) xor k20
        z += (((y ushr 5) xor (y shl 4)) + y) xor k21
        y += (((z shl 4) xor (z ushr 5)) + z) xor k22
        z += (((y ushr 5) xor (y shl 4)) + y) xor k23
        y += (((z shl 4) xor (z ushr 5)) + z) xor k24
        z += (((y ushr 5) xor (y shl 4)) + y) xor k25
        y += (((z shl 4) xor (z ushr 5)) + z) xor k26
        z += (((y ushr 5) xor (y shl 4)) + y) xor k27
        y += (((z shl 4) xor (z ushr 5)) + z) xor k28
        z += (((y ushr 5) xor (y shl 4)) + y) xor k29
        y += (((z shl 4) xor (z ushr 5)) + z) xor k30
        z += (((y ushr 5) xor (y shl 4)) + y) xor k31
        INT_VH_BE.set(out, off, y)
        INT_VH_BE.set(out, off + 4, z)
    }

    private fun decryptBlock(`in`: ByteArray, out: ByteArray, off: Int) {
        var y = INT_VH_BE.get(`in`, off) as Int
        var z = INT_VH_BE.get(`in`, off + 4) as Int
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k31
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k30
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k29
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k28
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k27
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k26
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k25
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k24
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k23
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k22
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k21
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k20
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k19
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k18
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k17
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k16
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k15
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k14
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k13
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k12
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k11
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k10
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k9
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k8
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k7
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k6
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k5
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k4
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k3
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k2
        z -= (((y ushr 5) xor (y shl 4)) + y) xor k1
        y -= (((z shl 4) xor (z ushr 5)) + z) xor k0
        INT_VH_BE.set(out, off, y)
        INT_VH_BE.set(out, off + 4, z)
    }

    override fun getKeyLength(): Int {
        return 16
    }

    companion object {
        private const val DELTA = -0x61c88647
    }
}
