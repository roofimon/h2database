/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf.context

import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Contains metadata information about a table or a view.
 * This class is used by the H2 Console.
 */
class DbTableOrView @Throws(SQLException::class) constructor(
    /**
     * The schema this table belongs to.
     */
    private val schema: DbSchema,
    rs: ResultSet,
) {

    /**
     * The table name.
     */
    private val name: String?

    /**
     * The quoted table name.
     */
    private val quotedName: String?

    /**
     * True if this represents a view.
     */
    private val isView: Boolean

    /**
     * The column list.
     */
    private var columns: Array<DbColumn>? = null

    init {
        name = rs.getString("TABLE_NAME")
        val type = rs.getString("TABLE_TYPE")
        isView = "VIEW" == type
        quotedName = schema.getContents().quoteIdentifier(name)
    }

    /**
     * @return The schema this table belongs to.
     */
    fun getSchema(): DbSchema = schema

    /**
     * @return The column list.
     */
    fun getColumns(): Array<DbColumn>? = columns

    /**
     * @return The table name.
     */
    fun getName(): String? = name

    /**
     * @return True if this represents a view.
     */
    fun isView(): Boolean = isView

    /**
     * @return The quoted table name.
     */
    fun getQuotedName(): String? = quotedName

    /**
     * Read the column for this table from the database meta data.
     *
     * @param meta the database meta data
     * @param ps prepared statement with custom query for H2 database, null for
     *           others
     * @throws SQLException on failure
     */
    @Throws(SQLException::class)
    fun readColumns(meta: DatabaseMetaData, ps: PreparedStatement?) {
        val rs: ResultSet
        if (schema.getContents().isH2()) {
            ps!!.setString(1, schema.name)
            ps.setString(2, name)
            rs = ps.executeQuery()
        } else {
            rs = meta.getColumns(null, schema.name, name, null)
        }
        val list = ArrayList<DbColumn>()
        while (rs.next()) {
            val column = DbColumn.getColumn(schema.getContents(), rs)
            list.add(column)
        }
        rs.close()
        columns = list.toTypedArray()
    }
}
