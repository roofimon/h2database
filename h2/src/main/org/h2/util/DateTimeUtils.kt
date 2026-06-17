/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0, and the
 * EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 * Iso8601: Initial Developer: Robert Rathsack (firstName dot lastName at gmx
 * dot de)
 */
package org.h2.util

import java.time.Instant

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.value.TypeInfo
import org.h2.value.Value
import org.h2.value.ValueDate
import org.h2.value.ValueTime
import org.h2.value.ValueTimeTimeZone
import org.h2.value.ValueTimestamp
import org.h2.value.ValueTimestampTimeZone

/**
 * This utility class contains time conversion functions.
 *
 *
 * Date value: a bit field with bits for the year, month, and day. Absolute day:
 * the day number (0 means 1970-01-01).
 */
class DateTimeUtils private constructor() {

    companion object {

        /**
         * The number of milliseconds per day.
         */
        const val MILLIS_PER_DAY: Long = 24 * 60 * 60 * 1000L

        /**
         * The number of seconds per day.
         */
        const val SECONDS_PER_DAY: Long = (24 * 60 * 60).toLong()

        /**
         * The number of nanoseconds per second.
         */
        const val NANOS_PER_SECOND: Long = 1_000_000_000

        /**
         * The number of nanoseconds per minute.
         */
        const val NANOS_PER_MINUTE: Long = 60 * NANOS_PER_SECOND

        /**
         * The number of nanoseconds per hour.
         */
        const val NANOS_PER_HOUR: Long = 60 * NANOS_PER_MINUTE

        /**
         * The number of nanoseconds per day.
         */
        const val NANOS_PER_DAY: Long = MILLIS_PER_DAY * 1_000_000

        /**
         * The offset of year bits in date values.
         */
        const val SHIFT_YEAR: Int = 9

        /**
         * The offset of month bits in date values.
         */
        const val SHIFT_MONTH: Int = 5

        /**
         * Date value for 1970-01-01.
         */
        const val EPOCH_DATE_VALUE: Int = (1970 shl SHIFT_YEAR) + (1 shl SHIFT_MONTH) + 1

        /**
         * Minimum possible date value.
         */
        const val MIN_DATE_VALUE: Long = (-1_000_000_000L shl SHIFT_YEAR) + (1 shl SHIFT_MONTH) + 1

        /**
         * Maximum possible date value.
         */
        const val MAX_DATE_VALUE: Long = (1_000_000_000L shl SHIFT_YEAR) + (12 shl SHIFT_MONTH) + 31

        private val NORMAL_DAYS_PER_MONTH = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        /**
         * Multipliers for [.convertScale] and
         * [.appendNanos].
         */
        @JvmField
        val FRACTIONAL_SECONDS_TABLE = intArrayOf(
            1_000_000_000, 100_000_000,
            10_000_000, 1_000_000, 100_000, 10_000, 1_000, 100, 10, 1
        )

        @Volatile
        private var LOCAL: TimeZoneProvider? = null

        /**
         * Reset the cached calendar for default timezone, for example after
         * changing the default timezone.
         */
        @JvmStatic
        fun resetCalendar() {
            LOCAL = null
        }

        /**
         * Get the time zone provider for the default time zone.
         *
         * @return the time zone provider for the default time zone
         */
        @JvmStatic
        fun getTimeZone(): TimeZoneProvider {
            var local = LOCAL
            if (local == null) {
                local = TimeZoneProvider.getDefault()
                LOCAL = local
            }
            return local
        }

        /**
         * Returns current timestamp.
         *
         * @param timeZone
         * the time zone
         * @return current timestamp
         */
        @JvmStatic
        fun currentTimestamp(timeZone: TimeZoneProvider): ValueTimestampTimeZone {
            return currentTimestamp(timeZone, Instant.now())
        }

        /**
         * Returns current timestamp using the specified instant for its value.
         *
         * @param timeZone
         * the time zone
         * @param now
         * timestamp source, must be greater than or equal to
         * 1970-01-01T00:00:00Z
         * @return current timestamp
         */
        @JvmStatic
        fun currentTimestamp(timeZone: TimeZoneProvider, now: Instant): ValueTimestampTimeZone {
            /*
             * This code intentionally does not support properly dates before UNIX
             * epoch because such support is not required for current dates.
             */
            var second = now.epochSecond
            val offset = timeZone.getTimeZoneOffsetUTC(second)
            second += offset.toLong()
            return ValueTimestampTimeZone.fromDateValueAndNanos(
                dateValueFromAbsoluteDay(second / SECONDS_PER_DAY),
                second % SECONDS_PER_DAY * 1_000_000_000 + now.nano, offset
            )
        }

        /**
         * Parse a date string. The format is: [+|-]year-month-day
         * or [+|-]yyyyMMdd.
         *
         * @param s the string to parse
         * @param start the parse index start
         * @param end the parse index end
         * @return the date value
         * @throws IllegalArgumentException if there is a problem
         */
        @JvmStatic
        fun parseDateValue(s: String, start: Int, end: Int): Long {
            var start = start
            if (s[start] == '+') {
                // +year
                start++
            }
            // start at position 1 to support "-year"
            var yEnd = s.indexOf('-', start + 1)
            val mStart: Int
            val mEnd: Int
            val dStart: Int
            if (yEnd > 0) {
                // Standard [+|-]year-month-day format
                mStart = yEnd + 1
                mEnd = s.indexOf('-', mStart)
                if (mEnd <= mStart) {
                    throw IllegalArgumentException(s)
                }
                dStart = mEnd + 1
            } else {
                // Additional [+|-]yyyyMMdd format for compatibility
                dStart = end - 2
                mEnd = dStart
                mStart = mEnd - 2
                yEnd = mStart
                // Accept only 3 or more digits in year for now
                if (yEnd < start + 3) {
                    throw IllegalArgumentException(s)
                }
            }
            val year = Integer.parseInt(s, start, yEnd, 10)
            val month = StringUtils.parseUInt31(s, mStart, mEnd)
            val day = StringUtils.parseUInt31(s, dStart, end)
            if (!isValidDate(year, month, day)) {
                throw IllegalArgumentException("$year-$month-$day")
            }
            return dateValue(year.toLong(), month, day)
        }

        /**
         * Parse a time string. The format is: hour:minute[:second[.nanos]],
         * hhmm[ss[.nanos]], or hour.minute.second[.nanos].
         *
         * @param s the string to parse
         * @param start the parse index start
         * @param end the parse index end
         * @return the time in nanoseconds
         * @throws IllegalArgumentException if there is a problem
         */
        @JvmStatic
        fun parseTimeNanos(s: String, start: Int, end: Int): Long {
            val hour: Int
            val minute: Int
            val second: Int
            val nanos: Int
            var hEnd = s.indexOf(':', start)
            val mStart: Int
            var mEnd: Int
            val sStart: Int
            val sEnd: Int
            if (hEnd > 0) {
                mStart = hEnd + 1
                mEnd = s.indexOf(':', mStart)
                if (mEnd >= mStart) {
                    // Standard hour:minute:second[.nanos] format
                    sStart = mEnd + 1
                    sEnd = s.indexOf('.', sStart)
                } else {
                    // Additional hour:minute format for compatibility
                    mEnd = end
                    sEnd = -1
                    sStart = sEnd
                }
            } else {
                val t = s.indexOf('.', start)
                if (t < 0) {
                    // Additional hhmm[ss] format for compatibility
                    mStart = start + 2
                    hEnd = mStart
                    mEnd = mStart + 2
                    val len = end - start
                    if (len == 6) {
                        sStart = mEnd
                        sEnd = -1
                    } else if (len == 4) {
                        sEnd = -1
                        sStart = sEnd
                    } else {
                        throw IllegalArgumentException(s)
                    }
                } else if (t >= start + 6) {
                    // Additional hhmmss.nanos format for compatibility
                    if (t - start != 6) {
                        throw IllegalArgumentException(s)
                    }
                    mStart = start + 2
                    hEnd = mStart
                    sStart = mStart + 2
                    mEnd = sStart
                    sEnd = t
                } else {
                    // Additional hour.minute.second[.nanos] IBM DB2 time format
                    hEnd = t
                    mStart = hEnd + 1
                    mEnd = s.indexOf('.', mStart)
                    if (mEnd <= mStart) {
                        throw IllegalArgumentException(s)
                    }
                    sStart = mEnd + 1
                    sEnd = s.indexOf('.', sStart)
                }
            }
            hour = StringUtils.parseUInt31(s, start, hEnd)
            if (hour >= 24) {
                throw IllegalArgumentException(s)
            }
            minute = StringUtils.parseUInt31(s, mStart, mEnd)
            if (sStart > 0) {
                if (sEnd < 0) {
                    second = StringUtils.parseUInt31(s, sStart, end)
                    nanos = 0
                } else {
                    second = StringUtils.parseUInt31(s, sStart, sEnd)
                    nanos = parseNanos(s, sEnd + 1, end)
                }
            } else {
                nanos = 0
                second = nanos
            }
            if (minute >= 60 || second >= 60) {
                throw IllegalArgumentException(s)
            }
            return (((hour * 60L) + minute) * 60 + second) * NANOS_PER_SECOND + nanos
        }

        /**
         * Parse nanoseconds.
         *
         * @param s String to parse.
         * @param start Begin position at the string to read.
         * @param end End position at the string to read.
         * @return Parsed nanoseconds.
         */
        @JvmStatic
        fun parseNanos(s: String, start: Int, end: Int): Int {
            var start = start
            if (start >= end) {
                throw IllegalArgumentException(s)
            }
            var nanos = 0
            var mul = 100_000_000
            do {
                val c = s[start]
                if (c < '0' || c > '9') {
                    throw IllegalArgumentException(s)
                }
                nanos += mul * (c - '0')
                // mul can become 0, but continue loop anyway to ensure that all
                // remaining digits are valid
                mul /= 10
            } while (++start < end)
            return nanos
        }

        /**
         * Parses timestamp value from the specified string.
         *
         * @param s
         * string to parse
         * @param provider
         * the cast information provider, may be `null` for
         * Standard-compliant literals
         * @param withTimeZone
         * if `true` return [ValueTimestampTimeZone] instead of
         * [ValueTimestamp]
         * @return parsed timestamp
         */
        @JvmStatic
        fun parseTimestamp(s: String, provider: CastDataProvider?, withTimeZone: Boolean): Value {
            var dateEnd = s.indexOf(' ')
            if (dateEnd < 0) {
                // ISO 8601 compatibility
                dateEnd = s.indexOf('T')
                if (dateEnd < 0 && provider != null && provider.mode.allowDB2TimestampFormat) {
                    // DB2 also allows dash between date and time
                    dateEnd = s.indexOf('-', s.indexOf('-', s.indexOf('-') + 1) + 1)
                }
            }
            val timeStart: Int
            if (dateEnd < 0) {
                dateEnd = s.length
                timeStart = -1
            } else {
                timeStart = dateEnd + 1
            }
            var dateValue = parseDateValue(s, 0, dateEnd)
            var nanos: Long
            var tz: TimeZoneProvider? = null
            if (timeStart < 0) {
                nanos = 0
            } else {
                dateEnd++
                val timeEnd: Int
                if (s.endsWith("Z")) {
                    tz = TimeZoneProvider.UTC
                    timeEnd = s.length - 1
                } else {
                    var timeZoneStart = s.indexOf('+', dateEnd)
                    if (timeZoneStart < 0) {
                        timeZoneStart = s.indexOf('-', dateEnd)
                    }
                    if (timeZoneStart >= 0) {
                        // Allow [timeZoneName] part after time zone offset
                        var offsetEnd = s.indexOf('[', timeZoneStart + 1)
                        if (offsetEnd < 0) {
                            offsetEnd = s.length
                        }
                        tz = TimeZoneProvider.ofId(s.substring(timeZoneStart, offsetEnd))
                        if (s[timeZoneStart - 1] == ' ') {
                            timeZoneStart--
                        }
                        timeEnd = timeZoneStart
                    } else {
                        timeZoneStart = s.indexOf(' ', dateEnd)
                        if (timeZoneStart > 0) {
                            tz = TimeZoneProvider.ofId(s.substring(timeZoneStart + 1))
                            timeEnd = timeZoneStart
                        } else {
                            timeEnd = s.length
                        }
                    }
                }
                nanos = parseTimeNanos(s, dateEnd, timeEnd)
            }
            if (withTimeZone) {
                val tzSeconds: Int
                if (tz == null) {
                    tz = if (provider != null) provider.currentTimeZone() else getTimeZone()
                }
                tzSeconds = if (tz !== TimeZoneProvider.UTC) {
                    tz.getTimeZoneOffsetUTC(tz.getEpochSecondsFromLocal(dateValue, nanos))
                } else {
                    0
                }
                return ValueTimestampTimeZone.fromDateValueAndNanos(dateValue, nanos, tzSeconds)
            } else if (tz != null) {
                var seconds = tz.getEpochSecondsFromLocal(dateValue, nanos)
                seconds += (if (provider != null) provider.currentTimeZone() else getTimeZone())
                    .getTimeZoneOffsetUTC(seconds).toLong()
                dateValue = dateValueFromLocalSeconds(seconds)
                nanos = nanos % 1_000_000_000 + nanosFromLocalSeconds(seconds)
            }
            return ValueTimestamp.fromDateValueAndNanos(dateValue, nanos)
        }

        /**
         * Parses time value from the specified string.
         *
         * @param s
         * string to parse
         * @param provider
         * the cast information provider, or `null`
         * @param withTimeZone
         * if `true` return [ValueTimeTimeZone] instead of
         * [ValueTime]
         * @return parsed time
         */
        @JvmStatic
        fun parseTime(s: String, provider: CastDataProvider?, withTimeZone: Boolean): Value {
            val timeEnd: Int
            var tz: TimeZoneProvider? = null
            if (s.endsWith("Z")) {
                tz = TimeZoneProvider.UTC
                timeEnd = s.length - 1
            } else {
                var timeZoneStart = s.indexOf('+', 1)
                if (timeZoneStart < 0) {
                    timeZoneStart = s.indexOf('-', 1)
                }
                if (timeZoneStart >= 0) {
                    tz = TimeZoneProvider.ofId(s.substring(timeZoneStart))
                    if (s[timeZoneStart - 1] == ' ') {
                        timeZoneStart--
                    }
                    timeEnd = timeZoneStart
                } else {
                    timeZoneStart = s.indexOf(' ', 1)
                    if (timeZoneStart > 0) {
                        tz = TimeZoneProvider.ofId(s.substring(timeZoneStart + 1))
                        timeEnd = timeZoneStart
                    } else {
                        timeEnd = s.length
                    }
                }
                if (tz != null && !tz.hasFixedOffset()) {
                    throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, "TIME WITH TIME ZONE", s)
                }
            }
            var nanos = parseTimeNanos(s, 0, timeEnd)
            if (withTimeZone) {
                return ValueTimeTimeZone.fromNanos(
                    nanos,
                    if (tz != null) tz.getTimeZoneOffsetUTC(0L)
                    else (if (provider != null) provider.currentTimestamp() else currentTimestamp(getTimeZone()))
                        .timeZoneOffsetSeconds
                )
            }
            if (tz != null) {
                nanos = normalizeNanosOfDay(
                    nanos + ((if (provider != null) provider.currentTimestamp() else currentTimestamp(getTimeZone()))
                        .timeZoneOffsetSeconds - tz.getTimeZoneOffsetUTC(0L)) * NANOS_PER_SECOND
                )
            }
            return ValueTime.fromNanos(nanos)
        }

