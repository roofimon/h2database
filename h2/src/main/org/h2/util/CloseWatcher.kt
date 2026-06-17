/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 * Iso8601:
 * Initial Developer: Robert Rathsack (firstName dot lastName at gmx dot de)
 */
package org.h2.util

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.Collections
import java.util.HashSet

/**
 * A phantom reference to watch for unclosed objects.
 */
class CloseWatcher(
    referent: Any?,
    q: ReferenceQueue<Any?>,
    closeable: AutoCloseable?
) : PhantomReference<Any?>(referent, q) {

    /**
     * The stack trace of when the object was created. It is converted to a
     * string early on to avoid classloader problems (a classloader can't be
     * garbage collected if there is a static reference to one of its classes).
     */
    private var openStackTrace: String? = null

    /**
     * The closeable object.
     */
    private var closeable: AutoCloseable? = closeable

    /**
     * Get the open stack trace or null if none.
     *
     * @return the open stack trace
     */
    fun getOpenStackTrace(): String? {
        return openStackTrace
    }

    fun getCloseable(): AutoCloseable? {
        return closeable
    }

    companion object {
        /**
         * The queue (might be set to null at any time).
         */
        private val queue = ReferenceQueue<Any?>()

        /**
         * The reference set. Must keep it, otherwise the references are garbage
         * collected first and thus never enqueued.
         */
        private val refs: MutableSet<CloseWatcher> = Collections.synchronizedSet(HashSet())

        /**
         * Check for a collected object.
         *
         * @return the first watcher
         */
        @JvmStatic
        fun pollUnclosed(): CloseWatcher? {
            while (true) {
                val cw = queue.poll() as CloseWatcher?
                if (cw == null) {
                    return null
                }
                refs.remove(cw)
                if (cw.closeable != null) {
                    return cw
                }
            }
        }

        /**
         * Register an object. Before calling this method, pollUnclosed() should be
         * called in a loop to remove old references.
         *
         * @param o the object
         * @param closeable the object to close
         * @param stackTrace whether the stack trace should be registered (this is
         *            relatively slow)
         * @return the close watcher
         */
        @JvmStatic
        fun register(o: Any?, closeable: AutoCloseable?, stackTrace: Boolean): CloseWatcher {
            val cw = CloseWatcher(o, queue, closeable)
            if (stackTrace) {
                val e = Exception("Open Stack Trace")
                val s = StringWriter()
                e.printStackTrace(PrintWriter(s))
                cw.openStackTrace = s.toString()
            }
            refs.add(cw)
            return cw
        }

        /**
         * Unregister an object, so it is no longer tracked.
         *
         * @param w the reference
         */
        @JvmStatic
        fun unregister(w: CloseWatcher) {
            w.closeable = null
            refs.remove(w)
        }
    }
}
