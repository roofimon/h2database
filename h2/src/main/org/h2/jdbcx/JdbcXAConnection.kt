/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jdbcx

import java.sql.Connection
import java.sql.SQLException
import java.util.ArrayList
import javax.sql.ConnectionEvent
import javax.sql.ConnectionEventListener
import javax.sql.StatementEventListener
import javax.sql.XAConnection
import javax.transaction.xa.XAException
import javax.transaction.xa.XAResource
import javax.transaction.xa.Xid
import org.h2.api.ErrorCode
import org.h2.jdbc.JdbcConnection
import org.h2.message.DbException
import org.h2.message.TraceObject
import org.h2.util.Utils

/**
 * This class provides support for distributed transactions.
 * An application developer usually does not use this interface.
 * It is used by the transaction manager internally.
 */
class JdbcXAConnection internal constructor(
    private val factory: JdbcDataSourceFactory,
    id: Int,
    physicalConn: JdbcConnection,
) : TraceObject(), XAConnection, XAResource {

    // this connection is kept open as long as the XAConnection is alive
    private var physicalConn: JdbcConnection?

    // this connection is replaced whenever getConnection is called
    @Volatile
    private var handleConn: Connection? = null
    private val listeners = Utils.newSmallArrayList<ConnectionEventListener>()
    private var currentTransaction: Xid? = null
    private var prepared = false

    init {
        setTrace(factory.getTrace(), TraceObject.XA_DATA_SOURCE, id)
        this.physicalConn = physicalConn
    }

    /**
     * Get the XAResource object.
     *
     * @return itself
     */
    override fun getXAResource(): XAResource {
        debugCodeCall("getXAResource")
        return this
    }

    /**
     * Close the physical connection.
     * This method is usually called by the connection pool.
     */
    @Throws(SQLException::class)
    override fun close() {
        debugCodeCall("close")
        val lastHandle = handleConn
        if (lastHandle != null) {
            listeners.clear()
            lastHandle.close()
        }
        if (physicalConn != null) {
            try {
                physicalConn!!.close()
            } finally {
                physicalConn = null
            }
        }
    }

    /**
     * Get a connection that is a handle to the physical connection. This method
     * is usually called by the connection pool. This method closes the last
     * connection handle if one exists.
     *
     * @return the connection
     */
    @Throws(SQLException::class)
    override fun getConnection(): Connection {
        debugCodeCall("getConnection")
        val lastHandle = handleConn
        if (lastHandle != null) {
            lastHandle.close()
        }
        // this will ensure the rollback command is cached
        physicalConn!!.rollback()
        val newHandle = PooledJdbcConnection(physicalConn!!)
        handleConn = newHandle
        return newHandle
    }

    /**
     * Register a new listener for the connection.
     *
     * @param listener the event listener
     */
    override fun addConnectionEventListener(listener: ConnectionEventListener) {
        debugCode("addConnectionEventListener(listener)")
        listeners.add(listener)
    }

    /**
     * Remove the event listener.
     *
     * @param listener the event listener
     */
    override fun removeConnectionEventListener(listener: ConnectionEventListener) {
        debugCode("removeConnectionEventListener(listener)")
        listeners.remove(listener)
    }

    /**
     * INTERNAL
     */
    fun closedHandle() {
        debugCodeCall("closedHandle")
        val event = ConnectionEvent(this)
        // go backward so that a listener can remove itself
        // (otherwise we need to clone the list)
        for (i in listeners.indices.reversed()) {
            val listener = listeners[i]
            listener.connectionClosed(event)
        }
        handleConn = null
    }

    /**
     * Get the transaction timeout.
     *
     * @return 0
     */
    override fun getTransactionTimeout(): Int {
        debugCodeCall("getTransactionTimeout")
        return 0
    }

    /**
     * Set the transaction timeout.
     *
     * @param seconds ignored
     * @return false
     */
    override fun setTransactionTimeout(seconds: Int): Boolean {
        debugCodeCall("setTransactionTimeout", seconds.toLong())
        return false
    }

    /**
     * Checks if this is the same XAResource.
     *
     * @param xares the other object
     * @return true if this is the same object
     */
    override fun isSameRM(xares: XAResource?): Boolean {
        debugCode("isSameRM(xares)")
        return xares === this
    }

    /**
     * Get the list of prepared transaction branches. This method is called by
     * the transaction manager during recovery.
     *
     * @param flag TMSTARTRSCAN, TMENDRSCAN, or TMNOFLAGS. If no other flags are
     *            set, TMNOFLAGS must be used.
     * @return zero or more Xid objects
     */
    @Throws(XAException::class)
    override fun recover(flag: Int): Array<Xid> {
        debugCodeCall("recover", quoteFlags(flag))
        checkOpen()
        try {
            physicalConn!!.createStatement().use { stat ->
                val rs = stat.executeQuery(
                    "SELECT * FROM INFORMATION_SCHEMA.IN_DOUBT ORDER BY TRANSACTION_NAME")
                val list = Utils.newSmallArrayList<Xid>()
                while (rs.next()) {
                    val tid = rs.getString("TRANSACTION_NAME")
                    val id = getNextId(XID)
                    val xid: Xid = JdbcXid(factory, id, tid)
                    list.add(xid)
                }
                rs.close()
                val result = list.toTypedArray()
                if (!list.isEmpty()) {
                    prepared = true
                }
                return result
            }
        } catch (e: SQLException) {
            val xa = XAException(XAException.XAER_RMERR)
            xa.initCause(e)
            throw xa
        }
    }

    /**
     * Prepare a transaction.
     *
     * @param xid the transaction id
     * @return XA_OK
     */
    @Throws(XAException::class)
    override fun prepare(xid: Xid): Int {
        if (isDebugEnabled()) {
            debugCode("prepare(" + quoteXid(xid) + ')')
        }
        checkOpen()
        if (currentTransaction != xid) {
            throw XAException(XAException.XAER_INVAL)
        }

        try {
            physicalConn!!.createStatement().use { stat ->
                stat.execute(JdbcXid.toString(StringBuilder("PREPARE COMMIT \""), xid).append('"').toString())
                prepared = true
            }
        } catch (e: SQLException) {
            throw convertException(e)
        }
        return XAResource.XA_OK
    }

    /**
     * Forget a transaction.
     * This method does not have an effect for this database.
     *
     * @param xid the transaction id
     */
    override fun forget(xid: Xid) {
        if (isDebugEnabled()) {
            debugCode("forget(" + quoteXid(xid) + ')')
        }
        prepared = false
    }

    /**
     * Roll back a transaction.
     *
     * @param xid the transaction id
     */
    @Throws(XAException::class)
    override fun rollback(xid: Xid) {
        if (isDebugEnabled()) {
            debugCode("rollback(" + quoteXid(xid) + ')')
        }
        try {
            if (prepared) {
                physicalConn!!.createStatement().use { stat ->
                    stat.execute(JdbcXid.toString( //
                        StringBuilder("ROLLBACK TRANSACTION \""), xid).append('"').toString())
                }
                prepared = false
            } else {
                physicalConn!!.rollback()
            }
            physicalConn!!.setAutoCommit(true)
        } catch (e: SQLException) {
            throw convertException(e)
        }
        currentTransaction = null
    }

    /**
     * End a transaction.
     *
     * @param xid the transaction id
     * @param flags TMSUCCESS, TMFAIL, or TMSUSPEND
     */
    @Throws(XAException::class)
    override fun end(xid: Xid, flags: Int) {
        if (isDebugEnabled()) {
            debugCode("end(" + quoteXid(xid) + ", " + quoteFlags(flags) + ')')
        }
        // TODO transaction end: implement this method
        if (flags == XAResource.TMSUSPEND) {
            return
        }
        if (currentTransaction != xid) {
            throw XAException(XAException.XAER_OUTSIDE)
        }
        prepared = false
    }

    /**
     * Start or continue to work on a transaction.
     *
     * @param xid the transaction id
     * @param flags TMNOFLAGS, TMJOIN, or TMRESUME
     */
    @Throws(XAException::class)
    override fun start(xid: Xid, flags: Int) {
        if (isDebugEnabled()) {
            debugCode("start(" + quoteXid(xid) + ", " + quoteFlags(flags) + ')')
        }
        if (flags == XAResource.TMRESUME) {
            return
        }
        if (flags == XAResource.TMJOIN) {
            if (currentTransaction != null && currentTransaction != xid) {
                throw XAException(XAException.XAER_RMERR)
            }
        } else if (currentTransaction != null) {
            throw XAException(XAException.XAER_NOTA)
        }
        try {
            physicalConn!!.setAutoCommit(false)
        } catch (e: SQLException) {
            throw convertException(e)
        }
        currentTransaction = xid
        prepared = false
    }

    /**
     * Commit a transaction.
     *
     * @param xid the transaction id
     * @param onePhase use a one-phase protocol if true
     */
    @Throws(XAException::class)
    override fun commit(xid: Xid, onePhase: Boolean) {
        if (isDebugEnabled()) {
            debugCode("commit(" + quoteXid(xid) + ", " + onePhase + ')')
        }

        try {
            if (onePhase) {
                physicalConn!!.commit()
            } else {
                physicalConn!!.createStatement().use { stat ->
                    stat.execute(
                        JdbcXid.toString(StringBuilder("COMMIT TRANSACTION \""), xid).append('"').toString())
                    prepared = false
                }
            }
            physicalConn!!.setAutoCommit(true)
        } catch (e: SQLException) {
            throw convertException(e)
        }
        currentTransaction = null
    }

    /**
     * [Not supported] Add a statement event listener.
     *
     * @param listener the new statement event listener
     */
    override fun addStatementEventListener(listener: StatementEventListener) {
        throw UnsupportedOperationException()
    }

    /**
     * [Not supported] Remove a statement event listener.
     *
     * @param listener the statement event listener
     */
    override fun removeStatementEventListener(listener: StatementEventListener) {
        throw UnsupportedOperationException()
    }

    /**
     * INTERNAL
     */
    override fun toString(): String {
        return getTraceObjectName() + ": " + physicalConn
    }

    @Throws(XAException::class)
    private fun checkOpen() {
        if (physicalConn == null) {
            throw XAException(XAException.XAER_RMERR)
        }
    }

    /**
     * A pooled connection.
     */
    inner class PooledJdbcConnection(conn: JdbcConnection) : JdbcConnection(conn) {

        private var isClosed = false

        @Throws(SQLException::class)
        override fun close() {
            lock()
            try {
                if (!isClosed) {
                    try {
                        rollback()
                        setAutoCommit(true)
                    } catch (e: SQLException) {
                        // ignore
                    }
                    closedHandle()
                    isClosed = true
                }
            } finally {
                unlock()
            }
        }

        @Throws(SQLException::class)
        override fun isClosed(): Boolean {
            lock()
            try {
                return isClosed || super.isClosed()
            } finally {
                unlock()
            }
        }

        override fun checkClosed() {
            lock()
            try {
                if (isClosed) {
                    throw DbException.get(ErrorCode.OBJECT_CLOSED)
                }
                super.checkClosed()
            } finally {
                unlock()
            }
        }
    }

    companion object {

        private fun convertException(e: SQLException): XAException {
            val xa = XAException(e.message)
            xa.initCause(e)
            return xa
        }

        private fun quoteXid(xid: Xid): String {
            return JdbcXid.toString(StringBuilder(), xid).toString().replace('-', '$')
        }

        private fun quoteFlags(flags: Int): String {
            val buff = StringBuilder()
            if ((flags and XAResource.TMENDRSCAN) != 0) {
                buff.append("|XAResource.TMENDRSCAN")
            }
            if ((flags and XAResource.TMFAIL) != 0) {
                buff.append("|XAResource.TMFAIL")
            }
            if ((flags and XAResource.TMJOIN) != 0) {
                buff.append("|XAResource.TMJOIN")
            }
            if ((flags and XAResource.TMONEPHASE) != 0) {
                buff.append("|XAResource.TMONEPHASE")
            }
            if ((flags and XAResource.TMRESUME) != 0) {
                buff.append("|XAResource.TMRESUME")
            }
            if ((flags and XAResource.TMSTARTRSCAN) != 0) {
                buff.append("|XAResource.TMSTARTRSCAN")
            }
            if ((flags and XAResource.TMSUCCESS) != 0) {
                buff.append("|XAResource.TMSUCCESS")
            }
            if ((flags and XAResource.TMSUSPEND) != 0) {
                buff.append("|XAResource.TMSUSPEND")
            }
            if ((flags and XAResource.XA_RDONLY) != 0) {
                buff.append("|XAResource.XA_RDONLY")
            }
            if (buff.length == 0) {
                buff.append("|XAResource.TMNOFLAGS")
            }
            return buff.substring(1)
        }
    }
}
