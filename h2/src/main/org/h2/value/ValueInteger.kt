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
import org.h2.util.Bits.INT_VH_BE

/**
 * Implementation of the INTEGER data type.
 */
class ValueInteger private constructor(private val value: Int) : Value() {

    override fun add(v: Value): Value {
        val other = v as ValueInteger
        return checkRange(value.toLong() + other.value.toLong())
    }

    override fun getSignum(): Int {
        return Integer.signum(value)
    }

    override fun negate(): Value {
        return checkRange(-value.toLong())
    }

    override fun subtract(v: Value): Value {
        val other = v as ValueInteger
        return checkRange(value.toLong() - other.value.toLong())
    }

    override fun multiply(v: Value): Value {
        val other = v as ValueInteger
        return checkRange(value.toLong() * other.value.toLong())
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val y = (v as ValueInteger).value
        if (y == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        val x = value
        if (x == Int.MIN_VALUE && y == -1) {
            throw DbException.get(ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_1, "2147483648")
        }
        return get(x / y)
    }

    override fun modulus(v: Value): Value {
        val other = v as ValueInteger
        if (other.value == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(value % other.value)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return builder.append(value)
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_INTEGER
    }

    override fun getValueType(): Int {
        return INTEGER
    }

    override fun getBytes(): ByteArray {
        val b = ByteArray(4)
        INT_VH_BE.set(b, 0, getInt())
        return b
    }

    override fun getInt(): Int {
        return value
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
        return Integer.compare(value, (o as ValueInteger).value)
    }

    override fun getString(): String {
        return Integer.toString(value)
    }

    override fun hashCode(): Int {
        return value
    }

    override fun equals(other: Any?): Boolean {
        return other is ValueInteger && value == other.value
    }

    companion object {
        /**
         * The precision in bits.
         */
        const val PRECISION = 32

        /**
         * The approximate precision in decimal digits.
         */
        const val DECIMAL_PRECISION = 10

        /**
         * The maximum display size of an INT.
         * Example: -2147483648
         */
        const val DISPLAY_SIZE = 11

        private const val STATIC_SIZE = 128

        // must be a power of 2
        private const val DYNAMIC_SIZE = 256
        private val STATIC_CACHE = arrayOfNulls<ValueInteger>(STATIC_SIZE)
        private val DYNAMIC_CACHE = arrayOfNulls<ValueInteger>(DYNAMIC_SIZE)

        init {
            for (i in 0 until STATIC_SIZE) {
                STATIC_CACHE[i] = ValueInteger(i)
            }
        }

        /**
         * Get or create an INTEGER value for the given int.
         *
         * @param i the int
         * @return the value
         */
        @JvmStatic
        fun get(i: Int): ValueInteger {
            if (i in 0 until STATIC_SIZE) {
                return STATIC_CACHE[i]!!
            }
            var v = DYNAMIC_CACHE[i and (DYNAMIC_SIZE - 1)]
            if (v == null || v.value != i) {
                v = ValueInteger(i)
                DYNAMIC_CACHE[i and (DYNAMIC_SIZE - 1)] = v
            }
            return v
        }

        private fun checkRange(x: Long): ValueInteger {
            if (x.toInt().toLong() != x) {
                throw DbException.get(ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_1, java.lang.Long.toString(x))
            }
            return get(x.toInt())
        }
    }
}