        /**
         * Calculates the seconds since epoch for the specified date value,
         * nanoseconds since midnight, and time zone offset.
         * @param dateValue
         * date value
         * @param timeNanos
         * nanoseconds since midnight
         * @param offsetSeconds
         * time zone offset in seconds
         * @return seconds since epoch in UTC
         */
        @JvmStatic
        fun getEpochSeconds(dateValue: Long, timeNanos: Long, offsetSeconds: Int): Long {
            return absoluteDayFromDateValue(dateValue) * SECONDS_PER_DAY + timeNanos / NANOS_PER_SECOND - offsetSeconds
        }

        /**
         * Extracts date value and nanos of day from the specified value.
         *
         * @param value
         * value to extract fields from
         * @param provider
         * the cast information provider
         * @return array with date value and nanos of day
         */
        @JvmStatic
        fun dateAndTimeFromValue(value: Value, provider: CastDataProvider?): LongArray {
            var dateValue = EPOCH_DATE_VALUE.toLong()
            var timeNanos: Long = 0
            if (value is ValueTimestamp) {
                dateValue = value.dateValue
                timeNanos = value.timeNanos
            } else if (value is ValueDate) {
                dateValue = value.dateValue
            } else if (value is ValueTime) {
                timeNanos = value.nanos
            } else if (value is ValueTimestampTimeZone) {
                dateValue = value.dateValue
                timeNanos = value.timeNanos
            } else if (value is ValueTimeTimeZone) {
                timeNanos = value.nanos
            } else {
                val v = value.convertTo(TypeInfo.TYPE_TIMESTAMP, provider) as ValueTimestamp
                dateValue = v.dateValue
                timeNanos = v.timeNanos
            }
            return longArrayOf(dateValue, timeNanos)
        }

