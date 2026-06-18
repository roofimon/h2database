/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.IOException
import java.io.InputStream
import java.io.Reader
import org.h2.engine.SessionRemote
import org.h2.value.ValueBlob
import org.h2.value.ValueClob
import org.h2.value.ValueLob

/**
 * This factory creates in-memory objects and temporary files. It is used on the
 * client side.
 */
class LobStorageFrontend(private val sessionRemote: SessionRemote?) : LobStorageInterface {

    override fun removeLob(lob: ValueLob?) {
        // not stored in the database
    }

    @Throws(IOException::class)
    override fun getInputStream(lobId: Long, byteCount: Long): InputStream? {
        // this method is only implemented on the server side of a TCP connection
        throw IllegalStateException()
    }

    @Throws(IOException::class)
    override fun getInputStream(lobId: Long, tableId: Int, byteCount: Long): InputStream? {
        // this method is only implemented on the server side of a TCP
        // connection
        throw IllegalStateException()
    }

    override fun isReadOnly(): Boolean {
        return false
    }

    override fun copyLob(old: ValueLob?, tableId: Int): ValueLob? {
        throw UnsupportedOperationException()
    }

    override fun removeAllForTable(tableId: Int) {
        throw UnsupportedOperationException()
    }

    override fun createBlob(`in`: InputStream?, maxLength: Long): ValueBlob? {
        // need to use a temp file, because the input stream could come from
        // the same database, which would create a weird situation (trying
        // to read a block while writing something)
        return ValueBlob.createTempBlob(`in`, maxLength, sessionRemote)
    }

    /**
     * Create a CLOB object.
     *
     * @param reader the reader
     * @param maxLength the maximum length (-1 if not known)
     * @return the LOB
     */
    override fun createClob(reader: Reader?, maxLength: Long): ValueClob? {
        // need to use a temp file, because the input stream could come from
        // the same database, which would create a weird situation (trying
        // to read a block while writing something)
        return ValueClob.createTempClob(reader, maxLength, sessionRemote)
    }

    companion object {
        /**
         * The table id for session variables (LOBs not assigned to a table).
         */
        const val TABLE_ID_SESSION_VARIABLE: Int = -1

        /**
         * The table id for temporary objects (not assigned to any object).
         */
        const val TABLE_TEMP: Int = -2

        /**
         * The table id for result sets.
         */
        const val TABLE_RESULT: Int = -3
    }
}
