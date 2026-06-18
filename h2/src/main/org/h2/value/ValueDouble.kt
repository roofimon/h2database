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
 * Implementation of the DOUBLE PRECISION data type.
 */
class ValueDouble private constructor(private val value: Double) : Value() {

    override fun add(v: Value): Value {
        return get(value + (v as ValueDouble).value)
    }

    override fun subtract(v: Value): Value {
        return get(value - (v as ValueDouble).value)
    }

    override fun negate(): Value {
        return get(-value)
    }

    override fun multiply(v: Value): Value {
        return get(value * (v as ValueDouble).value)
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val v2 = v as ValueDouble
        if (v2.value == 0.0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(value / v2.value)
    }

    override fun modulus(v: Value): ValueDouble {
        val other = v as ValueDouble
        if (other.value == 0.0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(value % other.value)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            return getSQL(builder.append("CAST(")).append(" AS DOUBLE PRECISION)")
        }
        return getSQL(builder)
    }

    private fun getSQL(builder: StringBuilder): StringBuilder {
        return if (value == Double.POSITIVE_INFINITY) {
            builder.append("'Infinity'")
        } else if (value == Double.NEGATIVE_INFINITY) {
            builder.append("'-Infinity'")
        } else if (value.isNaN()) {
            builder.append("'NaN'")
        } else {
            builder.append(value)
        }
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_DOUBLE
    }

    override fun getValueType(): Int {
        return DOUBLE
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return java.lang.Double.compare(value, (o as ValueDouble).value)
    }

    override fun getSignum(): Int {
        return if (value == 0.0 || value.isNaN()) 0 else if (value < 0) -1 else 1
    }

    override fun getBigDecimal(): BigDecimal {
        if (java.lang.Double.isFinite(value)) {
            return BigDecimal.valueOf(value)
        }
        // Infinite or NaN
        throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, java.lang.Double.toString(value))
    }

    override fun getFloat(): Float {
        return value.toFloat()
    }

    override fun getDouble(): Double {
        return value
    }

    override fun getString(): String {
        return java.lang.Double.toString(value)
    }

    override fun hashCode(): Int {
        /*
         * NaNs are normalized in get() method, so it's safe to use
         * doubleToRawLongBits() instead of doubleToLongBits() here.
         */
        val hash = java.lang.Double.doubleToRawLongBits(value)
        return (hash xor (hash ushr 32)).toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ValueDouble) {
            return false
        }
        return compareTypeSafe(other, null, null) == 0
    }

    companion object {
        /**
         * The precision in bits.
         */
        const val PRECISION = 53

        /**
         * The approximate precision in decimal digits.
         */
        const val DECIMAL_PRECISION = 17

        /**
         * The maximum display size of a DOUBLE.
         * Example: -3.3333333333333334E-100
         */
        const val DISPLAY_SIZE = 24

        /**
         * Double.doubleToLongBits(0d)
         */
        const val ZERO_BITS = 0L

        /**
         * The value 0.
         */
        @JvmField
        val ZERO = ValueDouble(0.0)

        /**
         * The value 1.
         */
        @JvmField
        val ONE = ValueDouble(1.0)

        private val NAN = ValueDouble(Double.NaN)

        /**
         * Get or create a DOUBLE PRECISION value for the given double.
         *
         * @param d the double
         * @return the value
         */
        @JvmStatic
        fun get(d: Double): ValueDouble {
            if (d == 1.0) {
                return ONE
            } else if (d == 0.0) {
                // -0.0 == 0.0, and we want to return 0.0 for both
                return ZERO
            } else if (d.isNaN()) {
                return NAN
            }
            return Value.cache(ValueDouble(d)) as ValueDouble
        }
    }
}
