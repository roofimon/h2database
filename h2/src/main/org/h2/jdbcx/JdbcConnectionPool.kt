/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: Christian d'Heureuse, www.source-code.biz
 *
 * This class is multi-licensed under LGPL, MPL 2.0, and EPL 1.0.
 *
 * This module is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 * See https://www.gnu.org/licenses/lgpl-3.0.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 */
package org.h2.jdbcx

import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLException
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

import javax.sql.ConnectionEvent
import javax.sql.ConnectionEventListener
import javax.sql.ConnectionPoolDataSource
import javax.sql.DataSource
import javax.sql.PooledConnection

import org.h2.message.DbException

/**
 * A simple standalone JDBC connection pool.
 * It is based on the
 * <a href="http://www.source-code.biz/snippets/java/8.htm">
 *  MiniConnectionPoolManager written by Christian d'Heureuse (Java 1.5)
 * </a>. It is used as follows:
 * <pre>
 * import java.sql.*;
 * import org.h2.jdbcx.JdbcConnectionPool;
 * public class Test {
 *     public static void main(String... args) throws Exception {
 *         JdbcConnectionPool cp = JdbcConnectionPool.create(
 *             "jdbc:h2:~/test", "sa", "sa");
 *         for (String sql : args) {
 *             Connection conn = cp.getConnection();
 *             conn.createStatement().execute(sql);
 *             conn.close();
 *         }
 *         cp.dispose();
 *     }
 * }
 * </pre>
 *
 * @author Christian d'Heureuse
 *      (<a href="http://www.source-code.biz">www.source-code.biz</a>)
 * @author Thomas Mueller
 */
