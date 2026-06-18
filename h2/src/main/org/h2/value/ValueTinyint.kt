/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal
import java.math.BigInteger

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.HasSQL.Companion.NO_CASTS

/**
 * Implementation of the TINYINT data type.
 */
class ValueTinyint private constructor(private val value: Byte) : Value() {

    override fun add(v: Value): Value {
        val other = v as ValueTinyint
        return checkRange(value + other.value)
    }

    override fun getSignum(): Int {
        return Integer.signum(value.toInt())
    }

    override fun negate(): Value {
        return checkRange(-value.toInt())
    }

    override fun subtract(v: Value): Value {
        val other = v as ValueTinyint
        return checkRange(value - other.value)
    }

    override fun multiply(v: Value): Value {
        val other = v as ValueTinyint
        return checkRange(value * other.value)
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val other = v as ValueTinyint
        if (other.value.toInt() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return checkRange(value / other.value)
    }

    override fun modulus(v: Value): Value {
        val other = v as ValueTinyint
        if (other.value.toInt() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get((value % other.value).toByte())
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            return builder.append("CAST(").append(value.toInt()).append(" AS TINYINT)")
        }
        return builder.append(value.toInt())
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_TINYINT
    }

    override fun getValueType(): Int {
        return TINYINT
    }

    override fun getMemory(): Int {
        // All possible values are statically initialized
        return 0
    }

    override fun getBytes(): ByteArray {
        return byteArrayOf(value)
    }

    override fun getByte(): Byte {
        return value
    }

    override fun getShort(): Short {
        return value.toShort()
    }

    override fun getInt(): Int {
        return value.toInt()
    }

    override fun getLong(): Long {
        return value.toLong()
    }

    override fun getBigInteger(): BigInteger {
        return BigInteger.valueOf(value.toLong())
    }

    override fun getBigDecimal(): BigDecimal {
        return BigDecimal.valueOf(value.toLong())
    }

    override fun getFloat(): Float {
        return value.toFloat()
    }

    override fun getDouble(): Double {
        return value.toDouble()
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return Integer.compare(value.toInt(), (o as ValueTinyint).value.toInt())
    }

    override fun getString(): String {
        return Integer.toString(value.toInt())
    }

    override fun hashCode(): Int {
        return value.toInt()
    }

    override fun equals(other: Any?): Boolean {
        return other is ValueTinyint && value == other.value
    }

    companion object {
        /**
         * The precision in bits.
         */
        const val PRECISION = 8

        /**
         * The approximate precision in decimal digits.
         */
        const val DECIMAL_PRECISION = 3

        /**
         * The display size for a TINYINT.
         * Example: -127
         */
        const val DISPLAY_SIZE = 4

        private val STATIC_CACHE: Array<ValueTinyint?>

        init {
            val cache = arrayOfNulls<ValueTinyint>(256)
            for (i in 0 until 256) {
                cache[i] = ValueTinyint((i - 128).toByte())
            }
            STATIC_CACHE = cache
        }

        /**
         * Get a TINYINT value for the given byte.
         *
         * @param i the byte
         * @return the value
         */
        @JvmStatic
        fun get(i: Byte): ValueTinyint {
            return STATIC_CACHE[i + 128]!!
        }

        private fun checkRange(x: Int): ValueTinyint {
            if (x.toByte().toInt() != x) {
                throw DbException.get(ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_1, Integer.toString(x))
            }
            return get(x.toByte())
        }
    }
}
