/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.compress

/**
 * Each data compression algorithm must implement this interface.
 */
interface Compressor {

    companion object {

        /**
         * No compression is used.
         */
        const val NO = 0

        /**
         * The LZF compression algorithm is used
         */
        const val LZF = 1

        /**
         * The DEFLATE compression algorithm is used.
         */
        const val DEFLATE = 2
    }

    /**
     * Get the compression algorithm type.
     *
     * @return the type
     */
    fun getAlgorithm(): Int

    /**
     * Compress a number of bytes.
     *
     * @param in the input data
     * @param inPos the offset at the input array
     * @param inLen the number of bytes to compress
     * @param out the output area
     * @param outPos the offset at the output array
     * @return the end position
     */
    fun compress(`in`: ByteArray, inPos: Int, inLen: Int, out: ByteArray, outPos: Int): Int

    /**
     * Expand a number of compressed bytes.
     *
     * @param in the compressed data
     * @param inPos the offset at the input array
     * @param inLen the number of bytes to read
     * @param out the output area
     * @param outPos the offset at the output array
     * @param outLen the size of the uncompressed data
     */
    fun expand(`in`: ByteArray, inPos: Int, inLen: Int, out: ByteArray, outPos: Int, outLen: Int)

    /**
     * Set the compression options. This may include settings for
     * higher performance but less compression.
     *
     * @param options the options
     */
    fun setOptions(options: String?)
}
