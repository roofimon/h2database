/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: Jan Kotek
 */
package org.h2.util

import java.util.ArrayList

/**
 * Cache which wraps another cache (proxy pattern) and adds caching using map.
 * This is useful for WeakReference, SoftReference or hard reference cache.
 */
internal class CacheSecondLevel(
    private val baseCache: Cache,
    private val map: MutableMap<Int, CacheObject>
) : Cache {

    override fun clear() {
        map.clear()
        baseCache.clear()
    }

    override fun find(pos: Int): CacheObject? {
        var ret = baseCache.find(pos)
        if (ret == null) {
            ret = map[pos]
        }
        return ret
    }

    override fun get(pos: Int): CacheObject? {
        var ret = baseCache.get(pos)
        if (ret == null) {
            ret = map[pos]
        }
        return ret
    }

    override fun getAllChanged(): ArrayList<CacheObject> {
        return baseCache.getAllChanged()
    }

    override fun getMaxMemory(): Int {
        return baseCache.getMaxMemory()
    }

    override fun getMemory(): Int {
        return baseCache.getMemory()
    }

    override fun put(r: CacheObject) {
        baseCache.put(r)
        map[r.getPos()] = r
    }

    override fun remove(pos: Int): Boolean {
        var result = baseCache.remove(pos)
        result = result or (map.remove(pos) != null)
        return result
    }

    override fun setMaxMemory(size: Int) {
        baseCache.setMaxMemory(size)
    }

    override fun update(pos: Int, record: CacheObject): CacheObject? {
        val oldRec = baseCache.update(pos, record)
        map[pos] = record
        return oldRec
    }

}
