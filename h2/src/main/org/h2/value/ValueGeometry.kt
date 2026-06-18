/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import org.h2.api.ErrorCode
import org.h2.message.DbException
import org.h2.util.Bits.INT_VH_BE
import org.h2.util.HasSQL.Companion.ADD_PLAN_INFORMATION
import org.h2.util.HasSQL.Companion.DEFAULT_SQL_FLAGS
import org.h2.util.MathUtils
import org.h2.util.StringUtils
import org.h2.util.geometry.EWKBUtils
import org.h2.util.geometry.EWKBUtils.EWKB_SRID
import org.h2.util.geometry.EWKTUtils
import org.h2.util.geometry.EWKTUtils.EWKTTarget
import org.h2.util.geometry.GeometryUtils
import org.h2.util.geometry.GeometryUtils.EnvelopeTarget
import org.h2.util.geometry.JTSUtils
import org.locationtech.jts.geom.Geometry

/**
 * Implementation of the GEOMETRY data type.
 *
 * @author Thomas Mueller
 * @author Noel Grandin
 * @author Nicolas Fortin, Atelier SIG, IRSTV FR CNRS 24888
 */
class ValueGeometry
/**
 * Create a new geometry object.
 *
 * @param bytes the EWKB bytes
 * @param envelope the envelope
 */
