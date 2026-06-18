/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.h2.engine.CastDataProvider
import org.h2.util.DateTimeUtils.Companion.MILLIS_PER_DAY
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_SECOND
import org.h2.value.TypeInfo
import org.h2.value.Value
import org.h2.value.ValueDate
import org.h2.value.ValueNull
import org.h2.value.ValueTime
import org.h2.value.ValueTimestamp
import org.h2.value.ValueTimestampTimeZone

/**
 * Date and time utilities for [Date], [Time], and [Timestamp]
 * classes.
 */
object LegacyDateTimeUtils {

    /**
     * Gregorian change date for a [java.util.GregorianCalendar] that
     * represents a proleptic Gregorian calendar.
     */
    @JvmField
    val PROLEPTIC_GREGORIAN_CHANGE: Date = Date(Long.MIN_VALUE)

    /**
     * UTC time zone.
     */
    @JvmField
    val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * Get or create a date value for the given date.
     *
     * @param provider
     *            the cast information provider
     * @param timeZone
     *            time zone, or `null` for default
     * @param date
     *            the date
     * @return the value
     */
    @JvmStatic
    fun fromDate(provider: CastDataProvider?, timeZone: TimeZone?, date: Date): ValueDate {
        val ms = date.time
        return ValueDate.fromDateValue(
            dateValueFromLocalMillis(
                ms + (if (timeZone == null) getTimeZoneOffsetMillis(provider, ms) else timeZone.getOffset(ms))
            )
        )
    }

    /**
     * Get or create a time value for the given time.
     *
     * @param provider
     *            the cast information provider
     * @param timeZone
     *            time zone, or `null` for default
     * @param time
     *            the time
     * @return the value
     */
    @JvmStatic
    fun fromTime(provider: CastDataProvider?, timeZone: TimeZone?, time: Time): ValueTime {
        val ms = time.time
        return ValueTime.fromNanos(
            nanosFromLocalMillis(
                ms + (if (timeZone == null) getTimeZoneOffsetMillis(provider, ms) else timeZone.getOffset(ms))
            )
        )
    }

    /**
     * Get or create a timestamp value for the given timestamp.
     *
     * @param provider
     *            the cast information provider
     * @param timeZone
     *            time zone, or `null` for default
     * @param timestamp
     *            the timestamp
     * @return the value
     */
    @JvmStatic
    fun fromTimestamp(provider: CastDataProvider?, timeZone: TimeZone?, timestamp: Timestamp): ValueTimestamp {
        val ms = timestamp.time
        return timestampFromLocalMillis(
            ms + (if (timeZone == null) getTimeZoneOffsetMillis(provider, ms) else timeZone.getOffset(ms)),
            timestamp.nanos % 1_000_000
        )
    }

    /**
     * Get or create a timestamp value for the given date/time in millis.
     *
     * @param provider
     *            the cast information provider
     * @param ms
     *            the milliseconds
     * @param nanos
     *            the nanoseconds
     * @return the value
     */
    @JvmStatic
    fun fromTimestamp(provider: CastDataProvider?, ms: Long, nanos: Int): ValueTimestamp {
        return timestampFromLocalMillis(ms + getTimeZoneOffsetMillis(provider, ms), nanos)
    }

    private fun timestampFromLocalMillis(ms: Long, nanos: Int): ValueTimestamp {
        val dateValue = dateValueFromLocalMillis(ms)
        val timeNanos = nanos + nanosFromLocalMillis(ms)
        return ValueTimestamp.fromDateValueAndNanos(dateValue, timeNanos)
    }

    /**
     * Convert a local datetime in millis to an encoded date.
     *
     * @param ms
     *            the milliseconds
     * @return the date value
     */
    @JvmStatic
    fun dateValueFromLocalMillis(ms: Long): Long {
        var absoluteDay = ms / MILLIS_PER_DAY
        // Round toward negative infinity
        if (ms < 0 && (absoluteDay * MILLIS_PER_DAY != ms)) {
            absoluteDay--
        }
        return DateTimeUtils.dateValueFromAbsoluteDay(absoluteDay)
    }

    /**
     * Convert a time in milliseconds in local time to the nanoseconds since
     * midnight.
     *
     * @param ms
     *            the milliseconds
     * @return the nanoseconds
     */
    @JvmStatic
    fun nanosFromLocalMillis(ms: Long): Long {
        var ms = ms
        ms %= MILLIS_PER_DAY
        if (ms < 0) {
            ms += MILLIS_PER_DAY
        }
        return ms * 1_000_000
    }

    /**
     * Get the date value converted to the specified time zone.
     *
     * @param provider the cast information provider
     * @param timeZone the target time zone
     * @param value the value to convert
     * @return the date
     */
    @JvmStatic
    fun toDate(provider: CastDataProvider?, timeZone: TimeZone?, value: Value): Date? {
        return if (value !== ValueNull.INSTANCE) {
            Date(getMillis(provider, timeZone, value.convertToDate(provider).dateValue, 0))
        } else {
            null
        }
    }

    /**
     * Get the time value converted to the specified time zone.
     *
     * @param provider the cast information provider
     * @param timeZone the target time zone
     * @param value the value to convert
     * @return the time
     */
    @JvmStatic
    fun toTime(provider: CastDataProvider?, timeZone: TimeZone?, value: Value): Time? {
        var value = value
        when (value.getValueType()) {
            Value.NULL ->
                return null
            Value.TIME -> {
            }
            else ->
                value = value.convertTo(TypeInfo.TYPE_TIME, provider)
        }
        return Time(
            getMillis(provider, timeZone, DateTimeUtils.EPOCH_DATE_VALUE.toLong(), (value as ValueTime).nanos)
        )
    }

