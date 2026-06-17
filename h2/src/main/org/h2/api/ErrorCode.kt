/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api

/**
 * This class defines the error codes used for SQL exceptions.
 * Error messages are formatted as follows:
 * <pre>
 * { error message (possibly translated; may include quoted data) }
 * { error message in English if different }
 * { SQL statement if applicable }
 * { [ error code - build number ] }
 * </pre>
 * Example:
 * <pre>
 * Syntax error in SQL statement "SELECT * FORM[*] TEST ";
 * SQL statement: select * form test [42000-125]
 * </pre>
 * The [*] marks the position of the syntax error
 * (FORM instead of FROM in this case).
 * The error code is 42000, and the build number is 125,
 * meaning version 1.2.125.
 */
class ErrorCode private constructor() {

    companion object {

        // 02: no data
        const val NO_DATA_AVAILABLE = 2000

        // 07: dynamic SQL error
        const val INVALID_PARAMETER_COUNT_2 = 7001

        // 08: connection exception
        const val ERROR_OPENING_DATABASE_1 = 8000

        // 21: cardinality violation
        const val COLUMN_COUNT_DOES_NOT_MATCH = 21002

        // 22: data exception
        const val VALUE_TOO_LONG_2 = 22001
        const val NUMERIC_VALUE_OUT_OF_RANGE_1 = 22003
        const val NUMERIC_VALUE_OUT_OF_RANGE_2 = 22004
        const val INVALID_DATETIME_CONSTANT_2 = 22007
        const val DIVISION_BY_ZERO_1 = 22012
        const val INVALID_PRECEDING_OR_FOLLOWING_1 = 22013
        const val DATA_CONVERSION_ERROR_1 = 22018
        const val LIKE_ESCAPE_ERROR_1 = 22025
        const val ENUM_VALUE_NOT_PERMITTED = 22030
        const val ENUM_EMPTY = 22032
        const val ENUM_DUPLICATE = 22033
        const val ARRAY_ELEMENT_ERROR_2 = 22034
        const val NULL_VALUE_IN_ARRAY_TARGET = 22035

        // 23: constraint violation
        const val NULL_NOT_ALLOWED = 23502
        const val REFERENTIAL_INTEGRITY_VIOLATED_CHILD_EXISTS_1 = 23503
        const val DUPLICATE_KEY_1 = 23505
        const val REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1 = 23506
        const val NO_DEFAULT_SET_1 = 23507
        const val CHECK_CONSTRAINT_VIOLATED_1 = 23513
        const val CHECK_CONSTRAINT_INVALID = 23514

        // 28: invalid authorization specification
        const val WRONG_USER_OR_PASSWORD = 28000

        // 3B: savepoint exception
        const val DEADLOCK_1 = 40001

        // 42: syntax error or access rule violation
        const val SYNTAX_ERROR_1 = 42000
        const val SYNTAX_ERROR_2 = 42001
        const val TABLE_OR_VIEW_ALREADY_EXISTS_1 = 42101
        const val TABLE_OR_VIEW_NOT_FOUND_1 = 42102
        const val TABLE_OR_VIEW_NOT_FOUND_WITH_CANDIDATES_2 = 42103
        const val TABLE_OR_VIEW_NOT_FOUND_DATABASE_EMPTY_1 = 42104
        const val INDEX_ALREADY_EXISTS_1 = 42111
        const val INDEX_NOT_FOUND_1 = 42112
        const val DUPLICATE_COLUMN_NAME_1 = 42121
        const val COLUMN_NOT_FOUND_1 = 42122
        const val IDENTICAL_EXPRESSIONS_SHOULD_BE_USED = 42131
        const val INVALID_NAME_1 = 42602
        const val NAME_TOO_LONG_2 = 42622
        const val WRONG_OBJECT_TYPE = 42809

        // 54: program limit exceeded
        const val TOO_MANY_COLUMNS_1 = 54011

        // 0A: feature not supported
        // HZ: remote database access
        //
        const val GENERAL_ERROR_1 = 50000
        const val UNKNOWN_DATA_TYPE_1 = 50004
        const val FEATURE_NOT_SUPPORTED_1 = 50100
        const val LOCK_TIMEOUT_1 = 50200
        const val STATEMENT_WAS_CANCELED = 57014
        const val FUNCTION_MUST_RETURN_RESULT_SET_1 = 90000
        const val METHOD_NOT_ALLOWED_FOR_QUERY = 90001
        const val METHOD_ONLY_ALLOWED_FOR_QUERY = 90002
        const val HEX_STRING_ODD_1 = 90003
        const val HEX_STRING_WRONG_1 = 90004
        const val INVALID_TRIGGER_FLAGS_1 = 90005
        const val SEQUENCE_EXHAUSTED = 90006
        const val OBJECT_CLOSED = 90007
        const val INVALID_VALUE_2 = 90008
        const val SEQUENCE_ATTRIBUTES_INVALID_7 = 90009
        const val INVALID_TO_CHAR_FORMAT = 90010
        const val URL_RELATIVE_TO_CWD = 90011
        const val PARAMETER_NOT_SET_1 = 90012
        const val DATABASE_NOT_FOUND_1 = 90013
        const val PARSE_ERROR_1 = 90014
        const val SUM_OR_AVG_ON_WRONG_DATATYPE_1 = 90015
        const val MUST_GROUP_BY_COLUMN_1 = 90016
        const val SECOND_PRIMARY_KEY = 90017
        const val TRACE_CONNECTION_NOT_CLOSED = 90018
        const val CANNOT_DROP_CURRENT_USER = 90019
        const val DATABASE_ALREADY_OPEN_1 = 90020
        const val UNSUPPORTED_SETTING_COMBINATION = 90021
        const val FUNCTION_NOT_FOUND_1 = 90022
        const val COLUMN_MUST_NOT_BE_NULLABLE_1 = 90023
        const val FILE_RENAME_FAILED_2 = 90024
        const val FILE_DELETE_FAILED_1 = 90025
        const val SERIALIZATION_FAILED_1 = 90026
        const val DESERIALIZATION_FAILED_1 = 90027
        const val IO_EXCEPTION_1 = 90028
        const val NOT_ON_UPDATABLE_ROW = 90029
        const val FILE_CORRUPTED_1 = 90030
        const val IO_EXCEPTION_2 = 90031
        const val USER_NOT_FOUND_1 = 90032
        const val USER_ALREADY_EXISTS_1 = 90033
        const val TRACE_FILE_ERROR_2 = 90034
        const val SEQUENCE_ALREADY_EXISTS_1 = 90035
        const val SEQUENCE_NOT_FOUND_1 = 90036
        const val VIEW_NOT_FOUND_1 = 90037
        const val VIEW_ALREADY_EXISTS_1 = 90038
        const val LOB_CLOSED_ON_TIMEOUT_1 = 90039
        const val ADMIN_RIGHTS_REQUIRED = 90040
        const val TRIGGER_ALREADY_EXISTS_1 = 90041
        const val TRIGGER_NOT_FOUND_1 = 90042
        const val ERROR_CREATING_TRIGGER_OBJECT_3 = 90043
        const val ERROR_EXECUTING_TRIGGER_3 = 90044
        const val CONSTRAINT_ALREADY_EXISTS_1 = 90045
        const val URL_FORMAT_ERROR_2 = 90046
        const val DRIVER_VERSION_ERROR_2 = 90047
        const val FILE_VERSION_ERROR_1 = 90048
        const val FILE_ENCRYPTION_ERROR_1 = 90049
        const val WRONG_PASSWORD_FORMAT = 90050
        // 90051 was removed
        const val SUBQUERY_IS_NOT_SINGLE_COLUMN = 90052
        const val SCALAR_SUBQUERY_CONTAINS_MORE_THAN_ONE_ROW = 90053
        const val INVALID_USE_OF_AGGREGATE_FUNCTION_1 = 90054
        const val UNSUPPORTED_CIPHER = 90055
        const val INVALID_TO_DATE_FORMAT = 90056
        const val CONSTRAINT_NOT_FOUND_1 = 90057
        const val COMMIT_ROLLBACK_NOT_ALLOWED = 90058
        const val AMBIGUOUS_COLUMN_NAME_1 = 90059
        const val UNSUPPORTED_LOCK_METHOD_1 = 90060
        const val EXCEPTION_OPENING_PORT_2 = 90061
        const val FILE_CREATION_FAILED_1 = 90062
        const val SAVEPOINT_IS_INVALID_1 = 90063
        const val SAVEPOINT_IS_UNNAMED = 90064
        const val SAVEPOINT_IS_NAMED = 90065
        const val DUPLICATE_PROPERTY_1 = 90066
        const val CONNECTION_BROKEN_1 = 90067
        const val ORDER_BY_NOT_IN_RESULT = 90068
        const val ROLE_ALREADY_EXISTS_1 = 90069
        const val ROLE_NOT_FOUND_1 = 90070
        const val USER_OR_ROLE_NOT_FOUND_1 = 90071
        const val ROLES_AND_RIGHT_CANNOT_BE_MIXED = 90072
        const val METHODS_MUST_HAVE_DIFFERENT_PARAMETER_COUNTS_2 = 90073
        const val ROLE_ALREADY_GRANTED_1 = 90074
        const val COLUMN_IS_PART_OF_INDEX_1 = 90075
        const val FUNCTION_ALIAS_ALREADY_EXISTS_1 = 90076
        const val FUNCTION_ALIAS_NOT_FOUND_1 = 90077
        const val SCHEMA_ALREADY_EXISTS_1 = 90078
        const val SCHEMA_NOT_FOUND_1 = 90079
        const val SCHEMA_NAME_MUST_MATCH = 90080
        const val COLUMN_CONTAINS_NULL_VALUES_1 = 90081
        const val SEQUENCE_BELONGS_TO_A_TABLE_1 = 90082
        const val COLUMN_IS_REFERENCED_1 = 90083
        const val CANNOT_DROP_LAST_COLUMN = 90084
        const val INDEX_BELONGS_TO_CONSTRAINT_2 = 90085
        const val CLASS_NOT_FOUND_1 = 90086
        const val METHOD_NOT_FOUND_1 = 90087
        const val UNKNOWN_MODE_1 = 90088
        const val COLLATION_CHANGE_WITH_DATA_TABLE_1 = 90089
        const val SCHEMA_CAN_NOT_BE_DROPPED_1 = 90090
        const val ROLE_CAN_NOT_BE_DROPPED_1 = 90091
        const val CLUSTER_ERROR_DATABASE_RUNS_ALONE = 90093
        const val CLUSTER_ERROR_DATABASE_RUNS_CLUSTERED_1 = 90094
        const val STRING_FORMAT_ERROR_1 = 90095
        const val NOT_ENOUGH_RIGHTS_FOR_1 = 90096
        const val DATABASE_IS_READ_ONLY = 90097
        const val DATABASE_IS_CLOSED = 90098
        const val ERROR_SETTING_DATABASE_EVENT_LISTENER_2 = 90099
        const val WRONG_XID_FORMAT_1 = 90101
        const val UNSUPPORTED_COMPRESSION_OPTIONS_1 = 90102
        const val UNSUPPORTED_COMPRESSION_ALGORITHM_1 = 90103
        const val COMPRESSION_ERROR = 90104
        const val EXCEPTION_IN_FUNCTION_1 = 90105
        const val CANNOT_TRUNCATE_1 = 90106
        const val CANNOT_DROP_2 = 90107
        const val OUT_OF_MEMORY = 90108
        const val VIEW_IS_INVALID_2 = 90109
        const val TYPES_ARE_NOT_COMPARABLE_2 = 90110
        const val ERROR_ACCESSING_LINKED_TABLE_2 = 90111
        const val ROW_NOT_FOUND_WHEN_DELETING_1 = 90112
        const val UNSUPPORTED_SETTING_1 = 90113
        const val CONSTANT_ALREADY_EXISTS_1 = 90114
        const val CONSTANT_NOT_FOUND_1 = 90115
        const val LITERALS_ARE_NOT_ALLOWED = 90116
        const val REMOTE_CONNECTION_NOT_ALLOWED = 90117
        const val CANNOT_DROP_TABLE_1 = 90118
        const val DOMAIN_ALREADY_EXISTS_1 = 90119

        /**
         * The error with code <code>90119</code> is thrown when
         * trying to drop a user-defined data type if a data type with this name
         * already exists.
         * @deprecated since 1.4.198. Use [DOMAIN_ALREADY_EXISTS_1] instead.
         */
        @java.lang.Deprecated
        const val USER_DATA_TYPE_ALREADY_EXISTS_1 = DOMAIN_ALREADY_EXISTS_1

        const val DOMAIN_NOT_FOUND_1 = 90120

        /**
         * The error with code <code>90120</code> is thrown when
         * trying to drop a user-defined data type that does not exist.
         * @deprecated since 1.4.198. Use [DOMAIN_NOT_FOUND_1] instead.
         */
        @java.lang.Deprecated
        const val USER_DATA_TYPE_NOT_FOUND_1 = DOMAIN_NOT_FOUND_1

        const val DATABASE_CALLED_AT_SHUTDOWN = 90121
        const val WITH_TIES_WITHOUT_ORDER_BY = 90122
        const val CANNOT_MIX_INDEXED_AND_UNINDEXED_PARAMS = 90123
        const val FILE_NOT_FOUND_1 = 90124
        const val INVALID_CLASS_2 = 90125
        const val DATABASE_IS_NOT_PERSISTENT = 90126
        const val RESULT_SET_NOT_UPDATABLE = 90127
        const val RESULT_SET_NOT_SCROLLABLE = 90128
        const val TRANSACTION_NOT_FOUND_1 = 90129
        const val METHOD_NOT_ALLOWED_FOR_PREPARED_STATEMENT = 90130
        const val CONCURRENT_UPDATE_1 = 90131
        const val AGGREGATE_NOT_FOUND_1 = 90132
        const val CANNOT_CHANGE_SETTING_WHEN_OPEN_1 = 90133
        const val ACCESS_DENIED_TO_CLASS_1 = 90134
        const val DATABASE_IS_IN_EXCLUSIVE_MODE = 90135
        const val WINDOW_NOT_FOUND_1 = 90136
        const val CAN_ONLY_ASSIGN_TO_VARIABLE_1 = 90137
        const val INVALID_DATABASE_NAME_1 = 90138
        const val PUBLIC_STATIC_JAVA_METHOD_NOT_FOUND_1 = 90139
        const val RESULT_SET_READONLY = 90140
        const val JAVA_OBJECT_SERIALIZER_CHANGE_WITH_DATA_TABLE = 90141
        const val STEP_SIZE_MUST_NOT_BE_ZERO = 90142
        const val ROW_NOT_FOUND_IN_PRIMARY_INDEX = 90143
        const val AUTHENTICATOR_NOT_AVAILABLE = 90144
        const val FOR_UPDATE_IS_NOT_ALLOWED_IN_DISTINCT_OR_GROUPED_SELECT = 90145
        const val DATABASE_NOT_FOUND_WITH_IF_EXISTS_1 = 90146
        const val METHOD_DISABLED_ON_AUTOCOMMIT_TRUE = 90147
        const val CURRENT_SEQUENCE_VALUE_IS_NOT_DEFINED_IN_SESSION_1 = 90148
        const val REMOTE_DATABASE_NOT_FOUND_1 = 90149
        const val INVALID_VALUE_PRECISION = 90150
        const val INVALID_VALUE_SCALE = 90151
        const val CONSTRAINT_IS_USED_BY_CONSTRAINT_2 = 90152
        const val UNCOMPARABLE_REFERENCED_COLUMN_2 = 90153
        const val GENERATED_COLUMN_CANNOT_BE_ASSIGNED_1 = 90154
        const val GENERATED_COLUMN_CANNOT_BE_UPDATABLE_BY_CONSTRAINT_2 = 90155
        const val COLUMN_ALIAS_IS_NOT_SPECIFIED_1 = 90156
        const val GROUP_BY_NOT_IN_THE_RESULT = 90157

        // next is 90158

        /**
         * INTERNAL
         * @param errorCode to check
         * @return true if provided code is common, false otherwise
         */
        @JvmStatic
        fun isCommon(errorCode: Int): Boolean {
            // this list is sorted alphabetically
            when (errorCode) {
                DATA_CONVERSION_ERROR_1,
                DUPLICATE_KEY_1,
                FUNCTION_ALIAS_ALREADY_EXISTS_1,
                LOCK_TIMEOUT_1,
                NULL_NOT_ALLOWED,
                NO_DATA_AVAILABLE,
                NUMERIC_VALUE_OUT_OF_RANGE_1,
                OBJECT_CLOSED,
                REFERENTIAL_INTEGRITY_VIOLATED_CHILD_EXISTS_1,
                REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1,
                SYNTAX_ERROR_1,
                SYNTAX_ERROR_2,
                TABLE_OR_VIEW_ALREADY_EXISTS_1,
                TABLE_OR_VIEW_NOT_FOUND_1,
                TABLE_OR_VIEW_NOT_FOUND_WITH_CANDIDATES_2,
                TABLE_OR_VIEW_NOT_FOUND_DATABASE_EMPTY_1,
                VALUE_TOO_LONG_2 ->
                    return true
            }
            return false
        }

        /**
         * INTERNAL
         * @param errorCode to get state for
         * @return error state
         */
        @JvmStatic
        fun getState(errorCode: Int): String {
            // To convert SQLState to error code, replace
            // 21S: 210, 42S: 421, HY: 50, C: 1, T: 2

            return when (errorCode) {

                // 02: no data
                NO_DATA_AVAILABLE -> "02000"

                // 07: dynamic SQL error
                INVALID_PARAMETER_COUNT_2 -> "07001"

                // 08: connection exception
                ERROR_OPENING_DATABASE_1 -> "08000"

                // 21: cardinality violation
                COLUMN_COUNT_DOES_NOT_MATCH -> "21S02"

                // 22: data exception
                NULL_VALUE_IN_ARRAY_TARGET -> "2200E"
                ARRAY_ELEMENT_ERROR_2 -> "2202E"

                // 42: syntax error or access rule violation
                TABLE_OR_VIEW_ALREADY_EXISTS_1 -> "42S01"
                TABLE_OR_VIEW_NOT_FOUND_1 -> "42S02"
                TABLE_OR_VIEW_NOT_FOUND_WITH_CANDIDATES_2 -> "42S03"
                TABLE_OR_VIEW_NOT_FOUND_DATABASE_EMPTY_1 -> "42S04"
                INDEX_ALREADY_EXISTS_1 -> "42S11"
                INDEX_NOT_FOUND_1 -> "42S12"
                DUPLICATE_COLUMN_NAME_1 -> "42S21"
                COLUMN_NOT_FOUND_1 -> "42S22"
                IDENTICAL_EXPRESSIONS_SHOULD_BE_USED -> "42S31"

                // 0A: feature not supported

                // HZ: remote database access

                // HY
                GENERAL_ERROR_1 -> "HY000"
                UNKNOWN_DATA_TYPE_1 -> "HY004"

                FEATURE_NOT_SUPPORTED_1 -> "HYC00"
                LOCK_TIMEOUT_1 -> "HYT00"
                else -> Integer.toString(errorCode)
            }
        }
    }
}
