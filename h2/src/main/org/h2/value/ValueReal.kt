/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.HasSQL.Companion.NO_CASTS

/**
 * Implementation of the REAL data type.
 */
class ValueReal private constructor(private val value: Float) : Value() {

    override fun add(v: Value): Value {
        return get(value + (v as ValueReal).value)
    }

    override fun subtract(v: Value): Value {
        return get(value - (v as ValueReal).value)
    }

    override fun negate(): Value {
        return get(-value)
    }

    override fun multiply(v: Value): Value {
        return get(value * (v as ValueReal).value)
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val v2 = v as ValueReal
        if (v2.value == 0.0f) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(value / v2.value)
    }

    override fun modulus(v: Value): Value {
        val other = v as ValueReal
        if (other.value == 0f) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(value % other.value)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            return getSQL(builder.append("CAST(")).append(" AS REAL)")
        }
        return getSQL(builder)
    }

    private fun getSQL(builder: StringBuilder): StringBuilder {
        return if (value == Float.POSITIVE_INFINITY) {
            builder.append("'Infinity'")
        } else if (value == Float.NEGATIVE_INFINITY) {
            builder.append("'-Infinity'")
        } else if (value.isNaN()) {
            builder.append("'NaN'")
        } else {
            builder.append(value)
        }
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_REAL
    }

    override fun getValueType(): Int {
        return REAL
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return java.lang.Float.compare(value, (o as ValueReal).value)
    }

    override fun getSignum(): Int {
        return if (value == 0f || value.isNaN()) 0 else if (value < 0) -1 else 1
    }

    override fun getBigDecimal(): BigDecimal {
        if (java.lang.Float.isFinite(value)) {
            // better rounding behavior than BigDecimal.valueOf(f)
            return BigDecimal(java.lang.Float.toString(value))
        }
        // Infinite or NaN
        throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, java.lang.Float.toString(value))
    }

    override fun getFloat(): Float {
        return value
    }

    override fun getDouble(): Double {
        return value.toDouble()
    }

    override fun getString(): String {
        return java.lang.Float.toString(value)
    }

    override fun hashCode(): Int {
        /*
         * NaNs are normalized in get() method, so it's safe to use
         * floatToRawIntBits() instead of floatToIntBits() here.
         */
        return java.lang.Float.floatToRawIntBits(value)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ValueReal) {
            return false
        }
        return compareTypeSafe(other, null, null) == 0
    }

    companion object {
        /**
         * The precision in bits.
         */
        const val PRECISION = 24

        /**
         * The approximate precision in decimal digits.
         */
        const val DECIMAL_PRECISION = 7

        /**
         * The maximum display size of a REAL.
         * Example: -1.12345676E-20
         */
        const val DISPLAY_SIZE = 15

        /**
         * Float.floatToIntBits(0f).
         */
        const val ZERO_BITS = 0

        /**
         * The value 0.
         */
        @JvmField
        val ZERO = ValueReal(0f)

        /**
         * The value 1.
         */
        @JvmField
        val ONE = ValueReal(1f)

        private val NAN = ValueReal(Float.NaN)

        /**
         * Get or create a REAL value for the given float.
         *
         * @param d the float
         * @return the value
         */
        @JvmStatic
        fun get(d: Float): ValueReal {
            if (d == 1.0f) {
                return ONE
            } else if (d == 0.0f) {
                // -0.0 == 0.0, and we want to return 0.0 for both
                return ZERO
            } else if (d.isNaN()) {
                return NAN
            }
            return Value.cache(ValueReal(d)) as ValueReal
        }
    }
}
