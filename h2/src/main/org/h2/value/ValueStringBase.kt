/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.engine.Constants
import org.h2.message.DbException

/**
 * Base implementation of String based data types.
 */
abstract class ValueStringBase(v: String) : Value() {

    /**
     * The value.
     */
    @JvmField
    var value: String

    private var type: TypeInfo? = null

    init {
        val length = v.length
        if (length > Constants.MAX_STRING_LENGTH) {
            throw DbException.getValueTooLongException(getTypeName(getValueType()), v, length.toLong())
        }
        this.value = v
    }

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            val length = value.length
            type = TypeInfo(getValueType(), length.toLong(), 0, null)
            this.type = type
        }
        return type
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        return mode!!.compareString(value, (v as ValueStringBase).value, false)
    }

    override fun hashCode(): Int {
        // TODO hash performance: could build a quicker hash
        // by hashing the size and a few characters
        return javaClass.hashCode() xor value.hashCode()

        // proposed code:
//        private int hash = 0;
//
//        public int hashCode() {
//            int h = hash;
//            if (h == 0) {
//                String s = value;
//                int l = s.length();
//                if (l > 0) {
//                    if (l < 16)
//                        h = s.hashCode();
//                    else {
//                        h = l;
//                        for (int i = 1; i <= l; i <<= 1)
//                            h = 31 *
//                                (31 * h + s.charAt(i - 1)) +
//                                s.charAt(l - i);
//                    }
//                    hash = h;
//                }
//            }
//            return h;
//        }
    }

    override fun getString(): String {
        return value
    }

    override fun getBytes(): ByteArray {
        return value.toByteArray(StandardCharsets.UTF_8)
    }

    override fun getBoolean(): Boolean {
        val s = value.trim()
        if (s.equals("true", ignoreCase = true) || s.equals("t", ignoreCase = true)
            || s.equals("yes", ignoreCase = true) || s.equals("y", ignoreCase = true)) {
            return true
        } else if (s.equals("false", ignoreCase = true) || s.equals("f", ignoreCase = true)
            || s.equals("no", ignoreCase = true) || s.equals("n", ignoreCase = true)) {
            return false
        }
        try {
            // convert to a number, and if it is not 0 then it is true
            return BigDecimal(s).signum() != 0
        } catch (e: NumberFormatException) {
            throw getDataConversionError(BOOLEAN)
        }
    }

    override fun getByte(): Byte {
        try {
            return java.lang.Byte.parseByte(value.trim())
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getShort(): Short {
        try {
            return java.lang.Short.parseShort(value.trim())
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getInt(): Int {
        try {
            return value.trim().toInt()
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getLong(): Long {
        try {
            return value.trim().toLong()
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getBigInteger(): BigInteger {
        try {
            return BigInteger(value.trim())
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getBigDecimal(): BigDecimal {
        try {
            return BigDecimal(value.trim())
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getFloat(): Float {
        try {
            return value.trim().toFloat()
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getDouble(): Double {
        try {
            return value.trim().toDouble()
        } catch (e: NumberFormatException) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, value)
        }
    }

    override fun getMemory(): Int {
        /*
         * Java 11 with -XX:-UseCompressedOops
         * Empty string: 88 bytes
         * 1 to 4 UTF-16 chars: 96 bytes
         */
        return value.length * 2 + 94
    }

    override fun equals(other: Any?): Boolean {
        return other != null && javaClass == other.javaClass && value == (other as ValueStringBase).value
    }

}
