/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

/**
 * Metadata of a column.
 *
 * Notice: [equals] and [hashCode] use only the [name] field.
 */
class SimpleColumnInfo(
    /** Name of the column. */
    @JvmField val name: String,
    /** Type of the column, see [java.sql.Types]. */
    @JvmField val type: Int,
    /** Type name of the column. */
    @JvmField val typeName: String?,
    /** Precision of the column. */
    @JvmField val precision: Int,
    /** Scale of the column. */
    @JvmField val scale: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        return name == (other as SimpleColumnInfo).name
    }

    override fun hashCode(): Int = name.hashCode()
}
