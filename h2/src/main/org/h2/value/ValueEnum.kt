/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import org.h2.util.HasSQL.Companion.NO_CASTS
import org.h2.util.StringUtils

/**
 * ENUM value.
 */
class ValueEnum internal constructor(private val enumerators: ExtTypeInfoEnum, label: String?, ordinal: Int) :
    ValueEnumBase(label, ordinal) {

    override fun getType(): TypeInfo {
        return enumerators.getType()
    }

    fun getEnumerators(): ExtTypeInfoEnum {
        return enumerators
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            StringUtils.quoteStringSQL(builder.append("CAST("), label).append(" AS ")
            return enumerators.getType().getSQL(builder, sqlFlags).append(')')
        }
        return StringUtils.quoteStringSQL(builder, label)
    }

}
