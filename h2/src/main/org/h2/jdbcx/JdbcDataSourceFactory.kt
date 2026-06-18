/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jdbcx

import java.util.Hashtable

import javax.naming.Context
import javax.naming.Name
import javax.naming.Reference
import javax.naming.spi.ObjectFactory

import org.h2.engine.Constants
import org.h2.engine.SysProperties
import org.h2.message.Trace
import org.h2.message.TraceSystem

/**
 * This class is used to create new DataSource objects.
 * An application should not use this class directly.
 */
class JdbcDataSourceFactory
/**
 * The public constructor to create new factory objects.
 */
    : ObjectFactory {

    private val trace: Trace = traceSystem.getTrace(Trace.JDBCX)

    /**
     * Creates a new object using the specified location or reference
     * information.
     *
     * @param obj the reference (this factory only supports objects of type
     *            javax.naming.Reference)
     * @param name unused
     * @param nameCtx unused
     * @param environment unused
     * @return the new JdbcDataSource, or null if the reference class name is
     *         not JdbcDataSource.
     */
    @Synchronized
    @Throws(Exception::class)
    override fun getObjectInstance(
        obj: Any?, name: Name?,
        nameCtx: Context?, environment: Hashtable<*, *>?
    ): Any? {
        if (trace.isDebugEnabled()) {
            trace.debug(
                "getObjectInstance obj={0} name={1} " +
                    "nameCtx={2} environment={3}", obj, name, nameCtx, environment
            )
        }
        if (obj is Reference) {
            if (obj.className == JdbcDataSource::class.java.name) {
                val dataSource = JdbcDataSource()
                dataSource.setURL(obj.get("url").content as String?)
                dataSource.setUser(obj.get("user").content as String?)
                dataSource.setPassword(obj.get("password").content as String?)
                dataSource.setDescription(obj.get("description").content as String?)
                val s = obj.get("loginTimeout").content as String?
                dataSource.setLoginTimeout(Integer.parseInt(s))
                return dataSource
            }
        }
        return null
    }

    internal fun getTrace(): Trace {
        return trace
    }

    companion object {

        private val traceSystem: TraceSystem

        init {
            traceSystem = TraceSystem(
                SysProperties.CLIENT_TRACE_DIRECTORY + "h2datasource" +
                    Constants.SUFFIX_TRACE_FILE
            )
            traceSystem.setLevelFile(SysProperties.DATASOURCE_TRACE_LEVEL)
        }

        /**
         * INTERNAL
         * @return TraceSystem
         */
        @JvmStatic
        fun getTraceSystem(): TraceSystem {
            return traceSystem
        }
    }
}
