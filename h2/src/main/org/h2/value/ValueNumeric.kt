/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.HasSQL.Companion.NO_CASTS

/**
 * Implementation of the NUMERIC data type.
 */
class ValueNumeric private constructor(value: BigDecimal?) : ValueBigDecimalBase(value) {

    init {
        if (value == null) {
            throw IllegalArgumentException("null")
        }
        val scale = value.scale()
        if (scale < 0 || scale > MAXIMUM_SCALE) {
            throw DbException.get(ErrorCode.INVALID_VALUE_SCALE, Integer.toString(scale), "0", "" + MAXIMUM_SCALE)
        }
    }

    override fun getString(): String {
        return value!!.toPlainString()
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        val s = getString()
        if ((sqlFlags and NO_CASTS) == 0 && s.indexOf('.') < 0 && value!!.compareTo(MAX_LONG_DECIMAL) <= 0
                && value.compareTo(MIN_LONG_DECIMAL) >= 0) {
            return builder.append("CAST(").append(value).append(" AS NUMERIC(").append(value.precision()).append("))")
        }
        return builder.append(s)
    }

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            type = TypeInfo(NUMERIC, value!!.precision().toLong(), value.scale(), null)
            this.type = type
        }
        return type
    }

    override fun getValueType(): Int {
        return NUMERIC
    }

    override fun add(v: Value): Value {
        return get(value!!.add((v as ValueNumeric).value))
    }

    override fun subtract(v: Value): Value {
        return get(value!!.subtract((v as ValueNumeric).value))
    }

    override fun negate(): Value {
        return get(value!!.negate())
    }

    override fun multiply(v: Value): Value {
        return get(value!!.multiply((v as ValueNumeric).value))
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val divisor = (v as ValueNumeric).value
        if (divisor!!.signum() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(value!!.divide(divisor, quotientType.scale, RoundingMode.HALF_DOWN))
    }

    override fun modulus(v: Value): Value {
        val dec: ValueBigDecimalBase = v as ValueNumeric
        if (dec.value!!.signum() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        return get(value!!.remainder(dec.value))
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return value!!.compareTo((o as ValueNumeric).value)
    }

    override fun getSignum(): Int {
        return value!!.signum()
    }

    override fun getBigDecimal(): BigDecimal {
        return value!!
    }

    override fun getFloat(): Float {
        return value!!.toFloat()
    }

    override fun getDouble(): Double {
        return value!!.toDouble()
    }

    override fun hashCode(): Int {
        return javaClass.hashCode() * 31 + value!!.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is ValueNumeric && value == other.value
    }

    override fun getMemory(): Int {
        return value!!.precision() + 120
    }

    companion object {
        /**
         * The value 'zero'.
         */
        @JvmField
        val ZERO = ValueNumeric(BigDecimal.ZERO)

        /**
         * The value 'one'.
         */
        @JvmField
        val ONE = ValueNumeric(BigDecimal.ONE)

        /**
         * The default scale for a NUMERIC value.
         */
        const val DEFAULT_SCALE = 0

        /**
         * The maximum scale.
         */
        const val MAXIMUM_SCALE = 100_000

        /**
         * Get or create a NUMERIC value for the given big decimal.
         *
         * @param dec the big decimal
         * @return the value
         */
        @JvmStatic
        fun get(dec: BigDecimal): ValueNumeric {
            if (BigDecimal.ZERO == dec) {
                return ZERO
            } else if (BigDecimal.ONE == dec) {
                return ONE
            }
            return Value.cache(ValueNumeric(dec)) as ValueNumeric
        }

        /**
         * Get or create a NUMERIC value for the given big decimal with possibly
         * negative scale. If scale is negative, it is normalized to 0.
         *
         * @param dec
         *            the big decimal
         * @return the value
         */
        @JvmStatic
        fun getAnyScale(dec: BigDecimal): ValueNumeric {
            var d = dec
            if (d.scale() < 0) {
                d = d.setScale(0, RoundingMode.UNNECESSARY)
            }
            return get(d)
        }

        /**
         * Get or create a NUMERIC value for the given big integer.
         *
         * @param bigInteger the big integer
         * @return the value
         */
        @JvmStatic
        fun get(bigInteger: BigInteger): ValueNumeric {
            if (bigInteger.signum() == 0) {
                return ZERO
            } else if (BigInteger.ONE == bigInteger) {
                return ONE
            }
            return Value.cache(ValueNumeric(BigDecimal(bigInteger))) as ValueNumeric
        }

        /**
         * Set the scale of a BigDecimal value.
         *
         * @param bd the BigDecimal value
         * @param scale the new scale
         * @return the scaled value
         */
        @JvmStatic
        fun setScale(bd: BigDecimal, scale: Int): BigDecimal {
            if (scale < 0 || scale > MAXIMUM_SCALE) {
                throw DbException.getInvalidValueException("scale", scale)
            }
            return bd.setScale(scale, RoundingMode.HALF_UP)
        }
    }
}
