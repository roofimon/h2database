/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

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
import org.h2.value.ValueLob.Companion.BLOCK_COMPARISON_SIZE
import org.h2.util.HasSQL.Companion.NO_CASTS
import org.h2.util.HasSQL.Companion.REPLACE_LOBS_FOR_TRACE
import org.h2.util.IOUtils
import org.h2.util.MathUtils
import org.h2.util.StringUtils
import org.h2.util.Utils
import org.h2.value.lob.LobData
import org.h2.value.lob.LobDataDatabase
import org.h2.value.lob.LobDataFetchOnDemand
import org.h2.value.lob.LobDataFile
import org.h2.value.lob.LobDataInMemory

/**
 * Implementation of the BINARY LARGE OBJECT data type.
 */
class ValueBlob(lobData: LobData, octetLength: Long) : ValueLob(lobData, octetLength, -1L) {

    override fun getValueType(): Int {
        return BLOB
    }

    override fun getString(): String {
        var p = charLength
        if (p >= 0L) {
            if (p > Constants.MAX_STRING_LENGTH) {
                throw getStringTooLong(p)
            }
            return readString(p.toInt())
        }
        // 1 Java character may be encoded with up to 3 bytes
        if (octetLength > Constants.MAX_STRING_LENGTH * 3L) {
            throw getStringTooLong(charLength())
        }
        val s: String
        if (lobData is LobDataInMemory) {
            s = String(lobData.small, StandardCharsets.UTF_8)
        } else {
            s = readString(Integer.MAX_VALUE)
        }
        p = s.length.toLong()
        charLength = p
        if (p > Constants.MAX_STRING_LENGTH) {
            throw getStringTooLong(p)
        }
        return s
    }

    override fun getBytesInternal(): ByteArray {
        if (octetLength > Constants.MAX_STRING_LENGTH) {
            throw getBinaryTooLong(octetLength)
        }
        return readBytes(octetLength.toInt())
    }

    override fun getInputStream(): InputStream {
        return lobData.getInputStream(octetLength)
    }

    override fun getInputStream(oneBasedOffset: Long, length: Long): InputStream {
        val p = octetLength
        return ValueLob.rangeInputStream(lobData.getInputStream(p), oneBasedOffset, length, p)
    }

    override fun getReader(oneBasedOffset: Long, length: Long): Reader {
        return ValueLob.rangeReader(getReader(), oneBasedOffset, length, -1L)
    }

