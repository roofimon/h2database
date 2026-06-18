/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.message

import java.math.BigDecimal
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicIntegerArray
import org.h2.api.ErrorCode
import org.h2.util.StringUtils

/**
 * The base class for objects that can print trace information about themselves.
 */
abstract class TraceObject {

    /**
     * The trace module used by this object.
     */
    @JvmField
    protected var trace: Trace? = null

    private var traceType = 0
    private var id = 0

    /**
     * Set the options to use when writing trace message.
     *
     * @param trace the trace object
     * @param type the trace object type
     * @param id the trace object id
     */
    protected open fun setTrace(trace: Trace?, type: Int, id: Int) {
        this.trace = trace
        this.traceType = type
        this.id = id
    }

    /**
     * INTERNAL
     * @return id
     */
    fun getTraceId(): Int {
        return id
    }

    /**
     * INTERNAL
     * @return object name
     */
    open fun getTraceObjectName(): String {
        return PREFIX[traceType] + id
    }

    /**
     * Check if the debug trace level is enabled.
     *
     * @return true if it is
     */
    protected fun isDebugEnabled(): Boolean {
        return trace!!.isDebugEnabled()
    }

    /**
     * Check if info trace level is enabled.
     *
     * @return true if it is
     */
    protected fun isInfoEnabled(): Boolean {
        return trace!!.isInfoEnabled()
    }

    /**
     * Write trace information as an assignment in the form
     * className prefixId = objectName.value.
     *
     * @param className the class name of the result
     * @param newType the prefix type
     * @param newId the trace object id of the created object
     * @param value the value to assign this new object to
     */
    protected fun debugCodeAssign(className: String?, newType: Int, newId: Int, value: String?) {
        if (trace!!.isDebugEnabled()) {
            trace!!.debugCode(className + ' ' + PREFIX[newType] + newId + " = " + getTraceObjectName() + '.' + value
                    + ';')
        }
    }

    /**
     * Write trace information as a method call in the form
     * objectName.methodName().
     *
     * @param methodName the method name
     */
    protected fun debugCodeCall(methodName: String?) {
        if (trace!!.isDebugEnabled()) {
            trace!!.debugCode(getTraceObjectName() + '.' + methodName + "();")
        }
    }

    /**
     * Write trace information as a method call in the form
     * objectName.methodName(param) where the parameter is formatted as a long
     * value.
     *
     * @param methodName the method name
     * @param param one single long parameter
     */
    protected fun debugCodeCall(methodName: String?, param: Long) {
        if (trace!!.isDebugEnabled()) {
            trace!!.debugCode(getTraceObjectName() + '.' + methodName + '(' + param + ");")
        }
    }

    /**
     * Write trace information as a method call in the form
     * objectName.methodName(param) where the parameter is formatted as a Java
     * string.
     *
     * @param methodName the method name
     * @param param one single string parameter
     */
    protected fun debugCodeCall(methodName: String?, param: String?) {
        if (trace!!.isDebugEnabled()) {
            trace!!.debugCode(getTraceObjectName() + '.' + methodName + '(' + quote(param) + ");")
        }
    }

    /**
     * Write trace information in the form objectName.text.
     *
     * @param text the trace text
     */
    protected fun debugCode(text: String?) {
        if (trace!!.isDebugEnabled()) {
            trace!!.debugCode(getTraceObjectName() + '.' + text + ';')
        }
    }

    /**
     * Log an exception and convert it to a SQL exception if required.
     *
     * @param ex the exception
     * @return the SQL exception object
     */
    protected open fun logAndConvert(ex: Throwable): SQLException {
        var e: SQLException? = null
        try {
            e = DbException.toSQLException(ex)
            if (trace == null) {
                DbException.traceThrowable(e)
            } else {
                val errorCode = e.errorCode
                if (errorCode >= 23000 && errorCode < 24000) {
                    trace!!.info(e, "exception")
                } else {
                    trace!!.error(e, "exception")
                }
            }
        } catch (another: Throwable) {
            if (e == null) {
                try {
                    e = SQLException("GeneralError", "HY000", ErrorCode.GENERAL_ERROR_1, ex)
                } catch (ignored: OutOfMemoryError) {
                    return SQL_OOME
                } catch (ignored: NoClassDefFoundError) {
                    return SQL_OOME
                }
            }
            e!!.addSuppressed(another)
        }
        return e!!
    }

    /**
     * Get a SQL exception meaning this feature is not supported.
     *
     * @param message the message
     * @return the SQL exception
     */
    protected fun unsupported(message: String?): SQLException {
        try {
            throw DbException.getUnsupportedException(message)
        } catch (e: Exception) {
            return logAndConvert(e)
        }
    }

