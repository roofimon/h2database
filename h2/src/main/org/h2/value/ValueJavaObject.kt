/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value
import org.h2.util.HasSQL.Companion.NO_CASTS
import org.h2.util.HasSQL.Companion.DEFAULT_SQL_FLAGS

import org.h2.api.ErrorCode
import org.h2.engine.SysProperties
import org.h2.message.DbException
import org.h2.util.Utils

/**
 * Implementation of the JAVA_OBJECT data type.
 */
class ValueJavaObject private constructor(v: ByteArray) : ValueBytesBase(v) {

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_JAVA_OBJECT
    }

    override fun getValueType(): Int {
        return JAVA_OBJECT
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            return super.getSQL(builder.append("CAST("), DEFAULT_SQL_FLAGS).append(" AS JAVA_OBJECT)")
        }
        return super.getSQL(builder, DEFAULT_SQL_FLAGS)
    }

    override fun getString(): String {
        throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, "JAVA_OBJECT to CHARACTER VARYING")
    }

    companion object {
        private val EMPTY = ValueJavaObject(Utils.EMPTY_BYTES)

        /**
         * Get or create a java object value for the given byte array.
         * Do not clone the data.
         *
         * @param b the byte array
         * @return the value
         */
        @JvmStatic
        fun getNoCopy(b: ByteArray): ValueJavaObject {
            val length = b.size
            if (length == 0) {
                return EMPTY
            }
            val obj = ValueJavaObject(b)
            if (length > SysProperties.OBJECT_CACHE_MAX_PER_ELEMENT_SIZE) {
                return obj
            }
            return Value.cache(obj) as ValueJavaObject
        }
    }
}
