/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal
import java.math.RoundingMode

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.HasSQL.Companion.NO_CASTS

/**
 * Implementation of the DECFLOAT data type.
 */
class ValueDecfloat private constructor(value: BigDecimal?) : ValueBigDecimalBase(value) {

    override fun getString(): String {
        if (value == null) {
            return if (this === POSITIVE_INFINITY) {
                "Infinity"
            } else if (this === NEGATIVE_INFINITY) {
                "-Infinity"
            } else {
                "NaN"
            }
        }
        return value.toString()
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            return getSQL(builder.append("CAST(")).append(" AS DECFLOAT)")
        }
        return getSQL(builder)
    }

    private fun getSQL(builder: StringBuilder): StringBuilder {
        return if (value != null) {
            builder.append(value)
        } else if (this === POSITIVE_INFINITY) {
            builder.append("'Infinity'")
        } else if (this === NEGATIVE_INFINITY) {
            builder.append("'-Infinity'")
        } else {
            builder.append("'NaN'")
        }
    }

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            type = TypeInfo(DECFLOAT, if (value != null) value.precision().toLong() else 1L, 0, null)
            this.type = type
        }
        return type
    }

    override fun getValueType(): Int {
        return DECFLOAT
    }

    override fun add(v: Value): Value {
        val value2 = (v as ValueDecfloat).value
        if (value != null) {
            if (value2 != null) {
                return get(value.add(value2))
            }
            return v
        } else if (value2 != null || this === v) {
            return this
        }
        return NAN
    }

    override fun subtract(v: Value): Value {
        val value2 = (v as ValueDecfloat).value
        if (value != null) {
            if (value2 != null) {
                return get(value.subtract(value2))
            }
            return if (v === POSITIVE_INFINITY) NEGATIVE_INFINITY else if (v === NEGATIVE_INFINITY) POSITIVE_INFINITY else NAN
        } else if (value2 != null) {
            return this
        } else if (this === POSITIVE_INFINITY) {
            if (v === NEGATIVE_INFINITY) {
                return POSITIVE_INFINITY
            }
        } else if (this === NEGATIVE_INFINITY && v === POSITIVE_INFINITY) {
            return NEGATIVE_INFINITY
        }
        return NAN
    }

    override fun negate(): Value {
        if (value != null) {
            return get(value.negate())
        }
        return if (this === POSITIVE_INFINITY) NEGATIVE_INFINITY else if (this === NEGATIVE_INFINITY) POSITIVE_INFINITY else NAN
    }

    override fun multiply(v: Value): Value {
        val value2 = (v as ValueDecfloat).value
        if (value != null) {
            if (value2 != null) {
                return get(value.multiply(value2))
            }
            if (v === POSITIVE_INFINITY) {
                val s = value.signum()
                if (s > 0) {
                    return POSITIVE_INFINITY
                } else if (s < 0) {
                    return NEGATIVE_INFINITY
                }
            } else if (v === NEGATIVE_INFINITY) {
                val s = value.signum()
                if (s > 0) {
                    return NEGATIVE_INFINITY
                } else if (s < 0) {
                    return POSITIVE_INFINITY
                }
            }
        } else if (value2 != null) {
            if (this === POSITIVE_INFINITY) {
                val s = value2.signum()
                if (s > 0) {
                    return POSITIVE_INFINITY
                } else if (s < 0) {
                    return NEGATIVE_INFINITY
                }
            } else if (this === NEGATIVE_INFINITY) {
                val s = value2.signum()
                if (s > 0) {
                    return NEGATIVE_INFINITY
                } else if (s < 0) {
                    return POSITIVE_INFINITY
                }
            }
        } else if (this === POSITIVE_INFINITY) {
            if (v === POSITIVE_INFINITY) {
                return POSITIVE_INFINITY
            } else if (v === NEGATIVE_INFINITY) {
                return NEGATIVE_INFINITY
            }
        } else if (this === NEGATIVE_INFINITY) {
            if (v === POSITIVE_INFINITY) {
                return NEGATIVE_INFINITY
            } else if (v === NEGATIVE_INFINITY) {
                return POSITIVE_INFINITY
            }
        }
        return NAN
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val value2 = (v as ValueDecfloat).value
        if (value2 != null && value2.signum() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        if (value != null) {
            if (value2 != null) {
                return divide(value, value2, quotientType)
            } else {
                if (v !== NAN) {
                    return ZERO
                }
            }
        } else if (value2 != null && this !== NAN) {
            return if ((this === POSITIVE_INFINITY) == (value2.signum() > 0)) POSITIVE_INFINITY else NEGATIVE_INFINITY
        }
        return NAN
    }

    override fun modulus(v: Value): Value {
        val value2 = (v as ValueDecfloat).value
        if (value2 != null && value2.signum() == 0) {
            throw DbException.get(ErrorCode.DIVISION_BY_ZERO_1, getTraceSQL())
        }
        if (value != null) {
            if (value2 != null) {
                return get(value.remainder(value2))
            } else if (v !== NAN) {
                return this
            }
        }
        return NAN
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        val value2 = (o as ValueDecfloat).value
        if (value != null) {
            if (value2 != null) {
                return value.compareTo(value2)
            }
            return if (o === NEGATIVE_INFINITY) 1 else -1
        } else if (value2 != null) {
            return if (this === NEGATIVE_INFINITY) -1 else 1
        } else if (this === o) {
            return 0
        } else if (this === NEGATIVE_INFINITY) {
            return -1
        } else if (o === NEGATIVE_INFINITY) {
            return 1
        } else {
            return if (this === POSITIVE_INFINITY) -1 else 1
        }
    }

    override fun getSignum(): Int {
        if (value != null) {
            return value.signum()
        }
        return if (this === POSITIVE_INFINITY) 1 else if (this === NEGATIVE_INFINITY) -1 else 0
    }

    override fun getBigDecimal(): BigDecimal {
        if (value != null) {
            return value
        }
        throw getDataConversionError(NUMERIC)
    }

    override fun getFloat(): Float {
        return if (value != null) {
            value.toFloat()
        } else if (this === POSITIVE_INFINITY) {
            Float.POSITIVE_INFINITY
        } else if (this === NEGATIVE_INFINITY) {
            Float.NEGATIVE_INFINITY
        } else {
            Float.NaN
        }
    }

    override fun getDouble(): Double {
        return if (value != null) {
            value.toDouble()
        } else if (this === POSITIVE_INFINITY) {
            Double.POSITIVE_INFINITY
        } else if (this === NEGATIVE_INFINITY) {
            Double.NEGATIVE_INFINITY
        } else {
            Double.NaN
        }
    }

    override fun hashCode(): Int {
        return if (value != null) javaClass.hashCode() * 31 + value.hashCode() else System.identityHashCode(this)
    }

    override fun equals(other: Any?): Boolean {
        if (other is ValueDecfloat) {
            val value2 = other.value
            if (value != null) {
                return value == value2
            } else if (value2 == null && this === other) {
                return true
            }
        }
        return false
    }

    override fun getMemory(): Int {
        return if (value != null) value.precision() + 120 else 32
    }

    /**
     * Returns `true`, if this value is finite.
     *
     * @return `true`, if this value is finite, `false` otherwise
     */
    fun isFinite(): Boolean {
        return value != null
    }

    companion object {
        /**
         * The value 'zero'.
         */
        @JvmField
        val ZERO = ValueDecfloat(BigDecimal.ZERO)

        /**
         * The value 'one'.
         */
        @JvmField
        val ONE = ValueDecfloat(BigDecimal.ONE)

        /**
         * The positive infinity value.
         */
        @JvmField
        val POSITIVE_INFINITY = ValueDecfloat(null)

        /**
         * The negative infinity value.
         */
        @JvmField
        val NEGATIVE_INFINITY = ValueDecfloat(null)

        /**
         * The not a number value.
         */
        @JvmField
        val NAN = ValueDecfloat(null)

        /**
         * Divides to [BigDecimal] values and returns a `DECFLOAT`
         * result of the specified data type.
         *
         * @param dividend the dividend
         * @param divisor the divisor
         * @param quotientType the type of quotient
         * @return the quotient
         */
        @JvmStatic
        fun divide(dividend: BigDecimal, divisor: BigDecimal, quotientType: TypeInfo): ValueDecfloat {
            val quotientPrecision = quotientType.precision.toInt()
            var quotient = dividend.divide(divisor,
                    dividend.scale() - dividend.precision() + divisor.precision() - divisor.scale() + quotientPrecision,
                    RoundingMode.HALF_DOWN)
            val precision = quotient.precision()
            if (precision > quotientPrecision) {
                quotient = quotient.setScale(quotient.scale() - precision + quotientPrecision, RoundingMode.HALF_UP)
            }
            return get(quotient)
        }

        /**
         * Get or create a DECFLOAT value for the given big decimal.
         *
         * @param dec the big decimal
         * @return the value
         */
        @JvmStatic
        fun get(dec: BigDecimal): ValueDecfloat {
            val d = dec.stripTrailingZeros()
            if (BigDecimal.ZERO == d) {
                return ZERO
            } else if (BigDecimal.ONE == d) {
                return ONE
            }
            return Value.cache(ValueDecfloat(d)) as ValueDecfloat
        }
    }
}
