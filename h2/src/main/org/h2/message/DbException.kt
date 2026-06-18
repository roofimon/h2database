/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.message

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.PrintStream
import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.sql.SQLException
import java.text.MessageFormat
import java.util.Locale
import java.util.Properties

import org.h2.api.ErrorCode.Companion.ACCESS_DENIED_TO_CLASS_1
import org.h2.api.ErrorCode.Companion.ADMIN_RIGHTS_REQUIRED
import org.h2.api.ErrorCode.Companion.AGGREGATE_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.AMBIGUOUS_COLUMN_NAME_1
import org.h2.api.ErrorCode.Companion.AUTHENTICATOR_NOT_AVAILABLE
import org.h2.api.ErrorCode.Companion.CANNOT_CHANGE_SETTING_WHEN_OPEN_1
import org.h2.api.ErrorCode.Companion.CANNOT_DROP_2
import org.h2.api.ErrorCode.Companion.CANNOT_DROP_CURRENT_USER
import org.h2.api.ErrorCode.Companion.CANNOT_DROP_LAST_COLUMN
import org.h2.api.ErrorCode.Companion.CANNOT_DROP_TABLE_1
import org.h2.api.ErrorCode.Companion.CANNOT_MIX_INDEXED_AND_UNINDEXED_PARAMS
import org.h2.api.ErrorCode.Companion.CANNOT_TRUNCATE_1
import org.h2.api.ErrorCode.Companion.CAN_ONLY_ASSIGN_TO_VARIABLE_1
import org.h2.api.ErrorCode.Companion.CLASS_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.CLUSTER_ERROR_DATABASE_RUNS_ALONE
import org.h2.api.ErrorCode.Companion.CLUSTER_ERROR_DATABASE_RUNS_CLUSTERED_1
import org.h2.api.ErrorCode.Companion.COLLATION_CHANGE_WITH_DATA_TABLE_1
import org.h2.api.ErrorCode.Companion.COLUMN_ALIAS_IS_NOT_SPECIFIED_1
import org.h2.api.ErrorCode.Companion.COLUMN_CONTAINS_NULL_VALUES_1
import org.h2.api.ErrorCode.Companion.COLUMN_IS_PART_OF_INDEX_1
import org.h2.api.ErrorCode.Companion.COLUMN_IS_REFERENCED_1
import org.h2.api.ErrorCode.Companion.COLUMN_MUST_NOT_BE_NULLABLE_1
import org.h2.api.ErrorCode.Companion.COMMIT_ROLLBACK_NOT_ALLOWED
import org.h2.api.ErrorCode.Companion.COMPRESSION_ERROR
import org.h2.api.ErrorCode.Companion.CONCURRENT_UPDATE_1
import org.h2.api.ErrorCode.Companion.CONNECTION_BROKEN_1
import org.h2.api.ErrorCode.Companion.CONSTANT_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.CONSTANT_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.CONSTRAINT_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.CONSTRAINT_IS_USED_BY_CONSTRAINT_2
import org.h2.api.ErrorCode.Companion.CONSTRAINT_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.CURRENT_SEQUENCE_VALUE_IS_NOT_DEFINED_IN_SESSION_1
import org.h2.api.ErrorCode.Companion.DATABASE_ALREADY_OPEN_1
import org.h2.api.ErrorCode.Companion.DATABASE_CALLED_AT_SHUTDOWN
import org.h2.api.ErrorCode.Companion.DATABASE_IS_CLOSED
import org.h2.api.ErrorCode.Companion.DATABASE_IS_IN_EXCLUSIVE_MODE
import org.h2.api.ErrorCode.Companion.DATABASE_IS_NOT_PERSISTENT
import org.h2.api.ErrorCode.Companion.DATABASE_IS_READ_ONLY
import org.h2.api.ErrorCode.Companion.DATABASE_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.DATABASE_NOT_FOUND_WITH_IF_EXISTS_1
import org.h2.api.ErrorCode.Companion.DESERIALIZATION_FAILED_1
import org.h2.api.ErrorCode.Companion.DOMAIN_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.DOMAIN_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.DRIVER_VERSION_ERROR_2
import org.h2.api.ErrorCode.Companion.DUPLICATE_PROPERTY_1
import org.h2.api.ErrorCode.Companion.ERROR_ACCESSING_LINKED_TABLE_2
import org.h2.api.ErrorCode.Companion.ERROR_CREATING_TRIGGER_OBJECT_3
import org.h2.api.ErrorCode.Companion.ERROR_EXECUTING_TRIGGER_3
import org.h2.api.ErrorCode.Companion.ERROR_SETTING_DATABASE_EVENT_LISTENER_2
import org.h2.api.ErrorCode.Companion.EXCEPTION_IN_FUNCTION_1
import org.h2.api.ErrorCode.Companion.EXCEPTION_OPENING_PORT_2
import org.h2.api.ErrorCode.Companion.FEATURE_NOT_SUPPORTED_1
import org.h2.api.ErrorCode.Companion.FILE_CORRUPTED_1
import org.h2.api.ErrorCode.Companion.FILE_CREATION_FAILED_1
import org.h2.api.ErrorCode.Companion.FILE_DELETE_FAILED_1
import org.h2.api.ErrorCode.Companion.FILE_ENCRYPTION_ERROR_1
import org.h2.api.ErrorCode.Companion.FILE_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.FILE_RENAME_FAILED_2
import org.h2.api.ErrorCode.Companion.FILE_VERSION_ERROR_1
import org.h2.api.ErrorCode.Companion.FOR_UPDATE_IS_NOT_ALLOWED_IN_DISTINCT_OR_GROUPED_SELECT
import org.h2.api.ErrorCode.Companion.FUNCTION_ALIAS_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.FUNCTION_ALIAS_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.FUNCTION_MUST_RETURN_RESULT_SET_1
import org.h2.api.ErrorCode.Companion.FUNCTION_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.GENERAL_ERROR_1
import org.h2.api.ErrorCode.Companion.GENERATED_COLUMN_CANNOT_BE_ASSIGNED_1
import org.h2.api.ErrorCode.Companion.GENERATED_COLUMN_CANNOT_BE_UPDATABLE_BY_CONSTRAINT_2
import org.h2.api.ErrorCode.Companion.GROUP_BY_NOT_IN_THE_RESULT
import org.h2.api.ErrorCode.Companion.HEX_STRING_ODD_1
import org.h2.api.ErrorCode.Companion.HEX_STRING_WRONG_1
import org.h2.api.ErrorCode.Companion.INDEX_BELONGS_TO_CONSTRAINT_2
import org.h2.api.ErrorCode.Companion.INVALID_CLASS_2
import org.h2.api.ErrorCode.Companion.INVALID_DATABASE_NAME_1
import org.h2.api.ErrorCode.Companion.INVALID_TO_CHAR_FORMAT
import org.h2.api.ErrorCode.Companion.INVALID_TO_DATE_FORMAT
import org.h2.api.ErrorCode.Companion.INVALID_TRIGGER_FLAGS_1
import org.h2.api.ErrorCode.Companion.INVALID_USE_OF_AGGREGATE_FUNCTION_1
import org.h2.api.ErrorCode.Companion.INVALID_VALUE_2
import org.h2.api.ErrorCode.Companion.INVALID_VALUE_PRECISION
import org.h2.api.ErrorCode.Companion.INVALID_VALUE_SCALE
import org.h2.api.ErrorCode.Companion.IO_EXCEPTION_1
import org.h2.api.ErrorCode.Companion.IO_EXCEPTION_2
import org.h2.api.ErrorCode.Companion.JAVA_OBJECT_SERIALIZER_CHANGE_WITH_DATA_TABLE
import org.h2.api.ErrorCode.Companion.LITERALS_ARE_NOT_ALLOWED
import org.h2.api.ErrorCode.Companion.LOB_CLOSED_ON_TIMEOUT_1
import org.h2.api.ErrorCode.Companion.LOCK_TIMEOUT_1
import org.h2.api.ErrorCode.Companion.METHODS_MUST_HAVE_DIFFERENT_PARAMETER_COUNTS_2
import org.h2.api.ErrorCode.Companion.METHOD_DISABLED_ON_AUTOCOMMIT_TRUE
import org.h2.api.ErrorCode.Companion.METHOD_NOT_ALLOWED_FOR_PREPARED_STATEMENT
import org.h2.api.ErrorCode.Companion.METHOD_NOT_ALLOWED_FOR_QUERY
import org.h2.api.ErrorCode.Companion.METHOD_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.METHOD_ONLY_ALLOWED_FOR_QUERY
import org.h2.api.ErrorCode.Companion.MUST_GROUP_BY_COLUMN_1
import org.h2.api.ErrorCode.Companion.NOT_ENOUGH_RIGHTS_FOR_1
import org.h2.api.ErrorCode.Companion.NOT_ON_UPDATABLE_ROW
import org.h2.api.ErrorCode.Companion.OBJECT_CLOSED
import org.h2.api.ErrorCode.Companion.ORDER_BY_NOT_IN_RESULT
import org.h2.api.ErrorCode.Companion.OUT_OF_MEMORY
import org.h2.api.ErrorCode.Companion.PARAMETER_NOT_SET_1
import org.h2.api.ErrorCode.Companion.PARSE_ERROR_1
import org.h2.api.ErrorCode.Companion.PUBLIC_STATIC_JAVA_METHOD_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.REMOTE_CONNECTION_NOT_ALLOWED
import org.h2.api.ErrorCode.Companion.REMOTE_DATABASE_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.RESULT_SET_NOT_SCROLLABLE
import org.h2.api.ErrorCode.Companion.RESULT_SET_NOT_UPDATABLE
import org.h2.api.ErrorCode.Companion.RESULT_SET_READONLY
import org.h2.api.ErrorCode.Companion.ROLES_AND_RIGHT_CANNOT_BE_MIXED
import org.h2.api.ErrorCode.Companion.ROLE_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.ROLE_ALREADY_GRANTED_1
import org.h2.api.ErrorCode.Companion.ROLE_CAN_NOT_BE_DROPPED_1
import org.h2.api.ErrorCode.Companion.ROLE_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.ROW_NOT_FOUND_IN_PRIMARY_INDEX
import org.h2.api.ErrorCode.Companion.ROW_NOT_FOUND_WHEN_DELETING_1
import org.h2.api.ErrorCode.Companion.SAVEPOINT_IS_INVALID_1
import org.h2.api.ErrorCode.Companion.SAVEPOINT_IS_NAMED
import org.h2.api.ErrorCode.Companion.SAVEPOINT_IS_UNNAMED
import org.h2.api.ErrorCode.Companion.SCALAR_SUBQUERY_CONTAINS_MORE_THAN_ONE_ROW
import org.h2.api.ErrorCode.Companion.SCHEMA_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.SCHEMA_CAN_NOT_BE_DROPPED_1
import org.h2.api.ErrorCode.Companion.SCHEMA_NAME_MUST_MATCH
import org.h2.api.ErrorCode.Companion.SCHEMA_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.SECOND_PRIMARY_KEY
import org.h2.api.ErrorCode.Companion.SEQUENCE_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.SEQUENCE_ATTRIBUTES_INVALID_7
import org.h2.api.ErrorCode.Companion.SEQUENCE_BELONGS_TO_A_TABLE_1
import org.h2.api.ErrorCode.Companion.SEQUENCE_EXHAUSTED
import org.h2.api.ErrorCode.Companion.SEQUENCE_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.SERIALIZATION_FAILED_1
import org.h2.api.ErrorCode.Companion.STATEMENT_WAS_CANCELED
import org.h2.api.ErrorCode.Companion.STEP_SIZE_MUST_NOT_BE_ZERO
import org.h2.api.ErrorCode.Companion.STRING_FORMAT_ERROR_1
import org.h2.api.ErrorCode.Companion.SUBQUERY_IS_NOT_SINGLE_COLUMN
import org.h2.api.ErrorCode.Companion.SUM_OR_AVG_ON_WRONG_DATATYPE_1
import org.h2.api.ErrorCode.Companion.SYNTAX_ERROR_1
import org.h2.api.ErrorCode.Companion.SYNTAX_ERROR_2
import org.h2.api.ErrorCode.Companion.TRACE_CONNECTION_NOT_CLOSED
import org.h2.api.ErrorCode.Companion.TRACE_FILE_ERROR_2
import org.h2.api.ErrorCode.Companion.TRANSACTION_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.TRIGGER_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.TRIGGER_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.TYPES_ARE_NOT_COMPARABLE_2
import org.h2.api.ErrorCode.Companion.UNCOMPARABLE_REFERENCED_COLUMN_2
import org.h2.api.ErrorCode.Companion.UNKNOWN_DATA_TYPE_1
import org.h2.api.ErrorCode.Companion.UNKNOWN_MODE_1
import org.h2.api.ErrorCode.Companion.UNSUPPORTED_CIPHER
import org.h2.api.ErrorCode.Companion.UNSUPPORTED_COMPRESSION_ALGORITHM_1
import org.h2.api.ErrorCode.Companion.UNSUPPORTED_COMPRESSION_OPTIONS_1
import org.h2.api.ErrorCode.Companion.UNSUPPORTED_LOCK_METHOD_1
import org.h2.api.ErrorCode.Companion.UNSUPPORTED_SETTING_1
import org.h2.api.ErrorCode.Companion.UNSUPPORTED_SETTING_COMBINATION
import org.h2.api.ErrorCode.Companion.URL_FORMAT_ERROR_2
import org.h2.api.ErrorCode.Companion.URL_RELATIVE_TO_CWD
import org.h2.api.ErrorCode.Companion.USER_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.USER_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.USER_OR_ROLE_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.VALUE_TOO_LONG_2
import org.h2.api.ErrorCode.Companion.VIEW_ALREADY_EXISTS_1
import org.h2.api.ErrorCode.Companion.VIEW_IS_INVALID_2
import org.h2.api.ErrorCode.Companion.VIEW_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.WINDOW_NOT_FOUND_1
import org.h2.api.ErrorCode.Companion.WITH_TIES_WITHOUT_ORDER_BY
import org.h2.api.ErrorCode.Companion.WRONG_PASSWORD_FORMAT
import org.h2.api.ErrorCode.Companion.WRONG_XID_FORMAT_1
import org.h2.api.ErrorCode.Companion.getState
import org.h2.engine.Constants
import org.h2.jdbc.JdbcException
import org.h2.jdbc.JdbcSQLDataException
import org.h2.jdbc.JdbcSQLException
import org.h2.jdbc.JdbcSQLFeatureNotSupportedException
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException
import org.h2.jdbc.JdbcSQLInvalidAuthorizationSpecException
import org.h2.jdbc.JdbcSQLNonTransientConnectionException
import org.h2.jdbc.JdbcSQLNonTransientException
import org.h2.jdbc.JdbcSQLSyntaxErrorException
import org.h2.jdbc.JdbcSQLTimeoutException
import org.h2.jdbc.JdbcSQLTransactionRollbackException
import org.h2.jdbc.JdbcSQLTransientException
import org.h2.util.SortedProperties
import org.h2.util.StringUtils
import org.h2.util.Utils

