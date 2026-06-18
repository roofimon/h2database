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
import org.h2.util.Bits.LONG_VH_BE
import org.h2.util.HasSQL.Companion.NO_CASTS

/**
 * Implementation of the BIGINT data type.
 */
class ValueBigint private constructor(private val value: Long) : Value() {

    override fun add(v: Value): Value {
        val x = value
        val y = (v as ValueBigint).value
        val result = x + y
        /*
         * If signs of both summands are different from the sign of the sum there is an
         * overflow.
         */
        if (((x xor result) and (y xor result)) < 0) {
            throw getOverflow()
        }
        return get(result)
    }

    override fun getSignum(): Int {
        return java.lang.Long.signum(value)
    }

    override fun negate(): Value {
        if (value == Long.MIN_VALUE) {
            throw getOverflow()
        }
        return get(-value)
    }

    private fun getOverflow(): DbException {
        return DbException.get(ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_1, java.lang.Long.toString(value))
    }

    override fun subtract(v: Value): Value {
        val x = value
        val y = (v as ValueBigint).value
        val result = x - y
        /*
         * If minuend and subtrahend have different signs and minuend and difference
         * have different signs there is an overflow.
         */
        if (((x xor y) and (x xor result)) < 0) {
            throw getOverflow()
        }
        return get(result)
    }

    override fun multiply(v: Value): Value {
        val x = value
        val y = (v as ValueBigint).value
        val result = x * y
        // Check whether numbers are large enough to overflow and second value != 0
        if ((Math.abs(x) or Math.abs(y)) ushr 31 != 0L && y != 0L
            // Check with division
            && (result / y != x
                // Also check the special condition that is not handled above
                || x == Long.MIN_VALUE && y == -1L)) {
            throw getOverflow()
        }
        return get(result)
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val y = (v as ValueBigint).value
        if (y == 0L) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        val x = value
        if (x == Long.MIN_VALUE && y == -1L) {
            throw getOverflow()
        }
        return get(x / y)
    }

    override fun modulus(v: Value): Value {
        val other = v as ValueBigint
        if (other.value == 0L) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(this.value % other.value)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0 && value == value.toInt().toLong()) {
            return builder.append("CAST(").append(value).append(" AS BIGINT)")
        }
        return builder.append(value)
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_BIGINT
    }

    override fun getValueType(): Int {
        return BIGINT
    }

    override fun getBytes(): ByteArray {
        val b = ByteArray(8)
        LONG_VH_BE.set(b, 0, getLong())
        return b
    }

    override fun getLong(): Long {
        return value
    }

    override fun getBigInteger(): BigInteger {
        return BigInteger.valueOf(value)
    }

    override fun getBigDecimal(): BigDecimal {
        return BigDecimal.valueOf(value)
    }

    override fun getFloat(): Float {
        return value.toFloat()
    }

    override fun getDouble(): Double {
        return value.toDouble()
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return java.lang.Long.compare(value, (o as ValueBigint).value)
    }

    override fun getString(): String {
        return java.lang.Long.toString(value)
    }

    override fun hashCode(): Int {
        return (value xor (value shr 32)).toInt()
    }

    override fun equals(other: Any?): Boolean {
        return other is ValueBigint && value == other.value
    }

    companion object {
        /**
         * The smallest {@code ValueLong} value.
         */
        @JvmField
        val MIN: ValueBigint = get(Long.MIN_VALUE)

        /**
         * The largest {@code ValueLong} value.
         */
        @JvmField
        val MAX: ValueBigint = get(Long.MAX_VALUE)

        /**
         * The largest Long value, as a BigInteger.
         */
        @JvmField
        val MAX_BI: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)

        /**
         * The precision in bits.
         */
        const val PRECISION = 64

        /**
         * The approximate precision in decimal digits.
         */
        const val DECIMAL_PRECISION = 19

        /**
         * The maximum display size of a BIGINT.
         * Example: -9223372036854775808
         */
        const val DISPLAY_SIZE = 20

        private const val STATIC_SIZE = 100
        private val STATIC_CACHE: Array<ValueBigint?> = arrayOfNulls(STATIC_SIZE)

        init {
            for (i in 0 until STATIC_SIZE) {
                STATIC_CACHE[i] = ValueBigint(i.toLong())
            }
        }

        /**
         * Get or create a BIGINT value for the given long.
         *
         * @param i the long
         * @return the value
         */
        @JvmStatic
        fun get(i: Long): ValueBigint {
            if (i in 0 until STATIC_SIZE.toLong()) {
                return STATIC_CACHE[i.toInt()]!!
            }
            return Value.cache(ValueBigint(i)) as ValueBigint
        }
    }
}
