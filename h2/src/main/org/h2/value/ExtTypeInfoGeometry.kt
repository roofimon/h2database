/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.util.Objects

import org.h2.util.geometry.EWKTUtils

/**
 * Extended parameters of the GEOMETRY data type.
 *
 * @param type
 *            the type and dimension system of geometries, or 0 if not
 *            constrained
 * @param srid
 *            the SRID of geometries, or {@code null} if not constrained
 */
class ExtTypeInfoGeometry(private val type: Int, private val srid: Int?) : ExtTypeInfo() {

    override fun hashCode(): Int {
        return 31 * (if (srid == null) 0 else srid.hashCode()) + type
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || obj.javaClass != ExtTypeInfoGeometry::class.java) {
            return false
        }
        val other = obj as ExtTypeInfoGeometry
        return type == other.type && Objects.equals(srid, other.srid)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        return toSQL(builder, type, srid)
    }

    /**
     * Returns the type and dimension system of geometries.
     *
     * @return the type and dimension system of geometries, or 0 if not
     *         constrained
     */
    fun getType(): Int {
        return type
    }

    /**
     * Returns the SRID of geometries.
     *
     * @return the SRID of geometries, or {@code null} if not constrained
     */
    fun getSrid(): Int? {
        return srid
    }

    companion object {
        @JvmStatic
        fun toSQL(builder: StringBuilder, type: Int, srid: Int?): StringBuilder {
            if (type == 0 && srid == null) {
                return builder
            }
            builder.append('(')
            if (type == 0) {
                builder.append("GEOMETRY")
            } else {
                EWKTUtils.formatGeometryTypeAndDimensionSystem(builder, type)
            }
            if (srid != null) {
                builder.append(", ").append(srid.toInt())
            }
            return builder.append(')')
        }
    }

}
