/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.ArrayList
import java.util.Arrays
import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.message.DbException
import org.h2.util.DateTimeTemplate.FieldType.AMPM
import org.h2.util.DateTimeTemplate.FieldType.DAY_OF_MONTH
import org.h2.util.DateTimeTemplate.FieldType.DAY_OF_YEAR
import org.h2.util.DateTimeTemplate.FieldType.DELIMITER
import org.h2.util.DateTimeTemplate.FieldType.FRACTION
import org.h2.util.DateTimeTemplate.FieldType.HOUR12
import org.h2.util.DateTimeTemplate.FieldType.HOUR24
import org.h2.util.DateTimeTemplate.FieldType.MINUTE
import org.h2.util.DateTimeTemplate.FieldType.MONTH
import org.h2.util.DateTimeTemplate.FieldType.ROUNDED_YEAR
import org.h2.util.DateTimeTemplate.FieldType.SECOND_OF_DAY
import org.h2.util.DateTimeTemplate.FieldType.SECOND_OF_MINUTE
import org.h2.util.DateTimeTemplate.FieldType.TIME_ZONE_HOUR
import org.h2.util.DateTimeTemplate.FieldType.TIME_ZONE_MINUTE
import org.h2.util.DateTimeTemplate.FieldType.TIME_ZONE_SECOND
import org.h2.util.DateTimeTemplate.FieldType.YEAR
import org.h2.util.DateTimeUtils.Companion.FRACTIONAL_SECONDS_TABLE
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_HOUR
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_MINUTE
import org.h2.util.DateTimeUtils.Companion.NANOS_PER_SECOND
import org.h2.util.DateTimeUtils.Companion.SECONDS_PER_DAY
import org.h2.value.TypeInfo
import org.h2.value.Value
import org.h2.value.ValueDate
import org.h2.value.ValueTime
import org.h2.value.ValueTimeTimeZone
import org.h2.value.ValueTimestamp
import org.h2.value.ValueTimestampTimeZone

/**
 * Date-time template.
 */
