/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.security

import org.h2.util.Bits.INT_VH_BE

/**
 * An implementation of the AES block cipher algorithm,
 * also known as Rijndael. Only AES-128 is supported by this class.
 */
class AES : BlockCipher {

    private val encKey = kotlin.IntArray(44)
    private val decKey = kotlin.IntArray(44)

    override fun setKey(key: ByteArray) {
        var j = 0
        for (i in 0 until 4) {
            encKey[i] = ((key[j++].toInt() and 255) shl 24) or
                    ((key[j++].toInt() and 255) shl 16) or ((key[j++].toInt() and 255) shl 8) or
                    (key[j++].toInt() and 255)
            decKey[i] = encKey[i]
        }
        var e = 0
        var i = 0
        while (i < 10) {
            encKey[e + 4] = encKey[e] xor RCON[i] xor
                    (FS[(encKey[e + 3] shr 16) and 255] shl 24) xor
                    (FS[(encKey[e + 3] shr 8) and 255] shl 16) xor
                    (FS[encKey[e + 3] and 255] shl 8) xor
                    FS[(encKey[e + 3] shr 24) and 255]
            encKey[e + 5] = encKey[e + 1] xor encKey[e + 4]
            encKey[e + 6] = encKey[e + 2] xor encKey[e + 5]
            encKey[e + 7] = encKey[e + 3] xor encKey[e + 6]
            i++
            e += 4
        }
        var d = 0
        decKey[d++] = encKey[e++]
        decKey[d++] = encKey[e++]
        decKey[d++] = encKey[e++]
        decKey[d++] = encKey[e++]
        for (ii in 1 until 10) {
            e -= 8
            decKey[d++] = getDec(encKey[e++])
            decKey[d++] = getDec(encKey[e++])
            decKey[d++] = getDec(encKey[e++])
            decKey[d++] = getDec(encKey[e++])
        }
        e -= 8
        decKey[d++] = encKey[e++]
        decKey[d++] = encKey[e++]
        decKey[d++] = encKey[e++]
        decKey[d] = encKey[e]
    }

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
        val k = encKey
        var x0 = (INT_VH_BE.get(`in`, off) as Int) xor k[0]
        var x1 = (INT_VH_BE.get(`in`, off + 4) as Int) xor k[1]
        var x2 = (INT_VH_BE.get(`in`, off + 8) as Int) xor k[2]
        var x3 = (INT_VH_BE.get(`in`, off + 12) as Int) xor k[3]
        var y0 = FT0[(x0 shr 24) and 255] xor FT1[(x1 shr 16) and 255] xor
                FT2[(x2 shr 8) and 255] xor FT3[x3 and 255] xor k[4]
        var y1 = FT0[(x1 shr 24) and 255] xor FT1[(x2 shr 16) and 255] xor
                FT2[(x3 shr 8) and 255] xor FT3[x0 and 255] xor k[5]
        var y2 = FT0[(x2 shr 24) and 255] xor FT1[(x3 shr 16) and 255] xor
                FT2[(x0 shr 8) and 255] xor FT3[x1 and 255] xor k[6]
        var y3 = FT0[(x3 shr 24) and 255] xor FT1[(x0 shr 16) and 255] xor
                FT2[(x1 shr 8) and 255] xor FT3[x2 and 255] xor k[7]
        x0 = FT0[(y0 shr 24) and 255] xor FT1[(y1 shr 16) and 255] xor
                FT2[(y2 shr 8) and 255] xor FT3[y3 and 255] xor k[8]
        x1 = FT0[(y1 shr 24) and 255] xor FT1[(y2 shr 16) and 255] xor
                FT2[(y3 shr 8) and 255] xor FT3[y0 and 255] xor k[9]
        x2 = FT0[(y2 shr 24) and 255] xor FT1[(y3 shr 16) and 255] xor
                FT2[(y0 shr 8) and 255] xor FT3[y1 and 255] xor k[10]
        x3 = FT0[(y3 shr 24) and 255] xor FT1[(y0 shr 16) and 255] xor
                FT2[(y1 shr 8) and 255] xor FT3[y2 and 255] xor k[11]
        y0 = FT0[(x0 shr 24) and 255] xor FT1[(x1 shr 16) and 255] xor
                FT2[(x2 shr 8) and 255] xor FT3[x3 and 255] xor k[12]
        y1 = FT0[(x1 shr 24) and 255] xor FT1[(x2 shr 16) and 255] xor
                FT2[(x3 shr 8) and 255] xor FT3[x0 and 255] xor k[13]
        y2 = FT0[(x2 shr 24) and 255] xor FT1[(x3 shr 16) and 255] xor
                FT2[(x0 shr 8) and 255] xor FT3[x1 and 255] xor k[14]
        y3 = FT0[(x3 shr 24) and 255] xor FT1[(x0 shr 16) and 255] xor
                FT2[(x1 shr 8) and 255] xor FT3[x2 and 255] xor k[15]
        x0 = FT0[(y0 shr 24) and 255] xor FT1[(y1 shr 16) and 255] xor
                FT2[(y2 shr 8) and 255] xor FT3[y3 and 255] xor k[16]
        x1 = FT0[(y1 shr 24) and 255] xor FT1[(y2 shr 16) and 255] xor
                FT2[(y3 shr 8) and 255] xor FT3[y0 and 255] xor k[17]
        x2 = FT0[(y2 shr 24) and 255] xor FT1[(y3 shr 16) and 255] xor
                FT2[(y0 shr 8) and 255] xor FT3[y1 and 255] xor k[18]
        x3 = FT0[(y3 shr 24) and 255] xor FT1[(y0 shr 16) and 255] xor
                FT2[(y1 shr 8) and 255] xor FT3[y2 and 255] xor k[19]
        y0 = FT0[(x0 shr 24) and 255] xor FT1[(x1 shr 16) and 255] xor
                FT2[(x2 shr 8) and 255] xor FT3[x3 and 255] xor k[20]
        y1 = FT0[(x1 shr 24) and 255] xor FT1[(x2 shr 16) and 255] xor
                FT2[(x3 shr 8) and 255] xor FT3[x0 and 255] xor k[21]
        y2 = FT0[(x2 shr 24) and 255] xor FT1[(x3 shr 16) and 255] xor
                FT2[(x0 shr 8) and 255] xor FT3[x1 and 255] xor k[22]
        y3 = FT0[(x3 shr 24) and 255] xor FT1[(x0 shr 16) and 255] xor
                FT2[(x1 shr 8) and 255] xor FT3[x2 and 255] xor k[23]
        x0 = FT0[(y0 shr 24) and 255] xor FT1[(y1 shr 16) and 255] xor
                FT2[(y2 shr 8) and 255] xor FT3[y3 and 255] xor k[24]
        x1 = FT0[(y1 shr 24) and 255] xor FT1[(y2 shr 16) and 255] xor
                FT2[(y3 shr 8) and 255] xor FT3[y0 and 255] xor k[25]
        x2 = FT0[(y2 shr 24) and 255] xor FT1[(y3 shr 16) and 255] xor
                FT2[(y0 shr 8) and 255] xor FT3[y1 and 255] xor k[26]
        x3 = FT0[(y3 shr 24) and 255] xor FT1[(y0 shr 16) and 255] xor
                FT2[(y1 shr 8) and 255] xor FT3[y2 and 255] xor k[27]
        y0 = FT0[(x0 shr 24) and 255] xor FT1[(x1 shr 16) and 255] xor
                FT2[(x2 shr 8) and 255] xor FT3[x3 and 255] xor k[28]
        y1 = FT0[(x1 shr 24) and 255] xor FT1[(x2 shr 16) and 255] xor
                FT2[(x3 shr 8) and 255] xor FT3[x0 and 255] xor k[29]
        y2 = FT0[(x2 shr 24) and 255] xor FT1[(x3 shr 16) and 255] xor
                FT2[(x0 shr 8) and 255] xor FT3[x1 and 255] xor k[30]
        y3 = FT0[(x3 shr 24) and 255] xor FT1[(x0 shr 16) and 255] xor
                FT2[(x1 shr 8) and 255] xor FT3[x2 and 255] xor k[31]
        x0 = FT0[(y0 shr 24) and 255] xor FT1[(y1 shr 16) and 255] xor
                FT2[(y2 shr 8) and 255] xor FT3[y3 and 255] xor k[32]
        x1 = FT0[(y1 shr 24) and 255] xor FT1[(y2 shr 16) and 255] xor
                FT2[(y3 shr 8) and 255] xor FT3[y0 and 255] xor k[33]
        x2 = FT0[(y2 shr 24) and 255] xor FT1[(y3 shr 16) and 255] xor
                FT2[(y0 shr 8) and 255] xor FT3[y1 and 255] xor k[34]
        x3 = FT0[(y3 shr 24) and 255] xor FT1[(y0 shr 16) and 255] xor
                FT2[(y1 shr 8) and 255] xor FT3[y2 and 255] xor k[35]
        y0 = FT0[(x0 shr 24) and 255] xor FT1[(x1 shr 16) and 255] xor
                FT2[(x2 shr 8) and 255] xor FT3[x3 and 255] xor k[36]
        y1 = FT0[(x1 shr 24) and 255] xor FT1[(x2 shr 16) and 255] xor
                FT2[(x3 shr 8) and 255] xor FT3[x0 and 255] xor k[37]
        y2 = FT0[(x2 shr 24) and 255] xor FT1[(x3 shr 16) and 255] xor
                FT2[(x0 shr 8) and 255] xor FT3[x1 and 255] xor k[38]
        y3 = FT0[(x3 shr 24) and 255] xor FT1[(x0 shr 16) and 255] xor
                FT2[(x1 shr 8) and 255] xor FT3[x2 and 255] xor k[39]
        x0 = ((FS[(y0 shr 24) and 255] shl 24) or (FS[(y1 shr 16) and 255] shl 16) or
                (FS[(y2 shr 8) and 255] shl 8) or FS[y3 and 255]) xor k[40]
        x1 = ((FS[(y1 shr 24) and 255] shl 24) or (FS[(y2 shr 16) and 255] shl 16) or
                (FS[(y3 shr 8) and 255] shl 8) or FS[y0 and 255]) xor k[41]
        x2 = ((FS[(y2 shr 24) and 255] shl 24) or (FS[(y3 shr 16) and 255] shl 16) or
                (FS[(y0 shr 8) and 255] shl 8) or FS[y1 and 255]) xor k[42]
        x3 = ((FS[(y3 shr 24) and 255] shl 24) or (FS[(y0 shr 16) and 255] shl 16) or
                (FS[(y1 shr 8) and 255] shl 8) or FS[y2 and 255]) xor k[43]
        INT_VH_BE.set(out, off, x0)
        INT_VH_BE.set(out, off + 4, x1)
        INT_VH_BE.set(out, off + 8, x2)
        INT_VH_BE.set(out, off + 12, x3)
    }

    private fun decryptBlock(`in`: ByteArray, out: ByteArray, off: Int) {
        val k = decKey
        var x0 = (INT_VH_BE.get(`in`, off) as Int) xor k[0]
        var x1 = (INT_VH_BE.get(`in`, off + 4) as Int) xor k[1]
        var x2 = (INT_VH_BE.get(`in`, off + 8) as Int) xor k[2]
        var x3 = (INT_VH_BE.get(`in`, off + 12) as Int) xor k[3]
        var y0 = RT0[(x0 shr 24) and 255] xor RT1[(x3 shr 16) and 255] xor
                RT2[(x2 shr 8) and 255] xor RT3[x1 and 255] xor k[4]
        var y1 = RT0[(x1 shr 24) and 255] xor RT1[(x0 shr 16) and 255] xor
                RT2[(x3 shr 8) and 255] xor RT3[x2 and 255] xor k[5]
        var y2 = RT0[(x2 shr 24) and 255] xor RT1[(x1 shr 16) and 255] xor
                RT2[(x0 shr 8) and 255] xor RT3[x3 and 255] xor k[6]
        var y3 = RT0[(x3 shr 24) and 255] xor RT1[(x2 shr 16) and 255] xor
                RT2[(x1 shr 8) and 255] xor RT3[x0 and 255] xor k[7]
        x0 = RT0[(y0 shr 24) and 255] xor RT1[(y3 shr 16) and 255] xor
                RT2[(y2 shr 8) and 255] xor RT3[y1 and 255] xor k[8]
        x1 = RT0[(y1 shr 24) and 255] xor RT1[(y0 shr 16) and 255] xor
                RT2[(y3 shr 8) and 255] xor RT3[y2 and 255] xor k[9]
        x2 = RT0[(y2 shr 24) and 255] xor RT1[(y1 shr 16) and 255] xor
                RT2[(y0 shr 8) and 255] xor RT3[y3 and 255] xor k[10]
        x3 = RT0[(y3 shr 24) and 255] xor RT1[(y2 shr 16) and 255] xor
                RT2[(y1 shr 8) and 255] xor RT3[y0 and 255] xor k[11]
        y0 = RT0[(x0 shr 24) and 255] xor RT1[(x3 shr 16) and 255] xor
                RT2[(x2 shr 8) and 255] xor RT3[x1 and 255] xor k[12]
        y1 = RT0[(x1 shr 24) and 255] xor RT1[(x0 shr 16) and 255] xor
                RT2[(x3 shr 8) and 255] xor RT3[x2 and 255] xor k[13]
        y2 = RT0[(x2 shr 24) and 255] xor RT1[(x1 shr 16) and 255] xor
                RT2[(x0 shr 8) and 255] xor RT3[x3 and 255] xor k[14]
        y3 = RT0[(x3 shr 24) and 255] xor RT1[(x2 shr 16) and 255] xor
                RT2[(x1 shr 8) and 255] xor RT3[x0 and 255] xor k[15]
        x0 = RT0[(y0 shr 24) and 255] xor RT1[(y3 shr 16) and 255] xor
                RT2[(y2 shr 8) and 255] xor RT3[y1 and 255] xor k[16]
        x1 = RT0[(y1 shr 24) and 255] xor RT1[(y0 shr 16) and 255] xor
                RT2[(y3 shr 8) and 255] xor RT3[y2 and 255] xor k[17]
        x2 = RT0[(y2 shr 24) and 255] xor RT1[(y1 shr 16) and 255] xor
                RT2[(y0 shr 8) and 255] xor RT3[y3 and 255] xor k[18]
        x3 = RT0[(y3 shr 24) and 255] xor RT1[(y2 shr 16) and 255] xor
                RT2[(y1 shr 8) and 255] xor RT3[y0 and 255] xor k[19]
        y0 = RT0[(x0 shr 24) and 255] xor RT1[(x3 shr 16) and 255] xor
                RT2[(x2 shr 8) and 255] xor RT3[x1 and 255] xor k[20]
        y1 = RT0[(x1 shr 24) and 255] xor RT1[(x0 shr 16) and 255] xor
                RT2[(x3 shr 8) and 255] xor RT3[x2 and 255] xor k[21]
        y2 = RT0[(x2 shr 24) and 255] xor RT1[(x1 shr 16) and 255] xor
                RT2[(x0 shr 8) and 255] xor RT3[x3 and 255] xor k[22]
        y3 = RT0[(x3 shr 24) and 255] xor RT1[(x2 shr 16) and 255] xor
                RT2[(x1 shr 8) and 255] xor RT3[x0 and 255] xor k[23]
        x0 = RT0[(y0 shr 24) and 255] xor RT1[(y3 shr 16) and 255] xor
                RT2[(y2 shr 8) and 255] xor RT3[y1 and 255] xor k[24]
        x1 = RT0[(y1 shr 24) and 255] xor RT1[(y0 shr 16) and 255] xor
                RT2[(y3 shr 8) and 255] xor RT3[y2 and 255] xor k[25]
        x2 = RT0[(y2 shr 24) and 255] xor RT1[(y1 shr 16) and 255] xor
                RT2[(y0 shr 8) and 255] xor RT3[y3 and 255] xor k[26]
        x3 = RT0[(y3 shr 24) and 255] xor RT1[(y2 shr 16) and 255] xor
                RT2[(y1 shr 8) and 255] xor RT3[y0 and 255] xor k[27]
        y0 = RT0[(x0 shr 24) and 255] xor RT1[(x3 shr 16) and 255] xor
                RT2[(x2 shr 8) and 255] xor RT3[x1 and 255] xor k[28]
        y1 = RT0[(x1 shr 24) and 255] xor RT1[(x0 shr 16) and 255] xor
                RT2[(x3 shr 8) and 255] xor RT3[x2 and 255] xor k[29]
        y2 = RT0[(x2 shr 24) and 255] xor RT1[(x1 shr 16) and 255] xor
                RT2[(x0 shr 8) and 255] xor RT3[x3 and 255] xor k[30]
        y3 = RT0[(x3 shr 24) and 255] xor RT1[(x2 shr 16) and 255] xor
                RT2[(x1 shr 8) and 255] xor RT3[x0 and 255] xor k[31]
        x0 = RT0[(y0 shr 24) and 255] xor RT1[(y3 shr 16) and 255] xor
                RT2[(y2 shr 8) and 255] xor RT3[y1 and 255] xor k[32]
        x1 = RT0[(y1 shr 24) and 255] xor RT1[(y0 shr 16) and 255] xor
                RT2[(y3 shr 8) and 255] xor RT3[y2 and 255] xor k[33]
        x2 = RT0[(y2 shr 24) and 255] xor RT1[(y1 shr 16) and 255] xor
                RT2[(y0 shr 8) and 255] xor RT3[y3 and 255] xor k[34]
        x3 = RT0[(y3 shr 24) and 255] xor RT1[(y2 shr 16) and 255] xor
                RT2[(y1 shr 8) and 255] xor RT3[y0 and 255] xor k[35]
        y0 = RT0[(x0 shr 24) and 255] xor RT1[(x3 shr 16) and 255] xor
                RT2[(x2 shr 8) and 255] xor RT3[x1 and 255] xor k[36]
        y1 = RT0[(x1 shr 24) and 255] xor RT1[(x0 shr 16) and 255] xor
                RT2[(x3 shr 8) and 255] xor RT3[x2 and 255] xor k[37]
        y2 = RT0[(x2 shr 24) and 255] xor RT1[(x1 shr 16) and 255] xor
                RT2[(x0 shr 8) and 255] xor RT3[x3 and 255] xor k[38]
        y3 = RT0[(x3 shr 24) and 255] xor RT1[(x2 shr 16) and 255] xor
                RT2[(x1 shr 8) and 255] xor RT3[x0 and 255] xor k[39]
        x0 = ((RS[(y0 shr 24) and 255] shl 24) or (RS[(y3 shr 16) and 255] shl 16) or
                (RS[(y2 shr 8) and 255] shl 8) or RS[y1 and 255]) xor k[40]
        x1 = ((RS[(y1 shr 24) and 255] shl 24) or (RS[(y0 shr 16) and 255] shl 16) or
                (RS[(y3 shr 8) and 255] shl 8) or RS[y2 and 255]) xor k[41]
        x2 = ((RS[(y2 shr 24) and 255] shl 24) or (RS[(y1 shr 16) and 255] shl 16) or
                (RS[(y0 shr 8) and 255] shl 8) or RS[y3 and 255]) xor k[42]
        x3 = ((RS[(y3 shr 24) and 255] shl 24) or (RS[(y2 shr 16) and 255] shl 16) or
                (RS[(y1 shr 8) and 255] shl 8) or RS[y0 and 255]) xor k[43]
        INT_VH_BE.set(out, off, x0)
        INT_VH_BE.set(out, off + 4, x1)
        INT_VH_BE.set(out, off + 8, x2)
        INT_VH_BE.set(out, off + 12, x3)
    }

    override fun getKeyLength(): Int {
        return 16
    }

    companion object {

        @JvmField
        val RCON = kotlin.IntArray(10)
        @JvmField
        val FS = kotlin.IntArray(256)
        @JvmField
        val FT0 = kotlin.IntArray(256)
        @JvmField
        val FT1 = kotlin.IntArray(256)
        @JvmField
        val FT2 = kotlin.IntArray(256)
        @JvmField
        val FT3 = kotlin.IntArray(256)
        @JvmField
        val RS = kotlin.IntArray(256)
        @JvmField
        val RT0 = kotlin.IntArray(256)
        @JvmField
        val RT1 = kotlin.IntArray(256)
        @JvmField
        val RT2 = kotlin.IntArray(256)
        @JvmField
        val RT3 = kotlin.IntArray(256)

        private fun rot8(x: Int): Int {
            return (x ushr 8) or (x shl 24)
        }

        private fun xtime(x: Int): Int {
            return ((x shl 1) xor (if ((x and 0x80) != 0) 0x1b else 0)) and 255
        }

        private fun mul(pow: kotlin.IntArray, log: kotlin.IntArray, x: Int, y: Int): Int {
            return if (x != 0 && y != 0) pow[(log[x] + log[y]) % 255] else 0
        }

        init {
            val pow = kotlin.IntArray(256)
            val log = kotlin.IntArray(256)
            run {
                var i = 0
                var x = 1
                while (i < 256) {
                    pow[i] = x
                    log[x] = i
                    i++
                    x = x xor xtime(x)
                }
            }
            run {
                var i = 0
                var x = 1
                while (i < 10) {
                    RCON[i] = x shl 24
                    i++
                    x = xtime(x)
                }
            }
            FS[0x00] = 0x63
            RS[0x63] = 0x00
            for (i in 1 until 256) {
                var x = pow[255 - log[i]]
                var y = x
                y = ((y shl 1) or (y shr 7)) and 255
                x = x xor y
                y = ((y shl 1) or (y shr 7)) and 255
                x = x xor y
                y = ((y shl 1) or (y shr 7)) and 255
                x = x xor y
                y = ((y shl 1) or (y shr 7)) and 255
                x = x xor y xor 0x63
                FS[i] = x and 255
                RS[x] = i and 255
            }
            for (i in 0 until 256) {
                val x = FS[i]
                var y = xtime(x)
                FT0[i] = (x xor y) xor (x shl 8) xor (x shl 16) xor (y shl 24)
                FT1[i] = rot8(FT0[i])
                FT2[i] = rot8(FT1[i])
                FT3[i] = rot8(FT2[i])
                y = RS[i]
                RT0[i] = mul(pow, log, 0x0b, y) xor (mul(pow, log, 0x0d, y) shl 8) xor
                        (mul(pow, log, 0x09, y) shl 16) xor (mul(pow, log, 0x0e, y) shl 24)
                RT1[i] = rot8(RT0[i])
                RT2[i] = rot8(RT1[i])
                RT3[i] = rot8(RT2[i])
            }
        }

        private fun getDec(t: Int): Int {
            return RT0[FS[(t shr 24) and 255]] xor RT1[FS[(t shr 16) and 255]] xor
                    RT2[FS[(t shr 8) and 255]] xor RT3[FS[t and 255]]
        }
    }
}
