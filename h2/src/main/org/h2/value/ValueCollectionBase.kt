/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.engine.Constants
import org.h2.message.DbException

/**
 * Base class for ARRAY and ROW values.
 */
abstract class ValueCollectionBase(
        /**
         * Values.
         */
        @JvmField val values: Array<Value>) : Value() {

    private var hash: Int = 0

    fun getList(): Array<Value> {
        return values
    }

    override fun hashCode(): Int {
        if (hash != 0) {
            return hash
        }
        var h = getValueType()
        for (v in values) {
            h = h * 31 + v.hashCode()
        }
        hash = h
        return h
    }

    override fun compareWithNull(v: Value, forEquality: Boolean, provider: CastDataProvider?,
            compareMode: CompareMode?): Int {
        if (v === ValueNull.INSTANCE) {
            return Integer.MIN_VALUE
        }
        val l = this
        val leftType = l.getValueType()
        val rightType = v.getValueType()
        if (rightType != leftType) {
            throw v.getDataConversionError(leftType)
        }
        val r = v as ValueCollectionBase
        val leftArray = l.values
        val rightArray = r.values
        val leftLength = leftArray.size
        val rightLength = rightArray.size
        if (leftLength != rightLength) {
            if (leftType == ROW) {
                throw DbException.get(ErrorCode.COLUMN_COUNT_DOES_NOT_MATCH)
            }
            if (forEquality) {
                return 1
            }
        }
        if (forEquality) {
            var hasNull = false
            for (i in 0 until leftLength) {
                val v1 = leftArray[i]
                val v2 = rightArray[i]
                val comp = v1.compareWithNull(v2, forEquality, provider, compareMode)
                if (comp != 0) {
                    if (comp != Integer.MIN_VALUE) {
                        return comp
                    }
                    hasNull = true
                }
            }
            return if (hasNull) Integer.MIN_VALUE else 0
        }
        val len = Math.min(leftLength, rightLength)
        for (i in 0 until len) {
            val v1 = leftArray[i]
            val v2 = rightArray[i]
            val comp = v1.compareWithNull(v2, forEquality, provider, compareMode)
            if (comp != 0) {
                return comp
            }
        }
        return Integer.compare(leftLength, rightLength)
    }

    override fun containsNull(): Boolean {
        for (v in values) {
            if (v.containsNull()) {
                return true
            }
        }
        return false
    }

    override fun getValueWithFirstNullImpl(v: Value): Value? {
        val r = v as ValueCollectionBase
        val leftArray = values
        val rightArray = r.values
        val leftLength = leftArray.size
        val rightLength = rightArray.size
        val len = Math.min(leftLength, rightLength)
        for (i in 0 until len) {
            val v1 = leftArray[i]
            val v2 = rightArray[i]
            val c = v1.getValueWithFirstNull(v2)
            if (c === v1) {
                return this
            } else if (c === v2) {
                return v
            }
        }
        return null
    }

    override fun getMemory(): Int {
        var memory = 72 + values.size * Constants.MEMORY_POINTER
        for (v in values) {
            memory += v.getMemory()
        }
        return memory
    }

}
