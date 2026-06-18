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

/**
 * Implementation of the DATE data type.
 */
class ValueDate private constructor(val dateValue: Long) : Value() {

    init {
        if (dateValue < MIN_DATE_VALUE || dateValue > MAX_DATE_VALUE) {
            throw IllegalArgumentException("dateValue out of range $dateValue")
        }
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_DATE
    }

    override fun getValueType(): Int {
        return DATE
    }

    override fun getString(): String {
        return DateTimeUtils.appendDate(StringBuilder(PRECISION), dateValue).toString()
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return DateTimeUtils.appendDate(builder.append("DATE '"), dateValue).append('\'')
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return java.lang.Long.compare(dateValue, (o as ValueDate).dateValue)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is ValueDate && dateValue == other.dateValue
    }

    override fun hashCode(): Int {
        return (dateValue xor (dateValue ushr 32)).toInt()
    }

    companion object {
        /**
         * The default precision and display size of the textual representation of a date.
         * Example: 2000-01-02
         */
        const val PRECISION = 10

        /**
         * Get or create a date value for the given date.
         *
         * @param dateValue the date value
         * @return the value
         */
        @JvmStatic
        fun fromDateValue(dateValue: Long): ValueDate {
            return Value.cache(ValueDate(dateValue)) as ValueDate
        }

        /**
         * Parse a string to a ValueDate.
         *
         * @param s the string to parse
         * @return the date
         */
        @JvmStatic
        fun parse(s: String): ValueDate {
            try {
                return fromDateValue(DateTimeUtils.parseDateValue(s, 0, s.length))
            } catch (e: Exception) {
                throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, e, "DATE", s)
            }
        }
    }
}
