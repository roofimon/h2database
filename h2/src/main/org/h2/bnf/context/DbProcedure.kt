/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf.context

import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import org.h2.util.Utils.Companion.newSmallArrayList

/**
 * Contains meta data information about a procedure.
 * This class is used by the H2 Console.
 */
class DbProcedure @Throws(SQLException::class) constructor(private val schema: DbSchema, rs: ResultSet) {

    private val name: String?
    private val quotedName: String?
    private val returnsResult: Boolean
    private var parameters: Array<DbColumn?>? = null

    init {
        name = rs.getString("PROCEDURE_NAME")
        returnsResult = rs.getShort("PROCEDURE_TYPE").toInt() ==
                DatabaseMetaData.procedureReturnsResult
        quotedName = schema.getContents().quoteIdentifier(name)
    }

    /**
     * @return The schema this table belongs to.
     */
    fun getSchema(): DbSchema = schema

    /**
     * @return The column list.
     */
    fun getParameters(): Array<DbColumn?>? = parameters

    /**
     * @return The table name.
     */
    fun getName(): String? = name

    /**
     * @return The quoted table name.
     */
    fun getQuotedName(): String? = quotedName

    /**
     * @return True if this function return a value
     */
    fun isReturnsResult(): Boolean = returnsResult

    /**
     * Read the column for this table from the database meta data.
     *
     * @param meta the database meta data
     * @throws SQLException on failure
     */
    @Throws(SQLException::class)
    fun readParameters(meta: DatabaseMetaData) {
        val rs = meta.getProcedureColumns(null, schema.name, name, null)
        val list = newSmallArrayList<DbColumn>()
        while (rs.next()) {
            val column = DbColumn.getProcedureColumn(schema.getContents(), rs)
            if (column.getPosition() > 0) {
                // Not the return type
                list.add(column)
            }
        }
        rs.close()
        val parameters = arrayOfNulls<DbColumn>(list.size)
        this.parameters = parameters
        // Store the parameter in the good position [1-n]
        for (i in parameters.indices) {
            val column = list[i]
            if (column.getPosition() > 0 && column.getPosition() <= parameters.size) {
                parameters[column.getPosition() - 1] = column
            }
        }
    }
}