    companion object {

        /**
         * The trace type id  for callable statements.
         */
        const val CALLABLE_STATEMENT = 0

        /**
         * The trace type id  for connections.
         */
        const val CONNECTION = 1

        /**
         * The trace type id  for database meta data objects.
         */
        const val DATABASE_META_DATA = 2

        /**
         * The trace type id  for prepared statements.
         */
        const val PREPARED_STATEMENT = 3

        /**
         * The trace type id  for result sets.
         */
        const val RESULT_SET = 4

        /**
         * The trace type id  for result set meta data objects.
         */
        const val RESULT_SET_META_DATA = 5

        /**
         * The trace type id  for savepoint objects.
         */
        const val SAVEPOINT = 6

        /**
         * The trace type id  for statements.
         */
        const val STATEMENT = 8

        /**
         * The trace type id  for blobs.
         */
        const val BLOB = 9

        /**
         * The trace type id  for clobs.
         */
        const val CLOB = 10

        /**
         * The trace type id  for parameter meta data objects.
         */
        const val PARAMETER_META_DATA = 11

        /**
         * The trace type id  for data sources.
         */
        const val DATA_SOURCE = 12

        /**
         * The trace type id  for XA data sources.
         */
        const val XA_DATA_SOURCE = 13

        /**
         * The trace type id  for transaction ids.
         */
        const val XID = 15

        /**
         * The trace type id  for array objects.
         */
        const val ARRAY = 16

        /**
         * The trace type id  for SQLXML objects.
         */
        const val SQLXML = 17

        private const val LAST = SQLXML + 1
        private val ID = AtomicIntegerArray(LAST)

        private val PREFIX = arrayOf("call", "conn", "dbMeta", "prep",
                "rs", "rsMeta", "sp", "ex", "stat", "blob", "clob", "pMeta", "ds",
                "xads", "xares", "xid", "ar", "sqlxml")

        private val SQL_OOME: SQLException = DbException.SQL_OOME

        /**
         * Get the next trace object id for this object type.
         *
         * @param type the object type
         * @return the new trace object id
         */
        @JvmStatic
        protected fun getNextId(type: Int): Int {
            return ID.getAndIncrement(type)
        }

        /**
         * Format a string as a Java string literal.
         *
         * @param s the string to convert
         * @return the Java string literal
         */
        @JvmStatic
        protected fun quote(s: String?): String {
            return StringUtils.quoteJavaString(s)
        }

        /**
         * Format a time to the Java source code that represents this object.
         *
         * @param x the time to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteTime(x: java.sql.Time?): String {
            if (x == null) {
                return "null"
            }
            return "Time.valueOf(\"" + x.toString() + "\")"
        }

        /**
         * Format a timestamp to the Java source code that represents this object.
         *
         * @param x the timestamp to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteTimestamp(x: java.sql.Timestamp?): String {
            if (x == null) {
                return "null"
            }
            return "Timestamp.valueOf(\"" + x.toString() + "\")"
        }

        /**
         * Format a date to the Java source code that represents this object.
         *
         * @param x the date to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteDate(x: java.sql.Date?): String {
            if (x == null) {
                return "null"
            }
            return "Date.valueOf(\"" + x.toString() + "\")"
        }

        /**
         * Format a big decimal to the Java source code that represents this object.
         *
         * @param x the big decimal to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteBigDecimal(x: BigDecimal?): String {
            if (x == null) {
                return "null"
            }
            return "new BigDecimal(\"" + x.toString() + "\")"
        }

        /**
         * Format a byte array to the Java source code that represents this object.
         *
         * @param x the byte array to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteBytes(x: ByteArray?): String {
            if (x == null) {
                return "null"
            }
            val builder = StringBuilder(x.size * 2 + 45)
                    .append("org.h2.util.StringUtils.convertHexToBytes(\"")
            return StringUtils.convertBytesToHex(builder, x).append("\")").toString()
        }

        /**
         * Format a string array to the Java source code that represents this
         * object.
         *
         * @param s the string array to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteArray(s: Array<String?>?): String {
            return StringUtils.quoteJavaStringArray(s)
        }

        /**
         * Format an int array to the Java source code that represents this object.
         *
         * @param s the int array to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteIntArray(s: IntArray?): String {
            return StringUtils.quoteJavaIntArray(s)
        }

        /**
         * Format a map to the Java source code that represents this object.
         *
         * @param map the map to convert
         * @return the Java source code
         */
        @JvmStatic
        protected fun quoteMap(map: Map<String?, Class<*>?>?): String {
            if (map == null) {
                return "null"
            }
            if (map.size == 0) {
                return "new Map()"
            }
            return "new Map() /* " + map.toString() + " */"
        }
    }
}
