/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

/**
 * The head element of the linked list.
 */
class CacheHead : CacheObject() {

    override fun canRemove(): Boolean {
        return false
    }

    override fun getMemory(): Int {
        return 0
    }

}
