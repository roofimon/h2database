/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.ArrayList

/**
 * An alternative cache implementation. This implementation uses two caches: an
 * LRU cache and a FIFO cache. Entries are first kept in the FIFO cache, and if
 * referenced again then marked in a hash set. If referenced again, they are
 * moved to the LRU cache. Stream pages are never added to the LRU cache. It is
 * supposed to be more or less scan resistant, and it doesn't cache large rows
 * in the LRU cache.
 */
class CacheTQ internal constructor(writer: CacheWriter, maxMemoryKb: Int) : Cache {

    private val lru: Cache
    private val fifo: Cache
    private val recentlyUsed: SmallLRUCache<Int, Any> =
        SmallLRUCache.newInstance(1024)
    private var lastUsed = -1

    private var maxMemory: Int = maxMemoryKb

    init {
        lru = CacheLRU(writer, (maxMemoryKb * 0.8).toInt(), false)
        fifo = CacheLRU(writer, (maxMemoryKb * 0.2).toInt(), true)
        setMaxMemory(4 * maxMemoryKb)
    }

    override fun clear() {
        lru.clear()
        fifo.clear()
        recentlyUsed.clear()
        lastUsed = -1
    }

    override fun find(pos: Int): CacheObject? {
        var r = lru.find(pos)
        if (r == null) {
            r = fifo.find(pos)
        }
        return r
    }

    override fun get(pos: Int): CacheObject? {
        var r = lru.find(pos)
        if (r != null) {
            return r
        }
        r = fifo.find(pos)
        if (r != null && !r.isStream()) {
            if (recentlyUsed[pos] != null) {
                if (lastUsed != pos) {
                    fifo.remove(pos)
                    lru.put(r)
                }
            } else {
                recentlyUsed[pos] = this
            }
            lastUsed = pos
        }
        return r
    }

    override fun getAllChanged(): ArrayList<CacheObject> {
        val lruChanged = lru.getAllChanged()
        val fifoChanged = fifo.getAllChanged()
        val changed = ArrayList<CacheObject>(lruChanged.size + fifoChanged.size)
        changed.addAll(lruChanged)
        changed.addAll(fifoChanged)
        return changed
    }

    override fun getMaxMemory(): Int {
        return maxMemory
    }

    override fun getMemory(): Int {
        return lru.getMemory() + fifo.getMemory()
    }

    override fun put(r: CacheObject) {
        if (r.isStream()) {
            fifo.put(r)
        } else if (recentlyUsed[r.getPos()] != null) {
            lru.put(r)
        } else {
            fifo.put(r)
            lastUsed = r.getPos()
        }
    }

    override fun remove(pos: Int): Boolean {
        var result = lru.remove(pos)
        if (!result) {
            result = fifo.remove(pos)
        }
        recentlyUsed.remove(pos)
        return result
    }

    override fun setMaxMemory(maxMemoryKb: Int) {
        this.maxMemory = maxMemoryKb
        lru.setMaxMemory((maxMemoryKb * 0.8).toInt())
        fifo.setMaxMemory((maxMemoryKb * 0.2).toInt())
        recentlyUsed.setMaxSize(4 * maxMemoryKb)
    }

    override fun update(pos: Int, record: CacheObject): CacheObject? {
        if (lru.find(pos) != null) {
            return lru.update(pos, record)
        }
        return fifo.update(pos, record)
    }

    companion object {
        const val TYPE_NAME = "TQ"
    }

}
