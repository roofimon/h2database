/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.ArrayList

/**
 * The cache keeps frequently used objects in the main memory.
 */
interface Cache {

    /**
     * Get all objects in the cache that have been changed.
     *
     * @return the list of objects
     */
    fun getAllChanged(): ArrayList<CacheObject>

    /**
     * Clear the cache.
     */
    fun clear()

    /**
     * Get an element in the cache if it is available.
     * This will move the item to the front of the list.
     *
     * @param pos the unique key of the element
     * @return the element or null
     */
    fun get(pos: Int): CacheObject?

    /**
     * Add an element to the cache. Other items may fall out of the cache
     * because of this. It is not allowed to add the same record twice.
     *
     * @param r the object
     */
    fun put(r: CacheObject)

    /**
     * Update an element in the cache.
     * This will move the item to the front of the list.
     *
     * @param pos the unique key of the element
     * @param record the element
     * @return the element
     */
    fun update(pos: Int, record: CacheObject): CacheObject?

    /**
     * Remove an object from the cache.
     *
     * @param pos the unique key of the element
     * @return true if the key was in the cache
     */
    fun remove(pos: Int): Boolean

    /**
     * Get an element from the cache if it is available.
     * This will not move the item to the front of the list.
     *
     * @param pos the unique key of the element
     * @return the element or null
     */
    fun find(pos: Int): CacheObject?

    /**
     * Set the maximum memory to be used by this cache.
     *
     * @param size the maximum size in KB
     */
    fun setMaxMemory(size: Int)

    /**
     * Get the maximum memory to be used.
     *
     * @return the maximum size in KB
     */
    fun getMaxMemory(): Int

    /**
     * Get the used size in KB.
     *
     * @return the current size in KB
     */
    fun getMemory(): Int

}
