/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.concurrent.atomic.AtomicInteger

/**
 * A method call that is executed in a separate thread. If the method throws an
 * exception, it is wrapped in a RuntimeException.
 */
abstract class Task : Runnable {

    /**
     * A flag indicating the get() method has been called.
     */
    @JvmField
    @Volatile
    var stop = false

    /**
     * The result, if any.
     */
    @Volatile
    private var result: Any? = null

    @Volatile
    private var finished = false

    private var thread: Thread? = null

    @Volatile
    private var ex: Exception? = null

    /**
     * The method to be implemented.
     *
     * @throws Exception any exception is wrapped in a RuntimeException
     */
    @Throws(Exception::class)
    abstract fun call()

    override fun run() {
        try {
            call()
        } catch (e: Exception) {
            this.ex = e
        }
        finished = true
    }

    /**
     * Start the thread.
     *
     * @return this
     */
    fun execute(): Task {
        return execute(javaClass.name + ":" + counter.getAndIncrement())
    }

    /**
     * Start the thread.
     *
     * @param threadName the name of the thread
     * @return this
     */
    fun execute(threadName: String): Task {
        thread = Thread(this, threadName)
        thread!!.isDaemon = true
        thread!!.start()
        return this
    }

    /**
     * Calling this method will set the stop flag and wait until the thread is
     * stopped.
     *
     * @return the result, or null
     * @throws RuntimeException if an exception in the method call occurs
     */
    fun get(): Any? {
        val e = getException()
        if (e != null) {
            throw RuntimeException(e)
        }
        return result
    }

    /**
     * Whether the call method has returned (with or without exception).
     *
     * @return true if yes
     */
    fun isFinished(): Boolean {
        return finished
    }

    /**
     * Get the exception that was thrown in the call (if any).
     *
     * @return the exception or null
     */
    fun getException(): Exception? {
        join()
        if (ex != null) {
            return ex
        }
        return null
    }

    /**
     * Stop the thread and wait until it is no longer running. Exceptions are
     * ignored.
     */
    fun join() {
        stop = true
        if (thread == null) {
            throw IllegalStateException("Thread not started")
        }
        try {
            thread!!.join()
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    companion object {
        private val counter = AtomicInteger()
    }
}
