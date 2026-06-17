/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.util.ArrayList
import java.util.Arrays
import java.util.Comparator
import java.util.HashSet
import java.util.Properties

import javax.naming.Context
import javax.sql.DataSource

import org.h2.api.ErrorCode
import org.h2.api.JavaObjectSerializer
import org.h2.engine.Constants
import org.h2.engine.SysProperties
import org.h2.jdbc.JdbcConnection
import org.h2.jdbc.JdbcPreparedStatement
import org.h2.message.DbException
import org.h2.tools.SimpleResultSet
import org.h2.util.Bits.LONG_VH_BE
import org.h2.util.Utils.ClassFactory
import org.h2.value.Value
import org.h2.value.ValueLob
import org.h2.value.ValueToObjectConverter
import org.h2.value.ValueUuid

/**
 * This is a utility class with JDBC helper functions.
 */
class JdbcUtils private constructor() {
    // utility class

    companion object {

        /**
         * The serializer to use.
         */
        @JvmField
        var serializer: JavaObjectSerializer? = null

        private val DRIVERS = arrayOf(
            "h2:", "org.h2.Driver",
            "Cache:", "com.intersys.jdbc.CacheDriver",
            "daffodilDB://", "in.co.daffodil.db.rmi.RmiDaffodilDBDriver",
            "daffodil", "in.co.daffodil.db.jdbc.DaffodilDBDriver",
            "db2:", "com.ibm.db2.jcc.DB2Driver",
            "derby:net:", "org.apache.derby.client.ClientAutoloadedDriver",
            "derby://", "org.apache.derby.client.ClientAutoloadedDriver",
            "derby:", "org.apache.derby.iapi.jdbc.AutoloadedDriver",
            "FrontBase:", "com.frontbase.jdbc.FBJDriver",
            "firebirdsql:", "org.firebirdsql.jdbc.FBDriver",
            "hsqldb:", "org.hsqldb.jdbcDriver",
            "informix-sqli:", "com.informix.jdbc.IfxDriver",
            "jtds:", "net.sourceforge.jtds.jdbc.Driver",
            "microsoft:", "com.microsoft.jdbc.sqlserver.SQLServerDriver",
            "mimer:", "com.mimer.jdbc.Driver",
            "mysql:", "com.mysql.cj.jdbc.Driver",
            "mariadb:", "org.mariadb.jdbc.Driver",
            "odbc:", "sun.jdbc.odbc.JdbcOdbcDriver",
            "oracle:", "oracle.jdbc.driver.OracleDriver",
            "pervasive:", "com.pervasive.jdbc.v2.Driver",
            "pointbase:micro:", "com.pointbase.me.jdbc.jdbcDriver",
            "pointbase:", "com.pointbase.jdbc.jdbcUniversalDriver",
            "postgresql:", "org.postgresql.Driver",
            "sybase:", "com.sybase.jdbc3.jdbc.SybDriver",
            "sqlserver:", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "teradata:", "com.ncr.teradata.TeraDriver"
        )

        private val UUID_PREFIX = byteArrayOf(
            -84, -19, 0, 5, 115, 114, 0, 14, 106, 97, 118, 97, 46, 117, 116, 105, 108, 46, 85, 85, 73, 68,
            -68, -103, 3, -9, -104, 109, -123, 47, 2, 0, 2, 74, 0, 12, 108, 101, 97, 115, 116, 83, 105, 103,
            66, 105, 116, 115, 74, 0, 11, 109, 111, 115, 116, 83, 105, 103, 66, 105, 116, 115, 120, 112
        )

        private var allowAllClasses = false
        private var allowedClassNames: HashSet<String>? = null

        /**
         *  In order to manage more than one class loader
         */
        private val userClassFactories = ArrayList<ClassFactory>()

        private var allowedClassNamePrefixes: Array<String>? = null

        init {
            val clazz = SysProperties.JAVA_OBJECT_SERIALIZER
            if (clazz != null) {
                try {
                    serializer = loadUserClass<JavaObjectSerializer>(clazz).getDeclaredConstructor().newInstance()
                } catch (e: Exception) {
                    throw DbException.convert(e)
                }
            }
        }

        /**
         * Add a class factory in order to manage more than one class loader.
         *
         * @param classFactory An object that implements ClassFactory
         */
        @JvmStatic
        fun addClassFactory(classFactory: ClassFactory) {
            userClassFactories.add(classFactory)
        }

        /**
         * Remove a class factory
         *
         * @param classFactory Already inserted class factory instance
         */
        @JvmStatic
        fun removeClassFactory(classFactory: ClassFactory) {
            userClassFactories.remove(classFactory)
        }

        /**
         * Load a class, but check if it is allowed to load this class first. To
         * perform access rights checking, the system property h2.allowedClasses
         * needs to be set to a list of class file name prefixes.
         *
         * @param <Z> generic return type
         * @param className the name of the class
         * @return the class object
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <Z> loadUserClass(className: String): Class<Z> {
            if (allowedClassNames == null) {
                // initialize the static fields
                val s = SysProperties.ALLOWED_CLASSES
                val prefixes = ArrayList<String>()
                var allowAll = false
                val classNames = HashSet<String>()
                for (p in StringUtils.arraySplit(s, ',', true)!!) {
                    if (p == "*") {
                        allowAll = true
                    } else if (p.endsWith("*")) {
                        prefixes.add(p.substring(0, p.length - 1))
                    } else {
                        classNames.add(p)
                    }
                }
                allowedClassNamePrefixes = prefixes.toTypedArray()
                allowAllClasses = allowAll
                allowedClassNames = classNames
            }
            if (!allowAllClasses && !allowedClassNames!!.contains(className)) {
                var allowed = false
                for (s in allowedClassNamePrefixes!!) {
                    if (className.startsWith(s)) {
                        allowed = true
                        break
                    }
                }
                if (!allowed) {
                    throw DbException.get(
                        ErrorCode.ACCESS_DENIED_TO_CLASS_1, className
                    )
                }
            }
            // Use provided class factory first.
            for (classFactory in userClassFactories) {
                if (classFactory.match(className)) {
                    try {
                        val userClass = classFactory.loadClass(className)
                        if (userClass != null) {
                            return userClass as Class<Z>
                        }
                    } catch (e: Exception) {
                        throw DbException.get(
                            ErrorCode.CLASS_NOT_FOUND_1, e, className
                        )
                    }
                }
            }
            // Use local ClassLoader
            try {
                return Class.forName(className) as Class<Z>
            } catch (e: ClassNotFoundException) {
                try {
                    return Class.forName(
                        className, true,
                        Thread.currentThread().contextClassLoader
                    ) as Class<Z>
                } catch (e2: Exception) {
                    throw DbException.get(
                        ErrorCode.CLASS_NOT_FOUND_1, e, className
                    )
                }
            } catch (e: NoClassDefFoundError) {
                throw DbException.get(
                    ErrorCode.CLASS_NOT_FOUND_1, e, className
                )
            } catch (e: Error) {
                // UnsupportedClassVersionError
                throw DbException.get(
                    ErrorCode.GENERAL_ERROR_1, e, className
                )
            }
        }

        /**
         * Close a statement without throwing an exception.
         *
         * @param stat the statement or null
         */
        @JvmStatic
        fun closeSilently(stat: Statement?) {
            if (stat != null) {
                try {
                    stat.close()
                } catch (e: SQLException) {
                    // ignore
                }
            }
        }

        /**
         * Close a connection without throwing an exception.
         *
         * @param conn the connection or null
         */
        @JvmStatic
        fun closeSilently(conn: Connection?) {
            if (conn != null) {
                try {
                    conn.close()
                } catch (e: SQLException) {
                    // ignore
                }
            }
        }

        /**
         * Close a result set without throwing an exception.
         *
         * @param rs the result set or null
         */
        @JvmStatic
        fun closeSilently(rs: ResultSet?) {
            if (rs != null) {
                try {
                    rs.close()
                } catch (e: SQLException) {
                    // ignore
                }
            }
        }

        /**
         * Open a new database connection with the given settings.
         *
         * @param driver the driver class name
         * @param url the database URL
         * @param user the username
         * @param password the password
         * @return the database connection
         * @throws SQLException on failure
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun getConnection(driver: String?, url: String, user: String?, password: String?): Connection {
            return getConnection(driver, url, user, password, null, false)
        }

        /**
         * Open a new database connection with the given settings.
         *
         * @param driver the driver class name
         * @param url the database URL
         * @param user the username or {@code null}
         * @param password the password or {@code null}
         * @param networkConnectionInfo the network connection information, or {@code null}
         * @param forbidCreation whether database creation is forbidden
         * @return the database connection
         * @throws SQLException on failure
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun getConnection(
            driver: String?, url: String, user: String?, password: String?,
            networkConnectionInfo: NetworkConnectionInfo?, forbidCreation: Boolean
        ): Connection {
            if (url.startsWith(Constants.START_URL)) {
                val connection = JdbcConnection(url, null, user, password, forbidCreation)
                if (networkConnectionInfo != null) {
                    connection.getSession().setNetworkConnectionInfo(networkConnectionInfo)
                }
                return connection
            }
            if (StringUtils.isNullOrEmpty(driver)) {
                load(url)
            } else {
                val d = loadUserClass<Any>(driver!!)
                try {
                    if (java.sql.Driver::class.java.isAssignableFrom(d)) {
                        val driverInstance = d.getDeclaredConstructor().newInstance() as Driver
                        val prop = Properties()
                        if (user != null) {
                            prop.setProperty("user", user)
                        }
                        if (password != null) {
                            prop.setProperty("password", password)
                        }
                        /*
                         * fix issue #695 with drivers with the same jdbc
                         * subprotocol in classpath of jdbc drivers (as example
                         * redshift and postgresql drivers)
                         */
                        val connection = driverInstance.connect(url, prop)
                        if (connection != null) {
                            return connection
                        }
                        throw SQLException("Driver $driver is not suitable for $url", "08001")
                    } else if (javax.naming.Context::class.java.isAssignableFrom(d)) {
                        if (!url.startsWith("java:")) {
                            throw SQLException("Only java scheme is supported for JNDI lookups", "08001")
                        }
                        // JNDI context
                        val context = d.getDeclaredConstructor().newInstance() as Context
                        val ds = context.lookup(url) as DataSource
                        if (StringUtils.isNullOrEmpty(user) && StringUtils.isNullOrEmpty(password)) {
                            return ds.connection
                        }
                        return ds.getConnection(user, password)
                    }
                } catch (e: Exception) {
                    throw DbException.toSQLException(e)
                }
                // don't know, but maybe it loaded a JDBC Driver
            }
            return DriverManager.getConnection(url, user, password)
        }

        /**
         * Get the driver class name for the given URL, or null if the URL is
         * unknown.
         *
         * @param url the database URL
         * @return the driver class name
         */
        @JvmStatic
        fun getDriver(url: String): String? {
            var u = url
            if (u.startsWith("jdbc:")) {
                u = u.substring("jdbc:".length)
                var i = 0
                while (i < DRIVERS.size) {
                    val prefix = DRIVERS[i]
                    if (u.startsWith(prefix)) {
                        return DRIVERS[i + 1]
                    }
                    i += 2
                }
            }
            return null
        }

        /**
         * Load the driver class for the given URL, if the database URL is known.
         *
         * @param url the database URL
         */
        @JvmStatic
        fun load(url: String) {
            val driver = getDriver(url)
            if (driver != null) {
                loadUserClass<Any>(driver)
            }
        }

        /**
         * Serialize the object to a byte array, using the serializer specified by
         * the connection info if set, or the default serializer.
         *
         * @param obj the object to serialize
         * @param javaObjectSerializer the object serializer (might be null)
         * @return the byte array
         */
        @JvmStatic
        fun serialize(obj: Any?, javaObjectSerializer: JavaObjectSerializer?): ByteArray {
            try {
                if (javaObjectSerializer != null) {
                    return javaObjectSerializer.serialize(obj)
                }
                if (serializer != null) {
                    return serializer!!.serialize(obj)
                }
                val out = ByteArrayOutputStream()
                val os = ObjectOutputStream(out)
                os.writeObject(obj)
                return out.toByteArray()
            } catch (e: Throwable) {
                throw DbException.get(ErrorCode.SERIALIZATION_FAILED_1, e, e.toString())
            }
        }

        /**
         * De-serialize the byte array to an object, eventually using the serializer
         * specified by the connection info.
         *
         * @param data the byte array
         * @param javaObjectSerializer the object serializer (might be null)
         * @return the object
         * @throws DbException if serialization fails
         */
        @JvmStatic
        fun deserialize(data: ByteArray, javaObjectSerializer: JavaObjectSerializer?): Any? {
            try {
                if (javaObjectSerializer != null) {
                    return javaObjectSerializer.deserialize(data)
                }
                if (serializer != null) {
                    return serializer!!.deserialize(data)
                }
                val `in` = ByteArrayInputStream(data)
                val `is`: ObjectInputStream
                if (SysProperties.USE_THREAD_CONTEXT_CLASS_LOADER) {
                    val loader = Thread.currentThread().contextClassLoader
                    `is` = object : ObjectInputStream(`in`) {
                        @Throws(IOException::class, ClassNotFoundException::class)
                        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
                            return try {
                                Class.forName(desc.name, true, loader)
                            } catch (e: ClassNotFoundException) {
                                super.resolveClass(desc)
                            }
                        }
                    }
                } else {
                    `is` = ObjectInputStream(`in`)
                }
                return `is`.readObject()
            } catch (e: Throwable) {
                throw DbException.get(ErrorCode.DESERIALIZATION_FAILED_1, e, e.toString())
            }
        }

        /**
         * De-serialize the byte array to a UUID object. This method is called on
         * the server side where regular de-serialization of user-supplied Java
         * objects may create a security hole if object was maliciously crafted.
         * Unlike {@link #deserialize(byte[], JavaObjectSerializer)}, this method
         * does not try to de-serialize instances of other classes.
         *
         * @param data the byte array
         * @return the UUID object
         * @throws DbException if serialization fails
         */
        @JvmStatic
        fun deserializeUuid(data: ByteArray): ValueUuid {
            if (data.size == 80 && Arrays.mismatch(data, 0, 64, UUID_PREFIX, 0, 64) < 0) {
                return ValueUuid.get(LONG_VH_BE.get(data, 72) as Long, LONG_VH_BE.get(data, 64) as Long)
            }
            throw DbException.get(ErrorCode.DESERIALIZATION_FAILED_1, "Is not a UUID")
        }

        /**
         * Set a value as a parameter in a prepared statement.
         *
         * @param prep the prepared statement
         * @param parameterIndex the parameter index
         * @param value the value
         * @param conn the own connection
         * @throws SQLException on failure
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun set(prep: PreparedStatement, parameterIndex: Int, value: Value, conn: JdbcConnection) {
            if (prep is JdbcPreparedStatement) {
                if (value is ValueLob) {
                    setLob(prep, parameterIndex, value)
                } else {
                    prep.setObject(parameterIndex, value)
                }
            } else {
                setOther(prep, parameterIndex, value, conn)
            }
        }

        @Throws(SQLException::class)
        private fun setOther(prep: PreparedStatement, parameterIndex: Int, value: Value, conn: JdbcConnection) {
            val valueType = value.getValueType()
            when (valueType) {
                Value.NULL ->
                    prep.setNull(parameterIndex, Types.NULL)
                Value.BOOLEAN ->
                    prep.setBoolean(parameterIndex, value.getBoolean())
                Value.TINYINT ->
                    prep.setByte(parameterIndex, value.getByte())
                Value.SMALLINT ->
                    prep.setShort(parameterIndex, value.getShort())
                Value.INTEGER ->
                    prep.setInt(parameterIndex, value.getInt())
                Value.BIGINT ->
                    prep.setLong(parameterIndex, value.getLong())
                Value.NUMERIC, Value.DECFLOAT ->
                    prep.setBigDecimal(parameterIndex, value.getBigDecimal())
                Value.DOUBLE ->
                    prep.setDouble(parameterIndex, value.getDouble())
                Value.REAL ->
                    prep.setFloat(parameterIndex, value.getFloat())
                Value.TIME ->
                    try {
                        prep.setObject(parameterIndex, JSR310Utils.valueToLocalTime(value, null), Types.TIME)
                    } catch (ignore: SQLException) {
                        prep.setTime(parameterIndex, LegacyDateTimeUtils.toTime(null, null, value))
                    }
                Value.DATE ->
                    try {
                        prep.setObject(parameterIndex, JSR310Utils.valueToLocalDate(value, null), Types.DATE)
                    } catch (ignore: SQLException) {
                        prep.setDate(parameterIndex, LegacyDateTimeUtils.toDate(null, null, value))
                    }
                Value.TIMESTAMP ->
                    try {
                        prep.setObject(parameterIndex, JSR310Utils.valueToLocalDateTime(value, null), Types.TIMESTAMP)
                    } catch (ignore: SQLException) {
                        prep.setTimestamp(parameterIndex, LegacyDateTimeUtils.toTimestamp(null, null, value))
                    }
                Value.VARBINARY, Value.BINARY, Value.GEOMETRY, Value.JSON ->
                    prep.setBytes(parameterIndex, value.getBytesNoCopy())
                Value.VARCHAR, Value.VARCHAR_IGNORECASE, Value.ENUM,
                Value.INTERVAL_YEAR, Value.INTERVAL_MONTH, Value.INTERVAL_DAY,
                Value.INTERVAL_HOUR, Value.INTERVAL_MINUTE, Value.INTERVAL_SECOND,
                Value.INTERVAL_YEAR_TO_MONTH, Value.INTERVAL_DAY_TO_HOUR,
                Value.INTERVAL_DAY_TO_MINUTE, Value.INTERVAL_DAY_TO_SECOND,
                Value.INTERVAL_HOUR_TO_MINUTE, Value.INTERVAL_HOUR_TO_SECOND,
                Value.INTERVAL_MINUTE_TO_SECOND ->
                    prep.setString(parameterIndex, value.getString())
                Value.BLOB, Value.CLOB ->
                    setLob(prep, parameterIndex, value as ValueLob)
                Value.ARRAY ->
                    prep.setArray(
                        parameterIndex, prep.connection.createArrayOf(
                            "NULL",
                            ValueToObjectConverter.valueToDefaultObject(value, conn, true) as Array<Any?>
                        )
                    )
                Value.JAVA_OBJECT ->
                    prep.setObject(
                        parameterIndex,
                        deserialize(value.getBytesNoCopy(), conn.getJavaObjectSerializer()),
                        Types.JAVA_OBJECT
                    )
                Value.UUID ->
                    prep.setBytes(parameterIndex, value.getBytes())
                Value.CHAR ->
                    try {
                        prep.setObject(parameterIndex, value.getString(), Types.CHAR)
                    } catch (ignore: SQLException) {
                        prep.setString(parameterIndex, value.getString())
                    }
                Value.TIMESTAMP_TZ ->
                    try {
                        prep.setObject(
                            parameterIndex, JSR310Utils.valueToOffsetDateTime(value, null),
                            Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        return
                    } catch (ignore: SQLException) {
                        prep.setString(parameterIndex, value.getString())
                    }
                Value.TIME_TZ ->
                    try {
                        prep.setObject(parameterIndex, JSR310Utils.valueToOffsetTime(value, null), Types.TIME_WITH_TIMEZONE)
                        return
                    } catch (ignore: SQLException) {
                        prep.setString(parameterIndex, value.getString())
                    }
                else ->
                    throw DbException.getUnsupportedException(Value.getTypeName(valueType))
            }
        }

        @Throws(SQLException::class)
        private fun setLob(prep: PreparedStatement, parameterIndex: Int, value: ValueLob) {
            if (value.getValueType() == Value.BLOB) {
                val p = value.octetLength()
                prep.setBinaryStream(parameterIndex, value.getInputStream(), if (p > Integer.MAX_VALUE) -1 else p.toInt())
            } else {
                val p = value.charLength()
                prep.setCharacterStream(parameterIndex, value.getReader(), if (p > Integer.MAX_VALUE) -1 else p.toInt())
            }
        }

        /**
         * Get metadata from the database.
         *
         * @param conn the connection
         * @param sql the SQL statement
         * @return the metadata
         * @throws SQLException on failure
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun getMetaResultSet(conn: Connection, sql: String): ResultSet? {
            val meta = conn.metaData
            if (isBuiltIn(sql, "@best_row_identifier")) {
                val p = split(sql)
                val scale = if (p[4] == null) 0 else Integer.parseInt(p[4])
                val nullable = java.lang.Boolean.parseBoolean(p[5])
                return meta.getBestRowIdentifier(p[1], p[2], p[3], scale, nullable)
            } else if (isBuiltIn(sql, "@catalogs")) {
                return meta.catalogs
            } else if (isBuiltIn(sql, "@columns")) {
                val p = split(sql)
                return meta.getColumns(p[1], p[2], p[3], p[4])
            } else if (isBuiltIn(sql, "@column_privileges")) {
                val p = split(sql)
                return meta.getColumnPrivileges(p[1], p[2], p[3], p[4])
            } else if (isBuiltIn(sql, "@cross_references")) {
                val p = split(sql)
                return meta.getCrossReference(p[1], p[2], p[3], p[4], p[5], p[6])
            } else if (isBuiltIn(sql, "@exported_keys")) {
                val p = split(sql)
                return meta.getExportedKeys(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@imported_keys")) {
                val p = split(sql)
                return meta.getImportedKeys(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@index_info")) {
                val p = split(sql)
                val unique = java.lang.Boolean.parseBoolean(p[4])
                val approx = java.lang.Boolean.parseBoolean(p[5])
                return meta.getIndexInfo(p[1], p[2], p[3], unique, approx)
            } else if (isBuiltIn(sql, "@primary_keys")) {
                val p = split(sql)
                return meta.getPrimaryKeys(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@procedures")) {
                val p = split(sql)
                return meta.getProcedures(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@procedure_columns")) {
                val p = split(sql)
                return meta.getProcedureColumns(p[1], p[2], p[3], p[4])
            } else if (isBuiltIn(sql, "@schemas")) {
                return meta.schemas
            } else if (isBuiltIn(sql, "@tables")) {
                val p = split(sql)
                val types = if (p[4] == null) null else StringUtils.arraySplit(p[4], ',', false)
                return meta.getTables(p[1], p[2], p[3], types)
            } else if (isBuiltIn(sql, "@table_privileges")) {
                val p = split(sql)
                return meta.getTablePrivileges(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@table_types")) {
                return meta.tableTypes
            } else if (isBuiltIn(sql, "@type_info")) {
                return meta.typeInfo
            } else if (isBuiltIn(sql, "@udts")) {
                val p = split(sql)
                val types: kotlin.IntArray?
                if (p[4] == null) {
                    types = null
                } else {
                    val t = StringUtils.arraySplit(p[4], ',', false)!!
                    types = kotlin.IntArray(t.size)
                    for (i in t.indices) {
                        types[i] = Integer.parseInt(t[i])
                    }
                }
                return meta.getUDTs(p[1], p[2], p[3], types)
            } else if (isBuiltIn(sql, "@version_columns")) {
                val p = split(sql)
                return meta.getVersionColumns(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@memory")) {
                val rs = SimpleResultSet()
                rs.addColumn("Type", Types.VARCHAR, 0, 0)
                rs.addColumn("KB", Types.VARCHAR, 0, 0)
                rs.addRow("Used Memory", java.lang.Long.toString(Utils.getMemoryUsed()))
                rs.addRow("Free Memory", java.lang.Long.toString(Utils.getMemoryFree()))
                return rs
            } else if (isBuiltIn(sql, "@info")) {
                val rs = SimpleResultSet()
                rs.addColumn("KEY", Types.VARCHAR, 0, 0)
                rs.addColumn("VALUE", Types.VARCHAR, 0, 0)
                rs.addRow("conn.getCatalog", conn.catalog)
                rs.addRow("conn.getAutoCommit", java.lang.Boolean.toString(conn.autoCommit))
                rs.addRow("conn.getTransactionIsolation", Integer.toString(conn.transactionIsolation))
                rs.addRow("conn.getWarnings", java.lang.String.valueOf(conn.warnings))
                var map: String
                try {
                    map = java.lang.String.valueOf(conn.typeMap)
                } catch (e: SQLException) {
                    map = e.toString()
                }
                rs.addRow("conn.getTypeMap", map)
                rs.addRow("conn.isReadOnly", java.lang.Boolean.toString(conn.isReadOnly))
                rs.addRow("conn.getHoldability", Integer.toString(conn.holdability))
                addDatabaseMetaData(rs, meta)
                return rs
            } else if (isBuiltIn(sql, "@attributes")) {
                val p = split(sql)
                return meta.getAttributes(p[1], p[2], p[3], p[4])
            } else if (isBuiltIn(sql, "@super_tables")) {
                val p = split(sql)
                return meta.getSuperTables(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@super_types")) {
                val p = split(sql)
                return meta.getSuperTypes(p[1], p[2], p[3])
            } else if (isBuiltIn(sql, "@pseudo_columns")) {
                val p = split(sql)
                return meta.getPseudoColumns(p[1], p[2], p[3], p[4])
            }
            return null
        }

        private fun addDatabaseMetaData(rs: SimpleResultSet, meta: DatabaseMetaData) {
            val methods = DatabaseMetaData::class.java.declaredMethods
            Arrays.sort(methods, Comparator.comparing { m: Method -> m.toString() })
            for (m in methods) {
                if (m.parameterTypes.size == 0) {
                    try {
                        val o = m.invoke(meta)
                        rs.addRow("meta." + m.name, java.lang.String.valueOf(o))
                    } catch (e: InvocationTargetException) {
                        rs.addRow("meta." + m.name, e.targetException.toString())
                    } catch (e: Exception) {
                        rs.addRow("meta." + m.name, e.toString())
                    }
                }
            }
        }

        /**
         * Check is the SQL string starts with a prefix (case-insensitive).
         *
         * @param sql the SQL statement
         * @param builtIn the prefix
         * @return true if yes
         */
        @JvmStatic
        fun isBuiltIn(sql: String, builtIn: String): Boolean {
            return sql.regionMatches(0, builtIn, 0, builtIn.length, ignoreCase = true)
        }

        /**
         * Split the string using the space separator into at least 10 entries.
         *
         * @param s the string
         * @return the array
         */
        @JvmStatic
        fun split(s: String): Array<String?> {
            val t = StringUtils.arraySplit(s, ' ', true)!!
            val list = arrayOfNulls<String>(Math.max(10, t.size))
            System.arraycopy(t, 0, list, 0, t.size)
            for (i in list.indices) {
                if ("null" == list[i]) {
                    list[i] = null
                }
            }
            return list
        }
    }
}
