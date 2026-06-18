/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.nio.channels.FileChannel
import java.sql.SQLException
import java.util.ArrayList
import org.h2.api.ErrorCode
import org.h2.engine.Constants
import org.h2.message.DbException
import org.h2.message.TraceSystem
import org.h2.store.fs.FilePath
import org.h2.store.fs.FileUtils

/**
 * Utility class to list the files of a database.
 */
class FileLister private constructor() {
    // utility class

    companion object {

        /**
         * Try to lock the database, and then unlock it. If this worked, the
         * .lock.db file will be removed.
         *
         * @param files the database files to check
         * @param message the text to include in the error message
         * @throws SQLException if it failed
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun tryUnlockDatabase(files: List<String>, message: String?) {
            for (fileName in files) {
                if (fileName.endsWith(Constants.SUFFIX_LOCK_FILE)) {
                    val lock = FileLock(TraceSystem(null), fileName, Constants.LOCK_SLEEP)
                    try {
                        lock.lock(FileLockMethod.FILE)
                        lock.unlock()
                    } catch (e: DbException) {
                        throw DbException.getJdbcSQLException(
                            ErrorCode.CANNOT_CHANGE_SETTING_WHEN_OPEN_1,
                            message
                        )
                    }
                } else if (fileName.endsWith(Constants.SUFFIX_MV_FILE)) {
                    try {
                        FilePath.get(fileName).open("r").use { f ->
                            val lock: java.nio.channels.FileLock =
                                f.tryLock(0, Long.MAX_VALUE, true)
                            lock.release()
                        }
                    } catch (e: Exception) {
                        throw DbException.getJdbcSQLException(
                            ErrorCode.CANNOT_CHANGE_SETTING_WHEN_OPEN_1, e,
                            message
                        )
                    }
                }
            }
        }

        /**
         * Normalize the directory name.
         *
         * @param dir the directory (null for the current directory)
         * @return the normalized directory name
         */
        @JvmStatic
        fun getDir(dir: String?): String? {
            if (dir == null || dir.isEmpty()) {
                return "."
            }
            return FileUtils.toRealPath(dir)
        }

        /**
         * Get the list of database files.
         *
         * @param dir the directory (must be normalized)
         * @param db the database name (null for all databases)
         * @param all if true, files such as the lock, trace, and lob
         *            files are included. If false, only data, index, log,
         *            and lob files are returned
         * @return the list of files
         */
        @JvmStatic
        fun getDatabaseFiles(dir: String?, db: String?, all: Boolean): ArrayList<String> {
            val files = ArrayList<String>()
            val start = if (db == null) null else "$db."
            for (path in FilePath.get(dir).newDirectoryStream()) {
                var ok = false
                val f = path.toString()
                if (f.endsWith(Constants.SUFFIX_MV_FILE)) {
                    ok = true
                } else if (all) {
                    if (f.endsWith(Constants.SUFFIX_LOCK_FILE)) {
                        ok = true
                    } else if (f.endsWith(Constants.SUFFIX_TEMP_FILE)) {
                        ok = true
                    } else if (f.endsWith(Constants.SUFFIX_TRACE_FILE)) {
                        ok = true
                    }
                }
                if (ok) {
                    if (db == null || path.getName().startsWith(start!!)) {
                        files.add(f)
                    }
                }
            }
            return files
        }
    }
}
