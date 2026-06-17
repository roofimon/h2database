/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.AbstractMap
import java.util.HashMap

/**
 * Map which stores items using SoftReference. Items can be garbage collected
 * and removed. It is not a general purpose cache, as it doesn't implement some
 * methods, and others not according to the map definition, to improve speed.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
class SoftValuesHashMap<K, V> : AbstractMap<K, V>() {

    private val map: MutableMap<K, SoftValue<V>> = HashMap()
    private val queue = ReferenceQueue<V>()

    @Suppress("UNCHECKED_CAST")
    private fun processQueue() {
        while (true) {
            val o: Reference<out V>? = queue.poll()
            if (o == null) {
                return
            }
            val k = o as SoftValue<V>
            val key: Any? = k.key
            map.remove(key)
        }
    }

    override fun get(key: K): V? {
        processQueue()
        val o: SoftReference<V>? = map[key]
        if (o == null) {
            return null
        }
        return o.get()
    }

    /**
     * Store the object. The return value of this method is null or a
     * SoftReference.
     *
     * @param key the key
     * @param value the value
     * @return null or the old object.
     */
    override fun put(key: K, value: V): V? {
        processQueue()
        val old: SoftValue<V>? = map.put(key, SoftValue(value, queue, key))
        return old?.get()
    }

    /**
     * Remove an object.
     *
     * @param key the key
     * @return null or the old object
     */
    override fun remove(key: K): V? {
        processQueue()
        val ref: SoftReference<V>? = map.remove(key)
        return ref?.get()
    }

    override fun clear() {
        processQueue()
        map.clear()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = throw UnsupportedOperationException()

    /**
     * A soft reference that has a hard reference to the key.
     */
    private class SoftValue<T>(ref: T, q: ReferenceQueue<T>, @JvmField val key: Any?) :
        SoftReference<T>(ref, q)
}
