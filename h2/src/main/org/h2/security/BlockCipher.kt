/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.security

/**
 * A block cipher is a data encryption algorithm that operates on blocks.
 */
interface BlockCipher {

    /**
     * Set the encryption key used for encrypting and decrypting.
     * The key needs to be 16 bytes long.
     *
     * @param key the key
     */
    fun setKey(key: ByteArray)

    /**
     * Encrypt a number of bytes. This is done in-place, that
     * means the bytes are overwritten.
     *
     * @param bytes the byte array
     * @param off the start index
     * @param len the number of bytes to encrypt
     */
    fun encrypt(bytes: ByteArray, off: Int, len: Int)

    /**
     * Decrypt a number of bytes. This is done in-place, that
     * means the bytes are overwritten.
     *
     * @param bytes the byte array
     * @param off the start index
     * @param len the number of bytes to decrypt
     */
    fun decrypt(bytes: ByteArray, off: Int, len: Int)

    /**
     * Get the length of the key in bytes.
     *
     * @return the length of the key
     */
    fun getKeyLength(): Int

    companion object {

        /**
         * Blocks sizes are always multiples of this number.
         */
        const val ALIGN: Int = 16
    }
}
