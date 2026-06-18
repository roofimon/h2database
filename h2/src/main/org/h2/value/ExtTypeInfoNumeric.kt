/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

/**
 * Extended parameters of the NUMERIC data type.
 */
class ExtTypeInfoNumeric private constructor() : ExtTypeInfo() {

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return builder.append("DECIMAL")
    }

    companion object {
        /**
         * DECIMAL data type.
         */
        @JvmField
        val DECIMAL: ExtTypeInfoNumeric = ExtTypeInfoNumeric()
    }

}