        /**
         * Creates a new date-time value with the same type as original value. If
         * original value is a ValueTimestampTimeZone or ValueTimeTimeZone, returned
         * value will have the same time zone offset as original value.
         *
         * @param original
         * original value
         * @param dateValue
         * date value for the returned value
         * @param timeNanos
         * nanos of day for the returned value
         * @return new value with specified date value and nanos of day
         */
        @JvmStatic
        fun dateTimeToValue(original: Value, dateValue: Long, timeNanos: Long): Value {
            return when (original.valueType) {
                Value.DATE -> ValueDate.fromDateValue(dateValue)
                Value.TIME -> ValueTime.fromNanos(timeNanos)
                Value.TIME_TZ -> ValueTimeTimeZone.fromNanos(
                    timeNanos, (original as ValueTimeTimeZone).timeZoneOffsetSeconds
                )
                Value.TIMESTAMP_TZ -> ValueTimestampTimeZone.fromDateValueAndNanos(
                    dateValue, timeNanos,
                    (original as ValueTimestampTimeZone).timeZoneOffsetSeconds
                )
                Value.TIMESTAMP -> ValueTimestamp.fromDateValueAndNanos(dateValue, timeNanos)
                else -> ValueTimestamp.fromDateValueAndNanos(dateValue, timeNanos)
            }
        }

