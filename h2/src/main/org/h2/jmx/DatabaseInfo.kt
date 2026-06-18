/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jmx

import java.lang.management.ManagementFactory
import java.util.Hashtable
import javax.management.JMException
import javax.management.ObjectName
import org.h2.engine.ConnectionInfo
import org.h2.engine.Constants
import org.h2.engine.Database

/**
 * The MBean implementation.
 *
 * @author Eric Dong
 * @author Thomas Mueller
 */
class DatabaseInfo private constructor(database: Database?) : DatabaseInfoMBean {

    /** Database. */
    private val database: Database

    init {
        if (database == null) {
            throw IllegalArgumentException("Argument 'database' must not be null")
        }
        this.database = database
    }

    override fun isExclusive(): Boolean {
        return database.isInExclusiveMode()
    }

    override fun isReadOnly(): Boolean {
        return database.isReadOnly()
    }

    override fun getMode(): String {
        return database.getMode().getName()
    }

    override fun getTraceLevel(): Int {
        return database.getTraceSystem().getLevelFile()
    }

    override fun setTraceLevel(level: Int) {
        database.getTraceSystem().setLevelFile(level)
    }

    override fun getFileWriteCount(): Long {
        if (database.isPersistent()) {
            return database.getStore().getMvStore().getFileStore().getWriteCount()
        }
        return 0
    }

    override fun getFileReadCount(): Long {
        if (database.isPersistent()) {
            return database.getStore().getMvStore().getFileStore().getReadCount()
        }
        return 0
    }

    override fun getFileSize(): Long {
        var size: Long = 0
        if (database.isPersistent()) {
            size = database.getStore().getMvStore().getFileStore().size()
        }
        return size / 1024
    }

    override fun getCacheSizeMax(): Int {
        if (database.isPersistent()) {
            return database.getStore().getMvStore().getCacheSize() * 1024
        }
        return 0
    }

    override fun setCacheSizeMax(kb: Int) {
        if (database.isPersistent()) {
            database.setCacheSize(kb)
        }
    }

    override fun getCacheSize(): Int {
        if (database.isPersistent()) {
            return database.getStore().getMvStore().getCacheSizeUsed() * 1024
        }
        return 0
    }

    override fun getVersion(): String {
        return Constants.FULL_VERSION
    }

    override fun listSettings(): String {
        val builder = StringBuilder()
        for (e in database.getSettings().getSortedSettings()) {
            builder.append(e.key).append(" = ").append(e.value).append('\n')
        }
        return builder.toString()
    }

    override fun listSessions(): String {
        val buff = StringBuilder()
        for (session in database.getSessions(false)) {
            buff.append("session id: ").append(session.getId())
            buff.append(" user: ")
                .append(session.getUser().getName())
                .append('\n')
            val networkConnectionInfo = session.getNetworkConnectionInfo()
            if (networkConnectionInfo != null) {
                buff.append("server: ").append(networkConnectionInfo.getServer()).append('\n') //
                    .append("clientAddr: ").append(networkConnectionInfo.getClient()).append('\n')
                val clientInfo = networkConnectionInfo.getClientInfo()
                if (clientInfo != null) {
                    buff.append("clientInfo: ").append(clientInfo).append('\n')
                }
            }
            buff.append("connected: ")
                .append(session.getSessionStart().getString())
                .append('\n')
            val command = session.getCurrentCommand()
            if (command != null) {
                buff.append("statement: ")
                    .append(command)
                    .append('\n')
                    .append("started: ")
                    .append(session.getCommandStartOrEnd().getString())
                    .append('\n')
            }
            for (table in session.getLocks()) {
                if (table.isLockedExclusivelyBy(session)) {
                    buff.append("write lock on ")
                } else {
                    buff.append("read lock on ")
                }
                buff.append(table.getSchema().getName())
                    .append('.').append(table.getName())
                    .append('\n')
            }
            buff.append('\n')
        }
        return buff.toString()
    }

    companion object {

        private val MBEANS: MutableMap<String, ObjectName> = HashMap()

        /**
         * Returns a JMX new ObjectName instance.
         *
         * @param name name of the MBean
         * @param path the path
         * @return a new ObjectName instance
         * @throws JMException if the ObjectName could not be created
         */
        @Throws(JMException::class)
        private fun getObjectName(name: String, path: String): ObjectName {
            val n = name.replace(':', '_')
            val p = path.replace(':', '_')
            val map = Hashtable<String, String>()
            map["name"] = n
            map["path"] = p
            return ObjectName("org.h2", map)
        }

        /**
         * Registers an MBean for the database.
         *
         * @param connectionInfo connection info
         * @param database database
         * @throws JMException on failure
         */
        @JvmStatic
        @Throws(JMException::class)
        fun registerMBean(connectionInfo: ConnectionInfo, database: Database) {
            val path = connectionInfo.getName()
            if (!MBEANS.containsKey(path)) {
                val mbeanServer = ManagementFactory.getPlatformMBeanServer()
                val name = database.getShortName()
                val mbeanObjectName = getObjectName(name, path)
                MBEANS[path] = mbeanObjectName
                val info = DatabaseInfo(database)
                val mbean: Any = DocumentedMBean(info, DatabaseInfoMBean::class.java)
                mbeanServer.registerMBean(mbean, mbeanObjectName)
            }
        }

        /**
         * Unregisters the MBean for the database if one is registered.
         *
         * @param name database name
         * @throws JMException on failure
         */
        @JvmStatic
        @Throws(Exception::class)
        fun unregisterMBean(name: String) {
            val mbeanObjectName = MBEANS.remove(name)
            if (mbeanObjectName != null) {
                val mbeanServer = ManagementFactory.getPlatformMBeanServer()
                mbeanServer.unregisterMBean(mbeanObjectName)
            }
        }
    }
}
