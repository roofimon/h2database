/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.util.LinkedHashMap

import org.h2.api.ErrorCode
import org.h2.engine.Constants
import org.h2.message.DbException
import org.h2.util.ParserUtil

/**
 * Extended parameters of the ROW data type.
 */
class ExtTypeInfoRow : ExtTypeInfo {

    private val fields: LinkedHashMap<String, TypeInfo>

    private var hash: Int = 0

    /**
     * Creates new instance of extended parameters of ROW data type.
     *
     * @param fields
     *            fields
     */
    constructor(fields: Array<Typed>) : this(fields, fields.size)

    /**
     * Creates new instance of extended parameters of ROW data type.
     *
     * @param fields
     *            fields
     * @param degree
     *            number of fields to use
     */
    constructor(fields: Array<Typed>, degree: Int) {
        if (degree > Constants.MAX_COLUMNS) {
            throw DbException.get(ErrorCode.TOO_MANY_COLUMNS_1, "" + Constants.MAX_COLUMNS)
        }
        val map = LinkedHashMap<String, TypeInfo>(Math.ceil(degree / .75).toInt())
        var i = 0
        while (i < degree) {
            val t = fields[i].getType()
            map.put("C" + ++i, t)
        }
        this.fields = map
    }

    /**
     * Creates new instance of extended parameters of ROW data type.
     *
     * @param fields
     *            fields
     */
    constructor(fields: LinkedHashMap<String, TypeInfo>) {
        if (fields.size > Constants.MAX_COLUMNS) {
            throw DbException.get(ErrorCode.TOO_MANY_COLUMNS_1, "" + Constants.MAX_COLUMNS)
        }
        this.fields = fields
    }

    /**
     * Returns fields.
     *
     * @return fields
     */
    fun getFields(): Set<Map.Entry<String, TypeInfo>> {
        return fields.entries
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        builder.append('(')
        var f = false
        for (field in fields.entries) {
            if (f) {
                builder.append(", ")
            }
            f = true
            ParserUtil.quoteIdentifier(builder, field.key, sqlFlags).append(' ')
            field.value.getSQL(builder, sqlFlags)
        }
        return builder.append(')')
    }

    override fun hashCode(): Int {
        var h = hash
        if (h != 0) {
            return h
        }
        h = 67_378_403
        for (entry in fields.entries) {
            h = (h * 31 + entry.key.hashCode()) * 37 + entry.value.hashCode()
        }
        hash = h
        return h
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj!!.javaClass != ExtTypeInfoRow::class.java) {
            return false
        }
        val fields2 = (obj as ExtTypeInfoRow).fields
        val degree = fields.size
        if (degree != fields2.size) {
            return false
        }
        val i1 = fields.entries.iterator()
        val i2 = fields2.entries.iterator()
        while (i1.hasNext()) {
            val e1 = i1.next()
            val e2 = i2.next()
            if (e1.key != e2.key || e1.value != e2.value) {
                return false
            }
        }
        return true
    }

}
