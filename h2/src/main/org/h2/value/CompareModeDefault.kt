/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.text.CollationKey
import java.text.Collator

import org.h2.engine.SysProperties
import org.h2.message.DbException
import org.h2.util.SmallLRUCache

/**
 * The default implementation of CompareMode. It uses java.text.Collator.
 */
open class CompareModeDefault internal constructor(name: String, strength: Int) : CompareMode(name, strength) {

    private val collator: Collator
    private val collationKeys: SmallLRUCache<String, CollationKey>?

    @Volatile
    private var caseInsensitive: CompareModeDefault? = null

    init {
        collator = CompareMode.getCollator(name) ?: throw DbException.getInternalError(name)
        collator.strength = strength
        val cacheSize = SysProperties.COLLATOR_CACHE_SIZE
        collationKeys = if (cacheSize != 0) {
            SmallLRUCache.newInstance(cacheSize)
        } else {
            null
        }
    }

    override fun compareString(a: String, b: String, ignoreCase: Boolean): Int {
        if (ignoreCase && getStrength() > Collator.SECONDARY) {
            var i = caseInsensitive
            if (i == null) {
                i = CompareModeDefault(getName(), Collator.SECONDARY)
                caseInsensitive = i
            }
            return i.compareString(a, b, false)
        }
        val comp: Int
        if (collationKeys != null) {
            val aKey = getKey(a)
            val bKey = getKey(b)
            comp = aKey.compareTo(bKey)
        } else {
            comp = collator.compare(a, b)
        }
        return comp
    }

    override fun equalsChars(a: String, ai: Int, b: String, bi: Int, ignoreCase: Boolean): Boolean {
        return compareString(
            a.substring(ai, ai + 1), b.substring(bi, bi + 1),
            ignoreCase
        ) == 0
    }

    private fun getKey(a: String): CollationKey {
        synchronized(collationKeys!!) {
            var key = collationKeys.get(a)
            if (key == null) {
                key = collator.getCollationKey(a)
                collationKeys.put(a, key)
            }
            return key
        }
    }
}
