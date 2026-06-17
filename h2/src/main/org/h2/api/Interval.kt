/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api

import org.h2.message.DbException
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_MINUTE
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_SECOND
import org.h2.util.IntervalUtils

/**
 * INTERVAL representation for result sets.
 */
class Interval
/**
 * Creates a new interval. Do not use this constructor, use static methods
 * instead.
 *
 * @param qualifier
 *            qualifier
 * @param negative
 *            whether interval is negative
 * @param leading
 *            value of leading field
 * @param remaining
 *            combined value of all remaining fields
 */
(
    private val qualifier: IntervalQualifier,
    negative: Boolean,
    /**
     * Non-negative long with value of leading field. For INTERVAL SECOND
     * contains only integer part of seconds.
     */
    private val leading: Long,
    /**
     * Non-negative long with combined value of all remaining field, or 0 for
     * single-field intervals, with exception for INTERVAL SECOND that uses this
     * field to store fractional part of seconds measured in nanoseconds.
     */
    private val remaining: Long
) {

    /**
     * `false` for zero or positive intervals, `true` for negative
     * intervals.
     */
    private val negative: Boolean = try {
        IntervalUtils.validateInterval(qualifier, negative, leading, remaining)
    } catch (e: DbException) {
        throw IllegalArgumentException()
    }

    /**
     * Returns qualifier of this interval.
     *
     * @return qualifier
     */
    fun getQualifier(): IntervalQualifier {
        return qualifier
    }

    /**
     * Returns where the interval is negative.
     *
     * @return where the interval is negative
     */
    fun isNegative(): Boolean {
        return negative
    }

    /**
     * Returns value of leading field of this interval. For `SECOND`
     * intervals returns integer part of seconds.
     *
     * @return value of leading field
     */
    fun getLeading(): Long {
        return leading
    }

    /**
     * Returns combined value of remaining fields of this interval. For
     * `SECOND` intervals returns nanoseconds.
     *
     * @return combined value of remaining fields
     */
    fun getRemaining(): Long {
        return remaining
    }

    /**
     * Returns years value, if any.
     *
     * @return years, or 0
     */
    fun getYears(): Long {
        return IntervalUtils.yearsFromInterval(qualifier, negative, leading, remaining)
    }

    /**
     * Returns months value, if any.
     *
     * @return months, or 0
     */
    fun getMonths(): Long {
        return IntervalUtils.monthsFromInterval(qualifier, negative, leading, remaining)
    }

    /**
     * Returns days value, if any.
     *
     * @return days, or 0
     */
    fun getDays(): Long {
        return IntervalUtils.daysFromInterval(qualifier, negative, leading, remaining)
    }

    /**
     * Returns hours value, if any.
     *
     * @return hours, or 0
     */
    fun getHours(): Long {
        return IntervalUtils.hoursFromInterval(qualifier, negative, leading, remaining)
    }

    /**
     * Returns minutes value, if any.
     *
     * @return minutes, or 0
     */
    fun getMinutes(): Long {
        return IntervalUtils.minutesFromInterval(qualifier, negative, leading, remaining)
    }

    /**
     * Returns value of integer part of seconds, if any.
     *
     * @return seconds, or 0
     */
    fun getSeconds(): Long {
        if (qualifier == IntervalQualifier.SECOND) {
            return if (negative) -leading else leading
        }
        return getSecondsAndNanos() / NANOS_PER_SECOND
    }

    /**
     * Returns value of fractional part of seconds (in nanoseconds), if any.
     *
     * @return nanoseconds, or 0
     */
    fun getNanosOfSecond(): Long {
        if (qualifier == IntervalQualifier.SECOND) {
            return if (negative) -remaining else remaining
        }
        return getSecondsAndNanos() % NANOS_PER_SECOND
    }

    /**
     * Returns seconds value measured in nanoseconds, if any.
     *
     *
     * This method returns a long value that cannot fit all possible values of
     * INTERVAL SECOND. For a very large intervals of this type use
     * [.getSeconds] and [.getNanosOfSecond] instead. This
     * method can be safely used for intervals of other day-time types.
     *
     *
     * @return nanoseconds (including seconds), or 0
     */
    fun getSecondsAndNanos(): Long {
        return IntervalUtils.nanosFromInterval(qualifier, negative, leading, remaining)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + qualifier.hashCode()
        result = prime * result + (if (negative) 1231 else 1237)
        result = prime * result + (leading xor (leading ushr 32)).toInt()
        result = prime * result + (remaining xor (remaining ushr 32)).toInt()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj !is Interval) {
            return false
        }
        val other = obj
        return qualifier == other.qualifier && negative == other.negative && leading == other.leading &&
            remaining == other.remaining
    }

    override fun toString(): String {
        return IntervalUtils.appendInterval(StringBuilder(), getQualifier(), negative, leading, remaining)
            .toString()
    }

    companion object {

        /**
         * Creates a new INTERVAL YEAR.
         *
         * @param years
         *            years, |years|&lt;10<sup>18</sup>
         * @return INTERVAL YEAR
         */
        @JvmStatic
        fun ofYears(years: Long): Interval {
            return Interval(IntervalQualifier.YEAR, years < 0, Math.abs(years), 0)
        }

        /**
         * Creates a new INTERVAL MONTH.
         *
         * @param months
         *            months, |months|&lt;10<sup>18</sup>
         * @return INTERVAL MONTH
         */
        @JvmStatic
        fun ofMonths(months: Long): Interval {
            return Interval(IntervalQualifier.MONTH, months < 0, Math.abs(months), 0)
        }

        /**
         * Creates a new INTERVAL DAY.
         *
         * @param days
         *            days, |days|&lt;10<sup>18</sup>
         * @return INTERVAL DAY
         */
        @JvmStatic
        fun ofDays(days: Long): Interval {
            return Interval(IntervalQualifier.DAY, days < 0, Math.abs(days), 0)
        }

        /**
         * Creates a new INTERVAL HOUR.
         *
         * @param hours
         *            hours, |hours|&lt;10<sup>18</sup>
         * @return INTERVAL HOUR
         */
        @JvmStatic
        fun ofHours(hours: Long): Interval {
            return Interval(IntervalQualifier.HOUR, hours < 0, Math.abs(hours), 0)
        }

        /**
         * Creates a new INTERVAL MINUTE.
         *
         * @param minutes
         *            minutes, |minutes|&lt;10<sup>18</sup>
         * @return interval
         */
        @JvmStatic
        fun ofMinutes(minutes: Long): Interval {
            return Interval(IntervalQualifier.MINUTE, minutes < 0, Math.abs(minutes), 0)
        }

        /**
         * Creates a new INTERVAL SECOND.
         *
         * @param seconds
         *            seconds, |seconds|&lt;10<sup>18</sup>
         * @return INTERVAL SECOND
         */
        @JvmStatic
        fun ofSeconds(seconds: Long): Interval {
            return Interval(IntervalQualifier.SECOND, seconds < 0, Math.abs(seconds), 0)
        }

        /**
         * Creates a new INTERVAL SECOND.
         *
         *
         * If both arguments are not equal to zero they should have the same sign.
         *
         *
         * @param seconds
         *            seconds, |seconds|&lt;10<sup>18</sup>
         * @param nanos
         *            nanoseconds, |nanos|&lt;1,000,000,000
         * @return INTERVAL SECOND
         */
        @JvmStatic
        fun ofSeconds(seconds: Long, nanos: Int): Interval {
            var seconds = seconds
            var nanos = nanos
            // Interval is negative if any field is negative
            val negative = (seconds or nanos.toLong()) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (seconds > 0 || nanos > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                seconds = -seconds
                nanos = -nanos
                // Long.MIN_VALUE and Integer.MIN_VALUE will be rejected by
                // constructor
            }
            return Interval(IntervalQualifier.SECOND, negative, seconds, nanos.toLong())
        }

        /**
         * Creates a new INTERVAL SECOND.
         *
         * @param nanos
         *            nanoseconds (including seconds)
         * @return INTERVAL SECOND
         */
        @JvmStatic
        fun ofNanos(nanos: Long): Interval {
            var nanos = nanos
            val negative = nanos < 0
            if (negative) {
                nanos = -nanos
                if (nanos < 0) {
                    // Long.MIN_VALUE = -9_223_372_036_854_775_808L
                    return Interval(IntervalQualifier.SECOND, true, 9_223_372_036L, 854_775_808)
                }
            }
            return Interval(IntervalQualifier.SECOND, negative, nanos / NANOS_PER_SECOND, nanos % NANOS_PER_SECOND)
        }

        /**
         * Creates a new INTERVAL YEAR TO MONTH.
         *
         *
         * If both arguments are not equal to zero they should have the same sign.
         *
         *
         * @param years
         *            years, |years|&lt;10<sup>18</sup>
         * @param months
         *            months, |months|&lt;12
         * @return INTERVAL YEAR TO MONTH
         */
        @JvmStatic
        fun ofYearsMonths(years: Long, months: Int): Interval {
            var years = years
            var months = months
            // Interval is negative if any field is negative
            val negative = (years or months.toLong()) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (years > 0 || months > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                years = -years
                months = -months
                // Long.MIN_VALUE and Integer.MIN_VALUE will be rejected by
                // constructor
            }
            return Interval(IntervalQualifier.YEAR_TO_MONTH, negative, years, months.toLong())
        }

        /**
         * Creates a new INTERVAL DAY TO HOUR.
         *
         *
         * If both arguments are not equal to zero they should have the same sign.
         *
         *
         * @param days
         *            days, |days|&lt;10<sup>18</sup>
         * @param hours
         *            hours, |hours|&lt;24
         * @return INTERVAL DAY TO HOUR
         */
        @JvmStatic
        fun ofDaysHours(days: Long, hours: Int): Interval {
            var days = days
            var hours = hours
            // Interval is negative if any field is negative
            val negative = (days or hours.toLong()) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (days > 0 || hours > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                days = -days
                hours = -hours
                // Long.MIN_VALUE and Integer.MIN_VALUE will be rejected by
                // constructor
            }
            return Interval(IntervalQualifier.DAY_TO_HOUR, negative, days, hours.toLong())
        }

        /**
         * Creates a new INTERVAL DAY TO MINUTE.
         *
         *
         * Non-zero arguments should have the same sign.
         *
         *
         * @param days
         *            days, |days|&lt;10<sup>18</sup>
         * @param hours
         *            hours, |hours|&lt;24
         * @param minutes
         *            minutes, |minutes|&lt;60
         * @return INTERVAL DAY TO MINUTE
         */
        @JvmStatic
        fun ofDaysHoursMinutes(days: Long, hours: Int, minutes: Int): Interval {
            var days = days
            var hours = hours
            var minutes = minutes
            // Interval is negative if any field is negative
            val negative = (days or hours.toLong() or minutes.toLong()) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (days > 0 || hours > 0 || minutes > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                days = -days
                hours = -hours
                minutes = -minutes
                if ((hours or minutes) < 0) {
                    // Integer.MIN_VALUE
                    throw IllegalArgumentException()
                }
                // days = Long.MIN_VALUE will be rejected by constructor
            }
            // Check only minutes.
            // Overflow in days or hours will be detected by constructor
            if (minutes >= 60) {
                throw IllegalArgumentException()
            }
            return Interval(IntervalQualifier.DAY_TO_MINUTE, negative, days, hours * 60L + minutes)
        }

        /**
         * Creates a new INTERVAL DAY TO SECOND.
         *
         *
         * Non-zero arguments should have the same sign.
         *
         *
         * @param days
         *            days, |days|&lt;10<sup>18</sup>
         * @param hours
         *            hours, |hours|&lt;24
         * @param minutes
         *            minutes, |minutes|&lt;60
         * @param seconds
         *            seconds, |seconds|&lt;60
         * @return INTERVAL DAY TO SECOND
         */
        @JvmStatic
        fun ofDaysHoursMinutesSeconds(days: Long, hours: Int, minutes: Int, seconds: Int): Interval {
            return ofDaysHoursMinutesNanos(days, hours, minutes, seconds * NANOS_PER_SECOND)
        }

        /**
         * Creates a new INTERVAL DAY TO SECOND.
         *
         *
         * Non-zero arguments should have the same sign.
         *
         *
         * @param days
         *            days, |days|&lt;10<sup>18</sup>
         * @param hours
         *            hours, |hours|&lt;24
         * @param minutes
         *            minutes, |minutes|&lt;60
         * @param nanos
         *            nanoseconds, |nanos|&lt;60,000,000,000
         * @return INTERVAL DAY TO SECOND
         */
        @JvmStatic
        fun ofDaysHoursMinutesNanos(days: Long, hours: Int, minutes: Int, nanos: Long): Interval {
            var days = days
            var hours = hours
            var minutes = minutes
            var nanos = nanos
            // Interval is negative if any field is negative
            val negative = (days or hours.toLong() or minutes.toLong() or nanos) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (days > 0 || hours > 0 || minutes > 0 || nanos > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                days = -days
                hours = -hours
                minutes = -minutes
                nanos = -nanos
                if ((hours.toLong() or minutes.toLong() or nanos) < 0) {
                    // Integer.MIN_VALUE, Long.MIN_VALUE
                    throw IllegalArgumentException()
                }
                // days = Long.MIN_VALUE will be rejected by constructor
            }
            // Check only minutes and nanoseconds.
            // Overflow in days or hours will be detected by constructor
            if (minutes >= 60 || nanos >= NANOS_PER_MINUTE) {
                throw IllegalArgumentException()
            }
            return Interval(
                IntervalQualifier.DAY_TO_SECOND, negative, days,
                (hours * 60L + minutes) * NANOS_PER_MINUTE + nanos
            )
        }

        /**
         * Creates a new INTERVAL HOUR TO MINUTE.
         *
         *
         * If both arguments are not equal to zero they should have the same sign.
         *
         *
         * @param hours
         *            hours, |hours|&lt;10<sup>18</sup>
         * @param minutes
         *            minutes, |minutes|&lt;60
         * @return INTERVAL HOUR TO MINUTE
         */
        @JvmStatic
        fun ofHoursMinutes(hours: Long, minutes: Int): Interval {
            var hours = hours
            var minutes = minutes
            // Interval is negative if any field is negative
            val negative = (hours or minutes.toLong()) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (hours > 0 || minutes > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                hours = -hours
                minutes = -minutes
                // Long.MIN_VALUE and Integer.MIN_VALUE will be rejected by
                // constructor
            }
            return Interval(IntervalQualifier.HOUR_TO_MINUTE, negative, hours, minutes.toLong())
        }

        /**
         * Creates a new INTERVAL HOUR TO SECOND.
         *
         *
         * Non-zero arguments should have the same sign.
         *
         *
         * @param hours
         *            hours, |hours|&lt;10<sup>18</sup>
         * @param minutes
         *            minutes, |minutes|&lt;60
         * @param seconds
         *            seconds, |seconds|&lt;60
         * @return INTERVAL HOUR TO SECOND
         */
        @JvmStatic
        fun ofHoursMinutesSeconds(hours: Long, minutes: Int, seconds: Int): Interval {
            return ofHoursMinutesNanos(hours, minutes, seconds * NANOS_PER_SECOND)
        }

        /**
         * Creates a new INTERVAL HOUR TO SECOND.
         *
         *
         * Non-zero arguments should have the same sign.
         *
         *
         * @param hours
         *            hours, |hours|&lt;10<sup>18</sup>
         * @param minutes
         *            minutes, |minutes|&lt;60
         * @param nanos
         *            nanoseconds, |nanos|&lt;60,000,000,000
         * @return INTERVAL HOUR TO SECOND
         */
        @JvmStatic
        fun ofHoursMinutesNanos(hours: Long, minutes: Int, nanos: Long): Interval {
            var hours = hours
            var minutes = minutes
            var nanos = nanos
            // Interval is negative if any field is negative
            val negative = (hours or minutes.toLong() or nanos) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (hours > 0 || minutes > 0 || nanos > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                hours = -hours
                minutes = -minutes
                nanos = -nanos
                if ((minutes.toLong() or nanos) < 0) {
                    // Integer.MIN_VALUE, Long.MIN_VALUE
                    throw IllegalArgumentException()
                }
                // hours = Long.MIN_VALUE will be rejected by constructor
            }
            // Check only nanoseconds.
            // Overflow in hours or minutes will be detected by constructor
            if (nanos >= NANOS_PER_MINUTE) {
                throw IllegalArgumentException()
            }
            return Interval(IntervalQualifier.HOUR_TO_SECOND, negative, hours, minutes * NANOS_PER_MINUTE + nanos)
        }

        /**
         * Creates a new INTERVAL MINUTE TO SECOND.
         *
         *
         * If both arguments are not equal to zero they should have the same sign.
         *
         *
         * @param minutes
         *            minutes, |minutes|&lt;10<sup>18</sup>
         * @param seconds
         *            seconds, |seconds|&lt;60
         * @return INTERVAL MINUTE TO SECOND
         */
        @JvmStatic
        fun ofMinutesSeconds(minutes: Long, seconds: Int): Interval {
            return ofMinutesNanos(minutes, seconds * NANOS_PER_SECOND)
        }

        /**
         * Creates a new INTERVAL MINUTE TO SECOND.
         *
         *
         * If both arguments are not equal to zero they should have the same sign.
         *
         *
         * @param minutes
         *            minutes, |minutes|&lt;10<sup>18</sup>
         * @param nanos
         *            nanoseconds, |nanos|&lt;60,000,000,000
         * @return INTERVAL MINUTE TO SECOND
         */
        @JvmStatic
        fun ofMinutesNanos(minutes: Long, nanos: Long): Interval {
            var minutes = minutes
            var nanos = nanos
            // Interval is negative if any field is negative
            val negative = (minutes or nanos) < 0
            if (negative) {
                // Ensure that all fields are negative or zero
                if (minutes > 0 || nanos > 0) {
                    throw IllegalArgumentException()
                }
                // Make them positive
                minutes = -minutes
                nanos = -nanos
                // Long.MIN_VALUE will be rejected by constructor
            }
            return Interval(IntervalQualifier.MINUTE_TO_SECOND, negative, minutes, nanos)
        }
    }
}