private constructor(bytes: ByteArray, envelope: DoubleArray) : ValueBytesBase(bytes) {

    /**
     * Geometry type and dimension system in OGC geometry code format (type +
     * dimensionSystem * 1000).
     */
    private val typeAndDimensionSystem: Int

    /**
     * Spatial reference system identifier.
     */
    private val srid: Int

    /**
     * The envelope of the value. Calculated only on request.
     */
    private var envelope: DoubleArray

    /**
     * The value. Converted from WKB only on request as conversion from/to WKB
     * cost a significant amount of CPU cycles.
     */
    private var geometry: Any? = null

    init {
        if (bytes.size < 9 || bytes[0].toInt() != 0) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, StringUtils.convertBytesToHex(bytes))
        }
        this.value = bytes
        this.envelope = envelope
        val t = INT_VH_BE.get(bytes, 1) as Int
        srid = if (t and EWKB_SRID != 0) INT_VH_BE.get(bytes, 5) as Int else 0
        typeAndDimensionSystem = (t and 0xffff) % 1_000 + EWKBUtils.type2dimensionSystem(t) * 1_000
    }

    /**
     * Get a copy of geometry object. Geometry object is mutable. The returned
     * object is therefore copied before returning.
     *
     * @return a copy of the geometry object
     */
    fun getGeometry(): Geometry {
        if (geometry == null) {
            try {
                geometry = JTSUtils.ewkb2geometry(value, getDimensionSystem())
            } catch (ex: RuntimeException) {
                throw DbException.convert(ex)
            }
        }
        return (geometry as Geometry).copy()
    }

    /**
     * Returns geometry type and dimension system in OGC geometry code format
     * (type + dimensionSystem * 1000).
     *
     * @return geometry type and dimension system
     */
    fun getTypeAndDimensionSystem(): Int {
        return typeAndDimensionSystem
    }

    /**
     * Returns geometry type.
     *
     * @return geometry type and dimension system
     */
    fun getGeometryType(): Int {
        return typeAndDimensionSystem % 1_000
    }

    /**
     * Return a minimal dimension system that can be used for this geometry.
     *
     * @return dimension system
     */
    fun getDimensionSystem(): Int {
        return typeAndDimensionSystem / 1_000
    }

    /**
     * Return a spatial reference system identifier.
     *
     * @return spatial reference system identifier
     */
    fun getSRID(): Int {
        return srid
    }

    /**
     * Return an envelope of this geometry. Do not modify the returned value.
     *
     * @return envelope of this geometry
     */
    fun getEnvelopeNoCopy(): DoubleArray {
        if (envelope === UNKNOWN_ENVELOPE) {
            val target = EnvelopeTarget()
            EWKBUtils.parseEWKB(value, target)
            envelope = target.getEnvelope()
        }
        return envelope
    }

    /**
     * Test if this geometry envelope intersects with the other geometry
     * envelope.
     *
     * @param r the other geometry
     * @return true if the two overlap
     */
    fun intersectsBoundingBox(r: ValueGeometry): Boolean {
        return GeometryUtils.intersects(getEnvelopeNoCopy(), r.getEnvelopeNoCopy())
    }

    /**
     * Get the union.
     *
     * @param r the other geometry
     * @return the union of this geometry envelope and another geometry envelope
     */
    fun getEnvelopeUnion(r: ValueGeometry): Value {
        return fromEnvelope(GeometryUtils.union(getEnvelopeNoCopy(), r.getEnvelopeNoCopy()))
    }

    override fun getType(): TypeInfo {
        return TypeInfo.TYPE_GEOMETRY
    }

    override fun getValueType(): Int {
        return GEOMETRY
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        builder.append("GEOMETRY ")
        if (sqlFlags and ADD_PLAN_INFORMATION != 0) {
            EWKBUtils.parseEWKB(value, EWKTTarget(builder.append('\''), getDimensionSystem()))
            builder.append('\'')
        } else {
            super.getSQL(builder, DEFAULT_SQL_FLAGS)
        }
        return builder
    }

    override fun getString(): String {
        return EWKTUtils.ewkb2ewkt(value, getDimensionSystem())
    }

    override fun getMemory(): Int {
        return MathUtils.convertLongToInt(value.size * 20L + 24)
    }

    companion object {

        private val UNKNOWN_ENVELOPE = DoubleArray(0)

        /**
         * Get or create a geometry value for the given geometry.
         *
         * @param o the geometry object (of type
         *            org.locationtech.jts.geom.Geometry)
         * @return the value
         */
        @JvmStatic
        fun getFromGeometry(o: Any?): ValueGeometry {
            try {
                val g = o as Geometry
                return Value.cache(ValueGeometry(JTSUtils.geometry2ewkb(g), UNKNOWN_ENVELOPE)) as ValueGeometry
            } catch (ex: RuntimeException) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, o.toString())
            }
        }

        /**
         * Get or create a geometry value for the given geometry.
         *
         * @param s the WKT or EWKT representation of the geometry
         * @return the value
         */
        @JvmStatic
        fun get(s: String): ValueGeometry {
            try {
                return Value.cache(ValueGeometry(EWKTUtils.ewkt2ewkb(s), UNKNOWN_ENVELOPE)) as ValueGeometry
            } catch (ex: RuntimeException) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, s)
            }
        }

        /**
         * Get or create a geometry value for the given internal EWKB representation.
         *
         * @param bytes the WKB representation of the geometry. May not be modified.
         * @return the value
         */
        @JvmStatic
        fun get(bytes: ByteArray): ValueGeometry {
            return Value.cache(ValueGeometry(bytes, UNKNOWN_ENVELOPE)) as ValueGeometry
        }

        /**
         * Get or create a geometry value for the given EWKB value.
         *
         * @param bytes the WKB representation of the geometry
         * @return the value
         */
        @JvmStatic
        fun getFromEWKB(bytes: ByteArray): ValueGeometry {
            try {
                return Value.cache(ValueGeometry(EWKBUtils.ewkb2ewkb(bytes), UNKNOWN_ENVELOPE)) as ValueGeometry
            } catch (ex: RuntimeException) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, StringUtils.convertBytesToHex(bytes))
            }
        }

        /**
         * Creates a geometry value for the given envelope.
         *
         * @param envelope envelope. May not be modified.
         * @return the value
         */
        @JvmStatic
        fun fromEnvelope(envelope: DoubleArray?): Value {
            return if (envelope != null)
                Value.cache(ValueGeometry(EWKBUtils.envelope2wkb(envelope), envelope))
            else
                ValueNull.INSTANCE
        }
    }
}
