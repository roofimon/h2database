/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.LinkedHashMap

/**
 * This class implements a small LRU object cache.
 *
 * @param <K> the key
 * @param <V> the value
 */
class SmallLRUCache<K, V> private constructor(private var maxSize: Int) :
    LinkedHashMap<K, V>(maxSize, 0.75f, true) {

    fun setMaxSize(size: Int) {
        this.maxSize = size
    }

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxSize
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Create a new object with all elements of the given collection.
         *
         * @param <K> the key type
         * @param <V> the value type
         * @param size the number of elements
         * @return the object
         */
        @JvmStatic
        fun <K, V> newInstance(size: Int): SmallLRUCache<K, V> = SmallLRUCache(size)
    }
}
