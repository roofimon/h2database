/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.message

import java.text.MessageFormat
import java.util.ArrayList

import org.h2.expression.ParameterInterface
import org.h2.util.StringUtils

/**
 * This class represents a trace module.
 */
class Trace {

    private val traceWriter: TraceWriter
    private val module: String
    private val lineSeparator: String
    private var traceLevel = TraceSystem.PARENT

    internal constructor(traceWriter: TraceWriter, moduleId: Int) : this(traceWriter, MODULE_NAMES[moduleId])

    internal constructor(traceWriter: TraceWriter, module: String) {
        this.traceWriter = traceWriter
        this.module = module
        this.lineSeparator = System.lineSeparator()
    }

    /**
     * Set the trace level of this component. This setting overrides the parent
     * trace level.
     *
     * @param level the new level
     */
    fun setLevel(level: Int) {
        this.traceLevel = level
    }

    private fun isEnabled(level: Int): Boolean {
        if (this.traceLevel == TraceSystem.PARENT) {
            return traceWriter.isEnabled(level)
        }
        return level <= this.traceLevel
    }

    /**
     * Check if the trace level is equal or higher than INFO.
     *
     * @return true if it is
     */
    fun isInfoEnabled(): Boolean {
        return isEnabled(TraceSystem.INFO)
    }

    /**
     * Check if the trace level is equal or higher than DEBUG.
     *
     * @return true if it is
     */
    fun isDebugEnabled(): Boolean {
        return isEnabled(TraceSystem.DEBUG)
    }

    /**
     * Write a message with trace level ERROR to the trace system.
     *
     * @param t the exception
     * @param s the message
     */
    fun error(t: Throwable?, s: String) {
        if (isEnabled(TraceSystem.ERROR)) {
            traceWriter.write(TraceSystem.ERROR, module, s, t)
        }
    }

    /**
     * Write a message with trace level ERROR to the trace system.
     *
     * @param t the exception
     * @param s the message
     * @param params the parameters
     */
    fun error(t: Throwable?, s: String, vararg params: Any?) {
        if (isEnabled(TraceSystem.ERROR)) {
            val message = MessageFormat.format(s, *params)
            traceWriter.write(TraceSystem.ERROR, module, message, t)
        }
    }

    /**
     * Write a message with trace level INFO to the trace system.
     *
     * @param s the message
     */
    fun info(s: String) {
        if (isEnabled(TraceSystem.INFO)) {
            traceWriter.write(TraceSystem.INFO, module, s, null)
        }
    }

    /**
     * Write a message with trace level INFO to the trace system.
     *
     * @param s the message
     * @param params the parameters
     */
    fun info(s: String, vararg params: Any?) {
        if (isEnabled(TraceSystem.INFO)) {
            val message = MessageFormat.format(s, *params)
            traceWriter.write(TraceSystem.INFO, module, message, null)
        }
    }

    /**
     * Write a message with trace level INFO to the trace system.
     *
     * @param t the exception
     * @param s the message
     */
    fun info(t: Throwable?, s: String) {
        if (isEnabled(TraceSystem.INFO)) {
            traceWriter.write(TraceSystem.INFO, module, s, t)
        }
    }

    /**
     * Write a SQL statement with trace level INFO to the trace system.
     *
     * @param sql the SQL statement
     * @param params the parameters used, in the for {1:...}
     * @param count the update count
     * @param time the time it took to run the statement in ms
     */
    fun infoSQL(sql: String, params: String, count: Long, time: Long) {
        if (!isEnabled(TraceSystem.INFO)) {
            return
        }
        val buff = StringBuilder(sql.length + params.length + 20)
        buff.append(lineSeparator).append("/*SQL")
        var space = false
        if (params.length > 0) {
            // This looks like a bug, but it is intentional:
            // If there are no parameters, the SQL statement is
            // the rest of the line. If there are parameters, they
            // are appended at the end of the line. Knowing the size
            // of the statement simplifies separating the SQL statement
            // from the parameters (no need to parse).
            space = true
            buff.append(" l:").append(sql.length)
        }
        if (count > 0) {
            space = true
            buff.append(" #:").append(count)
        }
        if (time > 0) {
            space = true
            buff.append(" t:").append(time)
        }
        if (!space) {
            buff.append(' ')
        }
        buff.append("*/")
        StringUtils.javaEncode(sql, buff, false)
        StringUtils.javaEncode(params, buff, false)
        buff.append(';')
        val statement = buff.toString()
        traceWriter.write(TraceSystem.INFO, module, statement, null)
    }

