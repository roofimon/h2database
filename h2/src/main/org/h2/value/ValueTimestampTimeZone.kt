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
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_SECOND
import org.h2.util.DateTimeUtils.Companion.appendDate
import org.h2.util.DateTimeUtils.Companion.appendTime
import org.h2.util.DateTimeUtils.Companion.appendTimeZone
import org.h2.util.DateTimeUtils.Companion.decrementDateValue
import org.h2.util.DateTimeUtils.Companion.incrementDateValue

/**
 * Implementation of the TIMESTAMP WITH TIME ZONE data type.
 */
class ValueTimestampTimeZone private constructor(
    /**
     * A bit field with bits for the year, month, and day (see DateTimeUtils for
     * encoding)
     */
    val dateValue: Long,
    /**
     * The nanoseconds since midnight.
     */
    val timeNanos: Long,
    /**
     * Time zone offset from UTC in seconds, range of -18 hours to +18 hours. This
     * range is compatible with OffsetDateTime from JSR-310.
     */
    val timeZoneOffsetSeconds: Int
) : Value() {

    init {
        if (dateValue < MIN_DATE_VALUE || dateValue > MAX_DATE_VALUE) {
            throw IllegalArgumentException("dateValue out of range $dateValue")
        }
        if (timeNanos < 0 || timeNanos >= NANOS_PER_DAY) {
            throw IllegalArgumentException("timeNanos out of range $timeNanos")
        }
        /*
         * Some current and historic time zones have offsets larger than 12 hours.
         * JSR-310 determines 18 hours as maximum possible offset in both directions, so
         * we use this limit too for compatibility.
         */
        if (timeZoneOffsetSeconds < (-18 * 60 * 60) || timeZoneOffsetSeconds > (18 * 60 * 60)) {
            throw IllegalArgumentException("timeZoneOffsetSeconds out of range $timeZoneOffsetSeconds")
        }
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_TIMESTAMP_TZ
    }

    override fun getValueType(): Int {
        return TIMESTAMP_TZ
    }

    override fun getMemory(): Int {
        // Java 11 with -XX:-UseCompressedOops
        return 40
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
        return toString(builder.append("TIMESTAMP WITH TIME ZONE '"), false).append('\'')
    }

    private fun toString(builder: StringBuilder, iso: Boolean): StringBuilder {
        appendDate(builder, dateValue).append(if (iso) 'T' else ' ')
        appendTime(builder, timeNanos)
        return appendTimeZone(builder, timeZoneOffsetSeconds)
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        val t = o as ValueTimestampTimeZone
        // Maximum time zone offset is +/-18 hours so difference in days between local
        // and UTC cannot be more than one day
        var dateValueA = dateValue
        var timeA = timeNanos - timeZoneOffsetSeconds * NANOS_PER_SECOND
        if (timeA < 0) {
            timeA += NANOS_PER_DAY
            dateValueA = decrementDateValue(dateValueA)
        } else if (timeA >= NANOS_PER_DAY) {
            timeA -= NANOS_PER_DAY
            dateValueA = incrementDateValue(dateValueA)
        }
        var dateValueB = t.dateValue
        var timeB = t.timeNanos - t.timeZoneOffsetSeconds * NANOS_PER_SECOND
        if (timeB < 0) {
            timeB += NANOS_PER_DAY
            dateValueB = decrementDateValue(dateValueB)
        } else if (timeB >= NANOS_PER_DAY) {
            timeB -= NANOS_PER_DAY
            dateValueB = incrementDateValue(dateValueB)
        }
        val cmp = java.lang.Long.compare(dateValueA, dateValueB)
        if (cmp != 0) {
            return cmp
        }
        return java.lang.Long.compare(timeA, timeB)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is ValueTimestampTimeZone) {
            return false
        }
        return dateValue == other.dateValue && timeNanos == other.timeNanos &&
            timeZoneOffsetSeconds == other.timeZoneOffsetSeconds
    }

    override fun hashCode(): Int {
        return (dateValue xor (dateValue ushr 32) xor timeNanos xor (timeNanos ushr 32)
            xor timeZoneOffsetSeconds.toLong()).toInt()
    }

    companion object {
        /**
         * The default precision and display size of the textual representation of a timestamp.
         * Example: 2001-01-01 23:59:59.123456+10:00
         */
        const val DEFAULT_PRECISION = 32

        /**
         * The maximum precision and display size of the textual representation of a timestamp.
         * Example: 2001-01-01 23:59:59.123456789+10:00
         */
        const val MAXIMUM_PRECISION = 35

        /**
         * Get or create a date value for the given date.
         *
         * @param dateValue the date value, a bit field with bits for the year,
         *            month, and day
         * @param timeNanos the nanoseconds since midnight
         * @param timeZoneOffsetSeconds the timezone offset in seconds
         * @return the value
         */
        @JvmStatic
        fun fromDateValueAndNanos(
            dateValue: Long, timeNanos: Long,
            timeZoneOffsetSeconds: Int
        ): ValueTimestampTimeZone {
            return Value.cache(
                ValueTimestampTimeZone(dateValue, timeNanos, timeZoneOffsetSeconds)
            ) as ValueTimestampTimeZone
        }

        /**
         * Parse a string to a ValueTimestamp. This method supports the format
         * +/-year-month-day hour:minute:seconds.fractional and an optional timezone
         * part.
         *
         * @param s the string to parse
         * @param provider
         *            the cast information provider, may be `null` for
         *            literals with time zone
         * @return the date
         */
        @JvmStatic
        fun parse(s: String, provider: CastDataProvider?): ValueTimestampTimeZone {
            try {
                return DateTimeUtils.parseTimestamp(s, provider, true) as ValueTimestampTimeZone
            } catch (e: Exception) {
                throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, e, "TIMESTAMP WITH TIME ZONE", s)
            }
        }
    }
}
