/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.math.BigDecimal

import org.h2.api.ErrorCode
import org.h2.engine.Constants
import org.h2.message.DbException

/**
 * Base class for BigDecimal-based values.
 */
abstract class ValueBigDecimalBase(value: BigDecimal?) : Value() {

    @JvmField
    val value: BigDecimal?

    @JvmField
    var type: TypeInfo? = null

    init {
        if (value != null) {
            if (value.javaClass != BigDecimal::class.java) {
                throw DbException.get(ErrorCode.INVALID_CLASS_2, BigDecimal::class.java.name,
                        value.javaClass.name)
            }
            val length = value.precision()
            if (length > Constants.MAX_NUMERIC_PRECISION) {
                throw DbException.getValueTooLongException(Value.getTypeName(getValueType()), value.toString(),
                        length.toLong())
            }
        }
        this.value = value
    }

}