/**
 * This exception wraps a checked exception.
 * It is used in methods where checked exceptions are not supported,
 * for example in a Comparator.
 */
class DbException private constructor(e: SQLException) : RuntimeException(e.message, e) {

    private var source: Any? = null

    /**
     * Get the SQLException object.
     *
     * @return the exception
     */
    fun getSQLException(): SQLException {
        return cause as SQLException
    }

    /**
     * Get the error code.
     *
     * @return the error code
     */
    fun getErrorCode(): Int {
        return getSQLException().errorCode
    }

    /**
     * Set the SQL statement of the given exception.
     * This method may create a new object.
     *
     * @param sql the SQL statement
     * @return the exception
     */
    fun addSQL(sql: String?): DbException {
        var e: SQLException = getSQLException()
        if (e is JdbcException) {
            val j = e as JdbcException
            if (j.getSQL() == null) {
                j.setSQL(filterSQL(sql))
            }
            return this
        }
        e = getJdbcSQLException(e.message, sql, e.sqlState, e.errorCode, e, null)
        return DbException(e)
    }

    fun getSource(): Any? {
        return source
    }

    fun setSource(source: Any?) {
        this.source = source
    }

    companion object {

        private const val serialVersionUID = 1L

        /**
         * If the SQL statement contains this text, then it is never added to the
         * SQL exception. Hiding the SQL statement may be important if it contains a
         * passwords, such as a CREATE LINKED TABLE statement.
         */
        const val HIDE_SQL = "--hide--"

        private val MESSAGES = Properties()

        /**
         * Thrown when OOME exception happens on handle error
         * inside [.convert].
         */
        @JvmField
        val SQL_OOME: SQLException =
                SQLException("OutOfMemoryError", "HY000", OUT_OF_MEMORY, OutOfMemoryError())
        private val OOME = DbException(SQL_OOME)

        init {
            try {
                val messages = Utils.getResource("/org/h2/res/_messages_en.prop")
                if (messages != null) {
                    MESSAGES.load(ByteArrayInputStream(messages))
                }
                val language = Locale.getDefault().language
                if ("en" != language) {
                    val translations = Utils.getResource(
                            "/org/h2/res/_messages_" + language + ".prop")
                    // message: translated message + english
                    // (otherwise certain applications don't work)
                    if (translations != null) {
                        val p = SortedProperties.fromLines(
                                String(translations, StandardCharsets.UTF_8))
                        for (e in p.entries) {
                            val key = e.key as String
                            val translation = e.value as String?
                            if (translation != null && !translation.startsWith("#")) {
                                val original = MESSAGES.getProperty(key)
                                val message = translation + "\n" + original
                                MESSAGES.put(key, message)
                            }
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                traceThrowable(e)
            } catch (e: IOException) {
                traceThrowable(e)
            }
        }

        private fun translate(key: String, vararg params: String?): String {
            var message = MESSAGES.getProperty(key)
            if (message == null) {
                message = "(Message " + key + " not found)"
            }
            val args = arrayOfNulls<Any?>(params.size)
            for (i in params.indices) {
                val s = params[i]
                args[i] = if (s != null && s.length > 0) quote(s) else s
            }
            message = MessageFormat.format(message, *args)
            return message
        }

        private fun quote(s: String): String {
            val l = s.length
            val builder = StringBuilder(l + 2).append('"')
            var i = 0
            while (i < l) {
                val cp = s.codePointAt(i)
                i += Character.charCount(cp)
                val t = Character.getType(cp)
                if (t == 0 || t >= Character.SPACE_SEPARATOR.toInt() && t <= Character.SURROGATE.toInt()
                        && cp != ' '.code) {
                    if (cp <= 0xffff) {
                        StringUtils.appendHex(builder.append('\\'), cp.toLong(), 2)
                    } else {
                        StringUtils.appendHex(builder.append("\\+"), cp.toLong(), 3)
                    }
                } else {
                    if (cp == '"'.code || cp == '\\'.code) {
                        builder.append(cp.toChar())
                    }
                    builder.appendCodePoint(cp)
                }
            }
            return builder.append('"').toString()
        }

        /**
         * Create a database exception for a specific error code.
         *
         * @param errorCode the error code
         * @return the exception
         */
        @JvmStatic
        fun get(errorCode: Int): DbException {
            return get(errorCode, null as String?)
        }

        /**
         * Create a database exception for a specific error code.
         *
         * @param errorCode the error code
         * @param p1 the first parameter of the message
         * @return the exception
         */
        @JvmStatic
        fun get(errorCode: Int, p1: String?): DbException {
            return get(errorCode, *arrayOf(p1))
        }

        /**
         * Create a database exception for a specific error code.
         *
         * @param errorCode the error code
         * @param cause the cause of the exception
         * @param params the list of parameters of the message
         * @return the exception
         */
        @JvmStatic
        fun get(errorCode: Int, cause: Throwable?, vararg params: String?): DbException {
            return DbException(getJdbcSQLException(errorCode, cause, *params))
        }

        /**
         * Create a database exception for a specific error code.
         *
         * @param errorCode the error code
         * @param params the list of parameters of the message
         * @return the exception
         */
        @JvmStatic
        fun get(errorCode: Int, vararg params: String?): DbException {
            return DbException(getJdbcSQLException(errorCode, null, *params))
        }

        /**
         * Create a database exception for an arbitrary SQLState.
         *
         * @param sqlstate the state to use
         * @param message the message to use
         * @return the exception
         */
        @JvmStatic
        fun fromUser(sqlstate: String?, message: String?): DbException {
            // do not translate as sqlstate is arbitrary : avoid "message not found"
            return DbException(getJdbcSQLException(message, null, sqlstate, 0, null, null))
        }

        /**
         * Create a syntax error exception.
         *
         * @param sql the SQL statement
         * @param index the position of the error in the SQL statement
         * @return the exception
         */
        @JvmStatic
        fun getSyntaxError(sql: String?, index: Int): DbException {
            val s = StringUtils.addAsterisk(sql, index)
            return get(SYNTAX_ERROR_1, s)
        }

        /**
         * Create a syntax error exception.
         *
         * @param sql the SQL statement
         * @param index the position of the error in the SQL statement
         * @param message the message
         * @return the exception
         */
        @JvmStatic
        fun getSyntaxError(sql: String?, index: Int, message: String?): DbException {
            val s = StringUtils.addAsterisk(sql, index)
            return DbException(getJdbcSQLException(SYNTAX_ERROR_2, null, s, message))
        }

        /**
         * Create a syntax error exception for a specific error code.
         *
         * @param errorCode the error code
         * @param sql the SQL statement
         * @param index the position of the error in the SQL statement
         * @param params the list of parameters of the message
         * @return the exception
         */
        @JvmStatic
        fun getSyntaxError(errorCode: Int, sql: String?, index: Int, vararg params: String?): DbException {
            val s = StringUtils.addAsterisk(sql, index)
            val sqlstate = getState(errorCode)
            val message = translate(sqlstate, *params)
            return DbException(getJdbcSQLException(message, s, sqlstate, errorCode, null, null))
        }

        /**
         * Gets a SQL exception meaning this feature is not supported.
         *
         * @param message what exactly is not supported
         * @return the exception
         */
        @JvmStatic
        fun getUnsupportedException(message: String?): DbException {
            return get(FEATURE_NOT_SUPPORTED_1, message)
        }

        /**
         * Gets a SQL exception meaning this value is invalid.
         *
         * @param param the name of the parameter
         * @param value the value passed
         * @return the exception
         */
        @JvmStatic
        fun getInvalidValueException(param: String?, value: Any?): DbException {
            return get(INVALID_VALUE_2, if (value == null) "null" else value.toString(), param)
        }

        /**
         * Gets a SQL exception meaning this value is invalid.
         *
         * @param cause the cause of the exception
         * @param param the name of the parameter
         * @param value the value passed
         * @return the exception
         */
        @JvmStatic
        fun getInvalidValueException(cause: Throwable?, param: String?, value: Any?): DbException {
            return get(INVALID_VALUE_2, cause, if (value == null) "null" else value.toString(), param)
        }

        /**
         * Gets a SQL exception meaning this value is too long.
         *
         * @param columnOrType
         *            column with data type or data type name
         * @param value
         *            string representation of value, will be truncated to 80
         *            characters
         * @param valueLength
         *            the actual length of value, `-1L` if unknown
         * @return the exception
         */
        @JvmStatic
        fun getValueTooLongException(columnOrType: String?, value: String, valueLength: Long): DbException {
            val length = value.length
            val m = if (valueLength >= 0) 22 else 0
            val builder = if (length > 80)
                StringBuilder(83 + m).append(value, 0, 80).append("...")
            else
                StringBuilder(length + m).append(value)
            if (valueLength >= 0) {
                builder.append(" (").append(valueLength).append(')')
            }
            return get(VALUE_TOO_LONG_2, columnOrType, builder.toString())
        }

        /**
         * Gets a file version exception.
         *
         * @param dataFileName the name of the database
         * @return the exception
         */
        @JvmStatic
        fun getFileVersionError(dataFileName: String?): DbException {
            return get(FILE_VERSION_ERROR_1, "Old database: " + dataFileName
                    + " - please convert the database to a SQL script and re-create it.")
        }

        /**
         * Gets an internal error.
         *
         * @param s the message
         * @return the RuntimeException object
         */
        @JvmStatic
        fun getInternalError(s: String?): RuntimeException {
            val e = RuntimeException(s)
            traceThrowable(e)
            return e
        }

        /**
         * Gets an internal error.
         *
         * @return the RuntimeException object
         */
        @JvmStatic
        fun getInternalError(): RuntimeException {
            return getInternalError("Unexpected code path")
        }

        /**
         * Convert an exception to a SQL exception using the default mapping.
         *
         * @param e the root cause
         * @return the SQL exception object
         */
        @JvmStatic
        fun toSQLException(e: Throwable): SQLException {
            if (e is SQLException) {
                return e
            }
            return convert(e).getSQLException()
        }

        /**
         * Convert a throwable to an SQL exception using the default mapping. All
         * errors except the following are re-thrown: StackOverflowError,
         * LinkageError.
         *
         * @param e the root cause
         * @return the exception object
         */
        @JvmStatic
        fun convert(e: Throwable): DbException {
            try {
                if (e is DbException) {
                    return e
                } else if (e is SQLException) {
                    return DbException(e)
                } else if (e is InvocationTargetException) {
                    return convertInvocation(e, null)
                } else if (e is IOException) {
                    return get(IO_EXCEPTION_1, e, e.toString())
                } else if (e is OutOfMemoryError) {
                    return get(OUT_OF_MEMORY, e)
                } else if (e is StackOverflowError || e is LinkageError) {
                    return get(GENERAL_ERROR_1, e, e.toString())
                } else if (e is Error) {
                    throw e
                }
                return get(GENERAL_ERROR_1, e, e.toString())
            } catch (ignore: OutOfMemoryError) {
                return OOME
            } catch (ex: Throwable) {
                try {
                    val dbException = DbException(
                            SQLException("GeneralError", "HY000", GENERAL_ERROR_1, e))
                    dbException.addSuppressed(ex)
                    return dbException
                } catch (ignore: OutOfMemoryError) {
                    return OOME
                }
            }
        }

        /**
         * Convert an InvocationTarget exception to a database exception.
         *
         * @param te the root cause
         * @param message the added message or null
         * @return the database exception object
         */
        @JvmStatic
        fun convertInvocation(te: InvocationTargetException, message: String?): DbException {
            val t = te.targetException
            if (t is SQLException || t is DbException) {
                return convert(t)
            }
            val msg = if (message == null) t.message else message + ": " + t.message
            return get(EXCEPTION_IN_FUNCTION_1, t, msg)
        }

        /**
         * Convert an IO exception to a database exception.
         *
         * @param e the root cause
         * @param message the message or null
         * @return the database exception object
         */
        @JvmStatic
        fun convertIOException(e: IOException, message: String?): DbException {
            if (message == null) {
                val t = e.cause
                if (t is DbException) {
                    return t
                }
                return get(IO_EXCEPTION_1, e, e.toString())
            }
            return get(IO_EXCEPTION_2, e, e.toString(), message)
        }

        /**
         * Gets the SQL exception object for a specific error code.
         *
         * @param errorCode the error code
         * @return the SQLException object
         */
        @JvmStatic
        fun getJdbcSQLException(errorCode: Int): SQLException {
            return getJdbcSQLException(errorCode, null as Throwable?)
        }

        /**
         * Gets the SQL exception object for a specific error code.
         *
         * @param errorCode the error code
         * @param p1 the first parameter of the message
         * @return the SQLException object
         */
        @JvmStatic
        fun getJdbcSQLException(errorCode: Int, p1: String?): SQLException {
            return getJdbcSQLException(errorCode, null, p1)
        }

        /**
         * Gets the SQL exception object for a specific error code.
         *
         * @param errorCode the error code
         * @param cause the cause of the exception
         * @param params the list of parameters of the message
         * @return the SQLException object
         */
        @JvmStatic
        fun getJdbcSQLException(errorCode: Int, cause: Throwable?, vararg params: String?): SQLException {
            val sqlstate = getState(errorCode)
            val message = translate(sqlstate, *params)
            return getJdbcSQLException(message, null, sqlstate, errorCode, cause, null)
        }

        /**
         * Creates a SQLException.
         *
         * @param message the reason
         * @param sql the SQL statement
         * @param state the SQL state
         * @param errorCode the error code
         * @param cause the exception that was the reason for this exception
         * @param stackTrace the stack trace
         * @return the SQLException object
         */
        @JvmStatic
        fun getJdbcSQLException(message: String?, sql: String?, state: String?, errorCode: Int,
                cause: Throwable?, stackTrace: String?): SQLException {
            val filteredSql = filterSQL(sql)
            // Use SQLState class value to detect type
            when (errorCode / 1_000) {
                2 ->
                    return JdbcSQLNonTransientException(message, filteredSql, state, errorCode, cause, stackTrace)
                7, 21, 42, 54 ->
                    return JdbcSQLSyntaxErrorException(message, filteredSql, state, errorCode, cause, stackTrace)
                8 ->
                    return JdbcSQLNonTransientConnectionException(message, filteredSql, state, errorCode, cause, stackTrace)
                22 ->
                    return JdbcSQLDataException(message, filteredSql, state, errorCode, cause, stackTrace)
                23 ->
                    return JdbcSQLIntegrityConstraintViolationException(message, filteredSql, state, errorCode, cause, stackTrace)
                28 ->
                    return JdbcSQLInvalidAuthorizationSpecException(message, filteredSql, state, errorCode, cause, stackTrace)
                40 ->
                    return JdbcSQLTransactionRollbackException(message, filteredSql, state, errorCode, cause, stackTrace)
            }
            // Check error code
            when (errorCode) {
                GENERAL_ERROR_1,
                UNKNOWN_DATA_TYPE_1,
                METHOD_NOT_ALLOWED_FOR_QUERY,
                METHOD_ONLY_ALLOWED_FOR_QUERY,
                SEQUENCE_EXHAUSTED,
                OBJECT_CLOSED,
                CANNOT_DROP_CURRENT_USER,
                UNSUPPORTED_SETTING_COMBINATION,
                FILE_RENAME_FAILED_2,
                FILE_DELETE_FAILED_1,
                IO_EXCEPTION_1,
                NOT_ON_UPDATABLE_ROW,
                IO_EXCEPTION_2,
                TRACE_FILE_ERROR_2,
                ADMIN_RIGHTS_REQUIRED,
                ERROR_EXECUTING_TRIGGER_3,
                COMMIT_ROLLBACK_NOT_ALLOWED,
                FILE_CREATION_FAILED_1,
                SAVEPOINT_IS_INVALID_1,
                SAVEPOINT_IS_UNNAMED,
                SAVEPOINT_IS_NAMED,
                NOT_ENOUGH_RIGHTS_FOR_1,
                DATABASE_IS_READ_ONLY,
                WRONG_XID_FORMAT_1,
                UNSUPPORTED_COMPRESSION_OPTIONS_1,
                UNSUPPORTED_COMPRESSION_ALGORITHM_1,
                COMPRESSION_ERROR,
                EXCEPTION_IN_FUNCTION_1,
                ERROR_ACCESSING_LINKED_TABLE_2,
                FILE_NOT_FOUND_1,
                INVALID_CLASS_2,
                DATABASE_IS_NOT_PERSISTENT,
                RESULT_SET_NOT_UPDATABLE,
                RESULT_SET_NOT_SCROLLABLE,
                METHOD_NOT_ALLOWED_FOR_PREPARED_STATEMENT,
                ACCESS_DENIED_TO_CLASS_1,
                RESULT_SET_READONLY,
                CURRENT_SEQUENCE_VALUE_IS_NOT_DEFINED_IN_SESSION_1 ->
                    return JdbcSQLNonTransientException(message, filteredSql, state, errorCode, cause, stackTrace)
                FEATURE_NOT_SUPPORTED_1 ->
                    return JdbcSQLFeatureNotSupportedException(message, filteredSql, state, errorCode, cause, stackTrace)
                LOCK_TIMEOUT_1,
                STATEMENT_WAS_CANCELED,
                LOB_CLOSED_ON_TIMEOUT_1 ->
                    return JdbcSQLTimeoutException(message, filteredSql, state, errorCode, cause, stackTrace)
                FUNCTION_MUST_RETURN_RESULT_SET_1,
                INVALID_TRIGGER_FLAGS_1,
                SUM_OR_AVG_ON_WRONG_DATATYPE_1,
                MUST_GROUP_BY_COLUMN_1,
                SECOND_PRIMARY_KEY,
                FUNCTION_NOT_FOUND_1,
                COLUMN_MUST_NOT_BE_NULLABLE_1,
                USER_NOT_FOUND_1,
                USER_ALREADY_EXISTS_1,
                SEQUENCE_ALREADY_EXISTS_1,
                SEQUENCE_NOT_FOUND_1,
                VIEW_NOT_FOUND_1,
                VIEW_ALREADY_EXISTS_1,
                TRIGGER_ALREADY_EXISTS_1,
                TRIGGER_NOT_FOUND_1,
                ERROR_CREATING_TRIGGER_OBJECT_3,
                CONSTRAINT_ALREADY_EXISTS_1,
                SUBQUERY_IS_NOT_SINGLE_COLUMN,
                INVALID_USE_OF_AGGREGATE_FUNCTION_1,
                CONSTRAINT_NOT_FOUND_1,
                AMBIGUOUS_COLUMN_NAME_1,
                ORDER_BY_NOT_IN_RESULT,
                ROLE_ALREADY_EXISTS_1,
                ROLE_NOT_FOUND_1,
                USER_OR_ROLE_NOT_FOUND_1,
                ROLES_AND_RIGHT_CANNOT_BE_MIXED,
                METHODS_MUST_HAVE_DIFFERENT_PARAMETER_COUNTS_2,
                ROLE_ALREADY_GRANTED_1,
                COLUMN_IS_PART_OF_INDEX_1,
                FUNCTION_ALIAS_ALREADY_EXISTS_1,
                FUNCTION_ALIAS_NOT_FOUND_1,
                SCHEMA_ALREADY_EXISTS_1,
                SCHEMA_NOT_FOUND_1,
                SCHEMA_NAME_MUST_MATCH,
                COLUMN_CONTAINS_NULL_VALUES_1,
                SEQUENCE_BELONGS_TO_A_TABLE_1,
                COLUMN_IS_REFERENCED_1,
                CANNOT_DROP_LAST_COLUMN,
                INDEX_BELONGS_TO_CONSTRAINT_2,
                CLASS_NOT_FOUND_1,
                METHOD_NOT_FOUND_1,
                COLLATION_CHANGE_WITH_DATA_TABLE_1,
                SCHEMA_CAN_NOT_BE_DROPPED_1,
                ROLE_CAN_NOT_BE_DROPPED_1,
                CANNOT_TRUNCATE_1,
                CANNOT_DROP_2,
                VIEW_IS_INVALID_2,
                TYPES_ARE_NOT_COMPARABLE_2,
                CONSTANT_ALREADY_EXISTS_1,
                CONSTANT_NOT_FOUND_1,
                LITERALS_ARE_NOT_ALLOWED,
                CANNOT_DROP_TABLE_1,
                DOMAIN_ALREADY_EXISTS_1,
                DOMAIN_NOT_FOUND_1,
                WITH_TIES_WITHOUT_ORDER_BY,
                CANNOT_MIX_INDEXED_AND_UNINDEXED_PARAMS,
                TRANSACTION_NOT_FOUND_1,
                AGGREGATE_NOT_FOUND_1,
                WINDOW_NOT_FOUND_1,
                CAN_ONLY_ASSIGN_TO_VARIABLE_1,
                PUBLIC_STATIC_JAVA_METHOD_NOT_FOUND_1,
                JAVA_OBJECT_SERIALIZER_CHANGE_WITH_DATA_TABLE,
                FOR_UPDATE_IS_NOT_ALLOWED_IN_DISTINCT_OR_GROUPED_SELECT,
                INVALID_VALUE_PRECISION,
                INVALID_VALUE_SCALE,
                CONSTRAINT_IS_USED_BY_CONSTRAINT_2,
                UNCOMPARABLE_REFERENCED_COLUMN_2,
                GENERATED_COLUMN_CANNOT_BE_ASSIGNED_1,
                GENERATED_COLUMN_CANNOT_BE_UPDATABLE_BY_CONSTRAINT_2,
                COLUMN_ALIAS_IS_NOT_SPECIFIED_1,
                GROUP_BY_NOT_IN_THE_RESULT ->
                    return JdbcSQLSyntaxErrorException(message, filteredSql, state, errorCode, cause, stackTrace)
                HEX_STRING_ODD_1,
                HEX_STRING_WRONG_1,
                INVALID_VALUE_2,
                SEQUENCE_ATTRIBUTES_INVALID_7,
                INVALID_TO_CHAR_FORMAT,
                PARAMETER_NOT_SET_1,
                PARSE_ERROR_1,
                INVALID_TO_DATE_FORMAT,
                STRING_FORMAT_ERROR_1,
                SERIALIZATION_FAILED_1,
                DESERIALIZATION_FAILED_1,
                SCALAR_SUBQUERY_CONTAINS_MORE_THAN_ONE_ROW,
                STEP_SIZE_MUST_NOT_BE_ZERO ->
                    return JdbcSQLDataException(message, filteredSql, state, errorCode, cause, stackTrace)
                URL_RELATIVE_TO_CWD,
                DATABASE_NOT_FOUND_1,
                DATABASE_NOT_FOUND_WITH_IF_EXISTS_1,
                REMOTE_DATABASE_NOT_FOUND_1,
                TRACE_CONNECTION_NOT_CLOSED,
                DATABASE_ALREADY_OPEN_1,
                FILE_CORRUPTED_1,
                URL_FORMAT_ERROR_2,
                DRIVER_VERSION_ERROR_2,
                FILE_VERSION_ERROR_1,
                FILE_ENCRYPTION_ERROR_1,
                WRONG_PASSWORD_FORMAT,
                UNSUPPORTED_CIPHER,
                UNSUPPORTED_LOCK_METHOD_1,
                EXCEPTION_OPENING_PORT_2,
                DUPLICATE_PROPERTY_1,
                CONNECTION_BROKEN_1,
                UNKNOWN_MODE_1,
                CLUSTER_ERROR_DATABASE_RUNS_ALONE,
                CLUSTER_ERROR_DATABASE_RUNS_CLUSTERED_1,
                DATABASE_IS_CLOSED,
                ERROR_SETTING_DATABASE_EVENT_LISTENER_2,
                OUT_OF_MEMORY,
                UNSUPPORTED_SETTING_1,
                REMOTE_CONNECTION_NOT_ALLOWED,
                DATABASE_CALLED_AT_SHUTDOWN,
                CANNOT_CHANGE_SETTING_WHEN_OPEN_1,
                DATABASE_IS_IN_EXCLUSIVE_MODE,
                INVALID_DATABASE_NAME_1,
                AUTHENTICATOR_NOT_AVAILABLE,
                METHOD_DISABLED_ON_AUTOCOMMIT_TRUE ->
                    return JdbcSQLNonTransientConnectionException(message, filteredSql, state, errorCode, cause, stackTrace)
                ROW_NOT_FOUND_WHEN_DELETING_1,
                CONCURRENT_UPDATE_1,
                ROW_NOT_FOUND_IN_PRIMARY_INDEX ->
                    return JdbcSQLTransientException(message, filteredSql, state, errorCode, cause, stackTrace)
            }
            // Default
            return JdbcSQLException(message, filteredSql, state, errorCode, cause, stackTrace)
        }

        private fun filterSQL(sql: String?): String? {
            return if (sql == null || !sql.contains(HIDE_SQL)) sql else "-"
        }

        /**
         * Builds message for an exception.
         *
         * @param e exception
         * @return message
         */
        @JvmStatic
        fun buildMessageForException(e: JdbcException): String {
            var s = e.originalMessage
            val buff = StringBuilder(if (s != null) s else "- ")
            s = e.getSQL()
            if (s != null) {
                buff.append("; SQL statement:\n").append(s)
            }
            buff.append(" [").append(e.errorCode).append('-').append(Constants.BUILD_ID).append(']')
            return buff.toString()
        }

        /**
         * Prints up to 100 next exceptions for a specified SQL exception.
         *
         * @param e SQL exception
         * @param s print writer
         */
        @JvmStatic
        fun printNextExceptions(e: SQLException, s: PrintWriter) {
            // getNextException().printStackTrace(s) would be very slow
            // if many exceptions are joined
            var next: SQLException = e
            var i = 0
            while (true) {
                val n = next.nextException ?: break
                next = n
                if (i++ == 100) {
                    s.println("(truncated)")
                    return
                }
                s.println(next.toString())
            }
        }

        /**
         * Prints up to 100 next exceptions for a specified SQL exception.
         *
         * @param e SQL exception
         * @param s print stream
         */
        @JvmStatic
        fun printNextExceptions(e: SQLException, s: PrintStream) {
            // getNextException().printStackTrace(s) would be very slow
            // if many exceptions are joined
            var next: SQLException = e
            var i = 0
            while (true) {
                val n = next.nextException ?: break
                next = n
                if (i++ == 100) {
                    s.println("(truncated)")
                    return
                }
                s.println(next.toString())
            }
        }

        /**
         * Write the exception to the driver manager log writer if configured.
         *
         * @param e the exception
         */
        @JvmStatic
        fun traceThrowable(e: Throwable) {
            val writer = DriverManager.getLogWriter()
            if (writer != null) {
                e.printStackTrace(writer)
            }
        }
    }
}
