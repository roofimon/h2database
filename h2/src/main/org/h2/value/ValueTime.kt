/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.DateTimeUtils
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_HOUR

/**
 * Implementation of the TIME data type.
 */
class ValueTime private constructor(val nanos: Long) : Value() {

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_TIME
    }

    override fun getValueType(): Int {
        return TIME
    }

    override fun getString(): String {
        return DateTimeUtils.appendTime(StringBuilder(MAXIMUM_PRECISION), nanos).toString()
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return DateTimeUtils.appendTime(builder.append("TIME '"), nanos).append('\'')
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return java.lang.Long.compare(nanos, (o as ValueTime).nanos)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is ValueTime && nanos == other.nanos
    }

    override fun hashCode(): Int {
        return (nanos xor (nanos ushr 32)).toInt()
    }

    override fun add(v: Value): Value {
        val t = v as ValueTime
        return fromNanos(nanos + t.nanos)
    }

    override fun subtract(v: Value): Value {
        val t = v as ValueTime
        return fromNanos(nanos - t.nanos)
    }

    override fun multiply(v: Value): Value {
        return fromNanos((nanos * v.getDouble()).toLong())
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        return fromNanos((nanos / v.getDouble()).toLong())
    }

    companion object {
        /**
         * The default precision and display size of the textual representation of a time.
         * Example: 10:00:00
         */
        const val DEFAULT_PRECISION = 8

        /**
         * The maximum precision and display size of the textual representation of a time.
         * Example: 10:00:00.123456789
         */
        const val MAXIMUM_PRECISION = 18

        /**
         * The default scale for time.
         */
        const val DEFAULT_SCALE = 0

        /**
         * The maximum scale for time.
         */
        const val MAXIMUM_SCALE = 9

        private val STATIC_CACHE: Array<ValueTime?> = arrayOfNulls(24)

        init {
            for (hour in 0 until 24) {
                STATIC_CACHE[hour] = ValueTime(hour * NANOS_PER_HOUR)
            }
        }

        /**
         * Get or create a time value.
         *
         * @param nanos the nanoseconds since midnight
         * @return the value
         */
        @JvmStatic
        fun fromNanos(nanos: Long): ValueTime {
            if (nanos < 0L || nanos >= DateTimeUtils.NANOS_PER_DAY) {
                throw DbException.get(
                    ErrorCode.INVALID_DATETIME_CONSTANT_2, "TIME",
                    DateTimeUtils.appendTime(StringBuilder(), nanos).toString()
                )
            }
            if (nanos % NANOS_PER_HOUR == 0L) {
                return STATIC_CACHE[(nanos / NANOS_PER_HOUR).toInt()]!!
            }
            return Value.cache(ValueTime(nanos)) as ValueTime
        }

        /**
         * Parse a string to a ValueTime.
         *
         * @param s the string to parse
         * @param provider
         *            the cast information provider, may be {@code null} for
         *            literals without time zone
         * @return the time
         */
        @JvmStatic
        fun parse(s: String, provider: CastDataProvider?): ValueTime {
            try {
                return DateTimeUtils.parseTime(s, provider, false) as ValueTime
            } catch (e: Exception) {
                throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, e, "TIME", s)
            }
        }
    }
}