        /**
         * Returns day of week.
         *
         * @param dateValue
         * the date value
         * @param firstDayOfWeek
         * first day of week, Monday as 1, Sunday as 7 or 0
         * @return day of week
         * @see .getIsoDayOfWeek
         */
        @JvmStatic
        fun getDayOfWeek(dateValue: Long, firstDayOfWeek: Int): Int {
            return getDayOfWeekFromAbsolute(absoluteDayFromDateValue(dateValue), firstDayOfWeek)
        }

        /**
         * Get the day of the week from the absolute day value.
         *
         * @param absoluteValue the absolute day
         * @param firstDayOfWeek the first day of the week
         * @return the day of week
         */
        @JvmStatic
        fun getDayOfWeekFromAbsolute(absoluteValue: Long, firstDayOfWeek: Int): Int {
            return if (absoluteValue >= 0) ((absoluteValue - firstDayOfWeek + 11) % 7).toInt() + 1
            else ((absoluteValue - firstDayOfWeek - 2) % 7).toInt() + 7
        }

        /**
         * Returns number of day in year.
         *
         * @param dateValue
         * the date value
         * @return number of day in year
         */
        @JvmStatic
        fun getDayOfYear(dateValue: Long): Int {
            val m = monthFromDateValue(dateValue)
            var a = (367 * m - 362) / 12 + dayFromDateValue(dateValue)
            if (m > 2) {
                a--
                val y = yearFromDateValue(dateValue).toLong()
                if ((y and 3) != 0L || (y % 100 == 0L && y % 400 != 0L)) {
                    a--
                }
            }
            return a
        }

