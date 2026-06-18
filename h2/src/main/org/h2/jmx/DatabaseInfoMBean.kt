/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jmx

/**
 * Information and management operations for the given database.
 *
 * @author Eric Dong
 * @author Thomas Mueller
 */
interface DatabaseInfoMBean {

    /**
     * Is the database open in exclusive mode?
     *
     * @return true if the database is open in exclusive mode, false otherwise
     */
    fun isExclusive(): Boolean

    /**
     * Is the database read-only?
     *
     * @return true if the database is read-only, false otherwise
     */
    fun isReadOnly(): Boolean

    /**
     * The database compatibility mode (REGULAR if no compatibility mode is
     * used).
     *
     * @return the database mode
     */
    fun getMode(): String

    /**
     * The number of write operations since the database was opened.
     *
     * @return the write count
     */
    fun getFileWriteCount(): Long

    /**
     * The file read count since the database was opened.
     *
     * @return the read count
     */
    fun getFileReadCount(): Long

    /**
     * The database file size in KB.
     *
     * @return the number of pages
     */
    fun getFileSize(): Long

    /**
     * The maximum cache size in KB.
     *
     * @return the maximum size
     */
    fun getCacheSizeMax(): Int

    /**
     * Change the maximum size.
     *
     * @param kb the cache size in KB.
     */
    fun setCacheSizeMax(kb: Int)

    /**
     * The current cache size in KB.
     *
     * @return the current size
     */
    fun getCacheSize(): Int

    /**
     * The database version.
     *
     * @return the version
     */
    fun getVersion(): String

    /**
     * The trace level (0 disabled, 1 error, 2 info, 3 debug).
     *
     * @return the level
     */
    fun getTraceLevel(): Int

    /**
     * Set the trace level.
     *
     * @param level the new value
     */
    fun setTraceLevel(level: Int)

    /**
     * List the database settings.
     *
     * @return the database settings
     */
    fun listSettings(): String

    /**
     * List sessions, including the queries that are in
     * progress, and locked tables.
     *
     * @return information about the sessions
     */
    fun listSessions(): String
}