    override fun compareTypeSafe(v: Value, mode: CompareMode?, provider: CastDataProvider?): Int {
        if (v === this) {
            return 0
        }
        val v2 = v as ValueBlob
        val lobData = this.lobData
        val lobData2 = v2.lobData
        if (lobData.javaClass == lobData2.javaClass) {
            if (lobData is LobDataInMemory) {
                return Integer.signum(Arrays.compareUnsigned(lobData.small,
                        (lobData2 as LobDataInMemory).small))
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
                && (lobData !is LobDataInMemory || octetLength > SysProperties.MAX_TRACE_DATA_LENGTH)) {
            builder.append("CAST(REPEAT(CHAR(0), ").append(octetLength).append(") AS BINARY VARYING")
            formatLobDataComment(builder)
        } else {
            if ((sqlFlags and (REPLACE_LOBS_FOR_TRACE or NO_CASTS)) == 0) {
                builder.append("CAST(X'")
                StringUtils.convertBytesToHex(builder, getBytesNoCopy()).append("' AS BINARY LARGE OBJECT(")
                        .append(octetLength).append("))")
            } else {
                builder.append("X'")
                StringUtils.convertBytesToHex(builder, getBytesNoCopy()).append('\'')
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
    fun convertPrecision(precision: Long): ValueBlob {
        if (this.octetLength <= precision) {
            return this
        }
        val lob: ValueBlob
        val handler = lobData.dataHandler
        if (handler != null) {
            lob = createTempBlob(getInputStream(), precision, handler)
        } else {
            try {
                lob = createSmall(IOUtils.readBytesAndClose(getInputStream(), MathUtils.convertLongToInt(precision)))
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
                val v = s.createBlob(getInputStream(), octetLength)!!
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
        var p = charLength
        if (p < 0L) {
            if (lobData is LobDataInMemory) {
                p = String(lobData.small, StandardCharsets.UTF_8).length.toLong()
            } else {
                try {
                    getReader().use { r ->
                        p = 0L
                        while (true) {
                            p += r.skip(Long.MAX_VALUE)
                            if (r.read() < 0) {
                                break
                            }
                            p++
                        }
                    }
                } catch (e: IOException) {
                    throw DbException.convertIOException(e, null)
                }
            }
            charLength = p
        }
        return p
    }

    override fun octetLength(): Long {
        return octetLength
    }

    companion object {
        /**
         * Creates a small BLOB value that can be stored in the row directly.
         *
         * @param data
         *            the data
         * @return the BLOB
         */
        @JvmStatic
        fun createSmall(data: ByteArray): ValueBlob {
            return ValueBlob(LobDataInMemory(data), data.size.toLong())
        }

        /**
         * Create a temporary BLOB value from a stream.
         *
         * @param in
         *            the input stream
         * @param length
         *            the number of characters to read, or -1 for no limit
         * @param handler
         *            the data handler
         * @return the lob value
         */
        @JvmStatic
        fun createTempBlob(`in`: InputStream, length: Long, handler: DataHandler): ValueBlob {
            try {
                var remaining = Long.MAX_VALUE
                if (length in 0 until remaining) {
                    remaining = length
                }
                var len = ValueLob.getBufferSize(handler, remaining)
                val buff: ByteArray
                if (len >= Integer.MAX_VALUE) {
                    buff = IOUtils.readBytesAndClose(`in`, -1)
                    len = buff.size
                } else {
                    buff = Utils.newBytes(len)
                    len = IOUtils.readFully(`in`, buff, len)
                }
                if (len <= handler.maxLengthInplaceLob) {
                    return createSmall(Utils.copyBytes(buff, len))
                }
                return createTemporary(handler, buff, len, `in`, remaining)
            } catch (e: IOException) {
                throw DbException.convertIOException(e, null)
            }
        }

        /**
         * Create a BLOB in a temporary file.
         */
        @Throws(IOException::class)
        private fun createTemporary(handler: DataHandler, buff: ByteArray, len: Int, `in`: InputStream,
                remaining: Long): ValueBlob {
            var len = len
            var remaining = remaining
            val fileName = ValueLob.createTempLobFileName(handler)
            val tempFile = handler.openFile(fileName, "rw", false)!!
            tempFile.autoDelete()
            var tmpPrecision = 0L
            FileStoreOutputStream(tempFile, null).use { out ->
                while (true) {
                    tmpPrecision += len.toLong()
                    out.write(buff, 0, len)
                    remaining -= len.toLong()
                    if (remaining <= 0) {
                        break
                    }
                    len = ValueLob.getBufferSize(handler, remaining)
                    len = IOUtils.readFully(`in`, buff, len)
                    if (len <= 0) {
                        break
                    }
                }
            }
            return ValueBlob(LobDataFile(handler, fileName, tempFile), tmpPrecision)
        }

        /**
         * Compares two BLOB values directly.
         *
         * @param v1
         *            first BLOB value
         * @param v2
         *            second BLOB value
         * @return result of comparison
         */
        private fun compare(v1: ValueBlob, v2: ValueBlob): Int {
            var minPrec = Math.min(v1.octetLength, v2.octetLength)
            try {
                v1.getInputStream().use { is1 ->
                    v2.getInputStream().use { is2 ->
                        val buf1 = ByteArray(BLOCK_COMPARISON_SIZE)
                        val buf2 = ByteArray(BLOCK_COMPARISON_SIZE)
                        while (minPrec >= BLOCK_COMPARISON_SIZE) {
                            if (IOUtils.readFully(is1, buf1, BLOCK_COMPARISON_SIZE) != BLOCK_COMPARISON_SIZE
                                    || IOUtils.readFully(is2, buf2, BLOCK_COMPARISON_SIZE) != BLOCK_COMPARISON_SIZE) {
                                throw DbException.getUnsupportedException("Invalid LOB")
                            }
                            val cmp = Integer.signum(Arrays.compareUnsigned(buf1, buf2))
                            if (cmp != 0) {
                                return cmp
                            }
                            minPrec -= BLOCK_COMPARISON_SIZE.toLong()
                        }
                        while (true) {
                            val c1 = is1.read()
                            val c2 = is2.read()
                            if (c1 < 0) {
                                return if (c2 < 0) 0 else -1
                            }
                            if (c2 < 0) {
                                return 1
                            }
                            if (c1 != c2) {
                                return if ((c1 and 0xFF) < (c2 and 0xFF)) -1 else 1
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
