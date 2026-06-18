/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf.context

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException
import org.h2.engine.Session
import org.h2.jdbc.JdbcConnection
import org.h2.util.ParserUtil.Companion.isSimpleIdentifier
import org.h2.util.StringUtils
import org.h2.util.StringUtils.Companion.toUpperEnglish
import org.h2.util.Utils.Companion.newSmallArrayList

/**
 * Keeps meta data information about a database.
 * This class is used by the H2 Console.
 */
class DbContents {

    private var schemas: Array<DbSchema>? = null
    private var defaultSchema: DbSchema? = null
    private var isOracle = false
    private var isH2 = false
    private var isPostgreSQL = false
    private var isDerby = false
    private var isSQLite = false
    private var isMySQL = false
    private var isFirebird = false
    private var isMSSQLServer = false
    private var isDB2 = false

    private var databaseToUpper = false
    private var databaseToLower = false

    private var mayHaveStandardViews = true

    /**
     * @return the default schema.
     */
    fun getDefaultSchema(): DbSchema? = defaultSchema

    /**
     * @return true if this is an Apache Derby database.
     */
    fun isDerby(): Boolean = isDerby

    /**
     * @return true if this is a Firebird database.
     */
    fun isFirebird(): Boolean = isFirebird

    /**
     * @return true if this is a H2 database.
     */
    fun isH2(): Boolean = isH2

    /**
     * @return true if this is a MS SQL Server database.
     */
    fun isMSSQLServer(): Boolean = isMSSQLServer

    /**
     * @return true if this is a MySQL database.
     */
    fun isMySQL(): Boolean = isMySQL

    /**
     * @return true if this is an Oracle database.
     */
    fun isOracle(): Boolean = isOracle

    /**
     * @return true if this is a PostgreSQL database.
     */
    fun isPostgreSQL(): Boolean = isPostgreSQL

    /**
     * @return true if this is an SQLite database.
     */
    fun isSQLite(): Boolean = isSQLite

    /**
     * @return true if this is an IBM DB2 database.
     */
    fun isDB2(): Boolean = isDB2

    /**
     * @return the list of schemas.
     */
    fun getSchemas(): Array<DbSchema>? = schemas

    /**
     * Returns whether standard INFORMATION_SCHEMA.VIEWS may be supported.
     *
     * @return whether standard INFORMATION_SCHEMA.VIEWS may be supported
     */
    fun mayHaveStandardViews(): Boolean = mayHaveStandardViews

    /**
     * @param mayHaveStandardViews
     *            whether standard INFORMATION_SCHEMA.VIEWS is detected as
     *            supported
     */
    fun setMayHaveStandardViews(mayHaveStandardViews: Boolean) {
        this.mayHaveStandardViews = mayHaveStandardViews
    }

    /**
     * Read the contents of this database from the database meta data.
     *
     * @param url the database URL
     * @param conn the connection
     * @throws SQLException on failure
     */
    @Synchronized
    @Throws(SQLException::class)
    fun readContents(url: String, conn: Connection) {
        isH2 = url.startsWith("jdbc:h2:")
        isDB2 = url.startsWith("jdbc:db2:")
        isSQLite = url.startsWith("jdbc:sqlite:")
        isOracle = url.startsWith("jdbc:oracle:")
        // the Vertica engine is based on PostgreSQL
        isPostgreSQL = url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:vertica:")
        // isHSQLDB = url.startsWith("jdbc:hsqldb:");
        isMySQL = url.startsWith("jdbc:mysql:")
        isDerby = url.startsWith("jdbc:derby:")
        isFirebird = url.startsWith("jdbc:firebirdsql:")
        isMSSQLServer = url.startsWith("jdbc:sqlserver:")
        if (isH2) {
            val settings = (conn as JdbcConnection).staticSettings
            databaseToUpper = settings.databaseToUpper
            databaseToLower = settings.databaseToLower
        } else if (isMySQL || isPostgreSQL) {
            databaseToUpper = false
            databaseToLower = true
        } else {
            databaseToUpper = true
            databaseToLower = false
        }
        val meta = conn.metaData
        val defaultSchemaName = getDefaultSchemaName(meta)
        val schemaNames = getSchemaNames(meta)
        val schemas = arrayOfNulls<DbSchema>(schemaNames.size)
        for (i in schemaNames.indices) {
            val schemaName = schemaNames[i]
            val isDefault = defaultSchemaName == null || defaultSchemaName == schemaName
            val schema = DbSchema(this, schemaName, isDefault)
            if (isDefault) {
                defaultSchema = schema
            }
            schemas[i] = schema
            val tableTypes = arrayOf(
                "TABLE", "SYSTEM TABLE", "VIEW",
                "SYSTEM VIEW", "TABLE LINK", "SYNONYM", "EXTERNAL"
            )
            schema.readTables(meta, tableTypes)
            if (!isPostgreSQL && !isDB2) {
                schema.readProcedures(meta)
            }
        }
        @Suppress("UNCHECKED_CAST")
        this.schemas = schemas as Array<DbSchema>
        if (defaultSchema == null) {
            var best: String? = null
            for (schema in schemas) {
                if ("dbo" == schema!!.name) {
                    // MS SQL Server
                    defaultSchema = schema
                    break
                }
                if (defaultSchema == null || best == null ||
                    schema.name!!.length < best.length
                ) {
                    best = schema.name
                    defaultSchema = schema
                }
            }
        }
    }

