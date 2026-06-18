/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0, and the
 * EPL 1.0 (https://h2database.com/html/license.html). Initial Developer: H2
 * Group
 */
package org.h2.value

import java.io.IOException
import java.io.InputStream
import java.io.Reader

import org.h2.engine.Constants
import org.h2.engine.SysProperties
import org.h2.message.DbException
import org.h2.store.DataHandler
import org.h2.store.LobStorageFrontend
import org.h2.store.RangeInputStream
import org.h2.store.RangeReader
import org.h2.store.fs.FileUtils
import org.h2.util.IOUtils
import org.h2.util.MathUtils
import org.h2.util.StringUtils
import org.h2.util.Utils
import org.h2.value.lob.LobData
import org.h2.value.lob.LobDataDatabase
import org.h2.value.lob.LobDataFetchOnDemand
import org.h2.value.lob.LobDataInMemory

/**
 * A implementation of the BINARY LARGE OBJECT and CHARACTER LARGE OBJECT data
 * types. Small objects are kept in memory and stored in the record. Large
 * objects are either stored in the database, or in temporary files.
 */
abstract class ValueLob internal constructor(
    @JvmField val lobData: LobData,
    /**
     * Length in bytes.
     */
    @JvmField var octetLength: Long,
    /**
     * Length in characters.
     */
    @JvmField var charLength: Long
) : Value() {

    private var type: TypeInfo? = null

    /**
     * Cache the hashCode because it can be expensive to compute.
     */
    private var hash: Int = 0

    /**
     * Check if this value is linked to a specific table. For values that are
     * kept fully in memory, this method returns false.
     *
     * @return true if it is
     */
    open fun isLinkedToTable(): Boolean {
        return lobData.isLinkedToTable
    }

    /**
     * Remove the underlying resource, if any. For values that are kept fully in
     * memory this method has no effect.
     */
    open fun remove() {
        lobData.remove(this)
    }

    /**
     * Copy a large value, to be used in the given table. For values that are
     * kept fully in memory this method has no effect.
     *
     * @param database the data handler
     * @param tableId the table where this object is used
     * @return the new value or itself
     */
    abstract fun copy(database: DataHandler, tableId: Int): ValueLob

    override fun getType(): TypeInfo {
        var type = this.type
        if (type == null) {
            val valueType = getValueType()
            type = TypeInfo(valueType, if (valueType == CLOB) charLength else octetLength, 0, null)
            this.type = type
        }
        return type
    }

    fun getStringTooLong(precision: Long): DbException {
        return DbException.getValueTooLongException("CHARACTER VARYING", readString(81), precision)
    }

    fun readString(len: Int): String {
        try {
            return IOUtils.readStringAndClose(getReader(), len)
        } catch (e: IOException) {
            throw DbException.convertIOException(e, toString())
        }
    }

    override fun getReader(): Reader {
        return IOUtils.getReader(getInputStream())!!
    }

    override fun getBytes(): ByteArray {
        if (lobData is LobDataInMemory) {
            return Utils.cloneByteArray(getSmall())!!
        }
        return getBytesInternal()
    }

    override fun getBytesNoCopy(): ByteArray {
        if (lobData is LobDataInMemory) {
            return getSmall()
        }
        return getBytesInternal()
    }

    private fun getSmall(): ByteArray {
        val small = (lobData as LobDataInMemory).small
        val p = small.size
        if (p > Constants.MAX_STRING_LENGTH) {
            throw DbException.getValueTooLongException("BINARY VARYING",
                    StringUtils.convertBytesToHex(small, 41), p.toLong())
        }
        return small
    }

    abstract fun getBytesInternal(): ByteArray

    fun getBinaryTooLong(precision: Long): DbException {
        return DbException.getValueTooLongException("BINARY VARYING",
                StringUtils.convertBytesToHex(readBytes(41)), precision)
    }

    fun readBytes(len: Int): ByteArray {
        try {
            return IOUtils.readBytesAndClose(getInputStream(), len)
        } catch (e: IOException) {
            throw DbException.convertIOException(e, toString())
        }
    }

    override fun hashCode(): Int {
        if (hash == 0) {
            val valueType = getValueType()
            val length = if (valueType == Value.CLOB) charLength else octetLength
            if (length > 4096) {
                // TODO: should calculate the hash code when saving, and store
                // it in the database file
                return (length xor (length ushr 32)).toInt()
            }
            hash = Utils.getByteArrayHash(getBytesNoCopy())
        }
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ValueLob)
            return false
        if (hashCode() != other.hashCode())
            return false
        return compareTypeSafe(other, null, null) == 0
    }

    override fun getMemory(): Int {
        return lobData.memory
    }

    fun getLobData(): LobData {
        return lobData
    }

    /**
     * Create an independent copy of this value, that will be bound to a result.
     *
     * @return the value (this for small objects)
     */
    open fun copyToResult(): ValueLob {
        if (lobData is LobDataDatabase) {
            val s = lobData.dataHandler!!.lobStorage!!
            if (!s.isReadOnly()) {
                return s.copyLob(this, LobStorageFrontend.TABLE_RESULT)!!
            }
        }
        return this
    }

    fun formatLobDataComment(builder: StringBuilder) {
        if (lobData is LobDataDatabase) {
            val lobDb = lobData
            builder.append(" /* table: ").append(lobDb.tableId).append(" id: ").append(lobDb.lobId)
                    .append(" */)")
        } else if (lobData is LobDataFetchOnDemand) {
            val lobDemand = lobData
            builder.append(" /* table: ").append(lobDemand.tableId).append(" id: ")
                    .append(lobDemand.lobId).append(" */)")
        } else {
            builder.append(" /* ").append(lobData.toString().replace(Regex("\\*/"), "\\\\*\\\\/"))
                    .append(" */")
        }
    }

    companion object {

        const val BLOCK_COMPARISON_SIZE: Int = 512

        private fun rangeCheckUnknown(zeroBasedOffset: Long, length: Long) {
            if (zeroBasedOffset < 0) {
                throw DbException.getInvalidValueException("offset", zeroBasedOffset + 1)
            }
            if (length < 0) {
                throw DbException.getInvalidValueException("length", length)
            }
        }

        /**
         * Create an input stream that is s subset of the given stream.
         *
         * @param inputStream the source input stream
         * @param oneBasedOffset the offset (1 means no offset)
         * @param length the length of the result, in bytes
         * @param dataSize the length of the input, in bytes
         * @return the smaller input stream
         */
        @JvmStatic
        protected fun rangeInputStream(inputStream: InputStream, oneBasedOffset: Long, length: Long,
                dataSize: Long): InputStream {
            if (dataSize > 0) {
                Value.rangeCheck(oneBasedOffset - 1, length, dataSize)
            } else {
                rangeCheckUnknown(oneBasedOffset - 1, length)
            }
            try {
                return RangeInputStream(inputStream, oneBasedOffset - 1, length)
            } catch (e: IOException) {
                throw DbException.getInvalidValueException("offset", oneBasedOffset)
            }
        }

        /**
         * Create a reader that is s subset of the given reader.
         *
         * @param reader the input reader
         * @param oneBasedOffset the offset (1 means no offset)
         * @param length the length of the result, in bytes
         * @param dataSize the length of the input, in bytes
         * @return the smaller input stream
         */
        @JvmStatic
        fun rangeReader(reader: Reader, oneBasedOffset: Long, length: Long, dataSize: Long): Reader {
            if (dataSize > 0) {
                Value.rangeCheck(oneBasedOffset - 1, length, dataSize)
            } else {
                rangeCheckUnknown(oneBasedOffset - 1, length)
            }
            try {
                return RangeReader(reader, oneBasedOffset - 1, length)
            } catch (e: IOException) {
                throw DbException.getInvalidValueException("offset", oneBasedOffset)
            }
        }

        /**
         * Create file name for temporary LOB storage
         * @param handler to get path from
         * @return full path and name of the created file
         * @throws IOException if file creation fails
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createTempLobFileName(handler: DataHandler): String {
            var path = handler.databasePath!!
            if (path.isEmpty()) {
                path = SysProperties.PREFIX_TEMP_FILE
            }
            return FileUtils.createTempFile(path, Constants.SUFFIX_TEMP_FILE, true)
        }

        @JvmStatic
        fun getBufferSize(handler: DataHandler, remaining: Long): Int {
            var remaining = remaining
            if (remaining < 0 || remaining > Integer.MAX_VALUE) {
                remaining = Integer.MAX_VALUE.toLong()
            }
            val inplace = handler.maxLengthInplaceLob
            var m = Constants.IO_BUFFER_SIZE.toLong()
            if (m < remaining && m <= inplace) {
                // using "1L" to force long arithmetic because
                // inplace could be Integer.MAX_VALUE
                m = Math.min(remaining, inplace + 1L)
                // the buffer size must be bigger than the inplace lob, otherwise we
                // can't know if it must be stored in-place or not
                m = MathUtils.roundUpLong(m, Constants.IO_BUFFER_SIZE.toLong())
            }
            m = Math.min(remaining, m)
            m = MathUtils.convertLongToInt(m).toLong()
            if (m < 0) {
                m = Integer.MAX_VALUE.toLong()
            }
            return m.toInt()
        }
    }

}
