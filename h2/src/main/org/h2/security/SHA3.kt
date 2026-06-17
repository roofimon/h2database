/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.security

import org.h2.util.Bits.INT_VH_LE
import org.h2.util.Bits.LONG_VH_LE
import java.security.MessageDigest
import java.util.Arrays

/**
 * SHA-3 message digest family.
 */
class SHA3 private constructor(algorithm: String, private val digestLength: Int) : MessageDigest(algorithm) {

    private val rate: Int

    private var state00: Long = 0
    private var state01: Long = 0
    private var state02: Long = 0
    private var state03: Long = 0
    private var state04: Long = 0
    private var state05: Long = 0
    private var state06: Long = 0
    private var state07: Long = 0
    private var state08: Long = 0
    private var state09: Long = 0
    private var state10: Long = 0
    private var state11: Long = 0
    private var state12: Long = 0
    private var state13: Long = 0
    private var state14: Long = 0
    private var state15: Long = 0
    private var state16: Long = 0
    private var state17: Long = 0
    private var state18: Long = 0
    private var state19: Long = 0
    private var state20: Long = 0
    private var state21: Long = 0
    private var state22: Long = 0
    private var state23: Long = 0
    private var state24: Long = 0

    private val buf: ByteArray

    private var bufcnt: Int = 0

    init {
        rate = 200 - digestLength * 2
        buf = ByteArray(rate)
    }

    override fun engineDigest(): ByteArray {
        buf[bufcnt] = 0b110
        Arrays.fill(buf, bufcnt + 1, rate, 0.toByte())
        buf[rate - 1] = (buf[rate - 1].toInt() or 0x80).toByte()
        absorbQueue()
        val r = ByteArray(digestLength)
        when (digestLength) {
            64 -> {
                LONG_VH_LE.set(r, 56, state07)
                LONG_VH_LE.set(r, 48, state06)
                LONG_VH_LE.set(r, 40, state05)
                LONG_VH_LE.set(r, 32, state04)
                LONG_VH_LE.set(r, 24, state03)
            }
            48 -> {
                LONG_VH_LE.set(r, 40, state05)
                LONG_VH_LE.set(r, 32, state04)
                LONG_VH_LE.set(r, 24, state03)
            }
            32 -> {
                LONG_VH_LE.set(r, 24, state03)
            }
            28 -> {
                INT_VH_LE.set(r, 24, state03.toInt())
            }
        }
        LONG_VH_LE.set(r, 16, state02)
        LONG_VH_LE.set(r, 8, state01)
        LONG_VH_LE.set(r, 0, state00)
        engineReset()
        return r
    }

    override fun engineGetDigestLength(): Int {
        return digestLength
    }

    override fun engineReset() {
        state00 = 0L
        state01 = 0L
        state02 = 0L
        state03 = 0L
        state04 = 0L
        state05 = 0L
        state06 = 0L
        state07 = 0L
        state08 = 0L
        state09 = 0L
        state10 = 0L
        state11 = 0L
        state12 = 0L
        state13 = 0L
        state14 = 0L
        state15 = 0L
        state16 = 0L
        state17 = 0L
        state18 = 0L
        state19 = 0L
        state20 = 0L
        state21 = 0L
        state22 = 0L
        state23 = 0L
        state24 = 0L
        Arrays.fill(buf, 0.toByte())
        bufcnt = 0
    }

