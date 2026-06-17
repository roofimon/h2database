/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import org.h2.message.DbException

/**
 * The base object for all cached objects.
 */
abstract class CacheObject : Comparable<CacheObject> {

    /**
     * The previous element in the LRU linked list. If the previous element is
     * the head, then this element is the most recently used object.
     */
    @JvmField
    var cachePrevious: CacheObject? = null

    /**
     * The next element in the LRU linked list. If the next element is the head,
     * then this element is the least recently used object.
     */
    @JvmField
    var cacheNext: CacheObject? = null

    /**
     * The next element in the hash chain.
     */
    @JvmField
    var cacheChained: CacheObject? = null

    private var pos = 0
    private var changed = false

    /**
     * Check if the object can be removed from the cache.
     * For example pinned objects can not be removed.
     *
     * @return true if it can be removed
     */
    abstract fun canRemove(): Boolean

    /**
     * Get the estimated used memory.
     *
     * @return number of words (one word is 4 bytes)
     */
    abstract fun getMemory(): Int

    open fun setPos(pos: Int) {
        if (cachePrevious != null || cacheNext != null || cacheChained != null) {
            throw DbException.getInternalError("setPos too late")
        }
        this.pos = pos
    }

    open fun getPos(): Int {
        return pos
    }

    /**
     * Check if this cache object has been changed and thus needs to be written
     * back to the storage.
     *
     * @return if it has been changed
     */
    open fun isChanged(): Boolean {
        return changed
    }

    open fun setChanged(b: Boolean) {
        changed = b
    }

    override fun compareTo(other: CacheObject): Int {
        return Integer.compare(getPos(), other.getPos())
    }

    open fun isStream(): Boolean {
        return false
    }

}
