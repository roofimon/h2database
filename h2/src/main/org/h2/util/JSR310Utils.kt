/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import org.h2.util.DateTimeUtils.Companion.NANOS_PER_SECOND
import org.h2.util.DateTimeUtils.Companion.SECONDS_PER_DAY
import org.h2.util.DateTimeUtils.Companion.SHIFT_MONTH
import org.h2.util.DateTimeUtils.Companion.SHIFT_YEAR
import org.h2.util.DateTimeUtils.Companion.absoluteDayFromDateValue
import org.h2.util.DateTimeUtils.Companion.dateValue
import org.h2.util.DateTimeUtils.Companion.dateValueFromAbsoluteDay
import org.h2.util.DateTimeUtils.Companion.dayFromDateValue
import org.h2.util.DateTimeUtils.Companion.monthFromDateValue
import org.h2.util.DateTimeUtils.Companion.yearFromDateValue
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.h2.api.ErrorCode
import org.h2.api.IntervalQualifier
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.value.DataType
import org.h2.value.TypeInfo
import org.h2.value.Value
import org.h2.value.ValueDate
import org.h2.value.ValueInterval
import org.h2.value.ValueTime
import org.h2.value.ValueTimeTimeZone
import org.h2.value.ValueTimestamp
import org.h2.value.ValueTimestampTimeZone

/**
 * This utility class provides access to JSR 310 classes.
 */
class JSR310Utils private constructor() {

