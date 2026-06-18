/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.util.Arrays

import org.h2.engine.CastDataProvider
import org.h2.engine.Constants
import org.h2.message.DbException
import org.h2.util.StringUtils
import org.h2.util.Utils

/**
 * Base implementation of byte array based data types.
 */
abstract class ValueBytesBase(value: ByteArray) : Value() {

    /**
     * The value.
     */
    @JvmField
    var value: ByteArray

    /**
     * The hash code.
     */
    @JvmField
    var hash: Int = 0

    init {
        val length = value.size
        if (length > Constants.MAX_STRING_LENGTH) {
            throw DbException.getValueTooLongException(Value.getTypeName(getValueType()),
                    StringUtils.convertBytesToHex(value, 41), length.toLong())
        }
        this.value = value
    }

    final override fun getBytes(): ByteArray {
        return Utils.cloneByteArray(value)!!
    }

    final override fun getBytesNoCopy(): ByteArray {
        return value
    }

    final override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return Integer.signum(Arrays.compareUnsigned(value, (v as ValueBytesBase).value))
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return StringUtils.convertBytesToHex(builder.append("X'"), value).append('\'')
    }

    final override fun hashCode(): Int {
        var h = hash
        if (h == 0) {
            h = javaClass.hashCode() xor Utils.getByteArrayHash(value)
            if (h == 0) {
                h = 1_234_570_417
            }
            hash = h
        }
        return h
    }

    override fun getMemory(): Int {
        return value.size + 24
    }

    final override fun equals(other: Any?): Boolean {
        return other != null && javaClass == other.javaClass && Arrays.equals(value, (other as ValueBytesBase).value)
    }

}