    override fun engineUpdate(input: Byte) {
        buf[bufcnt++] = input
        if (bufcnt == rate) {
            absorbQueue()
        }
    }

    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        while (len > 0) {
            if (bufcnt == 0 && len >= rate) {
                do {
                    absorb(input, offset)
                    offset += rate
                    len -= rate
                } while (len >= rate)
            } else {
                val partialBlock = Math.min(len, rate - bufcnt)
                System.arraycopy(input, offset, buf, bufcnt, partialBlock)
                bufcnt += partialBlock
                offset += partialBlock
                len -= partialBlock
                if (bufcnt == rate) {
                    absorbQueue()
                }
            }
        }
    }

    private fun absorbQueue() {
        absorb(buf, 0)
        bufcnt = 0
    }

    private fun absorb(data: ByteArray, offset: Int) {
        /*
         * There is no need to copy 25 state* fields into local variables,
         * because so large number of local variables only hurts performance.
         */
        when (digestLength) {
            28 -> {
                state17 = state17 xor (LONG_VH_LE.get(data, offset + 136) as Long)
                state13 = state13 xor (LONG_VH_LE.get(data, offset + 104) as Long)
                state14 = state14 xor (LONG_VH_LE.get(data, offset + 112) as Long)
                state15 = state15 xor (LONG_VH_LE.get(data, offset + 120) as Long)
                state16 = state16 xor (LONG_VH_LE.get(data, offset + 128) as Long)
                state09 = state09 xor (LONG_VH_LE.get(data, offset + 72) as Long)
                state10 = state10 xor (LONG_VH_LE.get(data, offset + 80) as Long)
                state11 = state11 xor (LONG_VH_LE.get(data, offset + 88) as Long)
                state12 = state12 xor (LONG_VH_LE.get(data, offset + 96) as Long)
            }
            32 -> {
                state13 = state13 xor (LONG_VH_LE.get(data, offset + 104) as Long)
                state14 = state14 xor (LONG_VH_LE.get(data, offset + 112) as Long)
                state15 = state15 xor (LONG_VH_LE.get(data, offset + 120) as Long)
                state16 = state16 xor (LONG_VH_LE.get(data, offset + 128) as Long)
                state09 = state09 xor (LONG_VH_LE.get(data, offset + 72) as Long)
                state10 = state10 xor (LONG_VH_LE.get(data, offset + 80) as Long)
                state11 = state11 xor (LONG_VH_LE.get(data, offset + 88) as Long)
                state12 = state12 xor (LONG_VH_LE.get(data, offset + 96) as Long)
            }
            48 -> {
                state09 = state09 xor (LONG_VH_LE.get(data, offset + 72) as Long)
                state10 = state10 xor (LONG_VH_LE.get(data, offset + 80) as Long)
                state11 = state11 xor (LONG_VH_LE.get(data, offset + 88) as Long)
                state12 = state12 xor (LONG_VH_LE.get(data, offset + 96) as Long)
            }
        }
        state00 = state00 xor (LONG_VH_LE.get(data, offset) as Long)
        state01 = state01 xor (LONG_VH_LE.get(data, offset + 8) as Long)
        state02 = state02 xor (LONG_VH_LE.get(data, offset + 16) as Long)
        state03 = state03 xor (LONG_VH_LE.get(data, offset + 24) as Long)
        state04 = state04 xor (LONG_VH_LE.get(data, offset + 32) as Long)
        state05 = state05 xor (LONG_VH_LE.get(data, offset + 40) as Long)
        state06 = state06 xor (LONG_VH_LE.get(data, offset + 48) as Long)
        state07 = state07 xor (LONG_VH_LE.get(data, offset + 56) as Long)
        state08 = state08 xor (LONG_VH_LE.get(data, offset + 64) as Long)
        for (i in 0 until 24) {
            val c0 = state00 xor state05 xor state10 xor state15 xor state20
            val c1 = state01 xor state06 xor state11 xor state16 xor state21
            val c2 = state02 xor state07 xor state12 xor state17 xor state22
            val c3 = state03 xor state08 xor state13 xor state18 xor state23
            val c4 = state04 xor state09 xor state14 xor state19 xor state24
            var dX = (c1 shl 1 or (c1 ushr 63)) xor c4
            state00 = state00 xor dX
            state05 = state05 xor dX
            state10 = state10 xor dX
            state15 = state15 xor dX
            state20 = state20 xor dX
            dX = (c2 shl 1 or (c2 ushr 63)) xor c0
            state01 = state01 xor dX
            state06 = state06 xor dX
            state11 = state11 xor dX
            state16 = state16 xor dX
            state21 = state21 xor dX
            dX = (c3 shl 1 or (c3 ushr 63)) xor c1
            state02 = state02 xor dX
            state07 = state07 xor dX
            state12 = state12 xor dX
            state17 = state17 xor dX
            state22 = state22 xor dX
            dX = (c4 shl 1 or (c4 ushr 63)) xor c2
            state03 = state03 xor dX
            state08 = state08 xor dX
            state13 = state13 xor dX
            state18 = state18 xor dX
            state23 = state23 xor dX
            dX = (c0 shl 1 or (c0 ushr 63)) xor c3
            state04 = state04 xor dX
            state09 = state09 xor dX
            state14 = state14 xor dX
            state19 = state19 xor dX
            state24 = state24 xor dX
            val s00 = state00
            val s01 = state06 shl 44 or (state06 ushr 20)
            val s02 = state12 shl 43 or (state12 ushr 21)
            val s03 = state18 shl 21 or (state18 ushr 43)
            val s04 = state24 shl 14 or (state24 ushr 50)
            val s05 = state03 shl 28 or (state03 ushr 36)
            val s06 = state09 shl 20 or (state09 ushr 44)
            val s07 = state10 shl 3 or (state10 ushr 61)
            val s08 = state16 shl 45 or (state16 ushr 19)
            val s09 = state22 shl 61 or (state22 ushr 3)
            val s10 = state01 shl 1 or (state01 ushr 63)
            val s11 = state07 shl 6 or (state07 ushr 58)
            val s12 = state13 shl 25 or (state13 ushr 39)
            val s13 = state19 shl 8 or (state19 ushr 56)
            val s14 = state20 shl 18 or (state20 ushr 46)
            val s15 = state04 shl 27 or (state04 ushr 37)
            val s16 = state05 shl 36 or (state05 ushr 28)
            val s17 = state11 shl 10 or (state11 ushr 54)
            val s18 = state17 shl 15 or (state17 ushr 49)
            val s19 = state23 shl 56 or (state23 ushr 8)
            val s20 = state02 shl 62 or (state02 ushr 2)
            val s21 = state08 shl 55 or (state08 ushr 9)
            val s22 = state14 shl 39 or (state14 ushr 25)
            val s23 = state15 shl 41 or (state15 ushr 23)
            val s24 = state21 shl 2 or (state21 ushr 62)
            state00 = s00 xor (s01.inv() and s02) xor ROUND_CONSTANTS[i]
            state01 = s01 xor (s02.inv() and s03)
            state02 = s02 xor (s03.inv() and s04)
            state03 = s03 xor (s04.inv() and s00)
            state04 = s04 xor (s00.inv() and s01)
            state05 = s05 xor (s06.inv() and s07)
            state06 = s06 xor (s07.inv() and s08)
            state07 = s07 xor (s08.inv() and s09)
            state08 = s08 xor (s09.inv() and s05)
            state09 = s09 xor (s05.inv() and s06)
            state10 = s10 xor (s11.inv() and s12)
            state11 = s11 xor (s12.inv() and s13)
            state12 = s12 xor (s13.inv() and s14)
            state13 = s13 xor (s14.inv() and s10)
            state14 = s14 xor (s10.inv() and s11)
            state15 = s15 xor (s16.inv() and s17)
            state16 = s16 xor (s17.inv() and s18)
            state17 = s17 xor (s18.inv() and s19)
            state18 = s18 xor (s19.inv() and s15)
            state19 = s19 xor (s15.inv() and s16)
            state20 = s20 xor (s21.inv() and s22)
            state21 = s21 xor (s22.inv() and s23)
            state22 = s22 xor (s23.inv() and s24)
            state23 = s23 xor (s24.inv() and s20)
            state24 = s24 xor (s20.inv() and s21)
        }
    }

    companion object {

        private val ROUND_CONSTANTS: LongArray

        init {
            val rc = LongArray(24)
            var l: Byte = 1
            for (i in 0 until 24) {
                rc[i] = 0
                for (j in 0 until 7) {
                    val t = l
                    l = (if (t < 0) t.toInt() shl 1 xor 0x71 else t.toInt() shl 1).toByte()
                    if ((t.toInt() and 1) != 0) {
                        rc[i] = rc[i] xor (1L shl ((1 shl j) - 1))
                    }
                }
            }
            ROUND_CONSTANTS = rc
        }

        /**
         * Returns a new instance of SHA3-224 message digest.
         *
         * @return SHA3-224 message digest
         */
        @JvmStatic
        fun getSha3_224(): SHA3 {
            return SHA3("SHA3-224", 28)
        }

        /**
         * Returns a new instance of SHA3-256 message digest.
         *
         * @return SHA3-256 message digest
         */
        @JvmStatic
        fun getSha3_256(): SHA3 {
            return SHA3("SHA3-256", 32)
        }

        /**
         * Returns a new instance of SHA3-384 message digest.
         *
         * @return SHA3-384 message digest
         */
        @JvmStatic
        fun getSha3_384(): SHA3 {
            return SHA3("SHA3-384", 48)
        }

        /**
         * Returns a new instance of SHA3-512 message digest.
         *
         * @return SHA3-512 message digest
         */
        @JvmStatic
        fun getSha3_512(): SHA3 {
            return SHA3("SHA3-512", 64)
        }
    }
}
