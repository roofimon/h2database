/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.compress

import java.util.StringTokenizer
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

import org.h2.api.ErrorCode
import org.h2.mvstore.DataUtils

/**
 * This is a wrapper class for the Deflater class.
 * This algorithm supports the following options:
 * <ul>
 * <li>l or level: -1 (default), 0 (no compression),
 *  1 (best speed), ..., 9 (best compression)
 * </li><li>s or strategy: 0 (default),
 *  1 (filtered), 2 (huffman only)
 * </li></ul>
 * See also java.util.zip.Deflater for details.
 */
class CompressDeflate : Compressor {

    private var level = Deflater.DEFAULT_COMPRESSION
    private var strategy = Deflater.DEFAULT_STRATEGY

    override fun setOptions(options: String?) {
        if (options == null) {
            return
        }
        try {
            val tokenizer = StringTokenizer(options)
            while (tokenizer.hasMoreElements()) {
                val option = tokenizer.nextToken()
                if ("level" == option || "l" == option) {
                    level = tokenizer.nextToken().toInt()
                } else if ("strategy" == option || "s" == option) {
                    strategy = tokenizer.nextToken().toInt()
                }
                val deflater = Deflater(level)
                deflater.setStrategy(strategy)
            }
        } catch (e: Exception) {
            throw DataUtils.newMVStoreException(ErrorCode.UNSUPPORTED_COMPRESSION_OPTIONS_1, options)
        }
    }

    override fun compress(`in`: ByteArray, inPos: Int, inLen: Int, out: ByteArray, outPos: Int): Int {
        val deflater = Deflater(level)
        deflater.setStrategy(strategy)
        deflater.setInput(`in`, inPos, inLen)
        deflater.finish()
        val compressed = deflater.deflate(out, outPos, out.size - outPos)
        if (compressed == 0) {
            // the compressed length is 0, meaning compression didn't work
            // (sounds like a JDK bug)
            // try again, using the default strategy and compression level
            strategy = Deflater.DEFAULT_STRATEGY
            level = Deflater.DEFAULT_COMPRESSION
            return compress(`in`, inPos, inLen, out, outPos)
        }
        deflater.end()
        return outPos + compressed
    }

    override fun getAlgorithm(): Int {
        return Compressor.DEFLATE
    }

    override fun expand(`in`: ByteArray, inPos: Int, inLen: Int, out: ByteArray, outPos: Int, outLen: Int) {
        val decompresser = Inflater()
        decompresser.setInput(`in`, inPos, inLen)
        decompresser.finished()
        try {
            val len = decompresser.inflate(out, outPos, outLen)
            if (len != outLen) {
                throw DataFormatException("$len $outLen")
            }
        } catch (e: DataFormatException) {
            throw DataUtils.newMVStoreException(ErrorCode.COMPRESSION_ERROR, e.message, e)
        }
        decompresser.end()
    }
}
