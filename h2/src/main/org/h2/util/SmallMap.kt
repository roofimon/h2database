/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.HashMap

import org.h2.api.ErrorCode
import org.h2.message.DbException

/**
 * A simple hash table with an optimization for the last recently used object.
 *
 * @param maxElements the maximum number of entries
 */
class SmallMap(private val maxElements: Int) {

    private val map = HashMap<Int, Any?>()
    private var cache: Any? = null
    private var cacheId = 0
    private var lastId = 0

    /**
     * Add an object to the map. If the size of the map is larger than twice the
     * maximum size, objects with a low id are removed.
     *
     * @param id the object id
     * @param o the object
     * @return the id
     */
    fun addObject(id: Int, o: Any?): Int {
        if (map.size > maxElements * 2) {
            map.keys.removeIf { k -> k + maxElements < lastId }
        }
        if (id > lastId) {
            lastId = id
        }
        map[id] = o
        cacheId = id
        cache = o
        return id
    }

    /**
     * Remove an object from the map.
     *
     * @param id the id of the object to remove
     */
    fun freeObject(id: Int) {
        if (cacheId == id) {
            cacheId = -1
            cache = null
        }
        map.remove(id)
    }

    /**
     * Get an object from the map if it is stored.
     *
     * @param id the id of the object
     * @param ifAvailable only return it if available, otherwise return null
     * @return the object or null
     * @throws DbException if isAvailable is false and the object has not been
     *             found
     */
    fun getObject(id: Int, ifAvailable: Boolean): Any? {
        if (id == cacheId) {
            return cache
        }
        val obj = map[id]
        if (obj == null && !ifAvailable) {
            throw DbException.get(ErrorCode.OBJECT_CLOSED)
        }
        return obj
    }

}
