/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.util.HashMap
import org.h2.util.StringUtils

/**
 * A hash map with a case-insensitive string key.
 *
 * @param <V> the value type
 */
class CaseInsensitiveMap<V> : HashMap<String, V> {

    /**
     * Creates new instance of case-insensitive map.
     */
    constructor() : super()

    /**
     * Creates new instance of case-insensitive map with specified initial
     * capacity.
     *
     * @param initialCapacity the initial capacity
     */
    constructor(initialCapacity: Int) : super(initialCapacity)

    override fun get(key: String): V? {
        return super.get(StringUtils.toUpperEnglish(key as String))
    }

    override fun put(key: String, value: V): V? {
        return super.put(StringUtils.toUpperEnglish(key), value)
    }

    override fun putIfAbsent(key: String, value: V): V? {
        return super.putIfAbsent(StringUtils.toUpperEnglish(key), value)
    }

    override fun containsKey(key: String): Boolean {
        return super.containsKey(StringUtils.toUpperEnglish(key as String))
    }

    override fun remove(key: String): V? {
        return super.remove(StringUtils.toUpperEnglish(key as String))
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
