/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf.context

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLSyntaxErrorException
import org.h2.engine.SysProperties
import org.h2.util.StringUtils.Companion.toUpperEnglish
import org.h2.util.Utils.Companion.newSmallArrayList

/**
 * Contains meta data information about a database schema.
 * This class is used by the H2 Console.
 */
class DbSchema internal constructor(
    /**
     * The database content container.
     */
    private val contents: DbContents,
    /**
     * The schema name.
     */
    @JvmField val name: String?,
    /**
     * True if this is the default schema for this database.
     */
    @JvmField val isDefault: Boolean,
) {

    /**
     * The quoted schema name.
     */
    @JvmField
    val quotedName: String? = contents.quoteIdentifier(name)

    /**
     * True if this is a system schema (for example the INFORMATION_SCHEMA).
     */
    @JvmField
    val isSystem: Boolean = when {
        name == null -> true // firebird
        "INFORMATION_SCHEMA".equals(name, ignoreCase = true) -> true
        !contents.isH2() && toUpperEnglish(name).startsWith("INFO") -> true
        contents.isPostgreSQL() && toUpperEnglish(name).startsWith("PG_") -> true
        contents.isDerby() && name.startsWith("SYS") -> true
        else -> false
    }

    /**
     * The table list.
     */
    private var tables: Array<DbTableOrView>? = null

    /**
     * The procedures list.
     */
    private var procedures: Array<DbProcedure>? = null

    /**
     * @return The database content container.
     */
    fun getContents(): DbContents = contents

    /**
     * @return The table list.
     */
    fun getTables(): Array<DbTableOrView>? = tables

    /**
     * @return The procedure list.
     */
    fun getProcedures(): Array<DbProcedure>? = procedures

    /**
     * Read all tables for this schema from the database meta data.
     *
     * @param meta the database meta data
     * @param tableTypes the table types to read
     * @throws SQLException on failure
     */
    @Throws(SQLException::class)
    fun readTables(meta: DatabaseMetaData, tableTypes: Array<String>) {
        val rs = meta.getTables(null, name, null, tableTypes)
        val list = ArrayList<DbTableOrView>()
        while (rs.next()) {
            val table = DbTableOrView(this, rs)
            if (contents.isOracle() && table.getName()!!.indexOf('$') > 0) {
                continue
            }
            list.add(table)
        }
        rs.close()
        val tables = list.toTypedArray()
        this.tables = tables
        if (tables.size < SysProperties.CONSOLE_MAX_TABLES_LIST_COLUMNS) {
            (if (contents.isH2()) prepareColumnsQueryH2(meta.connection) else null).use { ps ->
                for (tab in tables) {
                    try {
                        tab.readColumns(meta, ps)
                    } catch (e: SQLException) {
                        // MySQL:
                        // View '...' references invalid table(s) or column(s)
                        // or function(s) or definer/invoker of view
                        // lack rights to use them HY000/1356
                        // ignore
                    }
                }
            }
        }
    }

    /**
     * Read all procedures in the database.
     *
     * @param meta the database meta data
     * @throws SQLException Error while fetching procedures
     */
    @Throws(SQLException::class)
    fun readProcedures(meta: DatabaseMetaData) {
        val rs = meta.getProcedures(null, name, null)
        val list = newSmallArrayList<DbProcedure>()
        while (rs.next()) {
            list.add(DbProcedure(this, rs))
        }
        rs.close()
        val procedures = list.toTypedArray()
        this.procedures = procedures
        if (procedures.size < SysProperties.CONSOLE_MAX_PROCEDURES_LIST_COLUMNS) {
            for (procedure in procedures) {
                procedure.readParameters(meta)
            }
        }
    }

    companion object {
        private const val COLUMNS_QUERY_H2_197 = "SELECT COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE " +
                "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ?1 AND TABLE_NAME = ?2"

        private const val COLUMNS_QUERY_H2_202 = "SELECT COLUMN_NAME, ORDINAL_POSITION, " +
                "DATA_TYPE_SQL(?1, ?2, 'TABLE', ORDINAL_POSITION) COLUMN_TYPE " +
                "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ?1 AND TABLE_NAME = ?2"

        @Throws(SQLException::class)
        private fun prepareColumnsQueryH2(connection: Connection): PreparedStatement {
            return try {
                connection.prepareStatement(COLUMNS_QUERY_H2_202)
            } catch (ex: SQLSyntaxErrorException) {
                connection.prepareStatement(COLUMNS_QUERY_H2_197)
            }
        }
    }
}
