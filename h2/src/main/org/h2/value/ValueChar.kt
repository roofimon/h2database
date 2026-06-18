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
 * Implementation of the CHARACTER data type.
 */
class ValueChar private constructor(value: String) : ValueStringBase(value) {

    override fun getValueType(): Int {
        return CHAR
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return mode!!.compareString(convertToChar().getString(), v.convertToChar().getString(), false)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if (sqlFlags and NO_CASTS == 0) {
            val length = value.length
            return StringUtils.quoteStringSQL(builder.append("CAST("), value).append(" AS CHAR(")
                .append(if (length > 0) length else 1).append("))")
        }
        return StringUtils.quoteStringSQL(builder, value)
    }

    companion object {
        /**
         * Get or create a CHAR value for the given string.
         *
         * @param s the string
         * @return the value
         */
        @JvmStatic
        fun get(s: String): ValueChar {
            val obj = ValueChar(StringUtils.cache(s)!!)
            if (s.length > SysProperties.OBJECT_CACHE_MAX_PER_ELEMENT_SIZE) {
                return obj
            }
            return Value.cache(obj) as ValueChar
        }
    }

}
