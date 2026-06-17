/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * The driver activator loads the H2 driver when starting the bundle. The driver
 * is unloaded when stopping the bundle.
 */
class DbDriverActivator : BundleActivator {

    /**
     * Start the bundle. If the 'org.osgi.service.jdbc.DataSourceFactory' class
     * is available in the class path, this will load the database driver and
     * register the DataSourceFactory service.
     *
     * @param bundleContext the bundle context
     */
    override fun start(bundleContext: BundleContext?) {
        val driver = org.h2.Driver.load()
        try {
            JdbcUtils.loadUserClass<Any>(DATASOURCE_FACTORY_CLASS)
        } catch (e: Exception) {
            // class not found - don't register
            return
        }
        // but don't ignore exceptions in this call
        OsgiDataSourceFactory.registerService(bundleContext, driver)
    }

    /**
     * Stop the bundle. This will unload the database driver. The
     * DataSourceFactory service is implicitly un-registered by the OSGi
     * framework.
     *
     * @param bundleContext the bundle context
     */
    override fun stop(bundleContext: BundleContext?) {
        org.h2.Driver.unload()
    }

    companion object {
        private const val DATASOURCE_FACTORY_CLASS = "org.osgi.service.jdbc.DataSourceFactory"
    }

}