class JdbcConnectionPool private constructor(private val dataSource: ConnectionPoolDataSource?) :
    DataSource, ConnectionEventListener, JdbcConnectionPoolBackwardsCompat {

    private val recycledConnections: Queue<PooledConnection> = ConcurrentLinkedQueue()
    private var logWriter: PrintWriter? = null

    @Volatile
    private var maxConnections = DEFAULT_MAX_CONNECTIONS

    @Volatile
    private var timeout = DEFAULT_TIMEOUT
    private val activeConnections = AtomicInteger()
    private val isDisposed = AtomicBoolean()

    init {
        if (dataSource != null) {
            try {
                logWriter = dataSource.logWriter
            } catch (e: SQLException) {
                // ignore
            }
        }
    }

    /**
     * Sets the maximum number of connections to use from now on.
     * The default value is 10 connections.
     *
     * @param max the maximum number of connections
     */
    fun setMaxConnections(max: Int) {
        require(max >= 1) { "Invalid maxConnections value: $max" }
        this.maxConnections = max
    }

    /**
     * Gets the maximum number of connections to use.
     *
     * @return the max the maximum number of connections
     */
    fun getMaxConnections(): Int {
        return maxConnections
    }

    /**
     * Gets the maximum time in seconds to wait for a free connection.
     *
     * @return the timeout in seconds
     */
    override fun getLoginTimeout(): Int {
        return timeout
    }

    /**
     * Sets the maximum time in seconds to wait for a free connection.
     * The default timeout is 30 seconds. Calling this method with the
     * value 0 will set the timeout to the default value.
     *
     * @param seconds the timeout, 0 meaning the default
     */
    override fun setLoginTimeout(seconds: Int) {
        var s = seconds
        if (s == 0) {
            s = DEFAULT_TIMEOUT
        }
        this.timeout = s
    }

    /**
     * Closes all unused pooled connections.
     * Exceptions while closing are written to the log stream (if set).
     */
    fun dispose() {
        isDisposed.set(true)

        var pc: PooledConnection?
        while (recycledConnections.poll().also { pc = it } != null) {
            closeConnection(pc!!)
        }
    }

    /**
     * Retrieves a connection from the connection pool. If
     * `maxConnections` connections are already in use, the method
     * waits until a connection becomes available or `timeout`
     * seconds elapsed. When the application is finished using the connection,
     * it must close it in order to return it to the pool.
     * If no connection becomes available within the given timeout, an exception
     * with SQL state 08001 and vendor code 8001 is thrown.
     *
     * @return a new Connection object.
     * @throws SQLException when a new connection could not be established,
     *      or a timeout occurred
     */
    @Throws(SQLException::class)
    override fun getConnection(): Connection {
        val max = System.nanoTime() + timeout * 1_000_000_000L
        var spin = 0
        do {
            if (activeConnections.incrementAndGet() <= maxConnections) {
                try {
                    return getConnectionNow()
                } catch (t: Throwable) {
                    activeConnections.decrementAndGet()
                    throw t
                }
            } else {
                activeConnections.decrementAndGet()
            }
            if (--spin >= 0) {
                continue
            }
            try {
                spin = 3
                Thread.sleep(1)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        } while (System.nanoTime() - max <= 0)
        throw SQLException("Login timeout", "08001", 8001)
    }

    /**
     * INTERNAL
     */
    @Throws(SQLException::class)
    override fun getConnection(user: String?, password: String?): Connection {
        throw UnsupportedOperationException()
    }

    @Throws(SQLException::class)
    private fun getConnectionNow(): Connection {
        check(!isDisposed.get()) { "Connection pool has been disposed." }
        var pc = recycledConnections.poll()
        if (pc == null) {
            pc = dataSource!!.pooledConnection
        }
        val conn = pc.connection
        pc.addConnectionEventListener(this)
        return conn
    }

    /**
     * This method usually puts the connection back into the pool. There are
     * some exceptions: if the pool is disposed, the connection is disposed as
     * well. If the pool is full, the connection is closed.
     *
     * @param pc the pooled connection
     */
    private fun recycleConnection(pc: PooledConnection) {
        val active = activeConnections.decrementAndGet()
        if (active < 0) {
            activeConnections.incrementAndGet()
            throw AssertionError()
        }
        if (!isDisposed.get() && active < maxConnections) {
            recycledConnections.add(pc)
            if (isDisposed.get()) {
                dispose()
            }
        } else {
            closeConnection(pc)
        }
    }

    private fun closeConnection(pc: PooledConnection) {
        try {
            pc.close()
        } catch (e: SQLException) {
            logWriter?.let { e.printStackTrace(it) }
        }
    }

    /**
     * INTERNAL
     */
    override fun connectionClosed(event: ConnectionEvent) {
        val pc = event.source as PooledConnection
        pc.removeConnectionEventListener(this)
        recycleConnection(pc)
    }

    /**
     * INTERNAL
     */
    override fun connectionErrorOccurred(event: ConnectionEvent) {
        // not used
    }

    /**
     * Returns the number of active (open) connections of this pool. This is the
     * number of `Connection` objects that have been issued by
     * getConnection() for which `Connection.close()` has
     * not yet been called.
     *
     * @return the number of active connections.
     */
    fun getActiveConnections(): Int {
        return activeConnections.get()
    }

    /**
     * INTERNAL
     */
    override fun getLogWriter(): PrintWriter? {
        return logWriter
    }

    /**
     * INTERNAL
     */
    override fun setLogWriter(logWriter: PrintWriter?) {
        this.logWriter = logWriter
    }

    /**
     * Return an object of this class if possible.
     *
     * @param iface the class
     * @return this
     */
    @Throws(SQLException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>?): T {
        try {
            if (isWrapperFor(iface)) {
                return this as T
            }
            throw DbException.getInvalidValueException("iface", iface)
        } catch (e: Exception) {
            throw DbException.toSQLException(e)
        }
    }

    /**
     * Checks if unwrap can return an object of this class.
     *
     * @param iface the class
     * @return whether or not the interface is assignable from this class
     */
    @Throws(SQLException::class)
    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return iface != null && iface.isAssignableFrom(javaClass)
    }

    /**
     * [Not supported]
     */
    override fun getParentLogger(): Logger? {
        return null
    }

    companion object {

        private const val DEFAULT_TIMEOUT = 30
        private const val DEFAULT_MAX_CONNECTIONS = 10

        /**
         * Constructs a new connection pool.
         *
         * @param dataSource the data source to create connections
         * @return the connection pool
         */
        @JvmStatic
        fun create(dataSource: ConnectionPoolDataSource?): JdbcConnectionPool {
            return JdbcConnectionPool(dataSource)
        }

        /**
         * Constructs a new connection pool for H2 databases.
         *
         * @param url the database URL of the H2 connection
         * @param user the user name
         * @param password the password
         * @return the connection pool
         */
        @JvmStatic
        fun create(
            url: String?, user: String?,
            password: String?
        ): JdbcConnectionPool {
            val ds = JdbcDataSource()
            ds.setURL(url)
            ds.setUser(user)
            ds.setPassword(password)
            return JdbcConnectionPool(ds)
        }
    }
}