        /**
         * Returns ISO day of week.
         *
         * @param dateValue
         * the date value
         * @return ISO day of week, Monday as 1 to Sunday as 7
         * @see .getSundayDayOfWeek
         */
        @JvmStatic
        fun getIsoDayOfWeek(dateValue: Long): Int {
            return getDayOfWeek(dateValue, 1)
        }

        /**
         * Returns ISO number of week in year.
         *
         * @param dateValue
         * the date value
         * @return number of week in year
         * @see .getIsoWeekYear
         * @see .getWeekOfYear
         */
        @JvmStatic
        fun getIsoWeekOfYear(dateValue: Long): Int {
            return getWeekOfYear(dateValue, 1, 4)
        }

        /**
         * Returns ISO week year.
         *
         * @param dateValue
         * the date value
         * @return ISO week year
         * @see .getIsoWeekOfYear
         * @see .getWeekYear
         */
        @JvmStatic
        fun getIsoWeekYear(dateValue: Long): Int {
            return getWeekYear(dateValue, 1, 4)
        }

        /**
         * Returns day of week with Sunday as 1.
         *
         * @param dateValue
         * the date value
         * @return day of week, Sunday as 1 to Monday as 7
         * @see .getIsoDayOfWeek
         */
        @JvmStatic
        fun getSundayDayOfWeek(dateValue: Long): Int {
            return getDayOfWeek(dateValue, 0)
        }

        /**
         * Returns number of week in year.
         *
         * @param dateValue
         * the date value
         * @param firstDayOfWeek
         * first day of week, Monday as 1, Sunday as 7 or 0
         * @param minimalDaysInFirstWeek
         * minimal days in first week of year
         * @return number of week in year
         * @see .getIsoWeekOfYear
         */
        @JvmStatic
        fun getWeekOfYear(dateValue: Long, firstDayOfWeek: Int, minimalDaysInFirstWeek: Int): Int {
            val abs = absoluteDayFromDateValue(dateValue)
            val year = yearFromDateValue(dateValue)
            var base = getWeekYearAbsoluteStart(year, firstDayOfWeek, minimalDaysInFirstWeek)
            if (abs - base < 0) {
                base = getWeekYearAbsoluteStart(year - 1, firstDayOfWeek, minimalDaysInFirstWeek)
            } else if (monthFromDateValue(dateValue) == 12 && 24 + minimalDaysInFirstWeek < dayFromDateValue(dateValue)) {
                if (abs >= getWeekYearAbsoluteStart(year + 1, firstDayOfWeek, minimalDaysInFirstWeek)) {
                    return 1
                }
            }
            return ((abs - base) / 7).toInt() + 1
        }

        /**
         * Get absolute day of the first day in the week year.
         *
         * @param weekYear
         * the week year
         * @param firstDayOfWeek
         * first day of week, Monday as 1, Sunday as 7 or 0
         * @param minimalDaysInFirstWeek
         * minimal days in first week of year
         * @return absolute day of the first day in the week year
         */
        @JvmStatic
        fun getWeekYearAbsoluteStart(weekYear: Int, firstDayOfWeek: Int, minimalDaysInFirstWeek: Int): Long {
            val first = absoluteDayFromYear(weekYear.toLong())
            val daysInFirstWeek = 8 - getDayOfWeekFromAbsolute(first, firstDayOfWeek)
            var base = first + daysInFirstWeek
            if (daysInFirstWeek >= minimalDaysInFirstWeek) {
                base -= 7
            }
            return base
        }

        /**
         * Returns week year.
         *
         * @param dateValue
         * the date value
         * @param firstDayOfWeek
         * first day of week, Monday as 1, Sunday as 7 or 0
         * @param minimalDaysInFirstWeek
         * minimal days in first week of year
         * @return week year
         * @see .getIsoWeekYear
         */
        @JvmStatic
        fun getWeekYear(dateValue: Long, firstDayOfWeek: Int, minimalDaysInFirstWeek: Int): Int {
            val abs = absoluteDayFromDateValue(dateValue)
            val year = yearFromDateValue(dateValue)
            val base = getWeekYearAbsoluteStart(year, firstDayOfWeek, minimalDaysInFirstWeek)
            if (abs < base) {
                return year - 1
            } else if (monthFromDateValue(dateValue) == 12 && 24 + minimalDaysInFirstWeek < dayFromDateValue(dateValue)) {
                if (abs >= getWeekYearAbsoluteStart(year + 1, firstDayOfWeek, minimalDaysInFirstWeek)) {
                    return year + 1
                }
            }
            return year
        }

