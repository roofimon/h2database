/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.util.Arrays
import java.util.Locale

import org.h2.api.ErrorCode
import org.h2.engine.CastDataProvider
import org.h2.engine.Constants
import org.h2.message.DbException

/**
 * Extended parameters of the ENUM data type.
 */
class ExtTypeInfoEnum
/**
 * Creates new instance of extended parameters of the ENUM data type.
 *
 * @param enumerators
 *            the enumerators. May not be modified by caller or this class.
 */
(enumerators: Array<String>?) : ExtTypeInfo() {

    private val enumerators: Array<String>
    private val cleaned: Array<String>

    private var type: TypeInfo? = null

    init {
        if (enumerators == null) {
            throw DbException.get(ErrorCode.ENUM_EMPTY)
        }
        val length = enumerators.size
        if (length == 0) {
            throw DbException.get(ErrorCode.ENUM_EMPTY)
        }
        if (length > Constants.MAX_ARRAY_CARDINALITY) {
            throw DbException.getValueTooLongException("ENUM", "($length elements)", length.toLong())
        }
        val cleaned = arrayOfNulls<String>(length)
        for (i in 0 until length) {
            val l = sanitize(enumerators[i])
            if (l == null || l.isEmpty()) {
                throw DbException.get(ErrorCode.ENUM_EMPTY)
            }
            for (j in 0 until i) {
                if (l == cleaned[j]) {
                    throw DbException.get(
                        ErrorCode.ENUM_DUPLICATE, //
                        toSQL(StringBuilder(), enumerators).toString()
                    )
                }
            }
            cleaned[i] = l
        }
        @Suppress("UNCHECKED_CAST")
        val cleanedArray = cleaned as Array<String>
        this.enumerators = enumerators
        this.cleaned = if (Arrays.equals(cleanedArray, enumerators)) enumerators else cleanedArray
    }

    fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            var p = 0
            for (s in enumerators) {
                val l = s.length
                if (l > p) {
                    p = l
                }
            }
            type = TypeInfo(Value.ENUM, p.toLong(), 0, this)
            this.type = type
        }
        return type
    }

    /**
     * Get count of elements in enumeration.
     *
     * @return count of elements in enumeration
     */
    fun getCount(): Int {
        return enumerators.size
    }

    /**
     * Returns an enumerator with specified 0-based ordinal value.
     *
     * @param ordinal
     *            ordinal value of an enumerator
     * @return the enumerator with specified ordinal value
     */
    fun getEnumerator(ordinal: Int): String {
        return enumerators[ordinal]
    }

    /**
     * Get ValueEnum instance for an ordinal.
     * @param ordinal ordinal value of an enum
     * @param provider the cast information provider
     * @return ValueEnum instance
     */
    fun getValue(ordinal: Int, provider: CastDataProvider?): ValueEnum {
        val label: String
        if (provider == null || !provider.zeroBasedEnums()) {
            if (ordinal < 1 || ordinal > enumerators.size) {
                throw DbException.get(ErrorCode.ENUM_VALUE_NOT_PERMITTED, getTraceSQL(), Integer.toString(ordinal))
            }
            label = enumerators[ordinal - 1]
        } else {
            if (ordinal < 0 || ordinal >= enumerators.size) {
                throw DbException.get(ErrorCode.ENUM_VALUE_NOT_PERMITTED, getTraceSQL(), Integer.toString(ordinal))
            }
            label = enumerators[ordinal]
        }
        return ValueEnum(this, label, ordinal)
    }

    /**
     * Get ValueEnum instance for a label string.
     * @param label label string
     * @param provider the cast information provider
     * @return ValueEnum instance
     */
    fun getValue(label: String?, provider: CastDataProvider?): ValueEnum {
        return getValueOrNull(label, provider)
            ?: throw DbException.get(ErrorCode.ENUM_VALUE_NOT_PERMITTED, toString(), label)
    }

    private fun getValueOrNull(label: String?, provider: CastDataProvider?): ValueEnum? {
        val l = sanitize(label)
        if (l != null) {
            var ordinal = if (provider == null || !provider.zeroBasedEnums()) 1 else 0
            var i = 0
            while (i < cleaned.size) {
                if (l == cleaned[i]) {
                    return ValueEnum(this, enumerators[i], ordinal)
                }
                i++
                ordinal++
            }
        }
        return null
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(enumerators) + 203_117
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || obj.javaClass != ExtTypeInfoEnum::class.java) {
            return false
        }
        return Arrays.equals(enumerators, (obj as ExtTypeInfoEnum).enumerators)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return toSQL(builder, enumerators)
    }

    companion object {

        /**
         * Returns enumerators for the two specified values for a binary operation.
         *
         * @param left
         *            left (first) operand
         * @param right
         *            right (second) operand
         * @return enumerators from the left or the right value, or an empty array
         *         if both values do not have enumerators
         */
        @JvmStatic
        fun getEnumeratorsForBinaryOperation(left: Value, right: Value): ExtTypeInfoEnum {
            return if (left.getValueType() == Value.ENUM) {
                (left as ValueEnum).getEnumerators()
            } else if (right.getValueType() == Value.ENUM) {
                (right as ValueEnum).getEnumerators()
            } else {
                throw DbException.get(
                    ErrorCode.UNKNOWN_DATA_TYPE_1,
                    "type1=" + left.getValueType() + ", type2=" + right.getValueType()
                )
            }
        }

        private fun sanitize(label: String?): String? {
            if (label == null) {
                return null
            }
            val length = label.length
            if (length > Constants.MAX_STRING_LENGTH) {
                throw DbException.getValueTooLongException("ENUM", label, length.toLong())
            }
            return (label as java.lang.String).trim().toUpperCase(Locale.ENGLISH)
        }

        private fun toSQL(builder: StringBuilder, enumerators: Array<String>): StringBuilder {
            builder.append('(')
            for (i in enumerators.indices) {
                if (i != 0) {
                    builder.append(", ")
                }
                builder.append('\'')
                val s = enumerators[i]
                var j = 0
                val length = s.length
                while (j < length) {
                    val c = s[j]
                    if (c == '\'') {
                        builder.append('\'')
                    }
                    builder.append(c)
                    j++
                }
                builder.append('\'')
            }
            return builder.append(')')
        }
    }

}
