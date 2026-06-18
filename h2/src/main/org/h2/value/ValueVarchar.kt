/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import org.h2.engine.CastDataProvider
import org.h2.engine.SysProperties
import org.h2.util.StringUtils

/**
 * Implementation of the CHARACTER VARYING data type.
 */
class ValueVarchar private constructor(value: String) : ValueStringBase(value) {

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return StringUtils.quoteStringSQL(builder, value)
    }

    override fun getValueType(): Int {
        return VARCHAR
    }

    companion object {
        /**
         * Empty string. Should not be used in places where empty string can be
         * treated as {@code NULL} depending on database mode.
         */
        @JvmField
        val EMPTY: ValueVarchar = ValueVarchar("")

        /**
         * Get or create a VARCHAR value for the given string.
         *
         * @param s the string
         * @return the value
         */
        @JvmStatic
        fun get(s: String): Value {
            return get(s, null)
        }

        /**
         * Get or create a VARCHAR value for the given string.
         *
         * @param s the string
         * @param provider the cast information provider, or {@code null}
         * @return the value
         */
        @JvmStatic
        fun get(s: String, provider: CastDataProvider?): Value {
            if (s.isEmpty()) {
                return if (provider != null && provider.getMode().treatEmptyStringsAsNull) ValueNull.INSTANCE else EMPTY
            }
            val obj = ValueVarchar(StringUtils.cache(s)!!)
            if (s.length > SysProperties.OBJECT_CACHE_MAX_PER_ELEMENT_SIZE) {
                return obj
            }
            return Value.cache(obj)
            // this saves memory, but is really slow
            // return new ValueString(s.intern());
        }
    }

}
