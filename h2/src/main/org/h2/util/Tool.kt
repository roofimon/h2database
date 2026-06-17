/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.PrintStream
import java.sql.SQLException
import java.util.Properties
import org.h2.api.ErrorCode
import org.h2.message.DbException
import org.h2.store.FileLister
import org.h2.store.fs.FileUtils

/**
 * Command line tools implement the tool interface so that they can be used in
 * the H2 Console.
 */
abstract class Tool {

    /**
     * The output stream where this tool writes to.
     */
    @JvmField
    protected var out: PrintStream = System.out

    private var resources: Properties? = null

    /**
     * Sets the standard output stream.
     *
     * @param out the new standard output stream
     */
    fun setOut(out: PrintStream) {
        this.out = out
    }

    /**
     * Run the tool with the given output stream and arguments.
     *
     * @param args the argument list
     * @throws SQLException on failure
     */
    @Throws(SQLException::class)
    abstract fun runTool(vararg args: String)

    /**
     * Throw a SQLException saying this command line option is not supported.
     *
     * @param option the unsupported option
     * @return this method never returns normally
     * @throws SQLException on failure
     */
    @Throws(SQLException::class)
    protected fun showUsageAndThrowUnsupportedOption(option: String): SQLException {
        showUsage()
        throw throwUnsupportedOption(option)
    }

    /**
     * Throw a SQLException saying this command line option is not supported.
     *
     * @param option the unsupported option
     * @return this method never returns normally
     * @throws SQLException on failure
     */
    @Throws(SQLException::class)
    protected fun throwUnsupportedOption(option: String): SQLException {
        throw DbException.getJdbcSQLException(
            ErrorCode.FEATURE_NOT_SUPPORTED_1, option
        )
    }

    /**
     * Print to the output stream that no database files have been found.
     *
     * @param dir the directory or null
     * @param db the database name or null
     */
    protected fun printNoDatabaseFilesFound(dir: String?, db: String?) {
        val buff: StringBuilder
        val directory = FileLister.getDir(dir)
        if (!FileUtils.isDirectory(directory)) {
            buff = StringBuilder("Directory not found: ")
            buff.append(directory)
        } else {
            buff = StringBuilder("No database files have been found")
            buff.append(" in directory ").append(directory)
            if (db != null) {
                buff.append(" for the database ").append(db)
            }
        }
        out.println(buff.toString())
    }

    /**
     * Print the usage of the tool. This method reads the description from the
     * resource file.
     */
    protected open fun showUsage() {
        var resources = this.resources
        if (resources == null) {
            resources = Properties()
            this.resources = resources
            val resourceName = "/org/h2/res/javadoc.properties"
            try {
                val buff = Utils.getResource(resourceName)
                if (buff != null) {
                    resources.load(ByteArrayInputStream(buff))
                }
            } catch (e: IOException) {
                out.println("Cannot load $resourceName")
            }
        }
        val className = getMainClassName()
        out.println(resources.get(className))
        out.println("Usage: java " + javaClass.name + " <options>")
        out.println(resources.get("$className.main"))
        out.println(
            "See also https://h2database.com/javadoc/" +
                className.replace('.', '/') + ".html"
        )
    }

    /**
     * Returns main class name of the tool.
     *
     * @return the name of the main class
     */
    protected open fun getMainClassName(): String {
        return javaClass.name
    }

    companion object {
        /**
         * Check if the argument matches the option.
         * If the argument starts with this option, but doesn't match,
         * then an exception is thrown.
         *
         * @param arg the argument
         * @param option the command line option
         * @return true if it matches
         */
        @JvmStatic
        fun isOption(arg: String, option: String): Boolean {
            if (arg == option) {
                return true
            } else if (arg.startsWith(option)) {
                throw DbException.getUnsupportedException(
                    "expected: $option got: $arg"
                )
            }
            return false
        }
    }
}