        /**
         * Returns number of days in month.
         *
         * @param year the year
         * @param month the month
         * @return number of days in the specified month
         */
        @JvmStatic
        fun getDaysInMonth(year: Int, month: Int): Int {
            if (month != 2) {
                return NORMAL_DAYS_PER_MONTH[month]
            }
            return if (isLeapYear(year)) 29 else 28
        }

        @JvmStatic
        fun isLeapYear(year: Int): Boolean {
            return (year and 3) == 0 && (year % 100 != 0 || year % 400 == 0)
        }

        /**
         * Verify if the specified date is valid.
         *
         * @param year the year
         * @param month the month (January is 1)
         * @param day the day (1 is the first of the month)
         * @return true if it is valid
         */
        @JvmStatic
        fun isValidDate(year: Int, month: Int, day: Int): Boolean {
            return month >= 1 && month <= 12 && day >= 1 && day <= getDaysInMonth(year, month)
        }

        /**
         * Get the year from a date value.
         *
         * @param x the date value
         * @return the year
         */
        @JvmStatic
        fun yearFromDateValue(x: Long): Int {
            return (x ushr SHIFT_YEAR).toInt()
        }

        /**
         * Get the month from a date value.
         *
         * @param x the date value
         * @return the month (1..12)
         */
        @JvmStatic
        fun monthFromDateValue(x: Long): Int {
            return (x ushr SHIFT_MONTH).toInt() and 15
        }

        /**
         * Get the day of month from a date value.
         *
         * @param x the date value
         * @return the day (1..31)
         */
        @JvmStatic
        fun dayFromDateValue(x: Long): Int {
            return (x and 31).toInt()
        }

        /**
         * Get the date value from a given date.
         *
         * @param year the year
         * @param month the month (1..12)
         * @param day the day (1..31)
         * @return the date value
         */
        @JvmStatic
        fun dateValue(year: Long, month: Int, day: Int): Long {
            return (year shl SHIFT_YEAR) or (month.toLong() shl SHIFT_MONTH) or day.toLong()
        }

        /**
         * Get the date value from a given denormalized date with possible out of range
         * values of month and/or day. Used after addition or subtraction month or years
         * to (from) it to get a valid date.
         *
         * @param year
         * the year
         * @param month
         * the month, if out of range month and year will be normalized
         * @param day
         * the day of the month, if out of range it will be saturated
         * @return the date value
         */
        @JvmStatic
        fun dateValueFromDenormalizedDate(year: Long, month: Long, day: Int): Long {
            var day = day
            val mm1 = month - 1
            var yd = mm1 / 12
            if (mm1 < 0 && yd * 12 != mm1) {
                yd--
            }
            val y = (year + yd).toInt()
            val m = (month - yd * 12).toInt()
            if (day < 1) {
                day = 1
            } else {
                val max = getDaysInMonth(y, m)
                if (day > max) {
                    day = max
                }
            }
            return dateValue(y.toLong(), m, day)
        }

        /**
         * Convert a local seconds to an encoded date.
         *
         * @param localSeconds the seconds since 1970-01-01
         * @return the date value
         */
        @JvmStatic
        fun dateValueFromLocalSeconds(localSeconds: Long): Long {
            var absoluteDay = localSeconds / SECONDS_PER_DAY
            // Round toward negative infinity
            if (localSeconds < 0 && (absoluteDay * SECONDS_PER_DAY != localSeconds)) {
                absoluteDay--
            }
            return dateValueFromAbsoluteDay(absoluteDay)
        }

        /**
         * Convert a time in seconds in local time to the nanoseconds since midnight.
         *
         * @param localSeconds the seconds since 1970-01-01
         * @return the nanoseconds
         */
        @JvmStatic
        fun nanosFromLocalSeconds(localSeconds: Long): Long {
            var localSeconds = localSeconds
            localSeconds %= SECONDS_PER_DAY
            if (localSeconds < 0) {
                localSeconds += SECONDS_PER_DAY
            }
            return localSeconds * NANOS_PER_SECOND
        }

        /**
         * Calculate the normalized nanos of day.
         *
         * @param nanos the nanoseconds (might be negative or larger than one day)
         * @return the nanos of day within a day
         */
        @JvmStatic
        fun normalizeNanosOfDay(nanos: Long): Long {
            var nanos = nanos
            nanos %= NANOS_PER_DAY
            if (nanos < 0) {
                nanos += NANOS_PER_DAY
            }
            return nanos
        }