class DateTimeTemplate private constructor(
    private val parts: Array<Part>,
    private val containsDate: Boolean,
    private val containsTime: Boolean,
    private val containsTimeZone: Boolean
) {

    object FieldType {

        const val YEAR = 0
        const val ROUNDED_YEAR = 1
        const val MONTH = 2
        const val DAY_OF_MONTH = 3
        const val DAY_OF_YEAR = 4

        const val HOUR12 = 5
        const val HOUR24 = 6
        const val MINUTE = 7
        const val SECOND_OF_MINUTE = 8
        const val SECOND_OF_DAY = 9
        const val FRACTION = 10
        const val AMPM = 11

        const val TIME_ZONE_HOUR = 12
        const val TIME_ZONE_MINUTE = 13
        const val TIME_ZONE_SECOND = 14

        const val DELIMITER = 15
    }

    private class Scanner(@JvmField val string: String) {

        private var offset = 0

        private val length: Int = string.length

        fun readChar(): Int {
            return if (offset < length) string[offset++].code else -1
        }

        fun readChar(c: Char) {
            if (offset >= length || string[offset] != c) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, string)
            }
            offset++
        }

        fun readCharIf(c: Char): Boolean {
            if (offset < length && string[offset] == c) {
                offset++
                return true
            }
            return false
        }

        fun readPositiveInt(digits: Int, delimited: Boolean): Int {
            val start = offset
            var end: Int
            if (delimited) {
                end = start
                while (end < length) {
                    val c = string[end]
                    if (c < '0' || c > '9') {
                        break
                    }
                    end++
                }
                if (start == end) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, string)
                }
            } else {
                end = start + digits
                if (end > length) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, string)
                }
            }
            try {
                offset = end
                return StringUtils.parseUInt31(string, start, end)
            } catch (e: NumberFormatException) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, string)
            }
        }

        fun readNanos(digits: Int, delimited: Boolean): Int {
            var start = offset
            var end = start
            var nanos = 0
            var mul = 100_000_000
            if (delimited) {
                end = start
                while (end < length) {
                    val c = string[end]
                    if (c < '0' || c > '9') {
                        break
                    }
                    nanos += mul * (c - '0')
                    mul /= 10
                    end++
                }
                if (start == end) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, string)
                }
            } else {
                end = start + digits
                if (end > length) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, string)
                }
                while (start < end) {
                    val c = string[start]
                    if (c < '0' || c > '9') {
                        throw DbException.get(ErrorCode.PARSE_ERROR_1, string)
                    }
                    nanos += mul * (c - '0')
                    mul /= 10
                    start++
                }
            }
            offset = end
            return nanos
        }
    }

    private abstract class Part {

        abstract fun type(): Int

        abstract fun format(builder: StringBuilder, dateValue: Long, timeNanos: Long, offsetSeconds: Int)

        abstract fun parse(target: kotlin.IntArray, s: Scanner, delimited: Boolean, year: Int)
    }

    private class Delimiter private constructor(private val delimiter: Char) : Part() {

        override fun type(): Int {
            return DELIMITER
        }

        override fun format(builder: StringBuilder, dateValue: Long, timeNanos: Long, offsetSeconds: Int) {
            builder.append(delimiter)
        }

        override fun parse(target: kotlin.IntArray, s: Scanner, delimited: Boolean, year: Int) {
            s.readChar(delimiter)
        }

        companion object {
            @JvmField val MINUS_SIGN = Delimiter('-')
            @JvmField val PERIOD = Delimiter('.')
            @JvmField val SOLIDUS = Delimiter('/')
            @JvmField val COMMA = Delimiter(',')
            @JvmField val APOSTROPHE = Delimiter('\'')
            @JvmField val SEMICOLON = Delimiter(';')
            @JvmField val COLON = Delimiter(':')
            @JvmField val SPACE = Delimiter(' ')
        }
    }

    private class Field(private val type: Int, private val digits: Int) : Part() {

        override fun type(): Int {
            return type
        }

        override fun format(builder: StringBuilder, dateValue: Long, timeNanos: Long, offsetSeconds: Int) {
            when (type) {
                YEAR, ROUNDED_YEAR -> {
                    var y = DateTimeUtils.yearFromDateValue(dateValue)
                    if (y < 0) {
                        builder.append('-')
                        y = -y
                    }
                    when (digits) {
                        1 -> y %= 10
                        2 -> y %= 100
                        3 -> y %= 1_000
                    }
                    formatLast(builder, y, digits)
                }
                MONTH ->
                    StringUtils.appendTwoDigits(builder, DateTimeUtils.monthFromDateValue(dateValue))
                DAY_OF_MONTH ->
                    StringUtils.appendTwoDigits(builder, DateTimeUtils.dayFromDateValue(dateValue))
                DAY_OF_YEAR ->
                    StringUtils.appendZeroPadded(builder, 3, DateTimeUtils.getDayOfYear(dateValue))
                HOUR12 -> {
                    var h = (timeNanos / NANOS_PER_HOUR).toInt()
                    if (h == 0) {
                        h = 12
                    } else if (h > 12) {
                        h -= 12
                    }
                    StringUtils.appendTwoDigits(builder, h)
                }
                HOUR24 ->
                    StringUtils.appendTwoDigits(builder, (timeNanos / NANOS_PER_HOUR).toInt())
                MINUTE ->
                    StringUtils.appendTwoDigits(builder, (timeNanos / NANOS_PER_MINUTE % 60).toInt())
                SECOND_OF_MINUTE ->
                    StringUtils.appendTwoDigits(builder, (timeNanos / NANOS_PER_SECOND % 60).toInt())
                SECOND_OF_DAY ->
                    StringUtils.appendZeroPadded(builder, 5, (timeNanos / NANOS_PER_SECOND).toInt())
                FRACTION ->
                    formatLast(
                        builder,
                        (timeNanos % NANOS_PER_SECOND).toInt() / FRACTIONAL_SECONDS_TABLE[digits],
                        digits
                    )
                AMPM -> {
                    val h = (timeNanos / NANOS_PER_HOUR).toInt()
                    builder.append(if (h < 12) "A.M." else "P.M.")
                }
                TIME_ZONE_HOUR -> {
                    var h = offsetSeconds / 3_600
                    if (offsetSeconds >= 0) {
                        builder.append('+')
                    } else {
                        h = -h
                        builder.append('-')
                    }
                    StringUtils.appendTwoDigits(builder, h)
                }
                TIME_ZONE_MINUTE ->
                    StringUtils.appendTwoDigits(builder, Math.abs(offsetSeconds % 3_600 / 60))
                TIME_ZONE_SECOND -> {
                    StringUtils.appendTwoDigits(builder, Math.abs(offsetSeconds % 60))
                }
            }
        }

        override fun parse(target: kotlin.IntArray, s: Scanner, delimited: Boolean, year: Int) {
            when (type) {
                YEAR, ROUNDED_YEAR -> {
                    val negative = s.readCharIf('-')
                    if (!negative) {
                        s.readCharIf('+')
                    }
                    var v = s.readPositiveInt(digits, delimited)
                    if (negative) {
                        if (digits < 4 || type == ROUNDED_YEAR) {
                            throw DbException.get(ErrorCode.PARSE_ERROR_1, s.string)
                        }
                        v = -v
                    } else if (digits < 4) {
                        if (digits == 1) {
                            if (v > 9) {
                                throw DbException.get(ErrorCode.PARSE_ERROR_1, s.string)
                            }
                            v += year / 10 * 10
                        } else if (digits == 2) {
                            if (v > 99) {
                                throw DbException.get(ErrorCode.PARSE_ERROR_1, s.string)
                            }
                            v += year / 100 * 100
                            if (type == ROUNDED_YEAR) {
                                if (v > year + 50) {
                                    v -= 100
                                } else if (v < year - 49) {
                                    v += 100
                                }
                            }
                        } else if (digits == 3) {
                            if (v > 999) {
                                throw DbException.get(ErrorCode.PARSE_ERROR_1, s.string)
                            }
                            v += year / 1_000 * 1_000
                        }
                    }
                    target[type] = v
                }
                MONTH, DAY_OF_MONTH, DAY_OF_YEAR, HOUR12, HOUR24, MINUTE, SECOND_OF_MINUTE,
                SECOND_OF_DAY, TIME_ZONE_MINUTE, TIME_ZONE_SECOND ->
                    target[type] = s.readPositiveInt(digits, delimited)
                FRACTION ->
                    target[FRACTION] = s.readNanos(digits, delimited)
                AMPM -> {
                    val v: Int
                    if (s.readCharIf('A')) {
                        v = 0
                    } else {
                        s.readChar('P')
                        v = 1
                    }
                    s.readChar('.')
                    s.readChar('M')
                    s.readChar('.')
                    target[AMPM] = v
                }
                TIME_ZONE_HOUR -> {
                    val negative = s.readCharIf('-')
                    if (!negative) {
                        if (!s.readCharIf('+')) {
                            s.readChar(' ')
                        }
                    }
                    val v = s.readPositiveInt(digits, delimited)
                    if (v > 18) {
                        throw DbException.get(ErrorCode.PARSE_ERROR_1, s.string)
                    }
                    target[TIME_ZONE_HOUR] = if (negative) (if (v == 0) -100 else -v) else v
                }
            }
        }

        companion object {
            @JvmField val Y = Field(YEAR, 1)
            @JvmField val YY = Field(YEAR, 2)
            @JvmField val YYY = Field(YEAR, 3)
            @JvmField val YYYY = Field(YEAR, 4)

            @JvmField val RR = Field(ROUNDED_YEAR, 2)
            @JvmField val RRRR = Field(ROUNDED_YEAR, 4)

            @JvmField val MM = Field(MONTH, 2)

            @JvmField val DD = Field(DAY_OF_MONTH, 2)

            @JvmField val DDD = Field(DAY_OF_YEAR, 3)

            @JvmField val HH12 = Field(HOUR12, 2)

            @JvmField val HH24 = Field(HOUR24, 2)

            @JvmField val MI = Field(MINUTE, 2)

            @JvmField val SS = Field(SECOND_OF_MINUTE, 2)

            @JvmField val SSSSS = Field(SECOND_OF_DAY, 5)

            private val FF: Array<Field>

            @JvmField val AM_PM = Field(AMPM, 4)

            @JvmField val TZH = Field(TIME_ZONE_HOUR, 2)

            @JvmField val TZM = Field(TIME_ZONE_MINUTE, 2)

            @JvmField val TZS = Field(TIME_ZONE_SECOND, 2)

            init {
                var i = 0
                FF = Array(9) { Field(FRACTION, ++i) }
            }

            fun ff(digits: Int): Field {
                return FF[digits - 1]
            }

            private fun formatLast(builder: StringBuilder, value: Int, digits: Int) {
                if (digits == 2) {
                    StringUtils.appendTwoDigits(builder, value)
                } else {
                    StringUtils.appendZeroPadded(builder, digits, value)
                }
            }
        }
    }

    fun format(value: Value): String? {
        val dateValue: Long
        val nanoOfDay: Long
        val offsetSeconds: Int
        when (value.getValueType()) {
            Value.NULL ->
                return null
            Value.DATE -> {
                if (containsTime || containsTimeZone) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "time or time zone fields with DATE")
                }
                dateValue = (value as ValueDate).getDateValue()
                nanoOfDay = 0L
                offsetSeconds = 0
            }
            Value.TIME -> {
                if (containsDate || containsTimeZone) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "date or time zone fields with TIME")
                }
                dateValue = 0L
                nanoOfDay = (value as ValueTime).getNanos()
                offsetSeconds = 0
            }
            Value.TIME_TZ -> {
                if (containsDate) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "date fields with TIME WITH TIME ZONE")
                }
                val vt = value as ValueTimeTimeZone
                dateValue = 0L
                nanoOfDay = vt.getNanos()
                offsetSeconds = vt.getTimeZoneOffsetSeconds()
            }
            Value.TIMESTAMP -> {
                if (containsTimeZone) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "time zone fields with TIMESTAMP")
                }
                val vt = value as ValueTimestamp
                dateValue = vt.getDateValue()
                nanoOfDay = vt.getTimeNanos()
                offsetSeconds = 0
            }
            Value.TIMESTAMP_TZ -> {
                val vt = value as ValueTimestampTimeZone
                dateValue = vt.getDateValue()
                nanoOfDay = vt.getTimeNanos()
                offsetSeconds = vt.getTimeZoneOffsetSeconds()
            }
            else ->
                throw DbException.getUnsupportedException(value.getType().getTraceSQL())
        }
        val builder = StringBuilder()
        for (part in parts) {
            part.format(builder, dateValue, nanoOfDay, offsetSeconds)
        }
        return builder.toString()
    }

    fun parse(string: String, targetType: TypeInfo, provider: CastDataProvider?): Value {
        when (targetType.getValueType()) {
            Value.DATE -> {
                if (containsTime || containsTimeZone) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "time or time zone fields with DATE")
                }
                val yearMonth = yearMonth(provider)
                return ValueDate.fromDateValue(constructDate(parse(string, yearMonth[0]), yearMonth))
            }
            Value.TIME -> {
                if (containsDate || containsTimeZone) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "date or time zone fields with TIME")
                }
                return ValueTime.fromNanos(constructTime(parse(string, 0)))
            }
            Value.TIME_TZ -> {
                if (containsDate) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "date fields with TIME WITH TIME ZONE")
                }
                val target = parse(string, 0)
                return ValueTimeTimeZone.fromNanos(constructTime(target), constructOffset(target))
            }
            Value.TIMESTAMP -> {
                if (containsTimeZone) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "time zone fields with TIMESTAMP")
                }
                val yearMonth = yearMonth(provider)
                val target = parse(string, yearMonth[0])
                return ValueTimestamp.fromDateValueAndNanos(constructDate(target, yearMonth), constructTime(target))
            }
            Value.TIMESTAMP_TZ -> {
                val yearMonth = yearMonth(provider)
                val target = parse(string, yearMonth[0])
                return ValueTimestampTimeZone.fromDateValueAndNanos(
                    constructDate(target, yearMonth),
                    constructTime(target), constructOffset(target)
                )
            }
            else ->
                throw DbException.getUnsupportedException(targetType.getTraceSQL())
        }
    }

    private fun parse(string: String, year: Int): kotlin.IntArray {
        val target = kotlin.IntArray(15)
        Arrays.fill(target, Integer.MIN_VALUE)
        val s = Scanner(string)
        val l = parts.size - 1
        for (i in 0..l) {
            val part = parts[i]
            part.parse(
                target, s,
                // Left-delimited
                (i == 0 ||
                    ((1 shl part.type()) and (1 shl AMPM or (1 shl TIME_ZONE_HOUR))) != 0 ||
                    ((1 shl parts[i - 1].type()) and (1 shl DELIMITER or (1 shl AMPM))) != 0) &&
                    // Right-delimited
                    (i == l ||
                        part.type() == AMPM ||
                        ((1 shl parts[i + 1].type()) and
                            (1 shl DELIMITER or (1 shl AMPM) or (1 shl TIME_ZONE_HOUR))) != 0),
                year
            )
        }
        return target
    }

    companion object {

        private val CACHE: SmallLRUCache<String, DateTimeTemplate> = SmallLRUCache.newInstance(100)

        @JvmStatic
        fun of(template: String): DateTimeTemplate {
            synchronized(CACHE) {
                val t = CACHE.get(template)
                if (t != null) {
                    return t
                }
            }
            val t = parseTemplate(template)
            val old: DateTimeTemplate?
            synchronized(CACHE) {
                old = CACHE.putIfAbsent(template, t)
            }
            return old ?: t
        }

        private fun parseTemplate(template: String): DateTimeTemplate {
            val parts = ArrayList<Part>()
            val s = Scanner(template)
            var usedFields = 0
            var c: Int
            while (s.readChar().also { c = it } >= 0) {
                val part: Part
                when (c) {
                    '-'.code ->
                        part = Delimiter.MINUS_SIGN
                    '.'.code ->
                        part = Delimiter.PERIOD
                    '/'.code ->
                        part = Delimiter.SOLIDUS
                    ','.code ->
                        part = Delimiter.COMMA
                    '\''.code ->
                        part = Delimiter.APOSTROPHE
                    ';'.code ->
                        part = Delimiter.SEMICOLON
                    ':'.code ->
                        part = Delimiter.COLON
                    ' '.code ->
                        part = Delimiter.SPACE
                    'Y'.code -> {
                        usedFields = checkUsed(usedFields, YEAR, template)
                        part = if (s.readCharIf('Y')) {
                            if (s.readCharIf('Y')) {
                                if (s.readCharIf('Y')) Field.YYYY else Field.YYY
                            } else {
                                Field.YY
                            }
                        } else {
                            Field.Y
                        }
                    }
                    'R'.code -> {
                        // Year and rounded year may not be used together, mark both as
                        // YEAR
                        usedFields = checkUsed(usedFields, YEAR, template)
                        s.readChar('R')
                        part = if (s.readCharIf('R')) {
                            s.readChar('R')
                            Field.RRRR
                        } else {
                            Field.RR
                        }
                    }
                    'M'.code ->
                        part = if (s.readCharIf('I')) {
                            usedFields = checkUsed(usedFields, MINUTE, template)
                            Field.MI
                        } else {
                            s.readChar('M')
                            usedFields = checkUsed(usedFields, MONTH, template)
                            Field.MM
                        }
                    'D'.code -> {
                        s.readChar('D')
                        part = if (s.readCharIf('D')) {
                            usedFields = checkUsed(usedFields, DAY_OF_YEAR, template)
                            Field.DDD
                        } else {
                            usedFields = checkUsed(usedFields, DAY_OF_MONTH, template)
                            Field.DD
                        }
                    }
                    'H'.code -> {
                        s.readChar('H')
                        part = if (s.readCharIf('2')) {
                            s.readChar('4')
                            usedFields = checkUsed(usedFields, HOUR24, template)
                            Field.HH24
                        } else {
                            if (s.readCharIf('1')) {
                                s.readChar('2')
                            }
                            usedFields = checkUsed(usedFields, HOUR12, template)
                            Field.HH12
                        }
                    }
                    'S'.code -> {
                        s.readChar('S')
                        part = if (s.readCharIf('S')) {
                            s.readChar('S')
                            s.readChar('S')
                            usedFields = checkUsed(usedFields, SECOND_OF_DAY, template)
                            Field.SSSSS
                        } else {
                            usedFields = checkUsed(usedFields, SECOND_OF_MINUTE, template)
                            Field.SS
                        }
                    }
                    'F'.code -> {
                        s.readChar('F')
                        c = s.readChar()
                        if (c < '1'.code || c > '9'.code) {
                            throw DbException.get(ErrorCode.PARSE_ERROR_1, template)
                        }
                        usedFields = checkUsed(usedFields, FRACTION, template)
                        part = Field.ff(c - '0'.code)
                    }
                    'A'.code, 'P'.code -> {
                        s.readChar('.')
                        s.readChar('M')
                        s.readChar('.')
                        usedFields = checkUsed(usedFields, AMPM, template)
                        part = Field.AM_PM
                    }
                    'T'.code -> {
                        s.readChar('Z')
                        part = if (s.readCharIf('H')) {
                            usedFields = checkUsed(usedFields, TIME_ZONE_HOUR, template)
                            Field.TZH
                        } else if (s.readCharIf('M')) {
                            usedFields = checkUsed(usedFields, TIME_ZONE_MINUTE, template)
                            Field.TZM
                        } else {
                            s.readChar('S')
                            usedFields = checkUsed(usedFields, TIME_ZONE_SECOND, template)
                            Field.TZS
                        }
                    }
                    else ->
                        throw DbException.get(ErrorCode.PARSE_ERROR_1, template)
                }
                parts.add(part)
            }
            if (((usedFields and (1 shl DAY_OF_YEAR)) != 0 &&
                    (usedFields and (1 shl MONTH or (1 shl DAY_OF_MONTH))) != 0) ||

                (((usedFields and (1 shl HOUR12)) != 0) !=
                    ((usedFields and (1 shl AMPM)) != 0)) ||

                ((usedFields and (1 shl HOUR24)) != 0 &&
                    (usedFields and (1 shl HOUR12)) != 0) ||

                ((usedFields and (1 shl SECOND_OF_DAY)) != 0 &&
                    ((usedFields and (1 shl HOUR12 or (1 shl HOUR24) or (1 shl MINUTE) or
                        (1 shl SECOND_OF_MINUTE))) != 0)) ||

                ((usedFields and (1 shl TIME_ZONE_SECOND)) != 0 &&
                    !((usedFields and (1 shl TIME_ZONE_MINUTE)) != 0)) ||

                ((usedFields and (1 shl TIME_ZONE_MINUTE)) != 0 &&
                    !((usedFields and (1 shl TIME_ZONE_HOUR)) != 0))
            ) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, template)
            }
            return DateTimeTemplate(
                parts.toTypedArray(),
                (usedFields and (1 shl YEAR or (1 shl MONTH) or (1 shl DAY_OF_MONTH) or
                    (1 shl DAY_OF_YEAR))) != 0,
                (usedFields and (1 shl HOUR24 or (1 shl HOUR12) or (1 shl MINUTE) or
                    (1 shl SECOND_OF_MINUTE) or (1 shl SECOND_OF_DAY) or (1 shl AMPM))) != 0,
                (usedFields and (1 shl TIME_ZONE_HOUR or (1 shl TIME_ZONE_MINUTE) or
                    (1 shl TIME_ZONE_SECOND))) != 0
            )
        }

        private fun checkUsed(usedFields: Int, type: Int, template: String): Int {
            val newUsedFields = usedFields or (1 shl type)
            if (usedFields == newUsedFields) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, template)
            }
            return newUsedFields
        }

        private fun yearMonth(provider: CastDataProvider?): kotlin.IntArray {
            val dateValue = provider!!.currentTimestamp().getDateValue()
            return kotlin.intArrayOf(
                DateTimeUtils.yearFromDateValue(dateValue),
                DateTimeUtils.monthFromDateValue(dateValue)
            )
        }

        private fun constructDate(target: kotlin.IntArray, yearMonth: kotlin.IntArray): Long {
            var year = target[YEAR]
            if (year == Integer.MIN_VALUE) {
                year = target[ROUNDED_YEAR]
            }
            if (year == Integer.MIN_VALUE) {
                year = yearMonth[0]
            }
            val dayOfYear = target[DAY_OF_YEAR]
            if (dayOfYear != Integer.MIN_VALUE) {
                if (dayOfYear < 1 || dayOfYear > (if (DateTimeUtils.isLeapYear(year)) 366 else 365)) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "Day of year $dayOfYear")
                }
                return DateTimeUtils.dateValueFromAbsoluteDay(
                    DateTimeUtils.absoluteDayFromYear(year.toLong()) + dayOfYear - 1
                )
            }
            var month = target[MONTH]
            if (month == Integer.MIN_VALUE) {
                month = yearMonth[1]
            }
            var day = target[DAY_OF_MONTH]
            if (day == Integer.MIN_VALUE) {
                day = 1
            }
            if (!DateTimeUtils.isValidDate(year, month, day)) {
                throw DbException.get(
                    ErrorCode.PARSE_ERROR_1,
                    "Invalid date, year=$year, month=$month, day=$day"
                )
            }
            return DateTimeUtils.dateValue(year.toLong(), month, day)
        }

        private fun constructTime(target: kotlin.IntArray): Long {
            var secondOfDay = target[SECOND_OF_DAY]
            if (secondOfDay == Integer.MIN_VALUE) {
                var hour = target[HOUR24]
                if (hour == Integer.MIN_VALUE) {
                    hour = target[HOUR12]
                    if (hour == Integer.MIN_VALUE) {
                        hour = 0
                    } else {
                        if (hour < 1 || hour > 12) {
                            throw DbException.get(ErrorCode.PARSE_ERROR_1, "Hour(12) $hour")
                        }
                        if (hour == 12) {
                            hour = 0
                        }
                        hour += target[AMPM] * 12
                    }
                } else {
                    if (hour < 0 || hour > 23) {
                        throw DbException.get(ErrorCode.PARSE_ERROR_1, "Hour(24) $hour")
                    }
                }
                var minute = target[MINUTE]
                if (minute == Integer.MIN_VALUE) {
                    minute = 0
                } else if (minute < 0 || minute > 59) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "Minute $minute")
                }
                var second = target[SECOND_OF_MINUTE]
                if (second == Integer.MIN_VALUE) {
                    second = 0
                } else if (second < 0 || second > 59) {
                    throw DbException.get(ErrorCode.PARSE_ERROR_1, "Second of minute $second")
                }
                secondOfDay = (hour * 60 + minute) * 60 + second
            } else if (secondOfDay < 0 || secondOfDay >= SECONDS_PER_DAY) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, "Second of day $secondOfDay")
            }
            var fraction = target[FRACTION]
            if (fraction == Integer.MIN_VALUE) {
                fraction = 0
            }
            return secondOfDay * NANOS_PER_SECOND + fraction
        }

        private fun constructOffset(target: kotlin.IntArray): Int {
            var hour = target[TIME_ZONE_HOUR]
            if (hour == Integer.MIN_VALUE) {
                return 0
            }
            val negative = hour < 0
            if (negative) {
                if (hour == -100) {
                    hour = 0
                } else {
                    hour = -hour
                }
            }
            var minute = target[TIME_ZONE_MINUTE]
            if (minute == Integer.MIN_VALUE) {
                minute = 0
            } else if (minute > 59) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, "Time zone minute $minute")
            }
            var second = target[TIME_ZONE_SECOND]
            if (second == Integer.MIN_VALUE) {
                second = 0
            } else if (second > 59) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, "Time zone second $second")
            }
            val offset = (hour * 60 + minute) * 60 + second
            if (offset > 18 * 60 * 60) {
                throw DbException.get(ErrorCode.PARSE_ERROR_1, "Time zone offset is too large")
            }
            return if (negative) -offset else offset
        }
    }
}
