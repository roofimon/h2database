/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Similar to ThreadLocal, except that it allows its data to be read from other
 * threads - useful for debugging info.
 *
 * @param <T> the type
 */
class DebuggingThreadLocal<T> {

    private val map = ConcurrentHashMap<Long, T>()

    fun set(value: T) {
        map[Thread.currentThread().id] = value
    }

    /**
     * Remove the value for the current thread.
     */
    fun remove() {
        map.remove(Thread.currentThread().id)
    }

    fun get(): T? {
        return map[Thread.currentThread().id]
    }

    /**
     * Get a snapshot of the data of all threads.
     *
     * @return a HashMap containing a mapping from thread-id to value
     */
    fun getSnapshotOfAllThreads(): HashMap<Long, T> {
        return HashMap(map)
    }

}