    /**
     * Get the timestamp value converted to the specified time zone.
     *
     * @param provider the cast information provider
     * @param timeZone the target time zone
     * @param value the value to convert
     * @return the timestamp
     */
    @JvmStatic
    fun toTimestamp(provider: CastDataProvider?, timeZone: TimeZone?, value: Value): Timestamp? {
        var value = value
        when (value.getValueType()) {
            Value.NULL ->
                return null
            Value.TIMESTAMP, Value.TIMESTAMP_TZ -> {
            }
            else ->
                value = value.convertTo(TypeInfo.TYPE_TIMESTAMP, provider)
        }
        when (value.getValueType()) {
            Value.TIMESTAMP_TZ -> {
                val v = value as ValueTimestampTimeZone
                val timeNanos = v.timeNanos
                val ts = Timestamp(
                    DateTimeUtils.absoluteDayFromDateValue(v.dateValue) * MILLIS_PER_DAY +
                        timeNanos / 1_000_000 - v.timeZoneOffsetSeconds * 1_000
                )
                ts.nanos = (timeNanos % NANOS_PER_SECOND).toInt()
                return ts
            }
            else -> {
                val v = value as ValueTimestamp
                val timeNanos = v.timeNanos
                val ts = Timestamp(getMillis(provider, timeZone, v.dateValue, timeNanos))
                ts.nanos = (timeNanos % NANOS_PER_SECOND).toInt()
                return ts
            }
        }
    }

    /**
     * Calculate the milliseconds since 1970-01-01 (UTC) for the given date and
     * time (in the specified timezone).
     *
     * @param provider the cast information provider
     * @param tz the timezone of the parameters, or null for the default
     *            timezone
     * @param dateValue date value
     * @param timeNanos nanoseconds since midnight
     * @return the number of milliseconds (UTC)
     */
    @JvmStatic
    fun getMillis(provider: CastDataProvider?, tz: TimeZone?, dateValue: Long, timeNanos: Long): Long {
        return (if (tz == null) {
            if (provider != null) provider.currentTimeZone() else DateTimeUtils.getTimeZone()
        } else {
            TimeZoneProvider.ofId(tz.id)
        }).getEpochSecondsFromLocal(dateValue, timeNanos) * 1_000 +
            timeNanos / 1_000_000 % 1_000
    }

    /**
     * Returns local time zone offset for a specified timestamp.
     *
     * @param provider the cast information provider
     * @param ms milliseconds since Epoch in UTC
     * @return local time zone offset
     */
    @JvmStatic
    fun getTimeZoneOffsetMillis(provider: CastDataProvider?, ms: Long): Int {
        var seconds = ms / 1_000
        // Round toward negative infinity
        if (ms < 0 && (seconds * 1_000 != ms)) {
            seconds--
        }
        return (if (provider != null) provider.currentTimeZone() else DateTimeUtils.getTimeZone())
            .getTimeZoneOffsetUTC(seconds) * 1_000
    }

    /**
     * Convert a legacy Java object to a value.
     *
     * @param session
     *            the session
     * @param x
     *            the value
     * @return the value, or `null` if not supported
     */
    @JvmStatic
    fun legacyObjectToValue(session: CastDataProvider?, x: Any): Value? {
        return when (x) {
            is Date ->
                fromDate(session, null, x)
            is Time ->
                fromTime(session, null, x)
            is Timestamp ->
                fromTimestamp(session, null, x)
            is java.util.Date ->
                fromTimestamp(session, x.time, 0)
            is Calendar -> {
                val ms = x.timeInMillis
                timestampFromLocalMillis(ms + x.timeZone.getOffset(ms), 0)
            }
            else ->
                null
        }
    }

    /**
     * Converts the specified value to an object of the specified legacy type.
     *
     * @param <T> the type
     * @param type the class
     * @param value the value
     * @param provider the cast information provider
     * @return an instance of the specified class, or `null` if not supported
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> valueToLegacyType(type: Class<T>, value: Value, provider: CastDataProvider?): T? {
        return if (type == Date::class.java) {
            toDate(provider, null, value) as T?
        } else if (type == Time::class.java) {
            toTime(provider, null, value) as T?
        } else if (type == Timestamp::class.java) {
            toTimestamp(provider, null, value) as T?
        } else if (type == java.util.Date::class.java) {
            java.util.Date(toTimestamp(provider, null, value)!!.time) as T?
        } else if (type == Calendar::class.java) {
            val calendar = GregorianCalendar()
            calendar.gregorianChange = PROLEPTIC_GREGORIAN_CHANGE
            calendar.time = toTimestamp(provider, calendar.timeZone, value)
            calendar as T?
        } else {
            null
        }
    }

    /**
     * Get the type information for the given legacy Java class.
     *
     * @param clazz
     *            the Java class
     * @return the value type, or `null` if not supported
     */
    @JvmStatic
    fun legacyClassToType(clazz: Class<*>): TypeInfo? {
        return if (Date::class.java.isAssignableFrom(clazz)) {
            TypeInfo.TYPE_DATE
        } else if (Time::class.java.isAssignableFrom(clazz)) {
            TypeInfo.TYPE_TIME
        } else if (java.util.Date::class.java.isAssignableFrom(clazz) ||
            Calendar::class.java.isAssignableFrom(clazz)
        ) {
            TypeInfo.TYPE_TIMESTAMP
        } else {
            null
        }
    }
}
