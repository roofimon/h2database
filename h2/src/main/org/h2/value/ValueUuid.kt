/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.time.Instant
import java.util.UUID

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.Bits
import org.h2.util.Bits.LONG_VH_BE
import org.h2.util.MathUtils
import org.h2.util.StringUtils

/**
 * Implementation of the UUID data type.
 */
class ValueUuid private constructor(
        private val high: Long,
        private val low: Long) : Value() {

    override fun hashCode(): Int {
        return ((high ushr 32) xor high xor (low ushr 32) xor low).toInt()
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return addString(builder.append("UUID '")).append('\'')
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_UUID
    }

    override fun getMemory(): Int {
        return 32
    }

    override fun getValueType(): Int {
        return UUID
    }

    override fun getString(): String {
        return addString(StringBuilder(36)).toString()
    }

    override fun getBytes(): ByteArray {
        return Bits.uuidToBytes(high, low)
    }

    private fun addString(builder: StringBuilder): StringBuilder {
        StringUtils.appendHex(builder, high shr 32, 4).append('-')
        StringUtils.appendHex(builder, high shr 16, 2).append('-')
        StringUtils.appendHex(builder, high, 2).append('-')
        StringUtils.appendHex(builder, low shr 48, 2).append('-')
        return StringUtils.appendHex(builder, low, 6)
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        if (v === this) {
            return 0
        }
        val o = v as ValueUuid
        val cmp = java.lang.Long.compareUnsigned(high, o.high)
        return if (cmp != 0) cmp else java.lang.Long.compareUnsigned(low, o.low)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ValueUuid) {
            return false
        }
        return high == other.high && low == other.low
    }

    /**
     * Returns the UUID.
     *
     * @return the UUID
     */
    fun getUuid(): UUID {
        return UUID(high, low)
    }

    /**
     * Get the most significant 64 bits of this UUID.
     *
     * @return the high order bits
     */
    fun getHigh(): Long {
        return high
    }

    /**
     * Get the least significant 64 bits of this UUID.
     *
     * @return the low order bits
     */
    fun getLow(): Long {
        return low
    }

    override fun charLength(): Long {
        return DISPLAY_SIZE.toLong()
    }

    override fun octetLength(): Long {
        return PRECISION.toLong()
    }

    companion object {

        /**
         * The precision of this value in number of bytes.
         */
        const val PRECISION = 16

        /**
         * The display size of the textual representation of a UUID.
         * Example: cd38d882-7ada-4589-b5fb-7da0ca559d9a
         */
        const val DISPLAY_SIZE = 36

        /**
         * Create a new UUID using the pseudo random number generator.
         *
         * @param version
         *            a version to use
         * @return the new UUID
         */
        @JvmStatic
        fun getNewRandom(version: Int): ValueUuid {
            val high: Long
            val low: Long
            when (version) {
                4 -> {
                    high = MathUtils.secureRandomLong()
                    low = MathUtils.secureRandomLong()
                }
                7 -> {
                    val now = Instant.now()
                    val nanos = now.nano
                    val sub = nanos % 1_000_000 * 2_000 / 488_281
                    high = (now.epochSecond * 1_000L + nanos / 1_000_000 shl 16) or sub.toLong()
                    low = MathUtils.secureRandomLong()
                }
                else -> throw DbException.getInvalidValueException("RANDOM_UUID version", version)
            }
            return ValueUuid((high and 0xf000L.inv()) or (version.toLong() shl 12),
                    /* variant 0b10 */ low and 0x3fff_ffff_ffff_ffffL or 0x8000_0000_0000_0000UL.toLong())
        }

        /**
         * Get or create a UUID for the given 16 bytes.
         *
         * @param binary the byte array
         * @return the UUID
         */
        @JvmStatic
        fun get(binary: ByteArray): ValueUuid {
            val length = binary.size
            if (length != 16) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, "UUID requires 16 bytes, got $length")
            }
            return get(LONG_VH_BE.get(binary, 0) as Long, LONG_VH_BE.get(binary, 8) as Long)
        }

        /**
         * Get or create a UUID for the given high and low order values.
         *
         * @param high the most significant bits
         * @param low the least significant bits
         * @return the UUID
         */
        @JvmStatic
        fun get(high: Long, low: Long): ValueUuid {
            return Value.cache(ValueUuid(high, low)) as ValueUuid
        }

        /**
         * Get or create a UUID for the given Java UUID.
         *
         * @param uuid Java UUID
         * @return the UUID
         */
        @JvmStatic
        fun get(uuid: UUID): ValueUuid {
            return get(uuid.mostSignificantBits, uuid.leastSignificantBits)
        }

        /**
         * Get or create a UUID for the given text representation.
         *
         * @param s the text representation of the UUID
         * @return the UUID
         */
        @JvmStatic
        fun get(s: String): ValueUuid {
            var low = 0L
            var high = 0L
            var j = 0
            var i = 0
            val length = s.length
            while (i < length) {
                val c = s[i]
                if (c in '0'..'9') {
                    low = (low shl 4) or (c - '0').toLong()
                } else if (c in 'a'..'f') {
                    low = (low shl 4) or (c.code - ('a'.code - 0xa)).toLong()
                } else if (c == '-') {
                    i++
                    continue
                } else if (c in 'A'..'F') {
                    low = (low shl 4) or (c.code - ('A'.code - 0xa)).toLong()
                } else if (c <= ' ') {
                    i++
                    continue
                } else {
                    throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, s)
                }
                if (++j == 16) {
                    high = low
                    low = 0
                }
                i++
            }
            if (j != 32) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, s)
            }
            return get(high, low)
        }
    }
}