    companion object {

        private const val MIN_DATE_VALUE = (-999_999_999L shl SHIFT_YEAR) +
                (1L shl SHIFT_MONTH) + 1L

        private const val MAX_DATE_VALUE = (999_999_999L shl SHIFT_YEAR) +
                (12L shl SHIFT_MONTH) + 31L

        private const val MIN_INSTANT_SECOND = -31_557_014_167_219_200L

        private const val MAX_INSTANT_SECOND = 31_556_889_864_403_199L

        /**
         * Converts a value to a LocalDate.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @param provider
         *            the cast information provider
         * @return the LocalDate
         */
        @JvmStatic
        fun valueToLocalDate(value: Value, provider: CastDataProvider?): LocalDate {
            val dateValue = value.convertToDate(provider).dateValue
            if (dateValue > MAX_DATE_VALUE) {
                return LocalDate.MAX
            } else if (dateValue < MIN_DATE_VALUE) {
                return LocalDate.MIN
            }
            return LocalDate.of(
                yearFromDateValue(dateValue), monthFromDateValue(dateValue),
                dayFromDateValue(dateValue)
            )
        }

        /**
         * Converts a value to a LocalTime.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @param provider
         *            the cast information provider
         * @return the LocalTime
         */
        @JvmStatic
        fun valueToLocalTime(value: Value, provider: CastDataProvider?): LocalTime {
            return LocalTime.ofNanoOfDay((value.convertTo(TypeInfo.TYPE_TIME, provider) as ValueTime).nanos)
        }

        /**
         * Converts a value to a LocalDateTime.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @param provider
         *            the cast information provider
         * @return the LocalDateTime
         */
        @JvmStatic
        fun valueToLocalDateTime(value: Value, provider: CastDataProvider?): LocalDateTime {
            val valueTimestamp = value.convertTo(TypeInfo.TYPE_TIMESTAMP, provider) as ValueTimestamp
            return localDateTimeFromDateNanos(valueTimestamp.dateValue, valueTimestamp.timeNanos)
        }

        /**
         * Converts a value to an Instant.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @param provider
         *            the cast information provider
         * @return the Instant
         */
        @JvmStatic
        fun valueToInstant(value: Value, provider: CastDataProvider?): Instant {
            val valueTimestampTimeZone = value
                .convertTo(TypeInfo.TYPE_TIMESTAMP_TZ, provider) as ValueTimestampTimeZone
            val timeNanos = valueTimestampTimeZone.timeNanos
            val epochSecond = absoluteDayFromDateValue(valueTimestampTimeZone.dateValue) *
                    SECONDS_PER_DAY +
                    timeNanos / NANOS_PER_SECOND -
                    valueTimestampTimeZone.timeZoneOffsetSeconds
            if (epochSecond > MAX_INSTANT_SECOND) {
                return Instant.MAX
            } else if (epochSecond < MIN_INSTANT_SECOND) {
                return Instant.MIN
            }
            return Instant.ofEpochSecond(epochSecond, timeNanos % NANOS_PER_SECOND)
        }

        /**
         * Converts a value to a OffsetDateTime.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @param provider
         *            the cast information provider
         * @return the OffsetDateTime
         */
        @JvmStatic
        fun valueToOffsetDateTime(value: Value, provider: CastDataProvider?): OffsetDateTime {
            val v = value.convertTo(TypeInfo.TYPE_TIMESTAMP_TZ, provider) as ValueTimestampTimeZone
            return OffsetDateTime.of(
                localDateTimeFromDateNanos(v.dateValue, v.timeNanos),
                ZoneOffset.ofTotalSeconds(v.timeZoneOffsetSeconds)
            )
        }

        /**
         * Converts a value to a ZonedDateTime.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @param provider
         *            the cast information provider
         * @return the ZonedDateTime
         */
        @JvmStatic
        fun valueToZonedDateTime(value: Value, provider: CastDataProvider?): ZonedDateTime {
            val v = value.convertTo(TypeInfo.TYPE_TIMESTAMP_TZ, provider) as ValueTimestampTimeZone
            return ZonedDateTime.of(
                localDateTimeFromDateNanos(v.dateValue, v.timeNanos),
                ZoneOffset.ofTotalSeconds(v.timeZoneOffsetSeconds)
            )
        }

        /**
         * Converts a value to a OffsetTime.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @param provider
         *            the cast information provider
         * @return the OffsetTime
         */
        @JvmStatic
        fun valueToOffsetTime(value: Value, provider: CastDataProvider?): OffsetTime {
            val valueTimeTimeZone = value.convertTo(TypeInfo.TYPE_TIME_TZ, provider) as ValueTimeTimeZone
            return OffsetTime.of(
                LocalTime.ofNanoOfDay(valueTimeTimeZone.nanos),
                ZoneOffset.ofTotalSeconds(valueTimeTimeZone.timeZoneOffsetSeconds)
            )
        }

        /**
         * Converts a value to a Period.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @return the Period
         */
        @JvmStatic
        fun valueToPeriod(value: Value): Period {
            var value = value
            if (value !is ValueInterval) {
                value = value.convertTo(TypeInfo.TYPE_INTERVAL_YEAR_TO_MONTH)
            }
            if (!DataType.isYearMonthIntervalType(value.valueType)) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, null as Throwable?, value.string)
            }
            val v = value as ValueInterval
            val qualifier = v.getQualifier()
            val negative = v.isNegative()
            val leading = v.leading
            val remaining = v.remaining
            val y = Value.convertToInt(IntervalUtils.yearsFromInterval(qualifier, negative, leading, remaining), null)
            val m = Value.convertToInt(IntervalUtils.monthsFromInterval(qualifier, negative, leading, remaining), null)
            return Period.of(y, m, 0)
        }

        /**
         * Converts a value to a Duration.
         *
         * This method should only be called from Java 8 or later version.
         *
         * @param value
         *            the value to convert
         * @return the Duration
         */
        @JvmStatic
        fun valueToDuration(value: Value): Duration {
            var value = value
            if (value !is ValueInterval) {
                value = value.convertTo(TypeInfo.TYPE_INTERVAL_DAY_TO_SECOND)
            }
            if (DataType.isYearMonthIntervalType(value.valueType)) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, null as Throwable?, value.string)
            }
            val dr = IntervalUtils.intervalToAbsolute(value as ValueInterval)
                .divideAndRemainder(BigInteger.valueOf(1_000_000_000))
            return Duration.ofSeconds(dr[0].toLong(), dr[1].toLong())
        }

        /**
         * Converts a LocalDate to a Value.
         *
         * @param localDate
         *            the LocalDate to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun localDateToValue(localDate: LocalDate): ValueDate {
            return ValueDate.fromDateValue(
                dateValue(localDate.year.toLong(), localDate.monthValue, localDate.dayOfMonth)
            )
        }

        /**
         * Converts a LocalTime to a Value.
         *
         * @param localTime
         *            the LocalTime to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun localTimeToValue(localTime: LocalTime): ValueTime {
            return ValueTime.fromNanos(localTime.toNanoOfDay())
        }

        /**
         * Converts a LocalDateTime to a Value.
         *
         * @param localDateTime
         *            the LocalDateTime to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun localDateTimeToValue(localDateTime: LocalDateTime): ValueTimestamp {
            val localDate = localDateTime.toLocalDate()
            return ValueTimestamp.fromDateValueAndNanos(
                dateValue(localDate.year.toLong(), localDate.monthValue, localDate.dayOfMonth),
                localDateTime.toLocalTime().toNanoOfDay()
            )
        }

        /**
         * Converts an Instant to a Value.
         *
         * @param instant
         *            the Instant to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun instantToValue(instant: Instant): ValueTimestampTimeZone {
            val epochSecond = instant.epochSecond
            val nano = instant.nano
            var absoluteDay = epochSecond / 86_400
            // Round toward negative infinity
            if (epochSecond < 0 && (absoluteDay * 86_400 != epochSecond)) {
                absoluteDay--
            }
            val timeNanos = (epochSecond - absoluteDay * 86_400) * 1_000_000_000 + nano
            return ValueTimestampTimeZone.fromDateValueAndNanos(
                dateValueFromAbsoluteDay(absoluteDay),
                timeNanos, 0
            )
        }

        /**
         * Converts a OffsetDateTime to a Value.
         *
         * @param offsetDateTime
         *            the OffsetDateTime to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun offsetDateTimeToValue(offsetDateTime: OffsetDateTime): ValueTimestampTimeZone {
            val localDateTime = offsetDateTime.toLocalDateTime()
            val localDate = localDateTime.toLocalDate()
            return ValueTimestampTimeZone.fromDateValueAndNanos(
                dateValue(localDate.year.toLong(), localDate.monthValue, localDate.dayOfMonth),
                localDateTime.toLocalTime().toNanoOfDay(),
                offsetDateTime.offset.totalSeconds
            )
        }

        /**
         * Converts a ZonedDateTime to a Value.
         *
         * @param zonedDateTime
         *            the ZonedDateTime to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun zonedDateTimeToValue(zonedDateTime: ZonedDateTime): ValueTimestampTimeZone {
            val localDateTime = zonedDateTime.toLocalDateTime()
            val localDate = localDateTime.toLocalDate()
            return ValueTimestampTimeZone.fromDateValueAndNanos(
                dateValue(localDate.year.toLong(), localDate.monthValue, localDate.dayOfMonth),
                localDateTime.toLocalTime().toNanoOfDay(),
                zonedDateTime.offset.totalSeconds
            )
        }

        /**
         * Converts a OffsetTime to a Value.
         *
         * @param offsetTime
         *            the OffsetTime to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun offsetTimeToValue(offsetTime: OffsetTime): ValueTimeTimeZone {
            return ValueTimeTimeZone.fromNanos(
                offsetTime.toLocalTime().toNanoOfDay(),
                offsetTime.offset.totalSeconds
            )
        }

        private fun localDateTimeFromDateNanos(dateValue: Long, timeNanos: Long): LocalDateTime {
            if (dateValue > MAX_DATE_VALUE) {
                return LocalDateTime.MAX
            } else if (dateValue < MIN_DATE_VALUE) {
                return LocalDateTime.MIN
            }
            return LocalDateTime.of(
                LocalDate.of(
                    yearFromDateValue(dateValue),
                    monthFromDateValue(dateValue), dayFromDateValue(dateValue)
                ),
                LocalTime.ofNanoOfDay(timeNanos)
            )
        }

        /**
         * Converts a Period to a Value.
         *
         * @param period
         *            the Period to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun periodToValue(period: Period): ValueInterval {
            val days = period.days
            if (days != 0) {
                throw DbException.getInvalidValueException("Period.days", days)
            }
            val years = period.years
            val months = period.months
            val qualifier: IntervalQualifier
            var negative = false
            var leading = 0L
            var remaining = 0L
            if (years == 0) {
                if (months == 0) {
                    // Use generic qualifier
                    qualifier = IntervalQualifier.YEAR_TO_MONTH
                } else {
                    qualifier = IntervalQualifier.MONTH
                    leading = months.toLong()
                    if (leading < 0) {
                        leading = -leading
                        negative = true
                    }
                }
            } else {
                if (months == 0) {
                    qualifier = IntervalQualifier.YEAR
                    leading = years.toLong()
                    if (leading < 0) {
                        leading = -leading
                        negative = true
                    }
                } else {
                    qualifier = IntervalQualifier.YEAR_TO_MONTH
                    leading = (years * 12 + months).toLong()
                    if (leading < 0) {
                        leading = -leading
                        negative = true
                    }
                    remaining = leading % 12
                    leading /= 12
                }
            }
            return ValueInterval.from(qualifier, negative, leading, remaining)
        }

        /**
         * Converts a Duration to a Value.
         *
         * @param duration
         *            the Duration to convert, not {@code null}
         * @return the value
         */
        @JvmStatic
        fun durationToValue(duration: Duration): ValueInterval {
            var seconds = duration.seconds
            var nano = duration.nano
            val negative = seconds < 0
            seconds = Math.abs(seconds)
            if (negative && nano != 0) {
                nano = 1_000_000_000 - nano
                seconds--
            }
            return ValueInterval.from(IntervalQualifier.SECOND, negative, seconds, nano.toLong())
        }
    }
}
