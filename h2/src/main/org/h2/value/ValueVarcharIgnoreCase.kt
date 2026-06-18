/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value
import org.h2.util.HasSQL.Companion.NO_CASTS

import org.h2.engine.CastDataProvider
import org.h2.engine.SysProperties
import org.h2.util.StringUtils

/**
 * Implementation of the VARCHAR_IGNORECASE data type.
 */
class ValueVarcharIgnoreCase private constructor(value: String) : ValueStringBase(value) {

    /**
     * The hash code.
     */
    private var hash = 0

    override fun getValueType(): Int {
        return VARCHAR_IGNORECASE
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return mode!!.compareString(value, (v as ValueStringBase).value, true)
    }

    override fun equals(other: Any?): Boolean {
        return other is ValueVarcharIgnoreCase && value.equals(other.value, ignoreCase = true)
    }

    override fun hashCode(): Int {
        if (hash == 0) {
            // this is locale sensitive
            hash = (value as java.lang.String).toUpperCase().hashCode()
        }
        return hash
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if (sqlFlags and NO_CASTS == 0) {
            return StringUtils.quoteStringSQL(builder.append("CAST("), value).append(" AS VARCHAR_IGNORECASE(")
                .append(value.length).append("))")
        }
        return StringUtils.quoteStringSQL(builder, value)
    }

    companion object {
        private val EMPTY = ValueVarcharIgnoreCase("")

        /**
         * Get or create a VARCHAR_IGNORECASE value for the given string.
         * The value will have the same case as the passed string.
         *
         * @param s the string
         * @return the value
         */
        @JvmStatic
        fun get(s: String): ValueVarcharIgnoreCase {
            val length = s.length
            if (length == 0) {
                return EMPTY
            }
            val obj = ValueVarcharIgnoreCase(StringUtils.cache(s)!!)
            if (length > SysProperties.OBJECT_CACHE_MAX_PER_ELEMENT_SIZE) {
                return obj
            }
            val cache = Value.cache(obj) as ValueVarcharIgnoreCase
            // the cached object could have the wrong case
            // (it would still be 'equal', but we don't like to store it)
            if (cache.value == s) {
                return cache
            }
            return obj
        }
    }

}
