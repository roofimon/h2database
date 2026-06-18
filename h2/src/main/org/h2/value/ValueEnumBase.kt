/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal
import java.math.BigInteger

import org.h2.engine.CastDataProvider
import org.h2.util.StringUtils

/**
 * Base implementation of the ENUM data type.
 *
 * This base implementation is only used in 2.0.* clients when they work with
 * 1.4.* servers.
 */
open class ValueEnumBase protected constructor(val label: String?, private val ordinal: Int) : Value() {

    override fun add(v: Value): Value {
        val iv = v.convertToInt(null)
        return convertToInt(null).add(iv)
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return Integer.compare(getInt(), v.getInt())
    }

    override fun divide(v: Value, quotientType: TypeInfo): Value {
        val iv = v.convertToInt(null)
        return convertToInt(null).divide(iv, quotientType)
    }

    override fun equals(other: Any?): Boolean {
        return other is ValueEnumBase &&
            getInt() == other.getInt()
    }

    override fun getInt(): Int {
        return ordinal
    }

    override fun getLong(): Long {
        return ordinal.toLong()
    }

    override fun getBigInteger(): BigInteger {
        return BigInteger.valueOf(ordinal.toLong())
    }

    override fun getBigDecimal(): BigDecimal {
        return BigDecimal.valueOf(ordinal.toLong())
    }

    override fun getFloat(): Float {
        return ordinal.toFloat()
    }

    override fun getDouble(): Double {
        return ordinal.toDouble()
    }

    override fun getSignum(): Int {
        return Integer.signum(ordinal)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return StringUtils.quoteStringSQL(builder, label)
    }

    override fun getString(): String? {
        return label
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_ENUM_UNDEFINED
    }

    override fun getValueType(): Int {
        return ENUM
    }

    override fun getMemory(): Int {
        return 120
    }

    override fun hashCode(): Int {
        var results = 31
        results += getString()!!.hashCode()
        results += getInt()
        return results
    }

    override fun modulus(v: Value): Value {
        val iv = v.convertToInt(null)
        return convertToInt(null).modulus(iv)
    }

    override fun multiply(v: Value): Value {
        val iv = v.convertToInt(null)
        return convertToInt(null).multiply(iv)
    }

    override fun subtract(v: Value): Value {
        val iv = v.convertToInt(null)
        return convertToInt(null).subtract(iv)
    }

    companion object {
        /**
         * Get or create an enum value with the given label and ordinal.
         *
         * @param label the label
         * @param ordinal the ordinal
         * @return the value
         */
        @JvmStatic
        fun get(label: String?, ordinal: Int): ValueEnumBase {
            return ValueEnumBase(label, ordinal)
        }
    }

}