    /**
     * Write a message with trace level DEBUG to the trace system.
     *
     * @param s the message
     * @param params the parameters
     */
    fun debug(s: String, vararg params: Any?) {
        if (isEnabled(TraceSystem.DEBUG)) {
            val message = MessageFormat.format(s, *params)
            traceWriter.write(TraceSystem.DEBUG, module, message, null)
        }
    }

    /**
     * Write a message with trace level DEBUG to the trace system.
     *
     * @param s the message
     */
    fun debug(s: String) {
        if (isEnabled(TraceSystem.DEBUG)) {
            traceWriter.write(TraceSystem.DEBUG, module, s, null)
        }
    }

    /**
     * Write a message with trace level DEBUG to the trace system.
     * @param t the exception
     * @param s the message
     */
    fun debug(t: Throwable?, s: String) {
        if (isEnabled(TraceSystem.DEBUG)) {
            traceWriter.write(TraceSystem.DEBUG, module, s, t)
        }
    }

    /**
     * Write Java source code with trace level INFO to the trace system.
     *
     * @param java the source code
     */
    fun infoCode(java: String) {
        if (isEnabled(TraceSystem.INFO)) {
            traceWriter.write(
                TraceSystem.INFO, module, lineSeparator +
                    "/**/" + java, null
            )
        }
    }

    /**
     * Write Java source code with trace level DEBUG to the trace system.
     *
     * @param java the source code
     */
    fun debugCode(java: String) {
        if (isEnabled(TraceSystem.DEBUG)) {
            traceWriter.write(
                TraceSystem.DEBUG, module, lineSeparator +
                    "/**/" + java, null
            )
        }
    }

    companion object {

        /**
         * The trace module id for commands.
         */
        const val COMMAND = 0

        /**
         * The trace module id for constraints.
         */
        const val CONSTRAINT = 1

        /**
         * The trace module id for databases.
         */
        const val DATABASE = 2

        /**
         * The trace module id for functions.
         */
        const val FUNCTION = 3

        /**
         * The trace module id for file locks.
         */
        const val FILE_LOCK = 4

        /**
         * The trace module id for indexes.
         */
        const val INDEX = 5

        /**
         * The trace module id for the JDBC API.
         */
        const val JDBC = 6

        /**
         * The trace module id for locks.
         */
        const val LOCK = 7

        /**
         * The trace module id for schemas.
         */
        const val SCHEMA = 8

        /**
         * The trace module id for sequences.
         */
        const val SEQUENCE = 9

        /**
         * The trace module id for settings.
         */
        const val SETTING = 10

        /**
         * The trace module id for tables.
         */
        const val TABLE = 11

        /**
         * The trace module id for triggers.
         */
        const val TRIGGER = 12

        /**
         * The trace module id for users.
         */
        const val USER = 13

        /**
         * The trace module id for the JDBCX API
         */
        const val JDBCX = 14

        /**
         * Module names by their ids as array indexes.
         */
        @JvmField
        internal val MODULE_NAMES = arrayOf(
            "command",
            "constraint",
            "database",
            "function",
            "fileLock",
            "index",
            "jdbc",
            "lock",
            "schema",
            "sequence",
            "setting",
            "table",
            "trigger",
            "user",
            "JDBCX"
        )

        /**
         * Format the parameter list.
         *
         * @param parameters the parameter list
         * @return the formatted text
         */
        @JvmStatic
        fun formatParams(parameters: ArrayList<out ParameterInterface>): String {
            if (parameters.isEmpty()) {
                return ""
            }
            val builder = StringBuilder()
            var i = 0
            for (p in parameters) {
                if (p.isValueSet()) {
                    builder.append(if (i == 0) " {" else ", ")
                        .append(++i).append(": ")
                        .append(p.getParamValue().getTraceSQL())
                }
            }
            if (i != 0) {
                builder.append('}')
            }
            return builder.toString()
        }
    }
}
