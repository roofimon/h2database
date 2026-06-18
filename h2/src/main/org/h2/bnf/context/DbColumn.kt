/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf.context

import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Keeps the meta data information of a column.
 * This class is used by the H2 Console.
 */
class DbColumn private constructor(contents: DbContents, rs: ResultSet, procedureColumn: Boolean) {

    private val name: String?

    private val quotedName: String?

    private val dataType: String?

    private val position: Int

    init {
        name = rs.getString("COLUMN_NAME")
        quotedName = contents.quoteIdentifier(name)
        position = rs.getInt("ORDINAL_POSITION")
        if (contents.isH2() && !procedureColumn) {
            dataType = rs.getString("COLUMN_TYPE")
        } else {
            var type = rs.getString("TYPE_NAME")
            // a procedures column size is identified by PRECISION, for table this
            // is COLUMN_SIZE
            val precisionColumnName: String
            val scaleColumnName: String
            if (procedureColumn) {
                precisionColumnName = "PRECISION"
                scaleColumnName = "SCALE"
            } else {
                precisionColumnName = "COLUMN_SIZE"
                scaleColumnName = "DECIMAL_DIGITS"
            }
            val precision = rs.getInt(precisionColumnName)
            if (precision > 0 && !contents.isSQLite()) {
                val scale = rs.getInt(scaleColumnName)
                type = if (scale > 0) {
                    "$type($precision, $scale)"
                } else {
                    "$type($precision)"
                }
            }
            if (rs.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls) {
                type += " NOT NULL"
            }
            dataType = type
        }
    }

    /**
     * @return The data type name (including precision and the NOT NULL flag if
     * applicable).
     */
    fun getDataType(): String? = dataType

    /**
     * @return The column name.
     */
    fun getName(): String? = name

    /**
     * @return The quoted table name.
     */
    fun getQuotedName(): String? = quotedName

    /**
     * @return Column index
     */
    fun getPosition(): Int = position

    companion object {
        /**
         * Create a column from a DatabaseMetaData.getProcedureColumns row.
         *
         * @param contents the database contents
         * @param rs the result set
         * @return the column
         * @throws SQLException on failure
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun getProcedureColumn(contents: DbContents, rs: ResultSet): DbColumn {
            return DbColumn(contents, rs, true)
        }

        /**
         * Create a column from a DatabaseMetaData.getColumns row.
         *
         * @param contents the database contents
         * @param rs the result set
         * @return the column
         * @throws SQLException on failure
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun getColumn(contents: DbContents, rs: ResultSet): DbColumn {
            return DbColumn(contents, rs, false)
        }
    }
}
