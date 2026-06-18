/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger

import org.h2.engine.CastDataProvider
import org.h2.message.DbException

/**
 * Implementation of NULL. NULL is not a regular data type.
 */
class ValueNull private constructor() : Value() {

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return builder.append("NULL")
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_NULL
    }

    override fun getValueType(): Int {
        return NULL
    }

    override fun getMemory(): Int {
        // Singleton value
        return 0
    }

    override fun getString(): String? {
        return null
    }

    override fun getReader(): Reader? {
        return null
    }

    override fun getReader(oneBasedOffset: Long, length: Long): Reader? {
        return null
    }

    override fun getBytes(): ByteArray? {
        return null
    }

    override fun getInputStream(): InputStream? {
        return null
    }

    override fun getInputStream(oneBasedOffset: Long, length: Long): InputStream? {
        return null
    }

    override fun getBoolean(): Boolean {
        throw DbException.getInternalError()
    }

    override fun getByte(): Byte {
        throw DbException.getInternalError()
    }

    override fun getShort(): Short {
        throw DbException.getInternalError()
    }

    override fun getInt(): Int {
        throw DbException.getInternalError()
    }

    override fun getLong(): Long {
        throw DbException.getInternalError()
    }

    override fun getBigInteger(): BigInteger? {
        return null
    }

    override fun getBigDecimal(): BigDecimal? {
        return null
    }

    override fun getFloat(): Float {
        throw DbException.getInternalError()
    }

    override fun getDouble(): Double {
        throw DbException.getInternalError()
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        throw DbException.getInternalError("compare null")
    }

    override fun containsNull(): Boolean {
        return true
    }

    override fun hashCode(): Int {
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return other === this
    }

    companion object {
        /**
         * The main NULL instance.
         */
        @JvmField
        val INSTANCE = ValueNull()

        /**
         * The precision of NULL.
         */
        @JvmField
        val PRECISION = 1

        /**
         * The display size of the textual representation of NULL.
         */
        @JvmField
        val DISPLAY_SIZE = 4
    }
}
