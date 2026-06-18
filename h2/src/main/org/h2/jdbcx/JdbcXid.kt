/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jdbcx

import java.util.Base64
import javax.transaction.xa.Xid

import org.h2.api.ErrorCode
import org.h2.message.DbException
import org.h2.message.TraceObject

/**
 * An object of this class represents a transaction id.
 */
class JdbcXid internal constructor(factory: JdbcDataSourceFactory, id: Int, tid: String) :
    TraceObject(), Xid {

    private val formatId: Int
    private val branchQualifier: ByteArray
    private val globalTransactionId: ByteArray

    init {
        setTrace(factory.getTrace(), XID, id)
        var fId = 0
        var bQualifier: ByteArray? = null
        var gTransactionId: ByteArray? = null
        try {
            val splits = tid.split("\\|".toRegex()).toTypedArray()
            if (splits.size == 4 && PREFIX == splits[0]) {
                fId = Integer.parseInt(splits[1])
                val decoder = Base64.getUrlDecoder()
                bQualifier = decoder.decode(splits[2])
                gTransactionId = decoder.decode(splits[3])
            }
        } catch (e: IllegalArgumentException) {
            // ignore
        }
        if (bQualifier == null || gTransactionId == null) {
            throw DbException.get(ErrorCode.WRONG_XID_FORMAT_1, tid)
        }
        formatId = fId
        branchQualifier = bQualifier
        globalTransactionId = gTransactionId
    }

    /**
     * Get the format id.
     *
     * @return the format id
     */
    override fun getFormatId(): Int {
        debugCodeCall("getFormatId")
        return formatId
    }

    /**
     * The transaction branch identifier.
     *
     * @return the identifier
     */
    override fun getBranchQualifier(): ByteArray {
        debugCodeCall("getBranchQualifier")
        return branchQualifier
    }

    /**
     * The global transaction identifier.
     *
     * @return the transaction id
     */
    override fun getGlobalTransactionId(): ByteArray {
        debugCodeCall("getGlobalTransactionId")
        return globalTransactionId
    }

    companion object {

        private const val PREFIX = "XID"

        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        /**
         * INTERNAL
         * @param builder to put result into
         * @param xid to provide string representation for
         * @return provided StringBuilder
         */
        @JvmStatic
        fun toString(builder: StringBuilder, xid: Xid): StringBuilder {
            return builder.append(PREFIX).append('|').append(xid.formatId)
                .append('|').append(ENCODER.encodeToString(xid.branchQualifier))
                .append('|').append(ENCODER.encodeToString(xid.globalTransactionId))
        }
    }
}
