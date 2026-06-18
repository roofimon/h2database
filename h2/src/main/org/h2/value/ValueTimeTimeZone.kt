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

/**
 * Implementation of the TIME WITH TIME ZONE data type.
 */
class ValueTimeTimeZone private constructor(
    /**
     * Nanoseconds since midnight
     */
    val nanos: Long,
    /**
     * Time zone offset from UTC in seconds, range of -18 hours to +18 hours.
     * This range is compatible with OffsetTime from JSR-310.
     */
    val timeZoneOffsetSeconds: Int
) : Value() {

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_TIME_TZ
    }

    override fun getValueType(): Int {
        return TIME_TZ
    }

    override fun getMemory(): Int {
        return 32
    }

    override fun getString(): String {
        return toString(StringBuilder(MAXIMUM_PRECISION)).toString()
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return toString(builder.append("TIME WITH TIME ZONE '")).append('\'')
    }

    private fun toString(builder: StringBuilder): StringBuilder {
        return DateTimeUtils.appendTimeZone(DateTimeUtils.appendTime(builder, nanos), timeZoneOffsetSeconds)
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        val t = o as ValueTimeTimeZone
        return java.lang.Long.compare(
            nanos - timeZoneOffsetSeconds * DateTimeUtils.NANOS_PER_SECOND,
            t.nanos - t.timeZoneOffsetSeconds * DateTimeUtils.NANOS_PER_SECOND
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is ValueTimeTimeZone) {
            return false
        }
        return nanos == other.nanos && timeZoneOffsetSeconds == other.timeZoneOffsetSeconds
    }

    override fun hashCode(): Int {
        return (nanos xor (nanos ushr 32) xor timeZoneOffsetSeconds.toLong()).toInt()
    }

    companion object {
        /**
         * The default precision and display size of the textual representation of a
         * time. Example: 10:00:00+10:00
         */
        const val DEFAULT_PRECISION = 14

        /**
         * The maximum precision and display size of the textual representation of a
         * time. Example: 10:00:00.123456789+10:00
         */
        const val MAXIMUM_PRECISION = 24

        /**
         * Get or create a time value.
         *
         * @param nanos
         *            the nanoseconds since midnight
         * @param timeZoneOffsetSeconds
         *            the timezone offset in seconds
         * @return the value
         */
        @JvmStatic
        fun fromNanos(nanos: Long, timeZoneOffsetSeconds: Int): ValueTimeTimeZone {
            if (nanos < 0L || nanos >= DateTimeUtils.NANOS_PER_DAY) {
                throw DbException.get(
                    ErrorCode.INVALID_DATETIME_CONSTANT_2, "TIME WITH TIME ZONE",
                    DateTimeUtils.appendTime(StringBuilder(), nanos).toString()
                )
            }
            /*
             * Some current and historic time zones have offsets larger than 12
             * hours. JSR-310 determines 18 hours as maximum possible offset in both
             * directions, so we use this limit too for compatibility.
             */
            if (timeZoneOffsetSeconds < (-18 * 60 * 60) || timeZoneOffsetSeconds > (18 * 60 * 60)) {
                throw IllegalArgumentException("timeZoneOffsetSeconds $timeZoneOffsetSeconds")
            }
            return Value.cache(ValueTimeTimeZone(nanos, timeZoneOffsetSeconds)) as ValueTimeTimeZone
        }

        /**
         * Parse a string to a ValueTime.
         *
         * @param s
         *            the string to parse
         * @param provider
         *            the cast information provider, may be {@code null} for
         *            literals with time zone
         * @return the time
         */
        @JvmStatic
        fun parse(s: String, provider: CastDataProvider?): ValueTimeTimeZone {
            try {
                return DateTimeUtils.parseTime(s, provider, true) as ValueTimeTimeZone
            } catch (e: Exception) {
                throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, e, "TIME WITH TIME ZONE", s)
            }
        }
    }
}
