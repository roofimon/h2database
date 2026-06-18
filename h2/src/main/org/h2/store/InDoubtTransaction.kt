/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import org.h2.message.DbException

/**
 * Represents an in-doubt transaction (a transaction in the prepare phase).
 */
interface InDoubtTransaction {

    /**
     * Change the state of this transaction.
     * This will also update the transaction log.
     *
     * @param state the new state
     */
    fun setState(state: Int)

    /**
     * Get the state of this transaction.
     *
     * @return the transaction state
     */
    fun getState(): Int

    /**
     * Get the state of this transaction as a text.
     *
     * @return the transaction state text
     */
    fun getStateDescription(): String {
        val state = getState()
        return when (state) {
            0 -> "IN_DOUBT"
            1 -> "COMMIT"
            2 -> "ROLLBACK"
            else -> throw DbException.getInternalError("state=$state")
        }
    }

    /**
     * Get the name of the transaction.
     *
     * @return the transaction name
     */
    fun getTransactionName(): String?

    companion object {
        /**
         * The transaction state meaning this transaction is not committed yet, but
         * also not rolled back (in-doubt).
         */
        const val IN_DOUBT: Int = 0

        /**
         * The transaction state meaning this transaction is committed.
         */
        const val COMMIT: Int = 1

        /**
         * The transaction state meaning this transaction is rolled back.
         */
        const val ROLLBACK: Int = 2
    }
}
