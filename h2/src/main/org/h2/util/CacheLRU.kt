/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.ArrayList
import java.util.Collections
import org.h2.engine.Constants
import org.h2.engine.SysProperties
import org.h2.message.DbException

/**
 * A cache implementation based on the last recently used (LRU) algorithm.
 */
class CacheLRU internal constructor(
    private val writer: CacheWriter,
    maxMemoryKb: Int,
    /**
     * Use First-In-First-Out (don't move recently used items to the front of
     * the queue).
     */
    private val fifo: Boolean
) : Cache {

    private val head: CacheObject = CacheHead()
    private val mask: Int
    private var values: Array<CacheObject?>? = null
    private var recordCount = 0

    /**
     * The number of cache buckets.
     */
    private val len: Int

    /**
     * The maximum memory, in words (4 bytes each).
     */
    private var maxMemory: Long = 0

    /**
     * The current memory used in this cache, in words (4 bytes each).
     */
    private var memory: Long = 0

    init {
        this.setMaxMemory(maxMemoryKb)
        try {
            // Since setMaxMemory() ensures that maxMemory is >=0,
            // we don't have to worry about an underflow.
            val tmpLen = maxMemory / 64
            if (tmpLen > Integer.MAX_VALUE) {
                throw IllegalArgumentException()
            }
            this.len = MathUtils.nextPowerOf2(tmpLen.toInt())
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("This much cache memory is not supported: " + maxMemoryKb + "kb", e)
        }
        this.mask = len - 1
        clear()
    }

    override fun clear() {
        head.cacheNext = head
        head.cachePrevious = head
        // first set to null - avoiding out of memory
        values = null
        values = arrayOfNulls(len)
        recordCount = 0
        memory = len * Constants.MEMORY_POINTER.toLong()
    }

    override fun put(rec: CacheObject) {
        if (SysProperties.CHECK) {
            val pos = rec.getPos()
            val old = find(pos)
            if (old != null) {
                throw DbException.getInternalError("try to add a record twice at pos $pos")
            }
        }
        val index = rec.getPos() and mask
        rec.cacheChained = values!![index]
        values!![index] = rec
        recordCount++
        memory += rec.getMemory()
        addToFront(rec)
        removeOldIfRequired()
    }

    override fun update(pos: Int, rec: CacheObject): CacheObject? {
        val old = find(pos)
        if (old == null) {
            put(rec)
        } else {
            if (old !== rec) {
                throw DbException.getInternalError("old!=record pos:$pos old:$old new:$rec")
            }
            if (!fifo) {
                removeFromLinkedList(rec)
                addToFront(rec)
            }
        }
        return old
    }

    private fun removeOldIfRequired() {
        // a small method, to allow inlining
        if (memory >= maxMemory) {
            removeOld()
        }
    }

    private fun removeOld() {
        var i = 0
        val changed = ArrayList<CacheObject>()
        var mem = memory
        var rc = recordCount
        var flushed = false
        var next = head.cacheNext
        while (true) {
            if (rc <= Constants.CACHE_MIN_RECORDS) {
                break
            }
            if (changed.isEmpty()) {
                if (mem <= maxMemory) {
                    break
                }
            } else {
                if (mem * 4 <= maxMemory * 3) {
                    break
                }
            }
            val check = next!!
            next = check.cacheNext
            i++
            if (i >= recordCount) {
                if (!flushed) {
                    writer.flushLog()
                    flushed = true
                    i = 0
                } else {
                    // can't remove any record, because the records can not be
                    // removed hopefully this does not happen frequently, but it
                    // can happen
                    writer.getTrace()
                        .info(
                            "cannot remove records, cache size too small? records:" +
                                recordCount + " memory:" + memory
                        )
                    break
                }
            }
            if (check === head) {
                throw DbException.getInternalError("try to remove head")
            }
            // we are not allowed to remove it if the log is not yet written
            // (because we need to log before writing the data)
            // also, can't write it if the record is pinned
            if (!check.canRemove()) {
                removeFromLinkedList(check)
                addToFront(check)
                continue
            }
            rc--
            mem -= check.getMemory()
            if (check.isChanged()) {
                changed.add(check)
            } else {
                remove(check.getPos())
            }
        }
        if (!changed.isEmpty()) {
            if (!flushed) {
                writer.flushLog()
            }
            Collections.sort(changed)
            val max = maxMemory
            val size = changed.size
            try {
                // temporary disable size checking,
                // to avoid stack overflow
                maxMemory = Long.MAX_VALUE
                i = 0
                while (i < size) {
                    val rec = changed[i]
                    writer.writeBack(rec)
                    i++
                }
            } finally {
                maxMemory = max
            }
            i = 0
            while (i < size) {
                val rec = changed[i]
                remove(rec.getPos())
                if (rec.cacheNext != null) {
                    throw DbException.getInternalError()
                }
                i++
            }
        }
    }

    private fun addToFront(rec: CacheObject) {
        if (rec === head) {
            throw DbException.getInternalError("try to move head")
        }
        rec.cacheNext = head
        rec.cachePrevious = head.cachePrevious
        rec.cachePrevious!!.cacheNext = rec
        head.cachePrevious = rec
    }

    private fun removeFromLinkedList(rec: CacheObject) {
        if (rec === head) {
            throw DbException.getInternalError("try to remove head")
        }
        rec.cachePrevious!!.cacheNext = rec.cacheNext
        rec.cacheNext!!.cachePrevious = rec.cachePrevious
        // TODO cache: mystery: why is this required? needs more memory if we
        // don't do this
        rec.cacheNext = null
        rec.cachePrevious = null
    }

    override fun remove(pos: Int): Boolean {
        val index = pos and mask
        var rec = values!![index]
        if (rec == null) {
            return false
        }
        if (rec.getPos() == pos) {
            values!![index] = rec.cacheChained
        } else {
            var last: CacheObject
            do {
                last = rec!!
                rec = rec.cacheChained
                if (rec == null) {
                    return false
                }
            } while (rec.getPos() != pos)
            last.cacheChained = rec.cacheChained
        }
        recordCount--
        memory -= rec.getMemory()
        removeFromLinkedList(rec)
        if (SysProperties.CHECK) {
            rec.cacheChained = null
            val o = find(pos)
            if (o != null) {
                throw DbException.getInternalError("not removed: $o")
            }
        }
        return true
    }

    override fun find(pos: Int): CacheObject? {
        var rec = values!![pos and mask]
        while (rec != null && rec.getPos() != pos) {
            rec = rec.cacheChained
        }
        return rec
    }

    override fun get(pos: Int): CacheObject? {
        val rec = find(pos)
        if (rec != null) {
            if (!fifo) {
                removeFromLinkedList(rec)
                addToFront(rec)
            }
        }
        return rec
    }

    // private void testConsistency() {
    // int s = size;
    // HashSet set = new HashSet();
    // for(int i=0; i<values.length; i++) {
    // Record rec = values[i];
    // if(rec == null) {
    // continue;
    // }
    // set.add(rec);
    // while(rec.chained != null) {
    // rec = rec.chained;
    // set.add(rec);
    // }
    // }
    // Record rec = head.next;
    // while(rec != head) {
    // set.add(rec);
    // rec = rec.next;
    // }
    // rec = head.previous;
    // while(rec != head) {
    // set.add(rec);
    // rec = rec.previous;
    // }
    // if(set.size() != size) {
    // System.out.println("size="+size+" but el.size="+set.size());
    // }
    // }

    override fun getAllChanged(): ArrayList<CacheObject> {
        // if(Database.CHECK) {
        // testConsistency();
        // }
        val list = ArrayList<CacheObject>()
        var rec = head.cacheNext
        while (rec !== head) {
            if (rec!!.isChanged()) {
                list.add(rec)
            }
            rec = rec.cacheNext
        }
        return list
    }

    override fun setMaxMemory(maxKb: Int) {
        val newSize = maxKb * 1024L / 4
        maxMemory = if (newSize < 0) 0 else newSize
        // can not resize, otherwise existing records are lost
        // resize(maxSize);
        removeOldIfRequired()
    }

    override fun getMaxMemory(): Int {
        return (maxMemory * 4L / 1024).toInt()
    }

    override fun getMemory(): Int {
        // CacheObject rec = head.cacheNext;
        // while (rec != head) {
        // System.out.println(rec.getMemory() + " " +
        // MemoryFootprint.getObjectSize(rec) + " " + rec);
        // rec = rec.cacheNext;
        // }
        return (memory * 4L / 1024).toInt()
    }

    companion object {
        const val TYPE_NAME = "LRU"

        /**
         * Create a cache of the given type and size.
         *
         * @param writer the cache writer
         * @param cacheType the cache type
         * @param cacheSize the size
         * @return the cache object
         */
        @JvmStatic
        fun getCache(writer: CacheWriter, cacheType: String, cacheSize: Int): Cache {
            var type = cacheType
            var secondLevel: MutableMap<Int, CacheObject>? = null
            if (type.startsWith("SOFT_")) {
                secondLevel = SoftValuesHashMap()
                type = type.substring("SOFT_".length)
            }
            var cache: Cache
            if (TYPE_NAME == type) {
                cache = CacheLRU(writer, cacheSize, false)
            } else if (CacheTQ.TYPE_NAME == type) {
                cache = CacheTQ(writer, cacheSize)
            } else {
                throw DbException.getInvalidValueException("CACHE_TYPE", type)
            }
            if (secondLevel != null) {
                cache = CacheSecondLevel(cache, secondLevel)
            }
            return cache
        }
    }

}
