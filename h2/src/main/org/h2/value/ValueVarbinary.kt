/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.nio.charset.StandardCharsets

import org.h2.engine.SysProperties
import org.h2.util.Utils

/**
 * Implementation of the BINARY VARYING data type.
 */
class ValueVarbinary private constructor(value: ByteArray) : ValueBytesBase(value) {

    /**
     * Associated TypeInfo.
     */
    private var type: TypeInfo? = null

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            val precision = value.size.toLong()
            type = TypeInfo(Value.VARBINARY, precision, 0, null)
            this.type = type
        }
        return type
    }

    override fun getValueType(): Int {
        return Value.VARBINARY
    }

    override fun getString(): String? {
        return String(value, StandardCharsets.UTF_8)
    }

    companion object {
        /**
         * Empty value.
         */
        @JvmField
        val EMPTY: ValueVarbinary = ValueVarbinary(Utils.EMPTY_BYTES)

        /**
         * Get or create a VARBINARY value for the given byte array.
         * Clone the data.
         *
         * @param b the byte array
         * @return the value
         */
        @JvmStatic
        fun get(b: ByteArray): ValueVarbinary {
            if (b.size == 0) {
                return EMPTY
            }
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
        fun getNoCopy(b: ByteArray): ValueVarbinary {
            if (b.size == 0) {
                return EMPTY
            }
            val obj = ValueVarbinary(b)
            if (b.size > SysProperties.OBJECT_CACHE_MAX_PER_ELEMENT_SIZE) {
                return obj
            }
            return Value.cache(obj) as ValueVarbinary
        }
    }

}
