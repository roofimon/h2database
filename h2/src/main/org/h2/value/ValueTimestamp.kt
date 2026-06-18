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
import org.h2.util.DateTimeUtils.Companion.MAX_DATE_VALUE
import org.h2.util.DateTimeUtils.Companion.MIN_DATE_VALUE
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_DAY
import org.h2.util.DateTimeUtils.Companion.absoluteDayFromDateValue
import org.h2.util.DateTimeUtils.Companion.appendDate
import org.h2.util.DateTimeUtils.Companion.appendTime
import org.h2.util.DateTimeUtils.Companion.dateValueFromAbsoluteDay

/**
 * Implementation of the TIMESTAMP data type.
 */
class ValueTimestamp private constructor(
    /**
     * A bit field with bits for the year, month, and day (see DateTimeUtils for
     * encoding)
     */
    val dateValue: Long,
    /**
     * The nanoseconds since midnight.
     */
    val timeNanos: Long
) : Value() {

    init {
        if (dateValue < MIN_DATE_VALUE || dateValue > MAX_DATE_VALUE) {
            throw IllegalArgumentException("dateValue out of range $dateValue")
        }
        if (timeNanos < 0 || timeNanos >= NANOS_PER_DAY) {
            throw IllegalArgumentException("timeNanos out of range $timeNanos")
        }
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_TIMESTAMP
    }

    override fun getValueType(): Int {
        return TIMESTAMP
    }

    override fun getMemory(): Int {
        return 32
    }

    override fun getString(): String {
        return toString(StringBuilder(MAXIMUM_PRECISION), false).toString()
    }

    /**
     * Returns value as string in ISO format.
     *
     * @return value as string in ISO format
     */
    fun getISOString(): String {
        return toString(StringBuilder(MAXIMUM_PRECISION), true).toString()
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return toString(builder.append("TIMESTAMP '"), false).append('\'')
    }

    private fun toString(builder: StringBuilder, iso: Boolean): StringBuilder {
        appendDate(builder, dateValue).append(if (iso) 'T' else ' ')
        return appendTime(builder, timeNanos)
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        val t = o as ValueTimestamp
        val c = java.lang.Long.compare(dateValue, t.dateValue)
        if (c != 0) {
            return c
        }
        return java.lang.Long.compare(timeNanos, t.timeNanos)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is ValueTimestamp) {
            return false
        }
        return dateValue == other.dateValue && timeNanos == other.timeNanos
    }

    override fun hashCode(): Int {
        return (dateValue xor (dateValue ushr 32) xor timeNanos xor (timeNanos ushr 32)).toInt()
    }

    override fun add(v: Value): Value {
        val t = v as ValueTimestamp
        var absoluteDay = absoluteDayFromDateValue(dateValue) + absoluteDayFromDateValue(t.dateValue)
        var nanos = timeNanos + t.timeNanos
        if (nanos >= NANOS_PER_DAY) {
            nanos -= NANOS_PER_DAY
            absoluteDay++
        }
        return fromDateValueAndNanos(dateValueFromAbsoluteDay(absoluteDay), nanos)
    }

    override fun subtract(v: Value): Value {
        val t = v as ValueTimestamp
        var absoluteDay = absoluteDayFromDateValue(dateValue) - absoluteDayFromDateValue(t.dateValue)
        var nanos = timeNanos - t.timeNanos
        if (nanos < 0) {
            nanos += NANOS_PER_DAY
            absoluteDay--
        }
        return fromDateValueAndNanos(dateValueFromAbsoluteDay(absoluteDay), nanos)
    }

    companion object {
        /**
         * The default precision and display size of the textual representation of a timestamp.
         * Example: 2001-01-01 23:59:59.123456
         */
        const val DEFAULT_PRECISION = 26

        /**
         * The maximum precision and display size of the textual representation of a timestamp.
         * Example: 2001-01-01 23:59:59.123456789
         */
        const val MAXIMUM_PRECISION = 29

        /**
         * The default scale for timestamps.
         */
        const val DEFAULT_SCALE = 6

        /**
         * The maximum scale for timestamps.
         */
        const val MAXIMUM_SCALE = 9

        /**
         * Get or create a date value for the given date.
         *
         * @param dateValue the date value, a bit field with bits for the year,
         *            month, and day
         * @param timeNanos the nanoseconds since midnight
         * @return the value
         */
        @JvmStatic
        fun fromDateValueAndNanos(dateValue: Long, timeNanos: Long): ValueTimestamp {
            return Value.cache(ValueTimestamp(dateValue, timeNanos)) as ValueTimestamp
        }

        /**
         * Parse a string to a ValueTimestamp, using the given [CastDataProvider].
         * This method supports the format +/-year-month-day[ -]hour[:.]minute[:.]seconds.fractional
         * and an optional timezone part.
         *
         * @param s the string to parse
         * @param provider
         *            the cast information provider, may be `null` for
         *            literals without time zone
         * @return the date
         */
        @JvmStatic
        fun parse(s: String, provider: CastDataProvider?): ValueTimestamp {
            try {
                return DateTimeUtils.parseTimestamp(s, provider, false) as ValueTimestamp
            } catch (e: Exception) {
                throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, e, "TIMESTAMP", s)
            }
        }
    }
}
