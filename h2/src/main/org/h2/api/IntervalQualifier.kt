/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api

/**
 * Interval qualifier.
 */
enum class IntervalQualifier {

    /**
     * `YEAR`
     */
    YEAR,

    /**
     * `MONTH`
     */
    MONTH,

    /**
     * `DAY`
     */
    DAY,

    /**
     * `HOUR`
     */
    HOUR,

    /**
     * `MINUTE`
     */
    MINUTE,

    /**
     * `SECOND`
     */
    SECOND,

    /**
     * `YEAR TO MONTH`
     */
    YEAR_TO_MONTH,

    /**
     * `DAY TO HOUR`
     */
    DAY_TO_HOUR,

    /**
     * `DAY TO MINUTE`
     */
    DAY_TO_MINUTE,

    /**
     * `DAY TO SECOND`
     */
    DAY_TO_SECOND,

    /**
     * `HOUR TO MINUTE`
     */
    HOUR_TO_MINUTE,

    /**
     * `HOUR TO SECOND`
     */
    HOUR_TO_SECOND,

    /**
     * `MINUTE TO SECOND`
     */
    MINUTE_TO_SECOND;

    private val string: String = name.replace('_', ' ').intern()

    /**
     * Returns whether interval with this qualifier is a year-month interval.
     *
     * @return whether interval with this qualifier is a year-month interval
     */
    fun isYearMonth(): Boolean {
        return this == YEAR || this == MONTH || this == YEAR_TO_MONTH
    }

    /**
     * Returns whether interval with this qualifier is a day-time interval.
     *
     * @return whether interval with this qualifier is a day-time interval
     */
    fun isDayTime(): Boolean {
        return !isYearMonth()
    }

    /**
     * Returns whether interval with this qualifier has years.
     *
     * @return whether interval with this qualifier has years
     */
    fun hasYears(): Boolean {
        return this == YEAR || this == YEAR_TO_MONTH
    }

    /**
     * Returns whether interval with this qualifier has months.
     *
     * @return whether interval with this qualifier has months
     */
    fun hasMonths(): Boolean {
        return this == MONTH || this == YEAR_TO_MONTH
    }

    /**
     * Returns whether interval with this qualifier has days.
     *
     * @return whether interval with this qualifier has days
     */
    fun hasDays(): Boolean {
        return when (this) {
            DAY, DAY_TO_HOUR, DAY_TO_MINUTE, DAY_TO_SECOND -> true
            else -> false
        }
    }

    /**
     * Returns whether interval with this qualifier has hours.
     *
     * @return whether interval with this qualifier has hours
     */
    fun hasHours(): Boolean {
        return when (this) {
            HOUR, DAY_TO_HOUR, DAY_TO_MINUTE, DAY_TO_SECOND, HOUR_TO_MINUTE, HOUR_TO_SECOND -> true
            else -> false
        }
    }

    /**
     * Returns whether interval with this qualifier has minutes.
     *
     * @return whether interval with this qualifier has minutes
     */
    fun hasMinutes(): Boolean {
        return when (this) {
            MINUTE, DAY_TO_MINUTE, DAY_TO_SECOND, HOUR_TO_MINUTE, HOUR_TO_SECOND, MINUTE_TO_SECOND -> true
            else -> false
        }
    }

    /**
     * Returns whether interval with this qualifier has seconds.
     *
     * @return whether interval with this qualifier has seconds
     */
    fun hasSeconds(): Boolean {
        return when (this) {
            SECOND, DAY_TO_SECOND, HOUR_TO_SECOND, MINUTE_TO_SECOND -> true
            else -> false
        }
    }

    /**
     * Returns whether interval with this qualifier has multiple fields.
     *
     * @return whether interval with this qualifier has multiple fields
     */
    fun hasMultipleFields(): Boolean {
        return ordinal > 5
    }

    override fun toString(): String {
        return string
    }

    /**
     * Returns full type name.
     *
     * @param precision precision, or `-1`
     * @param scale fractional seconds precision, or `-1`
     * @return full type name
     */
    fun getTypeName(precision: Int, scale: Int): String {
        return getTypeName(StringBuilder(), precision, scale, false).toString()
    }

    /**
     * Appends full type name to the specified string builder.
     *
     * @param builder string builder
     * @param precision precision, or `-1`
     * @param scale fractional seconds precision, or `-1`
     * @param qualifierOnly if `true`, don't add the INTERVAL prefix
     * @return the specified string builder
     */
    fun getTypeName(builder: StringBuilder, precision: Int, scale: Int, qualifierOnly: Boolean): StringBuilder {
        if (!qualifierOnly) {
            builder.append("INTERVAL ")
        }
        when (this) {
            YEAR, MONTH, DAY, HOUR, MINUTE -> {
                builder.append(string)
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
            }
            SECOND -> {
                builder.append(string)
                if (precision > 0 || scale >= 0) {
                    builder.append('(').append(if (precision > 0) precision else 2)
                    if (scale >= 0) {
                        builder.append(", ").append(scale)
                    }
                    builder.append(')')
                }
            }
            YEAR_TO_MONTH -> {
                builder.append("YEAR")
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
                builder.append(" TO MONTH")
            }
            DAY_TO_HOUR -> {
                builder.append("DAY")
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
                builder.append(" TO HOUR")
            }
            DAY_TO_MINUTE -> {
                builder.append("DAY")
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
                builder.append(" TO MINUTE")
            }
            DAY_TO_SECOND -> {
                builder.append("DAY")
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
                builder.append(" TO SECOND")
                if (scale >= 0) {
                    builder.append('(').append(scale).append(')')
                }
            }
            HOUR_TO_MINUTE -> {
                builder.append("HOUR")
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
                builder.append(" TO MINUTE")
            }
            HOUR_TO_SECOND -> {
                builder.append("HOUR")
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
                builder.append(" TO SECOND")
                if (scale >= 0) {
                    builder.append('(').append(scale).append(')')
                }
            }
            MINUTE_TO_SECOND -> {
                builder.append("MINUTE")
                if (precision > 0) {
                    builder.append('(').append(precision).append(')')
                }
                builder.append(" TO SECOND")
                if (scale >= 0) {
                    builder.append('(').append(scale).append(')')
                }
            }
        }
        return builder
    }

    companion object {

        /**
         * Returns the interval qualifier with the specified ordinal value.
         *
         * @param ordinal
         * Java ordinal value (0-based)
         * @return interval qualifier with the specified ordinal value
         */
        @JvmStatic
        fun valueOf(ordinal: Int): IntervalQualifier {
            return when (ordinal) {
                0 -> YEAR
                1 -> MONTH
                2 -> DAY
                3 -> HOUR
                4 -> MINUTE
                5 -> SECOND
                6 -> YEAR_TO_MONTH
                7 -> DAY_TO_HOUR
                8 -> DAY_TO_MINUTE
                9 -> DAY_TO_SECOND
                10 -> HOUR_TO_MINUTE
                11 -> HOUR_TO_SECOND
                12 -> MINUTE_TO_SECOND
                else -> throw IllegalArgumentException()
            }
        }
    }
}
