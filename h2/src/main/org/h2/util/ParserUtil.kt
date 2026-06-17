/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.HashMap

class ParserUtil private constructor() {
    // utility class

    companion object {

        /**
         * A keyword.
         */
        const val KEYWORD = 1

        /**
         * An identifier (table name, column name,...).
         */
        const val IDENTIFIER = 2

        // Constants below must be sorted

        /**
         * The token "ALL".
         */
        const val ALL = IDENTIFIER + 1

        /**
         * The token "AND".
         */
        const val AND = ALL + 1

        /**
         * The token "ANY".
         */
        const val ANY = AND + 1

        /**
         * The token "ARRAY".
         */
        const val ARRAY = ANY + 1

        /**
         * The token "AS".
         */
        const val AS = ARRAY + 1

        /**
         * The token "ASYMMETRIC".
         */
        const val ASYMMETRIC = AS + 1

        /**
         * The token "AUTHORIZATION".
         */
        const val AUTHORIZATION = ASYMMETRIC + 1

        /**
         * The token "BETWEEN".
         */
        const val BETWEEN = AUTHORIZATION + 1

        /**
         * The token "CASE".
         */
        const val CASE = BETWEEN + 1

        /**
         * The token "CAST".
         */
        const val CAST = CASE + 1

        /**
         * The token "CHECK".
         */
        const val CHECK = CAST + 1

        /**
         * The token "CONSTRAINT".
         */
        const val CONSTRAINT = CHECK + 1

        /**
         * The token "CROSS".
         */
        const val CROSS = CONSTRAINT + 1

        /**
         * The token "CURRENT_CATALOG".
         */
        const val CURRENT_CATALOG = CROSS + 1

        /**
         * The token "CURRENT_DATE".
         */
        const val CURRENT_DATE = CURRENT_CATALOG + 1

        /**
         * The token "CURRENT_PATH".
         */
        const val CURRENT_PATH = CURRENT_DATE + 1

        /**
         * The token "CURRENT_ROLE".
         */
        const val CURRENT_ROLE = CURRENT_PATH + 1

        /**
         * The token "CURRENT_SCHEMA".
         */
        const val CURRENT_SCHEMA = CURRENT_ROLE + 1

        /**
         * The token "CURRENT_TIME".
         */
        const val CURRENT_TIME = CURRENT_SCHEMA + 1

        /**
         * The token "CURRENT_TIMESTAMP".
         */
        const val CURRENT_TIMESTAMP = CURRENT_TIME + 1

        /**
         * The token "CURRENT_USER".
         */
        const val CURRENT_USER = CURRENT_TIMESTAMP + 1

        /**
         * The token "DAY".
         */
        const val DAY = CURRENT_USER + 1

        /**
         * The token "DEFAULT".
         */
        const val DEFAULT = DAY + 1

        /**
         * The token "DISTINCT".
         */
        const val DISTINCT = DEFAULT + 1

        /**
         * The token "ELSE".
         */
        const val ELSE = DISTINCT + 1

        /**
         * The token "END".
         */
        const val END = ELSE + 1

        /**
         * The token "EXCEPT".
         */
        const val EXCEPT = END + 1

        /**
         * The token "EXISTS".
         */
        const val EXISTS = EXCEPT + 1

        /**
         * The token "FALSE".
         */
        const val FALSE = EXISTS + 1

        /**
         * The token "FETCH".
         */
        const val FETCH = FALSE + 1

        /**
         * The token "FOR".
         */
        const val FOR = FETCH + 1

        /**
         * The token "FOREIGN".
         */
        const val FOREIGN = FOR + 1

        /**
         * The token "FROM".
         */
        const val FROM = FOREIGN + 1

        /**
         * The token "FULL".
         */
        const val FULL = FROM + 1

        /**
         * The token "GROUP".
         */
        const val GROUP = FULL + 1

        /**
         * The token "HAVING".
         */
        const val HAVING = GROUP + 1

        /**
         * The token "HOUR".
         */
        const val HOUR = HAVING + 1

        /**
         * The token "IF".
         */
        const val IF = HOUR + 1

        /**
         * The token "IN".
         */
        const val IN = IF + 1

        /**
         * The token "INNER".
         */
        const val INNER = IN + 1

        /**
         * The token "INTERSECT".
         */
        const val INTERSECT = INNER + 1

        /**
         * The token "INTERVAL".
         */
        const val INTERVAL = INTERSECT + 1

        /**
         * The token "IS".
         */
        const val IS = INTERVAL + 1

        /**
         * The token "JOIN".
         */
        const val JOIN = IS + 1

        /**
         * The token "KEY".
         */
        const val KEY = JOIN + 1

        /**
         * The token "LEFT".
         */
        const val LEFT = KEY + 1

        /**
         * The token "LIKE".
         */
        const val LIKE = LEFT + 1

        /**
         * The token "LIMIT".
         */
        const val LIMIT = LIKE + 1

        /**
         * The token "LOCALTIME".
         */
        const val LOCALTIME = LIMIT + 1

        /**
         * The token "LOCALTIMESTAMP".
         */
        const val LOCALTIMESTAMP = LOCALTIME + 1

        /**
         * The token "MINUS".
         */
        const val MINUS = LOCALTIMESTAMP + 1

        /**
         * The token "MINUTE".
         */
        const val MINUTE = MINUS + 1

        /**
         * The token "MONTH".
         */
        const val MONTH = MINUTE + 1

        /**
         * The token "NATURAL".
         */
        const val NATURAL = MONTH + 1

        /**
         * The token "NOT".
         */
        const val NOT = NATURAL + 1

        /**
         * The token "NULL".
         */
        const val NULL = NOT + 1

        /**
         * The token "OFFSET".
         */
        const val OFFSET = NULL + 1

        /**
         * The token "ON".
         */
        const val ON = OFFSET + 1

        /**
         * The token "OR".
         */
        const val OR = ON + 1

        /**
         * The token "ORDER".
         */
        const val ORDER = OR + 1

        /**
         * The token "PRIMARY".
         */
        const val PRIMARY = ORDER + 1

        /**
         * The token "QUALIFY".
         */
        const val QUALIFY = PRIMARY + 1

        /**
         * The token "RIGHT".
         */
        const val RIGHT = QUALIFY + 1

        /**
         * The token "ROW".
         */
        const val ROW = RIGHT + 1

        /**
         * The token "ROWNUM".
         */
        const val ROWNUM = ROW + 1

        /**
         * The token "SECOND".
         */
        const val SECOND = ROWNUM + 1

        /**
         * The token "SELECT".
         */
        const val SELECT = SECOND + 1

        /**
         * The token "SESSION_USER".
         */
        const val SESSION_USER = SELECT + 1

        /**
         * The token "SET".
         */
        const val SET = SESSION_USER + 1

        /**
         * The token "SOME".
         */
        const val SOME = SET + 1

        /**
         * The token "SYMMETRIC".
         */
        const val SYMMETRIC = SOME + 1

        /**
         * The token "SYSTEM_USER".
         */
        const val SYSTEM_USER = SYMMETRIC + 1

        /**
         * The token "TABLE".
         */
        const val TABLE = SYSTEM_USER + 1

        /**
         * The token "TO".
         */
        const val TO = TABLE + 1

        /**
         * The token "TRUE".
         */
        const val TRUE = TO + 1

        /**
         * The token "UESCAPE".
         */
        const val UESCAPE = TRUE + 1

        /**
         * The token "UNION".
         */
        const val UNION = UESCAPE + 1

        /**
         * The token "UNIQUE".
         */
        const val UNIQUE = UNION + 1

        /**
         * The token "UNKNOWN".
         */
        const val UNKNOWN = UNIQUE + 1

        /**
         * The token "USER".
         */
        const val USER = UNKNOWN + 1

        /**
         * The token "USING".
         */
        const val USING = USER + 1

        /**
         * The token "VALUE".
         */
        const val VALUE = USING + 1

        /**
         * The token "VALUES".
         */
        const val VALUES = VALUE + 1

        /**
         * The token "WHEN".
         */
        const val WHEN = VALUES + 1

        /**
         * The token "WHERE".
         */
        const val WHERE = WHEN + 1

        /**
         * The token "WINDOW".
         */
        const val WINDOW = WHERE + 1

        /**
         * The token "WITH".
         */
        const val WITH = WINDOW + 1

        /**
         * The token "YEAR".
         */
        const val YEAR = WITH + 1

        /**
         * The token "_ROWID_".
         */
        const val _ROWID_ = YEAR + 1

        // Constants above must be sorted

        /**
         * The ordinal number of the first keyword.
         */
        const val FIRST_KEYWORD = IDENTIFIER + 1

        /**
         * The ordinal number of the last keyword.
         */
        const val LAST_KEYWORD = _ROWID_

        private val KEYWORDS: HashMap<String, Int>

        init {
            val map = HashMap<String, Int>(256)
            map["ALL"] = ALL
            map["AND"] = AND
            map["ANY"] = ANY
            map["ARRAY"] = ARRAY
            map["AS"] = AS
            map["ASYMMETRIC"] = ASYMMETRIC
            map["AUTHORIZATION"] = AUTHORIZATION
            map["BETWEEN"] = BETWEEN
            map["CASE"] = CASE
            map["CAST"] = CAST
            map["CHECK"] = CHECK
            map["CONSTRAINT"] = CONSTRAINT
            map["CROSS"] = CROSS
            map["CURRENT_CATALOG"] = CURRENT_CATALOG
            map["CURRENT_DATE"] = CURRENT_DATE
            map["CURRENT_PATH"] = CURRENT_PATH
            map["CURRENT_ROLE"] = CURRENT_ROLE
            map["CURRENT_SCHEMA"] = CURRENT_SCHEMA
            map["CURRENT_TIME"] = CURRENT_TIME
            map["CURRENT_TIMESTAMP"] = CURRENT_TIMESTAMP
            map["CURRENT_USER"] = CURRENT_USER
            map["DAY"] = DAY
            map["DEFAULT"] = DEFAULT
            map["DISTINCT"] = DISTINCT
            map["ELSE"] = ELSE
            map["END"] = END
            map["EXCEPT"] = EXCEPT
            map["EXISTS"] = EXISTS
            map["FALSE"] = FALSE
            map["FETCH"] = FETCH
            map["FOR"] = FOR
            map["FOREIGN"] = FOREIGN
            map["FROM"] = FROM
            map["FULL"] = FULL
            map["GROUP"] = GROUP
            map["HAVING"] = HAVING
            map["HOUR"] = HOUR
            map["IF"] = IF
            map["IN"] = IN
            map["INNER"] = INNER
            map["INTERSECT"] = INTERSECT
            map["INTERVAL"] = INTERVAL
            map["IS"] = IS
            map["JOIN"] = JOIN
            map["KEY"] = KEY
            map["LEFT"] = LEFT
            map["LIKE"] = LIKE
            map["LIMIT"] = LIMIT
            map["LOCALTIME"] = LOCALTIME
            map["LOCALTIMESTAMP"] = LOCALTIMESTAMP
            map["MINUS"] = MINUS
            map["MINUTE"] = MINUTE
            map["MONTH"] = MONTH
            map["NATURAL"] = NATURAL
            map["NOT"] = NOT
            map["NULL"] = NULL
            map["OFFSET"] = OFFSET
            map["ON"] = ON
            map["OR"] = OR
            map["ORDER"] = ORDER
            map["PRIMARY"] = PRIMARY
            map["QUALIFY"] = QUALIFY
            map["RIGHT"] = RIGHT
            map["ROW"] = ROW
            map["ROWNUM"] = ROWNUM
            map["SECOND"] = SECOND
            map["SELECT"] = SELECT
            map["SESSION_USER"] = SESSION_USER
            map["SET"] = SET
            map["SOME"] = SOME
            map["SYMMETRIC"] = SYMMETRIC
            map["SYSTEM_USER"] = SYSTEM_USER
            map["TABLE"] = TABLE
            map["TO"] = TO
            map["TRUE"] = TRUE
            map["UESCAPE"] = UESCAPE
            map["UNION"] = UNION
            map["UNIQUE"] = UNIQUE
            map["UNKNOWN"] = UNKNOWN
            map["USER"] = USER
            map["USING"] = USING
            map["VALUE"] = VALUE
            map["VALUES"] = VALUES
            map["WHEN"] = WHEN
            map["WHERE"] = WHERE
            map["WINDOW"] = WINDOW
            map["WITH"] = WITH
            map["YEAR"] = YEAR
            map["_ROWID_"] = _ROWID_
            // Additional keywords
            map["BOTH"] = KEYWORD
            map["GROUPS"] = KEYWORD
            map["ILIKE"] = KEYWORD
            map["LEADING"] = KEYWORD
            map["OVER"] = KEYWORD
            map["PARTITION"] = KEYWORD
            map["RANGE"] = KEYWORD
            map["REGEXP"] = KEYWORD
            map["ROWS"] = KEYWORD
            map["TOP"] = KEYWORD
            map["TRAILING"] = KEYWORD
            KEYWORDS = map
        }

        /**
         * Add double quotes around an identifier if required and appends it to the
         * specified string builder.
         *
         * @param builder string builder to append to
         * @param s the identifier
         * @param sqlFlags formatting flags
         * @return the specified builder
         */
        @JvmStatic
        fun quoteIdentifier(builder: StringBuilder, s: String?, sqlFlags: Int): StringBuilder {
            if (s == null) {
                return builder.append("\"\"")
            }
            if ((sqlFlags and HasSQL.QUOTE_ONLY_WHEN_REQUIRED) != 0 && isSimpleIdentifier(s, false, false)) {
                return builder.append(s)
            }
            return StringUtils.quoteIdentifier(builder, s)
        }

        /**
         * Checks if this string is a SQL keyword.
         *
         * @param s the token to check
         * @param ignoreCase true if case should be ignored, false if only upper case
         *            tokens are detected as keywords
         * @return true if it is a keyword
         */
        @JvmStatic
        fun isKeyword(s: String, ignoreCase: Boolean): Boolean {
            return getTokenType(s, ignoreCase, false) != IDENTIFIER
        }

        /**
         * Is this a simple identifier (in the JDBC specification sense).
         *
         * @param s identifier to check
         * @param databaseToUpper whether unquoted identifiers are converted to upper case
         * @param databaseToLower whether unquoted identifiers are converted to lower case
         * @return is specified identifier may be used without quotes
         * @throws NullPointerException if s is {@code null}
         */
        @JvmStatic
        fun isSimpleIdentifier(s: String, databaseToUpper: Boolean, databaseToLower: Boolean): Boolean {
            if (databaseToUpper && databaseToLower) {
                throw IllegalArgumentException("databaseToUpper && databaseToLower")
            }
            val length = s.length
            if (length == 0 || !checkLetter(databaseToUpper, databaseToLower, s[0])) {
                return false
            }
            for (i in 1 until length) {
                val c = s[i]
                if (c != '_' && (c < '0' || c > '9') && !checkLetter(databaseToUpper, databaseToLower, c)) {
                    return false
                }
            }
            return getTokenType(s, !databaseToUpper, true) == IDENTIFIER
        }

        private fun checkLetter(databaseToUpper: Boolean, databaseToLower: Boolean, c: Char): Boolean {
            if (databaseToUpper) {
                if (c < 'A' || c > 'Z') {
                    return false
                }
            } else if (databaseToLower) {
                if (c < 'a' || c > 'z') {
                    return false
                }
            } else {
                if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z')) {
                    return false
                }
            }
            return true
        }

        /**
         * Get the token type.
         *
         * @param s the string with token
         * @param ignoreCase true if case should be ignored, false if only upper case
         *            tokens are detected as keywords
         * @param additionalKeywords
         *            whether context-sensitive keywords are returned as
         *            {@link #KEYWORD}
         * @return the token type
         */
        @JvmStatic
        fun getTokenType(s: String, ignoreCase: Boolean, additionalKeywords: Boolean): Int {
            var token = s
            val length = token.length
            if (length <= 1 || length > 17) {
                return IDENTIFIER
            }
            if (ignoreCase) {
                token = StringUtils.toUpperEnglish(token)
            }
            val type = KEYWORDS[token] ?: return IDENTIFIER
            val t = type
            return if (t == KEYWORD && !additionalKeywords) IDENTIFIER else t
        }
    }
}
