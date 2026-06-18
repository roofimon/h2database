/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.util.LinkedHashMap

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.engine.Constants
import org.h2.message.DbException
import org.h2.result.SimpleResult

/**
 * Row value.
 */
class ValueRow private constructor(type: TypeInfo?, list: Array<Value>) : ValueCollectionBase(list) {

    private var type: TypeInfo? = null

    init {
        val degree = list.size
        if (degree > Constants.MAX_COLUMNS) {
            throw DbException.get(ErrorCode.TOO_MANY_COLUMNS_1, "" + Constants.MAX_COLUMNS)
        }
        if (type != null) {
            if (type.getValueType() != ROW || (type.getExtTypeInfo() as ExtTypeInfoRow).getFields().size != degree) {
                throw DbException.getInternalError()
            }
            this.type = type
        }
    }

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            @Suppress("UNCHECKED_CAST")
            type = TypeInfo.getTypeInfo(Value.ROW, 0L, 0, ExtTypeInfoRow(values as Array<Typed>))
            this.type = type
        }
        return type
    }

    override fun getValueType(): Int {
        return ROW
    }

    override fun getString(): String {
        val builder = StringBuilder("ROW (")
        for (i in values.indices) {
            if (i > 0) {
                builder.append(", ")
            }
            builder.append(values[i].getString())
        }
        return builder.append(')').toString()
    }

    fun getResult(): SimpleResult {
        val result = SimpleResult()
        var i = 0
        val l = values.size
        while (i < l) {
            val v = values[i++]
            result.addColumn("C" + i, v.getType())
        }
        result.addRow(*values)
        return result
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        val v = o as ValueRow
        if (values === v.values) {
            return 0
        }
        val len = values.size
        if (len != v.values.size) {
            throw DbException.get(ErrorCode.COLUMN_COUNT_DOES_NOT_MATCH)
        }
        for (i in 0 until len) {
            val v1 = values[i]
            val v2 = v.values[i]
            val comp = v1.compareTo(v2, provider, mode)
            if (comp != 0) {
                return comp
            }
        }
        return 0
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        builder.append("ROW (")
        val length = values.size
        for (i in 0 until length) {
            if (i > 0) {
                builder.append(", ")
            }
            values[i].getSQL(builder, sqlFlags)
        }
        return builder.append(')')
    }

    /**
     * Creates a copy of this row but the new instance will contain the {@link #values} according to
     * {@code newOrder}.<br />
     * E.g.: ROW('a', 'b').cloneWithOrder([1, 0]) returns ROW('b', 'a')
     * @param newOrder array of indexes to create the new values array
     */
    fun cloneWithOrder(newOrder: IntArray): ValueRow {
        val length = values.size
        if (newOrder.size != values.size) {
            throw DbException.getInternalError("Length of the new orders is different than values count.")
        }

        val newValues = arrayOfNulls<Value>(length)
        for (i in 0 until length) {
            newValues[i] = values[newOrder[i]]
        }

        val typeInfoRow = type!!.getExtTypeInfo() as ExtTypeInfoRow
        val fields = createEntriesArray<String, TypeInfo>(length)
        var fi = 0
        for (entry in typeInfoRow.getFields()) {
            fields[fi++] = entry
        }
        val newFields = LinkedHashMap<String, TypeInfo>(length)
        for (i in 0 until length) {
            val field = fields[newOrder[i]]
            newFields.put(field.key, field.value)
        }
        val newTypeInfoRow = ExtTypeInfoRow(newFields)
        val newType = TypeInfo(type!!.getValueType(), type!!.getDeclaredPrecision(),
                type!!.getDeclaredScale(), newTypeInfoRow)

        @Suppress("UNCHECKED_CAST")
        return ValueRow(newType, newValues as Array<Value>)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ValueRow) {
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
         * Empty row.
         */
        @JvmField
        val EMPTY: ValueRow = get(Value.EMPTY_VALUES)

        /**
         * Get or create a row value for the given value array.
         * Do not clone the data.
         *
         * @param list the value array
         * @return the value
         */
        @JvmStatic
        fun get(list: Array<Value>): ValueRow {
            return ValueRow(null, list)
        }

        /**
         * Get or create a typed row value for the given value array.
         * Do not clone the data.
         *
         * @param extTypeInfo the extended data type information
         * @param list the value array
         * @return the value
         */
        @JvmStatic
        fun get(extTypeInfo: ExtTypeInfoRow, list: Array<Value>): ValueRow {
            return ValueRow(TypeInfo(ROW, -1L, -1, extTypeInfo), list)
        }

        /**
         * Get or create a typed row value for the given value array.
         * Do not clone the data.
         *
         * @param typeInfo the data type information
         * @param list the value array
         * @return the value
         */
        @JvmStatic
        fun get(typeInfo: TypeInfo, list: Array<Value>): ValueRow {
            return ValueRow(typeInfo, list)
        }

        @Suppress("UNCHECKED_CAST")
        private fun <K, V> createEntriesArray(length: Int): Array<Map.Entry<K, V>> {
            return arrayOfNulls<Map.Entry<*, *>>(length) as Array<Map.Entry<K, V>>
        }
    }

}
