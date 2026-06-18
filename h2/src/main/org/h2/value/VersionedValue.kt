/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

/**
 * A versioned value (possibly null).
 * It contains current value and latest committed value if current one is uncommitted.
 * Also for uncommitted values it contains operationId - a combination of
 * transactionId and logId.
 */
abstract class VersionedValue<T> protected constructor() {

    open fun isCommitted(): Boolean {
        return true
    }

    open fun getOperationId(): Long {
        return NO_OPERATION_ID
    }

    open fun getEntryId(): Long {
        return NO_ENTRY_ID
    }

    @Suppress("UNCHECKED_CAST")
    open fun getCurrentValue(): T {
        return this as T
    }

    @Suppress("UNCHECKED_CAST")
    open fun getCommittedValue(): T {
        return this as T
    }

    companion object {
        const val NO_ENTRY_ID = -1L
        const val NO_OPERATION_ID = 0L
    }
}
