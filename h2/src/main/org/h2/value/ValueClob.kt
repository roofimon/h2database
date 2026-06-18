/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.Arrays

import org.h2.engine.CastDataProvider
import org.h2.engine.Constants
import org.h2.engine.SysProperties
import org.h2.message.DbException
import org.h2.store.DataHandler
import org.h2.store.FileStoreOutputStream
import org.h2.store.RangeReader
import org.h2.value.ValueLob.Companion.BLOCK_COMPARISON_SIZE
import org.h2.util.HasSQL.Companion.NO_CASTS
import org.h2.util.HasSQL.Companion.REPLACE_LOBS_FOR_TRACE
import org.h2.util.IOUtils
import org.h2.util.MathUtils
import org.h2.util.StringUtils
import org.h2.value.lob.LobData
import org.h2.value.lob.LobDataDatabase
import org.h2.value.lob.LobDataFetchOnDemand
import org.h2.value.lob.LobDataFile
import org.h2.value.lob.LobDataInMemory

/**
 * Implementation of the CHARACTER LARGE OBJECT data type.
 */
class ValueClob(lobData: LobData, octetLength: Long, charLength: Long) :
        ValueLob(lobData, octetLength, charLength) {

    override fun getValueType(): Int {
        return CLOB
    }

    override fun getString(): String {
        if (charLength > Constants.MAX_STRING_LENGTH) {
            throw getStringTooLong(charLength)
        }
        if (lobData is LobDataInMemory) {
            return String(lobData.small, StandardCharsets.UTF_8)
        }
        return readString(charLength.toInt())
    }

    override fun getBytesInternal(): ByteArray {
        var p = octetLength
        if (p >= 0L) {
            if (p > Constants.MAX_STRING_LENGTH) {
                throw getBinaryTooLong(p)
            }
            return readBytes(p.toInt())
        }
        if (octetLength > Constants.MAX_STRING_LENGTH) {
            throw getBinaryTooLong(octetLength())
        }
        val b = readBytes(Integer.MAX_VALUE)
        p = b.size.toLong()
        octetLength = p
        if (p > Constants.MAX_STRING_LENGTH) {
            throw getBinaryTooLong(p)
        }
        return b
    }

    override fun getInputStream(): InputStream {
        return lobData.getInputStream(-1L)
    }

    override fun getInputStream(oneBasedOffset: Long, length: Long): InputStream {
        return ValueLob.rangeInputStream(lobData.getInputStream(-1L), oneBasedOffset, length, -1L)
    }

    override fun getReader(oneBasedOffset: Long, length: Long): Reader {
        return ValueLob.rangeReader(getReader(), oneBasedOffset, length, charLength)
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        if (v === this) {
            return 0
        }
        val v2 = v as ValueClob
        val lobData = this.lobData
        val lobData2 = v2.lobData
        if (lobData.javaClass == lobData2.javaClass) {
            if (lobData is LobDataInMemory) {
                return Integer.signum(getString().compareTo(v2.getString()))
            } else if (lobData is LobDataDatabase) {
                if (lobData.lobId == (lobData2 as LobDataDatabase).lobId) {
                    return 0
                }
            } else if (lobData is LobDataFetchOnDemand) {
                if (lobData.lobId == (lobData2 as LobDataFetchOnDemand).lobId) {
                    return 0
                }
            }
        }
        return compare(this, v2)
    }

    override fun getSQL(builder: StringBuilder, sqlFlags: Int): StringBuilder {
        if ((sqlFlags and REPLACE_LOBS_FOR_TRACE) != 0
                && (lobData !is LobDataInMemory || charLength > SysProperties.MAX_TRACE_DATA_LENGTH)) {
            builder.append("SPACE(").append(charLength)
            formatLobDataComment(builder)
        } else {
            if ((sqlFlags and (REPLACE_LOBS_FOR_TRACE or NO_CASTS)) == 0) {
                StringUtils.quoteStringSQL(builder.append("CAST("), getString()).append(" AS CHARACTER LARGE OBJECT(")
                        .append(charLength).append("))")
            } else {
                StringUtils.quoteStringSQL(builder, getString())
            }
        }
        return builder
    }

    /**
     * Convert the precision to the requested value.
     *
     * @param precision
     *            the new precision
     * @return the truncated or this value
     */
    fun convertPrecision(precision: Long): ValueClob {
        if (this.charLength <= precision) {
            return this
        }
        val lob: ValueClob
        val handler = lobData.dataHandler
        if (handler != null) {
            lob = createTempClob(getReader(), precision, handler)
        } else {
            try {
                lob = createSmall(IOUtils.readStringAndClose(getReader(), MathUtils.convertLongToInt(precision)))
            } catch (e: IOException) {
                throw DbException.convertIOException(e, null)
            }
        }
        return lob
    }

    override fun copy(database: DataHandler, tableId: Int): ValueLob {
        if (lobData is LobDataInMemory) {
            val small = lobData.small
            if (small.size > database.maxLengthInplaceLob) {
                val s = database.lobStorage!!
                val v = s.createClob(getReader(), charLength)!!
                val v2 = v.copy(database, tableId)
                v.remove()
                return v2
            }
            return this
        } else if (lobData is LobDataDatabase) {
            return database.lobStorage!!.copyLob(this, tableId)!!
        } else {
            throw UnsupportedOperationException()
        }
    }

    override fun charLength(): Long {
        return charLength
    }

    override fun octetLength(): Long {
        var p = octetLength
        if (p < 0L) {
            if (lobData is LobDataInMemory) {
                p = lobData.small.size.toLong()
            } else {
                try {
                    getInputStream().use { `is` ->
                        p = 0L
                        while (true) {
                            p += `is`.skip(Long.MAX_VALUE)
                            if (`is`.read() < 0) {
                                break
                            }
                            p++
                        }
                    }
                } catch (e: IOException) {
                    throw DbException.convertIOException(e, null)
                }
            }
            octetLength = p
        }
        return p
    }

    companion object {
        /**
         * Creates a small CLOB value that can be stored in the row directly.
         *
         * @param data
         *            the data in UTF-8 encoding
         * @return the CLOB
         */
        @JvmStatic
        fun createSmall(data: ByteArray): ValueClob {
            return ValueClob(LobDataInMemory(data), data.size.toLong(),
                    String(data, StandardCharsets.UTF_8).length.toLong())
        }

        /**
         * Creates a small CLOB value that can be stored in the row directly.
         *
         * @param data
         *            the data in UTF-8 encoding
         * @param charLength
         *            the count of characters, must be exactly the same as count of
         *            characters in the data
         * @return the CLOB
         */
        @JvmStatic
        fun createSmall(data: ByteArray, charLength: Long): ValueClob {
            return ValueClob(LobDataInMemory(data), data.size.toLong(), charLength)
        }

        /**
         * Creates a small CLOB value that can be stored in the row directly.
         *
         * @param string
         *            the string with value
         * @return the CLOB
         */
        @JvmStatic
        fun createSmall(string: String): ValueClob {
            val bytes = string.toByteArray(StandardCharsets.UTF_8)
            return ValueClob(LobDataInMemory(bytes), bytes.size.toLong(), string.length.toLong())
        }

        /**
         * Create a temporary CLOB value from a stream.
         *
         * @param in
         *            the reader
         * @param length
         *            the number of characters to read, or -1 for no limit
         * @param handler
         *            the data handler
         * @return the lob value
         */
        @JvmStatic
        fun createTempClob(`in`: Reader, length: Long, handler: DataHandler): ValueClob {
            var input = `in`
            if (length >= 0) {
                // Otherwise BufferedReader may try to read more data than needed
                // and that
                // blocks the network level
                try {
                    input = RangeReader(input, 0, length)
                } catch (e: IOException) {
                    throw DbException.convert(e)
                }
            }
            val reader: BufferedReader
            if (input is BufferedReader) {
                reader = input
            } else {
                reader = BufferedReader(input, Constants.IO_BUFFER_SIZE)
            }
            try {
                var remaining = Long.MAX_VALUE
                if (length in 0 until remaining) {
                    remaining = length
                }
                var len = ValueLob.getBufferSize(handler, remaining)
                val buff: CharArray
                if (len >= Integer.MAX_VALUE) {
                    val data = IOUtils.readStringAndClose(reader, -1)
                    buff = data.toCharArray()
                    len = buff.size
                } else {
                    buff = CharArray(len)
                    reader.mark(len)
                    len = IOUtils.readFully(reader, buff, len)
                }
                if (len <= handler.maxLengthInplaceLob) {
                    return createSmall(String(buff, 0, len))
                }
                reader.reset()
                return createTemporary(handler, reader, remaining)
            } catch (e: IOException) {
                throw DbException.convertIOException(e, null)
            }
        }

        /**
         * Create a CLOB in a temporary file.
         */
        @Throws(IOException::class)
        private fun createTemporary(handler: DataHandler, `in`: Reader, remaining: Long): ValueClob {
            val fileName = ValueLob.createTempLobFileName(handler)
            val tempFile = handler.openFile(fileName, "rw", false)!!
            tempFile.autoDelete()

            var octetLength = 0L
            var charLength = 0L
            FileStoreOutputStream(tempFile, null).use { out ->
                val buff = CharArray(Constants.IO_BUFFER_SIZE)
                while (true) {
                    var len = ValueLob.getBufferSize(handler, remaining)
                    len = IOUtils.readFully(`in`, buff, len)
                    if (len == 0) {
                        break
                    }
                    // TODO reduce memory allocation
                    val data = String(buff, 0, len).toByteArray(StandardCharsets.UTF_8)
                    out.write(data)
                    octetLength += data.size.toLong()
                    charLength += len.toLong()
                }
            }
            return ValueClob(LobDataFile(handler, fileName, tempFile), octetLength, charLength)
        }

        /**
         * Compares two CLOB values directly.
         *
         * @param v1
         *            first CLOB value
         * @param v2
         *            second CLOB value
         * @return result of comparison
         */
        private fun compare(v1: ValueClob, v2: ValueClob): Int {
            var minPrec = Math.min(v1.charLength, v2.charLength)
            try {
                v1.getReader().use { reader1 ->
                    v2.getReader().use { reader2 ->
                        val buf1 = CharArray(BLOCK_COMPARISON_SIZE)
                        val buf2 = CharArray(BLOCK_COMPARISON_SIZE)
                        while (minPrec >= BLOCK_COMPARISON_SIZE) {
                            if (IOUtils.readFully(reader1, buf1, BLOCK_COMPARISON_SIZE) != BLOCK_COMPARISON_SIZE
                                    || IOUtils.readFully(reader2, buf2, BLOCK_COMPARISON_SIZE) != BLOCK_COMPARISON_SIZE) {
                                throw DbException.getUnsupportedException("Invalid LOB")
                            }
                            val cmp = Integer.signum(Arrays.compare(buf1, buf2))
                            if (cmp != 0) {
                                return cmp
                            }
                            minPrec -= BLOCK_COMPARISON_SIZE.toLong()
                        }
                        while (true) {
                            val c1 = reader1.read()
                            val c2 = reader2.read()
                            if (c1 < 0) {
                                return if (c2 < 0) 0 else -1
                            }
                            if (c2 < 0) {
                                return 1
                            }
                            if (c1 != c2) {
                                return if (c1 < c2) -1 else 1
                            }
                        }
                    }
                }
            } catch (ex: IOException) {
                throw DbException.convert(ex)
            }
        }
    }

}
