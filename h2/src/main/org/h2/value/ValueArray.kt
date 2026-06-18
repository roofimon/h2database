/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import org.h2.engine.CastDataProvider
import org.h2.engine.Constants
import org.h2.message.DbException

/**
 * Implementation of the ARRAY data type.
 */
class ValueArray private constructor(componentType: TypeInfo, list: Array<Value>, provider: CastDataProvider?) :
        ValueCollectionBase(list) {

    private var type: TypeInfo? = null

    private val componentType: TypeInfo

    init {
        val length = list.size
        if (length > Constants.MAX_ARRAY_CARDINALITY) {
            val typeName = getTypeName(getValueType())
            throw DbException.getValueTooLongException(typeName, typeName, length.toLong())
        }
        for (i in 0 until length) {
            list[i] = list[i].castTo(componentType, provider)
        }
        this.componentType = componentType
    }

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            val componentType = getComponentType()
            type = TypeInfo.getTypeInfo(getValueType(), values.size.toLong(), 0, componentType)
            this.type = type
        }
        return type
    }

    override fun getValueType(): Int {
        return ARRAY
    }

    fun getComponentType(): TypeInfo {
        return componentType
    }

    override fun getString(): String {
        val builder = StringBuilder().append('[')
        for (i in values.indices) {
            if (i > 0) {
                builder.append(", ")
            }
            builder.append(values[i].getString())
        }
        return builder.append(']').toString()
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        val v = o as ValueArray
        if (values === v.values) {
            return 0
        }
        val l = values.size
        val ol = v.values.size
        val len = Math.min(l, ol)
        for (i in 0 until len) {
            val v1 = values[i]
            val v2 = v.values[i]
            val comp = v1.compareTo(v2, provider, mode)
            if (comp != 0) {
                return comp
            }
        }
        return Integer.compare(l, ol)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        builder.append("ARRAY [")
        val length = values.size
        for (i in 0 until length) {
            if (i > 0) {
                builder.append(", ")
            }
            values[i].getSQL(builder, sqlFlags)
        }
        return builder.append(']')
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ValueArray) {
            return false
        }
        val v = other
        if (values === v.values) {
            return true
        }
        val len = values.size
        if (len != v.values.size) {
            return false
        }
        for (i in 0 until len) {
            if (values[i] != v.values[i]) {
                return false
            }
        }
        return true
    }

    companion object {
        /**
         * Empty array.
         */
        @JvmField
        val EMPTY: ValueArray = get(TypeInfo.TYPE_NULL, Value.EMPTY_VALUES, null)

        /**
         * Get or create a array value for the given value array.
         * Do not clone the data.
         *
         * @param list the value array
         * @param provider the cast information provider
         * @return the value
         */
        @JvmStatic
        fun get(list: Array<Value>, provider: CastDataProvider?): ValueArray {
            return ValueArray(TypeInfo.getHigherType(list), list, provider)
        }

        /**
         * Get or create a array value for the given value array.
         * Do not clone the data.
         *
         * @param componentType the type of elements, or {@code null}
         * @param list the value array
         * @param provider the cast information provider
         * @return the value
         */
        @JvmStatic
        fun get(componentType: TypeInfo, list: Array<Value>, provider: CastDataProvider?): ValueArray {
            return ValueArray(componentType, list, provider)
        }
    }

}
