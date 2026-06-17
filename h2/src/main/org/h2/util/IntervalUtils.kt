/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.math.BigInteger

import org.h2.api.ErrorCode
import org.h2.api.IntervalQualifier
import org.h2.message.DbException
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_DAY
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_HOUR
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_MINUTE
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_SECOND
import org.h2.value.ValueInterval

/**
 * This utility class contains interval conversion functions.
 */
class IntervalUtils private constructor() {

    companion object {

        private val NANOS_PER_SECOND_BI: BigInteger = BigInteger.valueOf(NANOS_PER_SECOND)

        private val NANOS_PER_MINUTE_BI: BigInteger = BigInteger.valueOf(NANOS_PER_MINUTE)

        private val NANOS_PER_HOUR_BI: BigInteger = BigInteger.valueOf(NANOS_PER_HOUR)

        /**
         * The number of nanoseconds per day as BigInteger.
         */
        @JvmField
        val NANOS_PER_DAY_BI: BigInteger = BigInteger.valueOf(NANOS_PER_DAY)

        private val MONTHS_PER_YEAR_BI: BigInteger = BigInteger.valueOf(12)

        private val HOURS_PER_DAY_BI: BigInteger = BigInteger.valueOf(24)

        private val MINUTES_PER_DAY_BI: BigInteger = BigInteger.valueOf((24 * 60).toLong())

        private val MINUTES_PER_HOUR_BI: BigInteger = BigInteger.valueOf(60)

        private val LEADING_MIN: BigInteger = BigInteger.valueOf(-999_999_999_999_999_999L)

        private val LEADING_MAX: BigInteger = BigInteger.valueOf(999_999_999_999_999_999L)

        /**
         * Parses the specified string as `INTERVAL` value.
         *
         * @param qualifier
         * the default qualifier to use if string does not have one
         * @param s
         * the string with type information to parse
         * @return the interval value. Type of value can be different from the
         * specified qualifier.
         */
        @JvmStatic
        fun parseFormattedInterval(qualifier: IntervalQualifier, s: String): ValueInterval {
            var i = 0
            i = skipWS(s, i)
            if (!s.regionMatches(i, "INTERVAL", 0, 8, ignoreCase = true)) {
                return parseInterval(qualifier, false, s)
            }
            i = skipWS(s, i + 8)
            var negative = false
            var ch = s[i]
            if (ch == '-') {
                negative = true
                i = skipWS(s, i + 1)
                ch = s[i]
            } else if (ch == '+') {
                i = skipWS(s, i + 1)
                ch = s[i]
            }
            if (ch != '\'') {
                throw IllegalArgumentException(s)
            }
            val start = ++i
            val l = s.length
            while (true) {
                if (i == l) {
                    throw IllegalArgumentException(s)
                }
                if (s[i] == '\'') {
                    break
                }
                i++
            }
            val v = s.substring(start, i)
            i = skipWS(s, i + 1)
            if (s.regionMatches(i, "YEAR", 0, 4, ignoreCase = true)) {
                i += 4
                val j = skipWSEnd(s, i)
                if (j == l) {
                    return parseInterval(IntervalQualifier.YEAR, negative, v)
                }
                if (j > i && s.regionMatches(j, "TO", 0, 2, ignoreCase = true)) {
                    val j2 = j + 2
                    i = skipWS(s, j2)
                    if (i > j2 && s.regionMatches(i, "MONTH", 0, 5, ignoreCase = true)) {
                        if (skipWSEnd(s, i + 5) == l) {
                            return parseInterval(IntervalQualifier.YEAR_TO_MONTH, negative, v)
                        }
                    }
                }
            } else if (s.regionMatches(i, "MONTH", 0, 5, ignoreCase = true)) {
                if (skipWSEnd(s, i + 5) == l) {
                    return parseInterval(IntervalQualifier.MONTH, negative, v)
                }
            }
            if (s.regionMatches(i, "DAY", 0, 3, ignoreCase = true)) {
                i += 3
                val j = skipWSEnd(s, i)
                if (j == l) {
                    return parseInterval(IntervalQualifier.DAY, negative, v)
                }
                if (j > i && s.regionMatches(j, "TO", 0, 2, ignoreCase = true)) {
                    val j2 = j + 2
                    i = skipWS(s, j2)
                    if (i > j2) {
                        if (s.regionMatches(i, "HOUR", 0, 4, ignoreCase = true)) {
                            if (skipWSEnd(s, i + 4) == l) {
                                return parseInterval(IntervalQualifier.DAY_TO_HOUR, negative, v)
                            }
                        } else if (s.regionMatches(i, "MINUTE", 0, 6, ignoreCase = true)) {
                            if (skipWSEnd(s, i + 6) == l) {
                                return parseInterval(IntervalQualifier.DAY_TO_MINUTE, negative, v)
                            }
                        } else if (s.regionMatches(i, "SECOND", 0, 6, ignoreCase = true)) {
                            if (skipWSEnd(s, i + 6) == l) {
                                return parseInterval(IntervalQualifier.DAY_TO_SECOND, negative, v)
                            }
                        }
                    }
                }
            }
            if (s.regionMatches(i, "HOUR", 0, 4, ignoreCase = true)) {
                i += 4
                val j = skipWSEnd(s, i)
                if (j == l) {
                    return parseInterval(IntervalQualifier.HOUR, negative, v)
                }
                if (j > i && s.regionMatches(j, "TO", 0, 2, ignoreCase = true)) {
                    val j2 = j + 2
                    i = skipWS(s, j2)
                    if (i > j2) {
                        if (s.regionMatches(i, "MINUTE", 0, 6, ignoreCase = true)) {
                            if (skipWSEnd(s, i + 6) == l) {
                                return parseInterval(IntervalQualifier.HOUR_TO_MINUTE, negative, v)
                            }
                        } else if (s.regionMatches(i, "SECOND", 0, 6, ignoreCase = true)) {
                            if (skipWSEnd(s, i + 6) == l) {
                                return parseInterval(IntervalQualifier.HOUR_TO_SECOND, negative, v)
                            }
                        }
                    }
                }
            }
            if (s.regionMatches(i, "MINUTE", 0, 6, ignoreCase = true)) {
                i += 6
                val j = skipWSEnd(s, i)
                if (j == l) {
                    return parseInterval(IntervalQualifier.MINUTE, negative, v)
                }
                if (j > i && s.regionMatches(j, "TO", 0, 2, ignoreCase = true)) {
                    val j2 = j + 2
                    i = skipWS(s, j2)
                    if (i > j2 && s.regionMatches(i, "SECOND", 0, 6, ignoreCase = true)) {
                        if (skipWSEnd(s, i + 6) == l) {
                            return parseInterval(IntervalQualifier.MINUTE_TO_SECOND, negative, v)
                        }
                    }
                }
            }
            if (s.regionMatches(i, "SECOND", 0, 6, ignoreCase = true)) {
                if (skipWSEnd(s, i + 6) == l) {
                    return parseInterval(IntervalQualifier.SECOND, negative, v)
                }
            }
            throw IllegalArgumentException(s)
        }

        private fun skipWS(s: String, i: Int): Int {
            var i = i
            val l = s.length
            while (true) {
                if (i == l) {
                    throw IllegalArgumentException(s)
                }
                if (!Character.isWhitespace(s[i])) {
                    return i
                }
                i++
            }
        }

        private fun skipWSEnd(s: String, i: Int): Int {
            var i = i
            val l = s.length
            while (true) {
                if (i == l) {
                    return i
                }
                if (!Character.isWhitespace(s[i])) {
                    return i
                }
                i++
            }
        }

        /**
         * Parses the specified string as `INTERVAL` value.
         *
         * @param qualifier
         * the qualifier of interval
         * @param negative
         * whether the interval is negative
         * @param s
         * the string to parse
         * @return the interval value
         */
        @JvmStatic
        fun parseInterval(qualifier: IntervalQualifier, negative: Boolean, s: String): ValueInterval {
            var negative = negative
            val leading: Long
            val remaining: Long
            when (qualifier) {
                IntervalQualifier.YEAR,
                IntervalQualifier.MONTH,
                IntervalQualifier.DAY,
                IntervalQualifier.HOUR,
                IntervalQualifier.MINUTE -> {
                    leading = parseIntervalLeading(s, 0, s.length, negative)
                    remaining = 0
                }
                IntervalQualifier.SECOND -> {
                    val dot = s.indexOf('.')
                    if (dot < 0) {
                        leading = parseIntervalLeading(s, 0, s.length, negative)
                        remaining = 0
                    } else {
                        leading = parseIntervalLeading(s, 0, dot, negative)
                        remaining = DateTimeUtils.parseNanos(s, dot + 1, s.length).toLong()
                    }
                }
                IntervalQualifier.YEAR_TO_MONTH ->
                    return parseInterval2(qualifier, s, '-', 11, negative)
                IntervalQualifier.DAY_TO_HOUR ->
                    return parseInterval2(qualifier, s, ' ', 23, negative)
                IntervalQualifier.DAY_TO_MINUTE -> {
                    val space = s.indexOf(' ')
                    if (space < 0) {
                        leading = parseIntervalLeading(s, 0, s.length, negative)
                        remaining = 0
                    } else {
                        leading = parseIntervalLeading(s, 0, space, negative)
                        val colon = s.indexOf(':', space + 1)
                        remaining = if (colon < 0) {
                            parseIntervalRemaining(s, space + 1, s.length, 23) * 60
                        } else {
                            parseIntervalRemaining(s, space + 1, colon, 23) * 60 +
                                    parseIntervalRemaining(s, colon + 1, s.length, 59)
                        }
                    }
                }
                IntervalQualifier.DAY_TO_SECOND -> {
                    val space = s.indexOf(' ')
                    if (space < 0) {
                        leading = parseIntervalLeading(s, 0, s.length, negative)
                        remaining = 0
                    } else {
                        leading = parseIntervalLeading(s, 0, space, negative)
                        val colon = s.indexOf(':', space + 1)
                        if (colon < 0) {
                            remaining = parseIntervalRemaining(s, space + 1, s.length, 23) * NANOS_PER_HOUR
                        } else {
                            val colon2 = s.indexOf(':', colon + 1)
                            remaining = if (colon2 < 0) {
                                parseIntervalRemaining(s, space + 1, colon, 23) * NANOS_PER_HOUR +
                                        parseIntervalRemaining(s, colon + 1, s.length, 59) * NANOS_PER_MINUTE
                            } else {
                                parseIntervalRemaining(s, space + 1, colon, 23) * NANOS_PER_HOUR +
                                        parseIntervalRemaining(s, colon + 1, colon2, 59) * NANOS_PER_MINUTE +
                                        parseIntervalRemainingSeconds(s, colon2 + 1)
                            }
                        }
                    }
                }
                IntervalQualifier.HOUR_TO_MINUTE ->
                    return parseInterval2(qualifier, s, ':', 59, negative)
                IntervalQualifier.HOUR_TO_SECOND -> {
                    val colon = s.indexOf(':')
                    if (colon < 0) {
                        leading = parseIntervalLeading(s, 0, s.length, negative)
                        remaining = 0
                    } else {
                        leading = parseIntervalLeading(s, 0, colon, negative)
                        val colon2 = s.indexOf(':', colon + 1)
                        remaining = if (colon2 < 0) {
                            parseIntervalRemaining(s, colon + 1, s.length, 59) * NANOS_PER_MINUTE
                        } else {
                            parseIntervalRemaining(s, colon + 1, colon2, 59) * NANOS_PER_MINUTE +
                                    parseIntervalRemainingSeconds(s, colon2 + 1)
                        }
                    }
                }
                IntervalQualifier.MINUTE_TO_SECOND -> {
                    val dash = s.indexOf(':')
                    if (dash < 0) {
                        leading = parseIntervalLeading(s, 0, s.length, negative)
                        remaining = 0
                    } else {
                        leading = parseIntervalLeading(s, 0, dash, negative)
                        remaining = parseIntervalRemainingSeconds(s, dash + 1)
                    }
                }
            }
            negative = leading < 0
            var leadingResult = leading
            if (negative) {
                leadingResult = if (leading != Long.MIN_VALUE) {
                    -leading
                } else {
                    0
                }
            }
            return ValueInterval.from(qualifier, negative, leadingResult, remaining)
        }

        private fun parseInterval2(
            qualifier: IntervalQualifier, s: String,
            ch: Char, max: Int, negative: Boolean
        ): ValueInterval {
            var negative = negative
            var leading: Long
            val remaining: Long
            val dash = s.indexOf(ch, 1)
            if (dash < 0) {
                leading = parseIntervalLeading(s, 0, s.length, negative)
                remaining = 0
            } else {
                leading = parseIntervalLeading(s, 0, dash, negative)
                remaining = parseIntervalRemaining(s, dash + 1, s.length, max)
            }
            negative = leading < 0
            if (negative) {
                leading = if (leading != Long.MIN_VALUE) {
                    -leading
                } else {
                    0
                }
            }
            return ValueInterval.from(qualifier, negative, leading, remaining)
        }

        private fun parseIntervalLeading(s: String, start: Int, end: Int, negative: Boolean): Long {
            val leading = s.substring(start, end).toLong()
            if (leading == 0L) {
                return if (negative xor (s[start] == '-')) Long.MIN_VALUE else 0
            }
            return if (negative) -leading else leading
        }

        private fun parseIntervalRemaining(s: String, start: Int, end: Int, max: Int): Long {
            val v = StringUtils.parseUInt31(s, start, end)
            if (v > max) {
                throw IllegalArgumentException(s)
            }
            return v.toLong()
        }

        private fun parseIntervalRemainingSeconds(s: String, start: Int): Long {
            val seconds: Int
            val nanos: Int
            val dot = s.indexOf('.', start + 1)
            if (dot < 0) {
                seconds = StringUtils.parseUInt31(s, start, s.length)
                nanos = 0
            } else {
                seconds = StringUtils.parseUInt31(s, start, dot)
                nanos = DateTimeUtils.parseNanos(s, dot + 1, s.length)
            }
            if (seconds > 59) {
                throw IllegalArgumentException(s)
            }
            return seconds * NANOS_PER_SECOND + nanos
        }

        /**
         * Formats interval as a string and appends it to a specified string
         * builder.
         *
         * @param buff
         * string builder to append to
         * @param qualifier
         * qualifier of the interval
         * @param negative
         * whether interval is negative
         * @param leading
         * the value of leading field
         * @param remaining
         * the value of all remaining fields
         * @return the specified string builder
         */
        @JvmStatic
        fun appendInterval(
            buff: StringBuilder, qualifier: IntervalQualifier, negative: Boolean,
            leading: Long, remaining: Long
        ): StringBuilder {
            buff.append("INTERVAL '")
            if (negative) {
                buff.append('-')
            }
            when (qualifier) {
                IntervalQualifier.YEAR,
                IntervalQualifier.MONTH,
                IntervalQualifier.DAY,
                IntervalQualifier.HOUR,
                IntervalQualifier.MINUTE ->
                    buff.append(leading)
                IntervalQualifier.SECOND ->
                    DateTimeUtils.appendNanos(buff.append(leading), remaining.toInt())
                IntervalQualifier.YEAR_TO_MONTH ->
                    buff.append(leading).append('-').append(remaining)
                IntervalQualifier.DAY_TO_HOUR -> {
                    buff.append(leading).append(' ')
                    StringUtils.appendTwoDigits(buff, remaining.toInt())
                }
                IntervalQualifier.DAY_TO_MINUTE -> {
                    buff.append(leading).append(' ')
                    val r = remaining.toInt()
                    StringUtils.appendTwoDigits(buff, r / 60).append(':')
                    StringUtils.appendTwoDigits(buff, r % 60)
                }
                IntervalQualifier.DAY_TO_SECOND -> {
                    val nanos = remaining % NANOS_PER_MINUTE
                    val r = (remaining / NANOS_PER_MINUTE).toInt()
                    buff.append(leading).append(' ')
                    StringUtils.appendTwoDigits(buff, r / 60).append(':')
                    StringUtils.appendTwoDigits(buff, r % 60).append(':')
                    StringUtils.appendTwoDigits(buff, (nanos / NANOS_PER_SECOND).toInt())
                    DateTimeUtils.appendNanos(buff, (nanos % NANOS_PER_SECOND).toInt())
                }
                IntervalQualifier.HOUR_TO_MINUTE -> {
                    buff.append(leading).append(':')
                    StringUtils.appendTwoDigits(buff, remaining.toInt())
                }
                IntervalQualifier.HOUR_TO_SECOND -> {
                    buff.append(leading).append(':')
                    StringUtils.appendTwoDigits(buff, (remaining / NANOS_PER_MINUTE).toInt()).append(':')
                    val sec = remaining % NANOS_PER_MINUTE
                    StringUtils.appendTwoDigits(buff, (sec / NANOS_PER_SECOND).toInt())
                    DateTimeUtils.appendNanos(buff, (sec % NANOS_PER_SECOND).toInt())
                }
                IntervalQualifier.MINUTE_TO_SECOND -> {
                    buff.append(leading).append(':')
                    StringUtils.appendTwoDigits(buff, (remaining / NANOS_PER_SECOND).toInt())
                    DateTimeUtils.appendNanos(buff, (remaining % NANOS_PER_SECOND).toInt())
                }
            }
            return buff.append("' ").append(qualifier)
        }

        /**
         * Converts interval value to an absolute value.
         *
         * @param interval
         * the interval value
         * @return absolute value in months for year-month intervals, in nanoseconds
         * for day-time intervals
         */
        @JvmStatic
        fun intervalToAbsolute(interval: ValueInterval): BigInteger {
            val r: BigInteger
            when (interval.getQualifier()) {
                IntervalQualifier.YEAR ->
                    r = BigInteger.valueOf(interval.getLeading()).multiply(MONTHS_PER_YEAR_BI)
                IntervalQualifier.MONTH ->
                    r = BigInteger.valueOf(interval.getLeading())
                IntervalQualifier.DAY ->
                    r = BigInteger.valueOf(interval.getLeading()).multiply(NANOS_PER_DAY_BI)
                IntervalQualifier.HOUR ->
                    r = BigInteger.valueOf(interval.getLeading()).multiply(NANOS_PER_HOUR_BI)
                IntervalQualifier.MINUTE ->
                    r = BigInteger.valueOf(interval.getLeading()).multiply(NANOS_PER_MINUTE_BI)
                IntervalQualifier.SECOND ->
                    r = intervalToAbsolute(interval, NANOS_PER_SECOND_BI)
                IntervalQualifier.YEAR_TO_MONTH ->
                    r = intervalToAbsolute(interval, MONTHS_PER_YEAR_BI)
                IntervalQualifier.DAY_TO_HOUR ->
                    r = intervalToAbsolute(interval, HOURS_PER_DAY_BI, NANOS_PER_HOUR_BI)
                IntervalQualifier.DAY_TO_MINUTE ->
                    r = intervalToAbsolute(interval, MINUTES_PER_DAY_BI, NANOS_PER_MINUTE_BI)
                IntervalQualifier.DAY_TO_SECOND ->
                    r = intervalToAbsolute(interval, NANOS_PER_DAY_BI)
                IntervalQualifier.HOUR_TO_MINUTE ->
                    r = intervalToAbsolute(interval, MINUTES_PER_HOUR_BI, NANOS_PER_MINUTE_BI)
                IntervalQualifier.HOUR_TO_SECOND ->
                    r = intervalToAbsolute(interval, NANOS_PER_HOUR_BI)
                IntervalQualifier.MINUTE_TO_SECOND ->
                    r = intervalToAbsolute(interval, NANOS_PER_MINUTE_BI)
            }
            return if (interval.isNegative()) r.negate() else r
        }

        private fun intervalToAbsolute(
            interval: ValueInterval, multiplier: BigInteger,
            totalMultiplier: BigInteger
        ): BigInteger {
            return intervalToAbsolute(interval, multiplier).multiply(totalMultiplier)
        }

        private fun intervalToAbsolute(interval: ValueInterval, multiplier: BigInteger): BigInteger {
            return BigInteger.valueOf(interval.getLeading()).multiply(multiplier)
                .add(BigInteger.valueOf(interval.getRemaining()))
        }

        /**
         * Converts absolute value to an interval value.
         *
         * @param qualifier
         * the qualifier of interval
         * @param absolute
         * absolute value in months for year-month intervals, in
         * nanoseconds for day-time intervals
         * @return the interval value
         */
        @JvmStatic
        fun intervalFromAbsolute(qualifier: IntervalQualifier, absolute: BigInteger): ValueInterval {
            return when (qualifier) {
                IntervalQualifier.YEAR ->
                    ValueInterval.from(
                        qualifier, absolute.signum() < 0,
                        leadingExact(absolute.divide(MONTHS_PER_YEAR_BI)), 0
                    )
                IntervalQualifier.MONTH ->
                    ValueInterval.from(qualifier, absolute.signum() < 0, leadingExact(absolute), 0)
                IntervalQualifier.DAY ->
                    ValueInterval.from(
                        qualifier, absolute.signum() < 0,
                        leadingExact(absolute.divide(NANOS_PER_DAY_BI)), 0
                    )
                IntervalQualifier.HOUR ->
                    ValueInterval.from(
                        qualifier, absolute.signum() < 0,
                        leadingExact(absolute.divide(NANOS_PER_HOUR_BI)), 0
                    )
                IntervalQualifier.MINUTE ->
                    ValueInterval.from(
                        qualifier, absolute.signum() < 0,
                        leadingExact(absolute.divide(NANOS_PER_MINUTE_BI)), 0
                    )
                IntervalQualifier.SECOND ->
                    intervalFromAbsolute(qualifier, absolute, NANOS_PER_SECOND_BI)
                IntervalQualifier.YEAR_TO_MONTH ->
                    intervalFromAbsolute(qualifier, absolute, MONTHS_PER_YEAR_BI)
                IntervalQualifier.DAY_TO_HOUR ->
                    intervalFromAbsolute(qualifier, absolute.divide(NANOS_PER_HOUR_BI), HOURS_PER_DAY_BI)
                IntervalQualifier.DAY_TO_MINUTE ->
                    intervalFromAbsolute(qualifier, absolute.divide(NANOS_PER_MINUTE_BI), MINUTES_PER_DAY_BI)
                IntervalQualifier.DAY_TO_SECOND ->
                    intervalFromAbsolute(qualifier, absolute, NANOS_PER_DAY_BI)
                IntervalQualifier.HOUR_TO_MINUTE ->
                    intervalFromAbsolute(qualifier, absolute.divide(NANOS_PER_MINUTE_BI), MINUTES_PER_HOUR_BI)
                IntervalQualifier.HOUR_TO_SECOND ->
                    intervalFromAbsolute(qualifier, absolute, NANOS_PER_HOUR_BI)
                IntervalQualifier.MINUTE_TO_SECOND ->
                    intervalFromAbsolute(qualifier, absolute, NANOS_PER_MINUTE_BI)
            }
        }

        private fun intervalFromAbsolute(
            qualifier: IntervalQualifier, absolute: BigInteger,
            divisor: BigInteger
        ): ValueInterval {
            val dr = absolute.divideAndRemainder(divisor)
            return ValueInterval.from(qualifier, absolute.signum() < 0, leadingExact(dr[0]), Math.abs(dr[1].toLong()))
        }

        private fun leadingExact(absolute: BigInteger): Long {
            if (absolute.compareTo(LEADING_MAX) > 0 || absolute.compareTo(LEADING_MIN) < 0) {
                throw DbException.get(ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_1, absolute.toString())
            }
            return Math.abs(absolute.toLong())
        }

        /**
         * Ensures that all fields in interval are valid.
         *
         * @param qualifier
         * qualifier
         * @param negative
         * whether interval is negative
         * @param leading
         * value of leading field
         * @param remaining
         * values of all remaining fields
         * @return fixed value of negative field
         */
        @JvmStatic
        fun validateInterval(
            qualifier: IntervalQualifier?, negative: Boolean, leading: Long,
            remaining: Long
        ): Boolean {
            if (qualifier == null) {
                throw NullPointerException()
            }
            if (leading == 0L && remaining == 0L) {
                return false
            }
            // Upper bound for remaining value (exclusive)
            val bound: Long = when (qualifier) {
                IntervalQualifier.YEAR,
                IntervalQualifier.MONTH,
                IntervalQualifier.DAY,
                IntervalQualifier.HOUR,
                IntervalQualifier.MINUTE ->
                    1
                IntervalQualifier.SECOND ->
                    NANOS_PER_SECOND
                IntervalQualifier.YEAR_TO_MONTH ->
                    12
                IntervalQualifier.DAY_TO_HOUR ->
                    24
                IntervalQualifier.DAY_TO_MINUTE ->
                    (24 * 60).toLong()
                IntervalQualifier.DAY_TO_SECOND ->
                    NANOS_PER_DAY
                IntervalQualifier.HOUR_TO_MINUTE ->
                    60
                IntervalQualifier.HOUR_TO_SECOND ->
                    NANOS_PER_HOUR
                IntervalQualifier.MINUTE_TO_SECOND ->
                    NANOS_PER_MINUTE
            }
            if (leading < 0L || leading >= 1_000_000_000_000_000_000L) {
                throw DbException.getInvalidValueException("interval", leading.toString())
            }
            if (remaining < 0L || remaining >= bound) {
                throw DbException.getInvalidValueException("interval", remaining.toString())
            }
            return negative
        }

        /**
         * Returns years value of interval, if any.
         *
         * @param qualifier
         * qualifier
         * @param negative
         * whether interval is negative
         * @param leading
         * value of leading field
         * @param remaining
         * values of all remaining fields
         * @return years, or 0
         */
        @JvmStatic
        fun yearsFromInterval(
            qualifier: IntervalQualifier, negative: Boolean, leading: Long, remaining: Long
        ): Long {
            return if (qualifier == IntervalQualifier.YEAR || qualifier == IntervalQualifier.YEAR_TO_MONTH) {
                var v = leading
                if (negative) {
                    v = -v
                }
                v
            } else {
                0
            }
        }

        /**
         * Returns months value of interval, if any.
         *
         * @param qualifier
         * qualifier
         * @param negative
         * whether interval is negative
         * @param leading
         * value of leading field
         * @param remaining
         * values of all remaining fields
         * @return months, or 0
         */
        @JvmStatic
        fun monthsFromInterval(
            qualifier: IntervalQualifier, negative: Boolean, leading: Long,
            remaining: Long
        ): Long {
            var v: Long
            if (qualifier == IntervalQualifier.MONTH) {
                v = leading
            } else if (qualifier == IntervalQualifier.YEAR_TO_MONTH) {
                v = remaining
            } else {
                return 0
            }
            if (negative) {
                v = -v
            }
            return v
        }

        /**
         * Returns days value of interval, if any.
         *
         * @param qualifier
         * qualifier
         * @param negative
         * whether interval is negative
         * @param leading
         * value of leading field
         * @param remaining
         * values of all remaining fields
         * @return days, or 0
         */
        @JvmStatic
        fun daysFromInterval(
            qualifier: IntervalQualifier, negative: Boolean, leading: Long, remaining: Long
        ): Long {
            return when (qualifier) {
                IntervalQualifier.DAY,
                IntervalQualifier.DAY_TO_HOUR,
                IntervalQualifier.DAY_TO_MINUTE,
                IntervalQualifier.DAY_TO_SECOND -> {
                    var v = leading
                    if (negative) {
                        v = -v
                    }
                    v
                }
                else -> 0
            }
        }

        /**
         * Returns hours value of interval, if any.
         *
         * @param qualifier
         * qualifier
         * @param negative
         * whether interval is negative
         * @param leading
         * value of leading field
         * @param remaining
         * values of all remaining fields
         * @return hours, or 0
         */
        @JvmStatic
        fun hoursFromInterval(
            qualifier: IntervalQualifier, negative: Boolean, leading: Long, remaining: Long
        ): Long {
            var v: Long
            when (qualifier) {
                IntervalQualifier.HOUR,
                IntervalQualifier.HOUR_TO_MINUTE,
                IntervalQualifier.HOUR_TO_SECOND ->
                    v = leading
                IntervalQualifier.DAY_TO_HOUR ->
                    v = remaining
                IntervalQualifier.DAY_TO_MINUTE ->
                    v = remaining / 60
                IntervalQualifier.DAY_TO_SECOND ->
                    v = remaining / NANOS_PER_HOUR
                else -> return 0
            }
            if (negative) {
                v = -v
            }
            return v
        }

        /**
         * Returns minutes value of interval, if any.
         *
         * @param qualifier
         * qualifier
         * @param negative
         * whether interval is negative
         * @param leading
         * value of leading field
         * @param remaining
         * values of all remaining fields
         * @return minutes, or 0
         */
        @JvmStatic
        fun minutesFromInterval(
            qualifier: IntervalQualifier, negative: Boolean, leading: Long,
            remaining: Long
        ): Long {
            var v: Long
            when (qualifier) {
                IntervalQualifier.MINUTE,
                IntervalQualifier.MINUTE_TO_SECOND ->
                    v = leading
                IntervalQualifier.DAY_TO_MINUTE ->
                    v = remaining % 60
                IntervalQualifier.DAY_TO_SECOND ->
                    v = remaining / NANOS_PER_MINUTE % 60
                IntervalQualifier.HOUR_TO_MINUTE ->
                    v = remaining
                IntervalQualifier.HOUR_TO_SECOND ->
                    v = remaining / NANOS_PER_MINUTE
                else -> return 0
            }
            if (negative) {
                v = -v
            }
            return v
        }

        /**
         * Returns nanoseconds value of interval, if any.
         *
         * @param qualifier
         * qualifier
         * @param negative
         * whether interval is negative
         * @param leading
         * value of leading field
         * @param remaining
         * values of all remaining fields
         * @return nanoseconds, or 0
         */
        @JvmStatic
        fun nanosFromInterval(
            qualifier: IntervalQualifier, negative: Boolean, leading: Long, remaining: Long
        ): Long {
            var v: Long
            when (qualifier) {
                IntervalQualifier.SECOND ->
                    v = leading * NANOS_PER_SECOND + remaining
                IntervalQualifier.DAY_TO_SECOND,
                IntervalQualifier.HOUR_TO_SECOND ->
                    v = remaining % NANOS_PER_MINUTE
                IntervalQualifier.MINUTE_TO_SECOND ->
                    v = remaining
                else -> return 0
            }
            if (negative) {
                v = -v
            }
            return v
        }
    }
}
