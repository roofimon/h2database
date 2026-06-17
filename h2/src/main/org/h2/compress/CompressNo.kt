/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.compress

/**
 * This class implements a data compression algorithm that does in fact not
 * compress. This is useful if the data can not be compressed because it is
 * encrypted, already compressed, or random.
 */
class CompressNo : Compressor {

    override fun getAlgorithm(): Int {
        return Compressor.NO
    }

    override fun setOptions(options: String?) {
        // nothing to do
    }

    override fun compress(`in`: ByteArray, inPos: Int, inLen: Int, out: ByteArray, outPos: Int): Int {
        System.arraycopy(`in`, inPos, out, outPos, inLen)
        return outPos + inLen
    }

    override fun expand(`in`: ByteArray, inPos: Int, inLen: Int, out: ByteArray, outPos: Int, outLen: Int) {
        System.arraycopy(`in`, inPos, out, outPos, outLen)
    }
}