    @Throws(SQLException::class)
    private fun getSchemaNames(meta: DatabaseMetaData): Array<String?> {
        if (isMySQL || isSQLite) {
            return arrayOf("")
        } else if (isFirebird) {
            return arrayOf(null)
        }
        val rs = meta.schemas
        val schemaList = newSmallArrayList<String>()
        while (rs.next()) {
            var schema: String? = rs.getString("TABLE_SCHEM")
            var ignoreNames: Array<String>? = null
            if (isOracle) {
                ignoreNames = arrayOf(
                    "CTXSYS", "DIP", "DBSNMP",
                    "DMSYS", "EXFSYS", "FLOWS_020100", "FLOWS_FILES",
                    "MDDATA", "MDSYS", "MGMT_VIEW", "OLAPSYS", "ORDSYS",
                    "ORDPLUGINS", "OUTLN", "SI_INFORMTN_SCHEMA", "SYS",
                    "SYSMAN", "SYSTEM", "TSMSYS", "WMSYS", "XDB"
                )
            } else if (isMSSQLServer) {
                ignoreNames = arrayOf(
                    "sys", "db_accessadmin",
                    "db_backupoperator", "db_datareader", "db_datawriter",
                    "db_ddladmin", "db_denydatareader",
                    "db_denydatawriter", "db_owner", "db_securityadmin"
                )
            } else if (isDB2) {
                ignoreNames = arrayOf(
                    "NULLID", "SYSFUN",
                    "SYSIBMINTERNAL", "SYSIBMTS", "SYSPROC", "SYSPUBLIC",
                    // not empty, but not sure what they contain
                    "SYSCAT", "SYSIBM", "SYSIBMADM",
                    "SYSSTAT", "SYSTOOLS"
                )
            }
            if (ignoreNames != null) {
                for (ignore in ignoreNames) {
                    if (ignore == schema) {
                        schema = null
                        break
                    }
                }
            }
            if (schema == null) {
                continue
            }
            schemaList.add(schema)
        }
        rs.close()
        @Suppress("UNCHECKED_CAST")
        return schemaList.toTypedArray() as Array<String?>
    }

    private fun getDefaultSchemaName(meta: DatabaseMetaData): String? {
        val defaultSchemaName = ""
        try {
            if (isH2) {
                return if (meta.storesLowerCaseIdentifiers()) "public" else "PUBLIC"
            } else if (isOracle) {
                return meta.userName
            } else if (isPostgreSQL) {
                return "public"
            } else if (isMySQL) {
                return ""
            } else if (isDerby) {
                return toUpperEnglish(meta.userName)
            } else if (isFirebird) {
                return null
            }
        } catch (e: SQLException) {
            // Ignore
        }
        return defaultSchemaName
    }

    /**
     * Add double quotes around an identifier if required.
     *
     * @param identifier the identifier
     * @return the quoted identifier
     */
    fun quoteIdentifier(identifier: String?): String? {
        if (identifier == null) {
            return null
        }
        if (isSimpleIdentifier(identifier, databaseToUpper, databaseToLower)) {
            return identifier
        }
        return StringUtils.quoteIdentifier(identifier)
    }
}
