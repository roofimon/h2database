/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

import org.h2.api.Interval
import org.h2.api.IntervalQualifier
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.DateTimeUtils
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_DAY
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_HOUR
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_MINUTE
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_SECOND
import org.h2.util.DateTimeUtils.Companion.convertScale
import org.h2.util.IntervalUtils
import org.h2.util.IntervalUtils.Companion.appendInterval
import org.h2.util.IntervalUtils.Companion.intervalFromAbsolute
import org.h2.util.IntervalUtils.Companion.intervalToAbsolute
import org.h2.util.IntervalUtils.Companion.validateInterval

/**
 * Implementation of the INTERVAL data type.
 */
class ValueInterval private constructor(
    private val valueType: Int,
    private val negative: Boolean,
    val leading: Long,
    val remaining: Long
) : Value() {

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return appendInterval(builder, getQualifier(), negative, leading, remaining)
    }

    override fun getType(): TypeInfo {
        return TypeInfo.getTypeInfo(valueType)
    }

    override fun getValueType(): Int {
        return valueType
    }

    override fun getMemory(): Int {
        // Java 11 with -XX:-UseCompressedOops
        return 48
    }

    /**
     * Check if the precision is smaller or equal than the given precision.
     *
     * @param prec
     *            the maximum precision
     * @return true if the precision of this value is smaller or equal to the
     *         given precision
     */
    fun checkPrecision(prec: Long): Boolean {
        if (prec < MAXIMUM_PRECISION) {
            var p = 1L
            var precision = 0L
            while (leading >= p) {
                if (++precision > prec) {
                    return false
                }
                p *= 10
            }
        }
        return true
    }

    fun setPrecisionAndScale(targetType: TypeInfo, column: Any?): ValueInterval {
        val targetScale = targetType.getScale()
        var v: ValueInterval = this
        run convertScale@{
            if (targetScale < MAXIMUM_SCALE) {
                val range: Long = when (valueType) {
                    INTERVAL_SECOND -> NANOS_PER_SECOND
                    INTERVAL_DAY_TO_SECOND -> NANOS_PER_DAY
                    INTERVAL_HOUR_TO_SECOND -> NANOS_PER_HOUR
                    INTERVAL_MINUTE_TO_SECOND -> NANOS_PER_MINUTE
                    else -> return@convertScale
                }
                var l = leading
                var r = convertScale(
                    remaining, targetScale,
                    if (l == 999_999_999_999_999_999L) range else Long.MAX_VALUE
                )
                if (r != remaining) {
                    if (r >= range) {
                        l++
                        r -= range
                    }
                    v = from(v.getQualifier(), v.isNegative(), l, r)
                }
            }
        }
        if (!v.checkPrecision(targetType.getPrecision())) {
            throw v.getValueTooLongException(targetType, column)
        }
        return v
    }

    override fun getString(): String {
        return appendInterval(StringBuilder(), getQualifier(), negative, leading, remaining).toString()
    }

    override fun getLong(): Long {
        var l = leading
        if (valueType >= INTERVAL_SECOND && remaining != 0L &&
            remaining >= (MULTIPLIERS[valueType - INTERVAL_SECOND] shr 1)
        ) {
            l++
        }
        return if (negative) -l else l
    }

    override fun getBigInteger(): BigInteger {
        return BigInteger.valueOf(getLong())
    }

    override fun getBigDecimal(): BigDecimal {
        if (valueType < INTERVAL_SECOND || remaining == 0L) {
            return BigDecimal.valueOf(if (negative) -leading else leading)
        }
        val m = BigDecimal.valueOf(MULTIPLIERS[valueType - INTERVAL_SECOND])
        val bd = BigDecimal.valueOf(leading)
            .add(BigDecimal.valueOf(remaining).divide(m, m.precision(), RoundingMode.HALF_DOWN))
            .stripTrailingZeros()
        return if (negative) bd.negate() else bd
    }

    override fun getFloat(): Float {
        if (valueType < INTERVAL_SECOND || remaining == 0L) {
            return (if (negative) -leading else leading).toFloat()
        }
        return getBigDecimal().toFloat()
    }

    override fun getDouble(): Double {
        if (valueType < INTERVAL_SECOND || remaining == 0L) {
            return (if (negative) -leading else leading).toDouble()
        }
        return getBigDecimal().toDouble()
    }

    /**
     * Returns the interval.
     *
     * @return the interval
     */
    fun getInterval(): Interval {
        return Interval(getQualifier(), negative, leading, remaining)
    }

    /**
     * Returns the interval qualifier.
     *
     * @return the interval qualifier
     */
    fun getQualifier(): IntervalQualifier {
        return IntervalQualifier.valueOf(valueType - INTERVAL_YEAR)
    }

    /**
     * Returns where the interval is negative.
     *
     * @return where the interval is negative
     */
    fun isNegative(): Boolean {
        return negative
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + valueType
        result = prime * result + (if (negative) 1231 else 1237)
        result = prime * result + (leading xor (leading ushr 32)).toInt()
        result = prime * result + (remaining xor (remaining ushr 32)).toInt()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj !is ValueInterval) {
            return false
        }
        return valueType == obj.valueType && negative == obj.negative && leading == obj.leading &&
            remaining == obj.remaining
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        val other = v as ValueInterval
        if (negative != other.negative) {
            return if (negative) -1 else 1
        }
        var cmp = java.lang.Long.compare(leading, other.leading)
        if (cmp == 0) {
            cmp = java.lang.Long.compare(remaining, other.remaining)
        }
        return if (negative) -cmp else cmp
    }

    override fun getSignum(): Int {
        return if (negative) -1 else if (leading == 0L && remaining == 0L) 0 else 1
    }

    override fun add(v: Value): Value {
        return intervalFromAbsolute(
            getQualifier(),
            intervalToAbsolute(this).add(intervalToAbsolute(v as ValueInterval))
        )
    }

    override fun subtract(v: Value): Value {
        return intervalFromAbsolute(
            getQualifier(),
            intervalToAbsolute(this).subtract(intervalToAbsolute(v as ValueInterval))
        )
    }

    override fun negate(): Value {
        if (leading == 0L && remaining == 0L) {
            return this
        }
        return Value.cache(ValueInterval(valueType, !negative, leading, remaining))
    }

    companion object {
        /**
         * The default leading field precision for intervals.
         */
        const val DEFAULT_PRECISION = 2

        /**
         * The maximum leading field precision for intervals.
         */
        const val MAXIMUM_PRECISION = 18

        /**
         * The default scale for intervals with seconds.
         */
        const val DEFAULT_SCALE = 6

        /**
         * The maximum scale for intervals with seconds.
         */
        const val MAXIMUM_SCALE = 9

        private val MULTIPLIERS = longArrayOf(
            // INTERVAL_SECOND
            DateTimeUtils.NANOS_PER_SECOND,
            // INTERVAL_YEAR_TO_MONTH
            12L,
            // INTERVAL_DAY_TO_HOUR
            24L,
            // INTERVAL_DAY_TO_MINUTE
            24L * 60,
            // INTERVAL_DAY_TO_SECOND
            DateTimeUtils.NANOS_PER_DAY,
            // INTERVAL_HOUR_TO_MINUTE:
            60L,
            // INTERVAL_HOUR_TO_SECOND
            DateTimeUtils.NANOS_PER_HOUR,
            // INTERVAL_MINUTE_TO_SECOND
            DateTimeUtils.NANOS_PER_MINUTE //
        )

        /**
         * Create a ValueInterval instance.
         *
         * @param qualifier
         *            qualifier
         * @param negative
         *            whether interval is negative
         * @param leading
         *            value of leading field
         * @param remaining
         *            values of all remaining fields
         * @return interval value
         */
        @JvmStatic
        fun from(qualifier: IntervalQualifier, negative: Boolean, leading: Long, remaining: Long): ValueInterval {
            val neg = validateInterval(qualifier, negative, leading, remaining)
            return Value.cache(
                ValueInterval(qualifier.ordinal + INTERVAL_YEAR, neg, leading, remaining)
            ) as ValueInterval
        }

        /**
         * Returns display size for the specified qualifier, precision and
         * fractional seconds precision.
         *
         * @param type
         *            the value type
         * @param precision
         *            leading field precision
         * @param scale
         *            fractional seconds precision. Ignored if specified type of
         *            interval does not have seconds.
         * @return display size
         */
        @JvmStatic
        fun getDisplaySize(type: Int, precision: Int, scale: Int): Int {
            when (type) {
                INTERVAL_YEAR, INTERVAL_HOUR ->
                    // INTERVAL '-11' YEAR
                    // INTERVAL '-11' HOUR
                    return 17 + precision
                INTERVAL_MONTH ->
                    // INTERVAL '-11' MONTH
                    return 18 + precision
                INTERVAL_DAY ->
                    // INTERVAL '-11' DAY
                    return 16 + precision
                INTERVAL_MINUTE ->
                    // INTERVAL '-11' MINUTE
                    return 19 + precision
                INTERVAL_SECOND ->
                    // INTERVAL '-11' SECOND
                    // INTERVAL '-11.999999' SECOND
                    return if (scale > 0) 20 + precision + scale else 19 + precision
                INTERVAL_YEAR_TO_MONTH ->
                    // INTERVAL '-11-11' YEAR TO MONTH
                    return 29 + precision
                INTERVAL_DAY_TO_HOUR ->
                    // INTERVAL '-11 23' DAY TO HOUR
                    return 27 + precision
                INTERVAL_DAY_TO_MINUTE ->
                    // INTERVAL '-11 23:59' DAY TO MINUTE
                    return 32 + precision
                INTERVAL_DAY_TO_SECOND ->
                    // INTERVAL '-11 23:59.59' DAY TO SECOND
                    // INTERVAL '-11 23:59.59.999999' DAY TO SECOND
                    return if (scale > 0) 36 + precision + scale else 35 + precision
                INTERVAL_HOUR_TO_MINUTE ->
                    // INTERVAL '-11:59' HOUR TO MINUTE
                    return 30 + precision
                INTERVAL_HOUR_TO_SECOND ->
                    // INTERVAL '-11:59:59' HOUR TO SECOND
                    // INTERVAL '-11:59:59.999999' HOUR TO SECOND
                    return if (scale > 0) 34 + precision + scale else 33 + precision
                INTERVAL_MINUTE_TO_SECOND ->
                    // INTERVAL '-11:59' MINUTE TO SECOND
                    // INTERVAL '-11:59.999999' MINUTE TO SECOND
                    return if (scale > 0) 33 + precision + scale else 32 + precision
                else ->
                    throw DbException.getUnsupportedException(Integer.toString(type))
            }
        }
    }
}
