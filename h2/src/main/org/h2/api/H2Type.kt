/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api

import java.sql.SQLType

import org.h2.value.ExtTypeInfoRow
import org.h2.value.TypeInfo
import org.h2.value.Typed
import org.h2.value.Value

/**
 * Data types of H2.
 */
class H2Type private constructor(private val typeInfo: TypeInfo, field: String) : SQLType {

    private val field: String = "H2Type.$field"

    override fun getName(): String {
        return typeInfo.toString()
    }

    override fun getVendor(): String {
        return "com.h2database"
    }

    /**
     * Returns the vendor specific type number for the data type. The returned
     * value is actual only for the current version of H2.
     *
     * @return the vendor specific data type
     */
    override fun getVendorTypeNumber(): Int? {
        return typeInfo.valueType
    }

    override fun toString(): String {
        return field
    }

    companion object {

        // Character strings

        /**
         * The CHARACTER data type.
         */
        @JvmField
        val CHAR = H2Type(TypeInfo.getTypeInfo(Value.CHAR), "CHARACTER")

        /**
         * The CHARACTER VARYING data type.
         */
        @JvmField
        val VARCHAR = H2Type(TypeInfo.TYPE_VARCHAR, "CHARACTER VARYING")

        /**
         * The CHARACTER LARGE OBJECT data type.
         */
        @JvmField
        val CLOB = H2Type(TypeInfo.TYPE_CLOB, "CHARACTER LARGE OBJECT")

        /**
         * The VARCHAR_IGNORECASE data type.
         */
        @JvmField
        val VARCHAR_IGNORECASE = H2Type(TypeInfo.TYPE_VARCHAR_IGNORECASE, "VARCHAR_IGNORECASE")

        // Binary strings

        /**
         * The BINARY data type.
         */
        @JvmField
        val BINARY = H2Type(TypeInfo.getTypeInfo(Value.BINARY), "BINARY")

        /**
         * The BINARY VARYING data type.
         */
        @JvmField
        val VARBINARY = H2Type(TypeInfo.TYPE_VARBINARY, "BINARY VARYING")

        /**
         * The BINARY LARGE OBJECT data type.
         */
        @JvmField
        val BLOB = H2Type(TypeInfo.TYPE_BLOB, "BINARY LARGE OBJECT")

        // Boolean

        /**
         * The BOOLEAN data type
         */
        @JvmField
        val BOOLEAN = H2Type(TypeInfo.TYPE_BOOLEAN, "BOOLEAN")

        // Exact numeric data types

        /**
         * The TINYINT data type.
         */
        @JvmField
        val TINYINT = H2Type(TypeInfo.TYPE_TINYINT, "TINYINT")

        /**
         * The SMALLINT data type.
         */
        @JvmField
        val SMALLINT = H2Type(TypeInfo.TYPE_SMALLINT, "SMALLINT")

        /**
         * The INTEGER data type.
         */
        @JvmField
        val INTEGER = H2Type(TypeInfo.TYPE_INTEGER, "INTEGER")

        /**
         * The BIGINT data type.
         */
        @JvmField
        val BIGINT = H2Type(TypeInfo.TYPE_BIGINT, "BIGINT")

        /**
         * The NUMERIC data type.
         */
        @JvmField
        val NUMERIC = H2Type(TypeInfo.TYPE_NUMERIC_FLOATING_POINT, "NUMERIC")

        // Approximate numeric data types

        /**
         * The REAL data type.
         */
        @JvmField
        val REAL = H2Type(TypeInfo.TYPE_REAL, "REAL")

        /**
         * The DOUBLE PRECISION data type.
         */
        @JvmField
        val DOUBLE_PRECISION = H2Type(TypeInfo.TYPE_DOUBLE, "DOUBLE PRECISION")

        // Decimal floating-point type

        /**
         * The DECFLOAT data type.
         */
        @JvmField
        val DECFLOAT = H2Type(TypeInfo.TYPE_DECFLOAT, "DECFLOAT")

        // Date-time data types

        /**
         * The DATE data type.
         */
        @JvmField
        val DATE = H2Type(TypeInfo.TYPE_DATE, "DATE")

        /**
         * The TIME data type.
         */
        @JvmField
        val TIME = H2Type(TypeInfo.TYPE_TIME, "TIME")

        /**
         * The TIME WITH TIME ZONE data type.
         */
        @JvmField
        val TIME_WITH_TIME_ZONE = H2Type(TypeInfo.TYPE_TIME_TZ, "TIME WITH TIME ZONE")

        /**
         * The TIMESTAMP data type.
         */
        @JvmField
        val TIMESTAMP = H2Type(TypeInfo.TYPE_TIMESTAMP, "TIMESTAMP")

        /**
         * The TIMESTAMP WITH TIME ZONE data type.
         */
        @JvmField
        val TIMESTAMP_WITH_TIME_ZONE = H2Type(TypeInfo.TYPE_TIMESTAMP_TZ, "TIMESTAMP WITH TIME ZONE")

        // Intervals

        /**
         * The INTERVAL YEAR data type.
         */
        @JvmField
        val INTERVAL_YEAR = H2Type(TypeInfo.getTypeInfo(Value.INTERVAL_YEAR), "INTERVAL_YEAR")

        /**
         * The INTERVAL MONTH data type.
         */
        @JvmField
        val INTERVAL_MONTH = H2Type(TypeInfo.getTypeInfo(Value.INTERVAL_MONTH), "INTERVAL_MONTH")

        /**
         * The INTERVAL DAY data type.
         */
        @JvmField
        val INTERVAL_DAY = H2Type(TypeInfo.TYPE_INTERVAL_DAY, "INTERVAL_DAY")

        /**
         * The INTERVAL HOUR data type.
         */
        @JvmField
        val INTERVAL_HOUR = H2Type(TypeInfo.getTypeInfo(Value.INTERVAL_HOUR), "INTERVAL_HOUR")

        /**
         * The INTERVAL MINUTE data type.
         */
        @JvmField
        val INTERVAL_MINUTE = H2Type(TypeInfo.getTypeInfo(Value.INTERVAL_MINUTE), "INTERVAL_MINUTE")

        /**
         * The INTERVAL SECOND data type.
         */
        @JvmField
        val INTERVAL_SECOND = H2Type(TypeInfo.getTypeInfo(Value.INTERVAL_SECOND), "INTERVAL_SECOND")

        /**
         * The INTERVAL YEAR TO MONTH data type.
         */
        @JvmField
        val INTERVAL_YEAR_TO_MONTH = H2Type(TypeInfo.TYPE_INTERVAL_YEAR_TO_MONTH, "INTERVAL_YEAR_TO_MONTH")

        /**
         * The INTERVAL DAY TO HOUR data type.
         */
        @JvmField
        val INTERVAL_DAY_TO_HOUR = H2Type(TypeInfo.getTypeInfo(Value.INTERVAL_DAY_TO_HOUR), "INTERVAL_DAY_TO_HOUR")

        /**
         * The INTERVAL DAY TO MINUTE data type.
         */
        @JvmField
        val INTERVAL_DAY_TO_MINUTE = H2Type(TypeInfo.getTypeInfo(Value.INTERVAL_DAY_TO_MINUTE), "INTERVAL_DAY_TO_MINUTE")

        /**
         * The INTERVAL DAY TO SECOND data type.
         */
        @JvmField
        val INTERVAL_DAY_TO_SECOND = H2Type(TypeInfo.TYPE_INTERVAL_DAY_TO_SECOND, "INTERVAL_DAY_TO_SECOND")

        /**
         * The INTERVAL HOUR TO MINUTE data type.
         */
        @JvmField
        val INTERVAL_HOUR_TO_MINUTE = H2Type( //
            TypeInfo.getTypeInfo(Value.INTERVAL_HOUR_TO_MINUTE), "INTERVAL_HOUR_TO_MINUTE"
        )

        /**
         * The INTERVAL HOUR TO SECOND data type.
         */
        @JvmField
        val INTERVAL_HOUR_TO_SECOND = H2Type(TypeInfo.TYPE_INTERVAL_HOUR_TO_SECOND, "INTERVAL_HOUR_TO_SECOND")

        /**
         * The INTERVAL MINUTE TO SECOND data type.
         */
        @JvmField
        val INTERVAL_MINUTE_TO_SECOND = H2Type(
            TypeInfo.getTypeInfo(Value.INTERVAL_MINUTE_TO_SECOND), "INTERVAL_MINUTE_TO_SECOND"
        )

        // Other JDBC

        /**
         * The JAVA_OBJECT data type.
         */
        @JvmField
        val JAVA_OBJECT = H2Type(TypeInfo.TYPE_JAVA_OBJECT, "JAVA_OBJECT")

        // Other non-standard

        /**
         * The ENUM data type.
         */
        @JvmField
        val ENUM = H2Type(TypeInfo.TYPE_ENUM_UNDEFINED, "ENUM")

        /**
         * The GEOMETRY data type.
         */
        @JvmField
        val GEOMETRY = H2Type(TypeInfo.TYPE_GEOMETRY, "GEOMETRY")

        /**
         * The JSON data type.
         */
        @JvmField
        val JSON = H2Type(TypeInfo.TYPE_JSON, "JSON")

        /**
         * The UUID data type.
         */
        @JvmField
        val UUID = H2Type(TypeInfo.TYPE_UUID, "UUID")

        // Collections

        // Use arrayOf() for ARRAY

        // Use row() for ROW

        /**
         * Returns ARRAY data type with the specified component type.
         *
         * @param componentType
         * the type of elements
         * @return ARRAY data type
         */
        @JvmStatic
        fun array(componentType: H2Type): H2Type {
            return H2Type(
                TypeInfo.getTypeInfo(Value.ARRAY, -1L, -1, componentType.typeInfo),
                "array(" + componentType.field + ')'
            )
        }

        /**
         * Returns ROW data type with specified types of fields and default names.
         *
         * @param fieldTypes
         * the type of fields
         * @return ROW data type
         */
        @JvmStatic
        fun row(vararg fieldTypes: H2Type): H2Type {
            val degree = fieldTypes.size
            val row = arrayOfNulls<Typed>(degree)
            val builder = StringBuilder("row(")
            for (i in 0 until degree) {
                val t = fieldTypes[i]
                row[i] = t.typeInfo
                if (i > 0) {
                    builder.append(", ")
                }
                builder.append(t.field)
            }
            return H2Type(
                TypeInfo.getTypeInfo(Value.ROW, -1L, -1, ExtTypeInfoRow(row)),
                builder.append(')').toString()
            )
        }
    }
}
