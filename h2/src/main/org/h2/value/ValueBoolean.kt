/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal
import java.math.BigInteger

import org.h2.engine.CastDataProvider

/**
 * Implementation of the BOOLEAN data type.
 */
class ValueBoolean private constructor(private val value: Boolean) : Value() {

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_BOOLEAN
    }

    override fun getValueType(): Int {
        return BOOLEAN
    }

    override fun getMemory(): Int {
        // Singleton TRUE and FALSE values
        return 0
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return builder.append(getString())
    }

    override fun getString(): String {
        return if (value) "TRUE" else "FALSE"
    }

    override fun getBoolean(): Boolean {
        return value
    }

    override fun getByte(): Byte {
        return if (value) 1.toByte() else 0.toByte()
    }

    override fun getShort(): Short {
        return if (value) 1.toShort() else 0.toShort()
    }

    override fun getInt(): Int {
        return if (value) 1 else 0
    }

    override fun getLong(): Long {
        return if (value) 1L else 0L
    }

    override fun getBigInteger(): BigInteger {
        return if (value) BigInteger.ONE else BigInteger.ZERO
    }

    override fun getBigDecimal(): BigDecimal {
        return if (value) BigDecimal.ONE else BigDecimal.ZERO
    }

    override fun getFloat(): Float {
        return if (value) 1f else 0f
    }

    override fun getDouble(): Double {
        return if (value) 1.0 else 0.0
    }

    override fun negate(): Value {
        return if (value) FALSE else TRUE
    }

    override fun compareTypeSafe(o: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return java.lang.Boolean.compare(value, (o as ValueBoolean).value)
    }

    override fun hashCode(): Int {
        return if (value) 1 else 0
    }

    override fun equals(other: Any?): Boolean {
        // there are only ever two instances, so the instance must match
        return this === other
    }

    companion object {
        /**
         * The precision in digits.
         */
        const val PRECISION = 1

        /**
         * The maximum display size of a boolean.
         * Example: FALSE
         */
        const val DISPLAY_SIZE = 5

        /**
         * TRUE value.
         */
        @JvmField
        val TRUE = ValueBoolean(true)

        /**
         * FALSE value.
         */
        @JvmField
        val FALSE = ValueBoolean(false)

        /**
         * Get the boolean value for the given boolean.
         *
         * @param b the boolean
         * @return the value
         */
        @JvmStatic
        fun get(b: Boolean): ValueBoolean {
            return if (b) TRUE else FALSE
        }
    }
}
