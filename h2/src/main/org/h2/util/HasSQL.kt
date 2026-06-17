/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

/**
 * An object that has an SQL representation.
 */
interface HasSQL {

    /**
     * Get a medium size SQL expression for debugging or tracing.
     *
     * @return the SQL expression
     */
    fun getTraceSQL(): String {
        return getSQL(TRACE_SQL_FLAGS)
    }

    /**
     * Get the SQL statement of this expression. This may not always be the
     * original SQL statement, specially after optimization.
     *
     * @param sqlFlags
     *            formatting flags
     * @return the SQL statement
     */
    fun getSQL(sqlFlags: Int): String {
        return getSQL(StringBuilder(), sqlFlags).toString()
    }

    /**
     * Appends the SQL statement of this object to the specified builder.
     *
     * @param builder
     *            string builder
     * @param sqlFlags
     *            formatting flags
     * @return the specified string builder
     */
    fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder

    companion object {
        /**
         * Quote identifiers only when it is strictly required (different case or
         * identifier is also a keyword).
         */
        const val QUOTE_ONLY_WHEN_REQUIRED = 1

        /**
         * Replace long LOB values with some generated values.
         */
        const val REPLACE_LOBS_FOR_TRACE = 2

        /**
         * Don't add casts around literals.
         */
        const val NO_CASTS = 4

        /**
         * Add execution plan information.
         */
        const val ADD_PLAN_INFORMATION = 8

        /**
         * Default flags.
         */
        const val DEFAULT_SQL_FLAGS = 0

        /**
         * Combined flags for trace.
         */
        const val TRACE_SQL_FLAGS = QUOTE_ONLY_WHEN_REQUIRED or REPLACE_LOBS_FOR_TRACE
    }

}
