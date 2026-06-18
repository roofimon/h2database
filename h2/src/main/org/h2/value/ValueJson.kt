/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: Lazarev Nikita <lazarevn@ispras.ru>
 */
package org.h2.value

import java.io.ByteArrayOutputStream
import java.lang.ref.SoftReference
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Arrays

import org.h2.api.ErrorCode
import org.h2.message.DbException
import org.h2.util.StringUtils
import org.h2.util.json.JSONBoolean
import org.h2.util.json.JSONByteArrayTarget
import org.h2.util.json.JSONBytesSource
import org.h2.util.json.JSONItemType
import org.h2.util.json.JSONNull
import org.h2.util.json.JSONNumber
import org.h2.util.json.JSONStringSource
import org.h2.util.json.JSONStringTarget
import org.h2.util.json.JSONValue
import org.h2.util.json.JSONValueTarget

/**
 * Implementation of the JSON data type.
 */
class ValueJson private constructor(value: ByteArray) : ValueBytesBase(value) {

    @Volatile
    private var decompositionRef: SoftReference<JSONValue>? = null

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        val s = JSONBytesSource.parse(value, JSONStringTarget(true))
        return builder.append("JSON '").append(s).append('\'')
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_JSON
    }

    override fun getValueType(): Int {
        return Value.JSON
    }

    override fun getString(): String {
        return String(value, StandardCharsets.UTF_8)
    }

    /**
     * Returns JSON item type.
     *
     * @return JSON item type
     */
    fun getItemType(): JSONItemType {
        return when (value[0].toInt().toChar()) {
            '[' -> JSONItemType.ARRAY
            '{' -> JSONItemType.OBJECT
            else -> JSONItemType.SCALAR
        }
    }

    /**
     * Returns decomposed value.
     *
     * @return decomposed value.
     */
    fun getDecomposition(): JSONValue {
        val decompositionRef = this.decompositionRef
        var decomposition = decompositionRef?.get()
        if (decompositionRef == null || decomposition == null) {
            decomposition = JSONBytesSource.parse(value, JSONValueTarget())
            this.decompositionRef = SoftReference(decomposition)
        }
        return decomposition
    }

    override fun getMemory(): Int {
        return value.size + 96
    }

    companion object {

        private val NULL_BYTES: ByteArray = "null".toByteArray(StandardCharsets.ISO_8859_1)
        private val TRUE_BYTES: ByteArray = "true".toByteArray(StandardCharsets.ISO_8859_1)
        private val FALSE_BYTES: ByteArray = "false".toByteArray(StandardCharsets.ISO_8859_1)

        /**
         * `null` JSON value.
         */
        @JvmField
        val NULL: ValueJson = ValueJson(NULL_BYTES)

        /**
         * `true` JSON value.
         */
        @JvmField
        val TRUE: ValueJson = ValueJson(TRUE_BYTES)

        /**
         * `false` JSON value.
         */
        @JvmField
        val FALSE: ValueJson = ValueJson(FALSE_BYTES)

        /**
         * `0` JSON value.
         */
        @JvmField
        val ZERO: ValueJson = ValueJson(byteArrayOf('0'.code.toByte()))

        /**
         * Returns JSON value with the specified content.
         *
         * @param s
         *            JSON representation, will be normalized
         * @return JSON value
         * @throws DbException
         *             on invalid JSON
         */
        @JvmStatic
        fun fromJson(s: String): ValueJson {
            var str = s
            val bytes: ByteArray
            try {
                bytes = JSONStringSource.normalize(str)
            } catch (ex: RuntimeException) {
                if (str.length > 80) {
                    str = StringBuilder(83).append(str, 0, 80).append("...").toString()
                }
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, str)
            }
            return getInternal(bytes)
        }

        /**
         * Returns JSON value with the specified content.
         *
         * @param bytes
         *            JSON representation, will be normalized
         * @return JSON value
         * @throws DbException
         *             on invalid JSON
         */
        @JvmStatic
        fun fromJson(bytes: ByteArray): ValueJson {
            val normalized: ByteArray
            try {
                normalized = JSONBytesSource.normalize(bytes)
            } catch (ex: RuntimeException) {
                val builder = StringBuilder().append("X'")
                if (bytes.size > 40) {
                    StringUtils.convertBytesToHex(builder, bytes, 40).append("...")
                } else {
                    StringUtils.convertBytesToHex(builder, bytes)
                }
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, builder.append('\'').toString())
            }
            return getInternal(normalized)
        }

        /**
         * Returns JSON value with the specified content.
         *
         * @param value
         *            JSON
         * @return JSON value
         * @throws DbException
         *             on invalid JSON
         */
        @JvmStatic
        fun fromJson(value: JSONValue): ValueJson {
            if (value is JSONNull) {
                return NULL
            }
            if (value is JSONBoolean) {
                return if (value.getBoolean()) TRUE else FALSE
            }
            if (value is JSONNumber) {
                // Use equals() to check both value and scale
                if (value.getBigDecimal() == BigDecimal.ZERO) {
                    return ZERO
                }
            }
            val target = JSONByteArrayTarget()
            value.addTo(target)
            val v = ValueJson(target.getResult())
            v.decompositionRef = SoftReference(value)
            return v
        }

        /**
         * Returns JSON value with the specified boolean content.
         *
         * @param bool
         *            boolean value
         * @return JSON value
         */
        @JvmStatic
        fun get(bool: Boolean): ValueJson {
            return if (bool) TRUE else FALSE
        }

        /**
         * Returns JSON value with the specified numeric content.
         *
         * @param number
         *            integer value
         * @return JSON value
         */
        @JvmStatic
        fun get(number: Int): ValueJson {
            return if (number != 0) getNumber(Integer.toString(number)) else ZERO
        }

        /**
         * Returns JSON value with the specified numeric content.
         *
         * @param number
         *            long value
         * @return JSON value
         */
        @JvmStatic
        fun get(number: Long): ValueJson {
            return if (number != 0L) getNumber(java.lang.Long.toString(number)) else ZERO
        }

        /**
         * Returns JSON value with the specified numeric content.
         *
         * @param number
         *            big decimal value
         * @return JSON value
         */
        @JvmStatic
        fun get(number: BigDecimal): ValueJson {
            if (number.signum() == 0 && number.scale() == 0) {
                return ZERO
            }
            var s = number.toString()
            var index = s.indexOf('E')
            if (index >= 0 && s[++index] == '+') {
                val length = s.length
                s = StringBuilder(length - 1).append(s, 0, index).append(s, index + 1, length).toString()
            }
            return getNumber(s)
        }

        /**
         * Returns JSON value with the specified string content.
         *
         * @param string
         *            string value
         * @return JSON value
         */
        @JvmStatic
        fun get(string: String): ValueJson {
            return ValueJson(JSONByteArrayTarget.encodeString( //
                    ByteArrayOutputStream(string.length + 2), string).toByteArray())
        }

        /**
         * Returns JSON value with the specified content.
         *
         * @param bytes
         *            normalized JSON representation
         * @return JSON value
         */
        @JvmStatic
        fun getInternal(bytes: ByteArray): ValueJson {
            when (bytes.size) {
                1 -> if (bytes[0] == '0'.code.toByte()) {
                    return ZERO
                }
                4 -> if (Arrays.equals(TRUE_BYTES, bytes)) {
                    return TRUE
                } else if (Arrays.equals(NULL_BYTES, bytes)) {
                    return NULL
                }
                5 -> if (Arrays.equals(FALSE_BYTES, bytes)) {
                    return FALSE
                }
            }
            return ValueJson(bytes)
        }

        private fun getNumber(s: String): ValueJson {
            return ValueJson(s.toByteArray(StandardCharsets.ISO_8859_1))
        }
    }
}