        /**
         * Calculate the absolute day for a January, 1 of the specified year.
         *
         * @param year
         * the year
         * @return the absolute day
         */
        @JvmStatic
        fun absoluteDayFromYear(year: Long): Long {
            var a = 365 * year - 719_528
            if (year >= 0) {
                a += (year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400
            } else {
                a -= year / -4 - year / -100 + year / -400
            }
            return a
        }

        /**
         * Calculate the absolute day from an encoded date value.
         *
         * @param dateValue the date value
         * @return the absolute day
         */
        @JvmStatic
        fun absoluteDayFromDateValue(dateValue: Long): Long {
            return absoluteDay(
                yearFromDateValue(dateValue).toLong(), monthFromDateValue(dateValue), dayFromDateValue(dateValue)
            )
        }

        /**
         * Calculate the absolute day.
         *
         * @param y year
         * @param m month
         * @param d day
         * @return the absolute day
         */
        @JvmStatic
        fun absoluteDay(y: Long, m: Int, d: Int): Long {
            var a = absoluteDayFromYear(y) + (367 * m - 362) / 12 + d - 1
            if (m > 2) {
                a--
                if ((y and 3) != 0L || (y % 100 == 0L && y % 400 != 0L)) {
                    a--
                }
            }
            return a
        }

        /**
         * Calculate the encoded date value from an absolute day.
         *
         * @param absoluteDay the absolute day
         * @return the date value
         */
        @JvmStatic
        fun dateValueFromAbsoluteDay(absoluteDay: Long): Long {
            var d = absoluteDay + 719_468
            var a: Long = 0
            if (d < 0) {
                a = (d + 1) / 146_097 - 1
                d -= a * 146_097
                a *= 400
            }
            var y = (400 * d + 591) / 146_097
            var day = (d - (365 * y + y / 4 - y / 100 + y / 400)).toInt()
            if (day < 0) {
                y--
                day = (d - (365 * y + y / 4 - y / 100 + y / 400)).toInt()
            }
            y += a
            var m = (day * 5 + 2) / 153
            day -= (m * 306 + 5) / 10 - 1
            if (m >= 10) {
                y++
                m -= 12
            }
            return dateValue(y, m + 3, day)
        }

        /**
         * Return the next date value.
         *
         * @param dateValue
         * the date value
         * @return the next date value
         */
        @JvmStatic
        fun incrementDateValue(dateValue: Long): Long {
            val day = dayFromDateValue(dateValue)
            if (day < 28) {
                return dateValue + 1
            }
            var year = yearFromDateValue(dateValue)
            var month = monthFromDateValue(dateValue)
            if (day < getDaysInMonth(year, month)) {
                return dateValue + 1
            }
            if (month < 12) {
                month++
            } else {
                month = 1
                year++
            }
            return dateValue(year.toLong(), month, 1)
        }

        /**
         * Return the previous date value.
         *
         * @param dateValue
         * the date value
         * @return the previous date value
         */
        @JvmStatic
        fun decrementDateValue(dateValue: Long): Long {
            if (dayFromDateValue(dateValue) > 1) {
                return dateValue - 1
            }
            var year = yearFromDateValue(dateValue)
            var month = monthFromDateValue(dateValue)
            if (month > 1) {
                month--
            } else {
                month = 12
                year--
            }
            return dateValue(year.toLong(), month, getDaysInMonth(year, month))
        }

        /**
         * Append a date to the string builder.
         *
         * @param builder the target string builder
         * @param dateValue the date value
         * @return the specified string builder
         */
        @JvmStatic
        fun appendDate(builder: StringBuilder, dateValue: Long): StringBuilder {
            var y = yearFromDateValue(dateValue)
            if (y < 1_000 && y > -1_000) {
                if (y < 0) {
                    builder.append('-')
                    y = -y
                }
                StringUtils.appendZeroPadded(builder, 4, y)
            } else {
                builder.append(y)
            }
            StringUtils.appendTwoDigits(builder.append('-'), monthFromDateValue(dateValue)).append('-')
            return StringUtils.appendTwoDigits(builder, dayFromDateValue(dateValue))
        }

        /**
         * Append a time to the string builder.
         *
         * @param builder the target string builder
         * @param nanos the time in nanoseconds
         * @return the specified string builder
         */
        @JvmStatic
        fun appendTime(builder: StringBuilder, nanos: Long): StringBuilder {
            var nanos = nanos
            if (nanos < 0) {
                builder.append('-')
                nanos = -nanos
            }
            /*
             * nanos now either in range from 0 to Long.MAX_VALUE or equals to
             * Long.MIN_VALUE. We need to divide nanos by 1,000,000,000 with
             * unsigned division to get correct result. The simplest way to do this
             * with such constraints is to divide -nanos by -1,000,000,000.
             */
            var s = -nanos / -1_000_000_000
            nanos -= s * 1_000_000_000
            var m = (s / 60).toInt()
            s -= (m * 60).toLong()
            val h = m / 60
            m -= h * 60
            StringUtils.appendTwoDigits(builder, h).append(':')
            StringUtils.appendTwoDigits(builder, m).append(':')
            StringUtils.appendTwoDigits(builder, s.toInt())
            return appendNanos(builder, nanos.toInt())
        }

        /**
         * Append nanoseconds of time, if any.
         *
         * @param builder string builder to append to
         * @param nanos nanoseconds of second
         * @return the specified string builder
         */
        @JvmStatic
        fun appendNanos(builder: StringBuilder, nanos: Int): StringBuilder {
            var nanos = nanos
            if (nanos > 0) {
                builder.append('.')
                var i = 1
                while (nanos < FRACTIONAL_SECONDS_TABLE[i]) {
                    builder.append('0')
                    i++
                }
                if (nanos % 1_000 == 0) {
                    nanos /= 1_000
                    if (nanos % 1_000 == 0) {
                        nanos /= 1_000
                    }
                }
                if (nanos % 10 == 0) {
                    nanos /= 10
                    if (nanos % 10 == 0) {
                        nanos /= 10
                    }
                }
                builder.append(nanos)
            }
            return builder
        }

        /**
         * Append a time zone to the string builder.
         *
         * @param builder the target string builder
         * @param tz the time zone offset in seconds
         * @return the specified string builder
         */
        @JvmStatic
        fun appendTimeZone(builder: StringBuilder, tz: Int): StringBuilder {
            var tz = tz
            if (tz < 0) {
                builder.append('-')
                tz = -tz
            } else {
                builder.append('+')
            }
            var rem = tz / 3_600
            StringUtils.appendTwoDigits(builder, rem)
            tz -= rem * 3_600
            if (tz != 0) {
                rem = tz / 60
                StringUtils.appendTwoDigits(builder.append(':'), rem)
                tz -= rem * 60
                if (tz != 0) {
                    StringUtils.appendTwoDigits(builder.append(':'), tz)
                }
            }
            return builder
        }

        /**
         * Generates time zone name for the specified offset in seconds.
         *
         * @param offsetSeconds
         * time zone offset in seconds
         * @return time zone name
         */
        @JvmStatic
        fun timeZoneNameFromOffsetSeconds(offsetSeconds: Int): String {
            var offsetSeconds = offsetSeconds
            if (offsetSeconds == 0) {
                return "UTC"
            }
            val b = StringBuilder(12)
            b.append("GMT")
            if (offsetSeconds < 0) {
                b.append('-')
                offsetSeconds = -offsetSeconds
            } else {
                b.append('+')
            }
            StringUtils.appendTwoDigits(b, offsetSeconds / 3_600).append(':')
            offsetSeconds %= 3_600
            StringUtils.appendTwoDigits(b, offsetSeconds / 60)
            offsetSeconds %= 60
            if (offsetSeconds != 0) {
                b.append(':')
                StringUtils.appendTwoDigits(b, offsetSeconds)
            }
            return b.toString()
        }


        /**
         * Converts scale of nanoseconds.
         *
         * @param nanosOfDay nanoseconds of day
         * @param scale fractional seconds precision
         * @param range the allowed range of values (0..range-1)
         * @return scaled value
         */
        @JvmStatic
        fun convertScale(nanosOfDay: Long, scale: Int, range: Long): Long {
            var nanosOfDay = nanosOfDay
            if (scale >= 9) {
                return nanosOfDay
            }
            val m = FRACTIONAL_SECONDS_TABLE[scale]
            val mod = nanosOfDay % m
            if (mod >= m ushr 1) {
                nanosOfDay += m.toLong()
            }
            var r = nanosOfDay - mod
            if (r >= range) {
                r = range - m
            }
            return r
        }

        /**
         * Moves timestamp with time zone to a new time zone.
         *
         * @param dateValue the date value
         * @param timeNanos the nanoseconds since midnight
         * @param oldOffset old offset
         * @param newOffset new offset
         * @return timestamp with time zone with new offset
         */
        @JvmStatic
        fun timestampTimeZoneAtOffset(
            dateValue: Long, timeNanos: Long, oldOffset: Int,
            newOffset: Int
        ): ValueTimestampTimeZone {
            var dateValue = dateValue
            var timeNanos = timeNanos
            timeNanos += (newOffset - oldOffset) * NANOS_PER_SECOND
            // Value can be 18+18 hours before or after the limit
            if (timeNanos < 0) {
                timeNanos += NANOS_PER_DAY
                dateValue = decrementDateValue(dateValue)
                if (timeNanos < 0) {
                    timeNanos += NANOS_PER_DAY
                    dateValue = decrementDateValue(dateValue)
                }
            } else if (timeNanos >= NANOS_PER_DAY) {
                timeNanos -= NANOS_PER_DAY
                dateValue = incrementDateValue(dateValue)
                if (timeNanos >= NANOS_PER_DAY) {
                    timeNanos -= NANOS_PER_DAY
                    dateValue = incrementDateValue(dateValue)
                }
            }
            return ValueTimestampTimeZone.fromDateValueAndNanos(dateValue, timeNanos, newOffset)
        }
    }
}
