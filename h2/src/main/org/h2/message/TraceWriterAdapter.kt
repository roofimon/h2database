/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.message

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This adapter sends log output to SLF4J. SLF4J supports multiple
 * implementations such as Logback, Log4j, Jakarta Commons Logging (JCL), JDK
 * 1.4 logging, x4juli, and Simple Log. To use SLF4J, you need to add the
 * required jar files to the classpath, and set the trace level to 4 when
 * opening a database:
 *
 * <pre>
 * jdbc:h2:&tilde;/test;TRACE_LEVEL_FILE=4
 * </pre>
 *
 * The logger name is 'h2database'.
 */
class TraceWriterAdapter : TraceWriter {

    private var name: String? = null
    private val logger: Logger = LoggerFactory.getLogger("h2database")

    override fun setName(name: String) {
        this.name = name
    }

    override fun isEnabled(level: Int): Boolean {
        return when (level) {
            TraceSystem.DEBUG -> logger.isDebugEnabled
            TraceSystem.INFO -> logger.isInfoEnabled
            TraceSystem.ERROR -> logger.isErrorEnabled
            else -> false
        }
    }

    override fun write(level: Int, moduleId: Int, s: String?, t: Throwable?) {
        write(level, Trace.MODULE_NAMES[moduleId], s, t)
    }

    override fun write(level: Int, module: String, s: String?, t: Throwable?) {
        if (isEnabled(level)) {
            var message = s
            message = if (name != null) {
                "$name:$module $message"
            } else {
                "$module $message"
            }
            when (level) {
                TraceSystem.DEBUG -> logger.debug(message, t)
                TraceSystem.INFO -> logger.info(message, t)
                TraceSystem.ERROR -> logger.error(message, t)
                else -> {
                }
            }
        }
    }
}
