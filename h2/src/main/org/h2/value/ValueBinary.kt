/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.nio.charset.StandardCharsets

import org.h2.engine.SysProperties
import org.h2.util.HasSQL.Companion.NO_CASTS
import org.h2.util.Utils

/**
 * Implementation of the BINARY data type.
 */
class ValueBinary private constructor(value: ByteArray) : ValueBytesBase(value) {

    /**
     * Associated TypeInfo.
     */
    private var type: TypeInfo? = null

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            val precision = value.size.toLong()
            type = TypeInfo(Value.BINARY, precision, 0, null)
            this.type = type
        }
        return type
    }

    override fun getValueType(): Int {
        return Value.BINARY
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and NO_CASTS) == 0) {
            val length = value.size
            return super.getSQL(builder.append("CAST("), sqlFlags).append(" AS BINARY(")
                    .append(if (length > 0) length else 1).append("))")
        }
        return super.getSQL(builder, sqlFlags)
    }

    override fun getString(): String? {
        return String(value, StandardCharsets.UTF_8)
    }

    companion object {
        /**
         * Get or create a VARBINARY value for the given byte array.
         * Clone the data.
         *
         * @param b the byte array
         * @return the value
         */
        @JvmStatic
        fun get(b: ByteArray): ValueBinary {
            return getNoCopy(Utils.cloneByteArray(b)!!)
        }

        /**
         * Get or create a VARBINARY value for the given byte array.
         * Do not clone the date.
         *
         * @param b the byte array
         * @return the value
         */
        @JvmStatic
        fun getNoCopy(b: ByteArray): ValueBinary {
            val obj = ValueBinary(b)
            if (b.size > SysProperties.OBJECT_CACHE_MAX_PER_ELEMENT_SIZE) {
                return obj
            }
            return Value.cache(obj) as ValueBinary
        }
    }

}
