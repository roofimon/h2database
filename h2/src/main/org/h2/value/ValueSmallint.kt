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
 * Implementation of the SMALLINT data type.
 */
class ValueSmallint private constructor(private val value: Short) : Value() {

    override fun add(v: Value): Value {
        val other = v as ValueSmallint
        return checkRange(value + other.value)
    }

    override fun getSignum(): Int {
        return Integer.signum(value.toInt())
    }

    override fun negate(): Value {
        return checkRange(-value.toInt())
    }

    override fun subtract(v: Value): Value {
        val other = v as ValueSmallint
        return checkRange(value - other.value)
    }

    override fun multiply(v: Value): Value {
        val other = v as ValueSmallint
        return checkRange(value * other.value)
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val other = v as ValueSmallint
        if (other.value.toInt() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return checkRange(value / other.value)
    }

    override fun modulus(v: Value): Value {
        val other = v as ValueSmallint
        if (other.value.toInt() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get((value % other.value).toShort())
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            return builder.append("CAST(").append(value.toInt()).append(" AS SMALLINT)")
        }
        return builder.append(value.toInt())
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_SMALLINT
    }

    override fun getValueType(): Int {
        return SMALLINT
    }

    override fun getBytes(): ByteArray {
        val value = this.value
        return byteArrayOf((value.toInt() shr 8).toByte(), value.toByte())
    }

    override fun getShort(): Short {
        return value
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
        return Integer.compare(value.toInt(), (o as ValueSmallint).value.toInt())
    }

    override fun getString(): String {
        return Integer.toString(value.toInt())
    }

    override fun hashCode(): Int {
        return value.toInt()
    }

    override fun equals(other: Any?): Boolean {
        return other is ValueSmallint && value == other.value
    }

    companion object {
        /**
         * The precision in bits.
         */
        const val PRECISION = 16

        /**
         * The approximate precision in decimal digits.
         */
        const val DECIMAL_PRECISION = 5

        /**
         * The maximum display size of a SMALLINT.
         * Example: -32768
         */
        const val DISPLAY_SIZE = 6

        /**
         * Get or create a SMALLINT value for the given short.
         *
         * @param i the short
         * @return the value
         */
        @JvmStatic
        fun get(i: Short): ValueSmallint {
            return Value.cache(ValueSmallint(i)) as ValueSmallint
        }

        private fun checkRange(x: Int): ValueSmallint {
            if (x.toShort().toInt() != x) {
                throw DbException.get(ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_1, Integer.toString(x))
            }
            return get(x.toShort())
        }
    }
}
