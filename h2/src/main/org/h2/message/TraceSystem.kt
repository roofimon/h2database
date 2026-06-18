/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.message

import java.io.IOException
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Writer
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField.HOUR_OF_DAY
import java.time.temporal.ChronoField.MINUTE_OF_HOUR
import java.time.temporal.ChronoField.NANO_OF_SECOND
import java.time.temporal.ChronoField.SECOND_OF_MINUTE
import java.util.Locale
import java.util.concurrent.atomic.AtomicReferenceArray

import org.h2.api.ErrorCode
import org.h2.engine.Constants
import org.h2.jdbc.JdbcException
import org.h2.store.fs.FileUtils
import org.h2.util.IOUtils

/**
 * The trace mechanism is the logging facility of this database. There is
 * usually one trace system per database. It is called 'trace' because the term
 * 'log' is already used in the database domain and means 'transaction log'. It
 * is possible to write after close was called, but that means for each write
 * the file will be opened and closed again (which is slower).
 */
open class TraceSystem
/**
 * Create a new trace system object.
 *
 * @param fileName the file name
 */
(private var fileName: String?) : TraceWriter {

    private var levelSystemOut = DEFAULT_TRACE_LEVEL_SYSTEM_OUT
    private var levelFile = DEFAULT_TRACE_LEVEL_FILE
    private var levelMax = 0
    private var maxFileSize = DEFAULT_MAX_FILE_SIZE
    private val traces = AtomicReferenceArray<Trace?>(Trace.MODULE_NAMES.size)
    private var fileWriter: Writer? = null
    private var printWriter: PrintWriter? = null

    /**
     * Starts at -1 so that we check the file size immediately upon open. This
     * Can be important if we open and close the trace file without managing to
     * have written CHECK_SIZE_EACH_WRITES bytes each time.
     */
    private var checkSize = -1
    private var closed = false
    private var writingErrorLogged = false
    private var writer: TraceWriter = this
    private var sysOut = System.out

    init {
        updateLevel()
    }

    private fun updateLevel() {
        levelMax = Math.max(levelSystemOut, levelFile)
    }

    /**
     * Set the print stream to use instead of System.out.
     *
     * @param out the new print stream
     */
    fun setSysOut(out: PrintStream) {
        this.sysOut = out
    }

    /**
     * Get or create a trace object for this module id. Trace modules with id
     * are cached.
     *
     * @param moduleId module id
     * @return the trace object
     */
    fun getTrace(moduleId: Int): Trace {
        var t = traces.get(moduleId)
        if (t == null) {
            t = Trace(writer, moduleId)
            if (!traces.compareAndSet(moduleId, null, t)) {
                t = traces.get(moduleId)
            }
        }
        return t!!
    }

    /**
     * Create a trace object for this module. Trace modules with names are not
     * cached.
     *
     * @param module the module name
     * @return the trace object
     */
    fun getTrace(module: String): Trace {
        return Trace(writer, module)
    }

    override fun isEnabled(level: Int): Boolean {
        if (levelMax == ADAPTER) {
            return writer.isEnabled(level)
        }
        return level <= this.levelMax
    }

    /**
     * Set the trace file name.
     *
     * @param name the file name
     */
    fun setFileName(name: String) {
        this.fileName = name
    }

    /**
     * Set the maximum trace file size in bytes.
     *
     * @param max the maximum size
     */
    fun setMaxFileSize(max: Int) {
        this.maxFileSize = max
    }

    /**
     * Set the trace level to use for System.out
     *
     * @param level the new level
     */
    fun setLevelSystemOut(level: Int) {
        if (level < PARENT || level > DEBUG) {
            throw DbException.getInvalidValueException("TRACE_LEVEL_SYSTEM_OUT", level)
        }
        levelSystemOut = level
        updateLevel()
    }

    /**
     * Set the file trace level.
     *
     * @param level the new level
     */
    fun setLevelFile(level: Int) {
        if (level == ADAPTER) {
            val adapterClass = "org.h2.message.TraceWriterAdapter"
            try {
                writer = Class.forName(adapterClass).getDeclaredConstructor().newInstance() as TraceWriter
            } catch (e: Throwable) {
                val ex = DbException.get(ErrorCode.CLASS_NOT_FOUND_1, e, adapterClass)
                write(ERROR, Trace.DATABASE, adapterClass, ex)
                return
            }
            var name = fileName
            if (name != null) {
                if (name.endsWith(Constants.SUFFIX_TRACE_FILE)) {
                    name = name.substring(0, name.length - Constants.SUFFIX_TRACE_FILE.length)
                }
                val idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'))
                if (idx >= 0) {
                    name = name.substring(idx + 1)
                }
                writer.setName(name)
            }
        } else if (level < PARENT || level > DEBUG) {
            throw DbException.getInvalidValueException("TRACE_LEVEL_FILE", level)
        }
        levelFile = level
        updateLevel()
    }

    fun getLevelFile(): Int {
        return levelFile
    }

    override fun write(level: Int, moduleId: Int, s: String, t: Throwable?) {
        write(level, Trace.MODULE_NAMES[moduleId], s, t)
    }

    override fun write(level: Int, module: String, s: String, t: Throwable?) {
        // level <= levelSystemOut: the system out level is set higher
        // level > levelMax: the level for this module is set higher
        val logToSystemOut = level <= levelSystemOut || level > levelMax
        val logToFile = fileName != null && level <= levelFile
        if (logToSystemOut || logToFile) {
            val row = format(module, s)
            if (logToSystemOut) {
                sysOut.println(row)
                if (t != null && levelSystemOut == DEBUG) {
                    t.printStackTrace(sysOut)
                }
            }
            if (logToFile) {
                writeFile(row, t)
            }
        }
    }

    @Synchronized
    private fun writeFile(s: String, t: Throwable?) {
        try {
            checkSize = (checkSize + 1) % CHECK_SIZE_EACH_WRITES
            if (checkSize == 0) {
                closeWriter()
                if (maxFileSize > 0 && FileUtils.size(fileName) > maxFileSize) {
                    val old = fileName + ".old"
                    FileUtils.delete(old)
                    FileUtils.move(fileName, old)
                }
            }
            if (!openWriter()) {
                return
            }
            val pw = printWriter!!
            pw.println(s)
            if (t != null) {
                if (levelFile == ERROR && t is JdbcException) {
                    val code = t.getErrorCode()
                    if (ErrorCode.isCommon(code)) {
                        pw.println(t)
                    } else {
                        t.printStackTrace(pw)
                    }
                } else {
                    t.printStackTrace(pw)
                }
            }
            pw.flush()
            if (closed) {
                closeWriter()
            }
        } catch (e: Exception) {
            logWritingError(e)
        }
    }

    private fun logWritingError(e: Exception) {
        if (writingErrorLogged) {
            return
        }
        writingErrorLogged = true
        val se = DbException.get(
            ErrorCode.TRACE_FILE_ERROR_2, e, fileName, e.toString()
        )
        // print this error only once
        fileName = null
        sysOut.println(se)
        se.printStackTrace()
    }

    private fun openWriter(): Boolean {
        if (printWriter == null) {
            try {
                FileUtils.createDirectories(FileUtils.getParent(fileName))
                if (FileUtils.exists(fileName) && !FileUtils.canWrite(fileName)) {
                    // read only database: don't log error if the trace file
                    // can't be opened
                    return false
                }
                fileWriter = IOUtils.getBufferedWriter(
                    FileUtils.newOutputStream(fileName, true)
                )
                printWriter = PrintWriter(fileWriter, true)
            } catch (e: Exception) {
                logWritingError(e)
                return false
            }
        }
        return true
    }

    @Synchronized
    private fun closeWriter() {
        if (printWriter != null) {
            printWriter!!.flush()
            printWriter!!.close()
            printWriter = null
        }
        if (fileWriter != null) {
            try {
                fileWriter!!.close()
            } catch (e: IOException) {
                // ignore
            }
            fileWriter = null
        }
    }

    /**
     * Close the writers, and the files if required. It is still possible to
     * write after closing, however after each write the file is closed again
     * (slowing down tracing).
     */
    fun close() {
        closeWriter()
        closed = true
    }

    override fun setName(name: String) {
        // nothing to do (the file name is already set)
    }

    companion object {

        /**
         * The parent trace level should be used.
         */
        const val PARENT = -1

        /**
         * This trace level means nothing should be written.
         */
        const val OFF = 0

        /**
         * This trace level means only errors should be written.
         */
        const val ERROR = 1

        /**
         * This trace level means errors and informational messages should be
         * written.
         */
        const val INFO = 2

        /**
         * This trace level means all type of messages should be written.
         */
        const val DEBUG = 3

        /**
         * This trace level means all type of messages should be written, but
         * instead of using the trace file the messages should be written to SLF4J.
         */
        const val ADAPTER = 4

        /**
         * The default level for system out trace messages.
         */
        const val DEFAULT_TRACE_LEVEL_SYSTEM_OUT = OFF

        /**
         * The default level for file trace messages.
         */
        const val DEFAULT_TRACE_LEVEL_FILE = ERROR

        /**
         * The default maximum trace file size. It is currently 64 MB. Additionally,
         * there could be a .old file of the same size.
         */
        private const val DEFAULT_MAX_FILE_SIZE = 64 * 1024 * 1024

        private const val CHECK_SIZE_EACH_WRITES = 4096

        private var DATE_TIME_FORMATTER: DateTimeFormatter? = null

        private fun format(module: String, s: String): String {
            var dateTimeFormatter = DATE_TIME_FORMATTER
            if (dateTimeFormatter == null) {
                dateTimeFormatter = initTimeFormatter()
            }
            return dateTimeFormatter.format(OffsetDateTime.now()) + ' ' + module + ": " + s
        }

        private fun initTimeFormatter(): DateTimeFormatter {
            return DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(':')
                .appendValue(SECOND_OF_MINUTE, 2)
                .appendFraction(NANO_OF_SECOND, 6, 6, true)
                .appendOffsetId()
                .toFormatter(Locale.ROOT)
                .also { DATE_TIME_FORMATTER = it }
        }
    }
}
