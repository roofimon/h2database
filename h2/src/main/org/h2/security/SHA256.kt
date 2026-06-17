/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.security

import org.h2.util.Bits.INT_VH_BE
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * This class implements the cryptographic hash function SHA-256.
 */
class SHA256 private constructor() {

    companion object {

        /**
         * Calculate the hash code by using the given salt. The salt is appended
         * after the data before the hash code is calculated. After generating the
         * hash code, the data and all internal buffers are filled with zeros to
         * avoid keeping insecure data in memory longer than required (and possibly
         * swapped to disk).
         *
         * @param data the data to hash
         * @param salt the salt to use
         * @return the hash code
         */
        @JvmStatic
        fun getHashWithSalt(data: ByteArray, salt: ByteArray): ByteArray {
            val buff = ByteArray(data.size + salt.size)
            System.arraycopy(data, 0, buff, 0, data.size)
            System.arraycopy(salt, 0, buff, data.size, salt.size)
            return getHash(buff, true)
        }

        /**
         * Calculate the hash of a password by prepending the user name and a '@'
         * character. Both the user name and the password are encoded to a byte
         * array using UTF-16. After generating the hash code, the password array
         * and all internal buffers are filled with zeros to avoid keeping the plain
         * text password in memory longer than required (and possibly swapped to
         * disk).
         *
         * @param userName the user name
         * @param password the password
         * @return the hash code
         */
        @JvmStatic
        fun getKeyPasswordHash(userName: String, password: CharArray): ByteArray {
            val user = "$userName@"
            val buff = ByteArray(2 * (user.length + password.size))
            var n = 0
            var i = 0
            val length = user.length
            while (i < length) {
                val c = user[i]
                buff[n++] = (c.code shr 8).toByte()
                buff[n++] = c.code.toByte()
                i++
            }
            for (c in password) {
                buff[n++] = (c.code shr 8).toByte()
                buff[n++] = c.code.toByte()
            }
            Arrays.fill(password, 0.toChar())
            return getHash(buff, true)
        }

        /**
         * Calculate the hash-based message authentication code.
         *
         * @param key the key
         * @param message the message
         * @return the hash
         */
        @JvmStatic
        fun getHMAC(key: ByteArray, message: ByteArray): ByteArray {
            return initMac(key).doFinal(message)
        }

        private fun initMac(key: ByteArray): Mac {
            // Java forbids empty keys
            var key = key
            if (key.size == 0) {
                key = ByteArray(1)
            }
            try {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(key, "HmacSHA256"))
                return mac
            } catch (e: GeneralSecurityException) {
                throw RuntimeException(e)
            }
        }

        /**
         * Calculate the hash using the password-based key derivation function 2.
         *
         * @param password the password
         * @param salt the salt
         * @param iterations the number of iterations
         * @param resultLen the number of bytes in the result
         * @return the result
         */
        @JvmStatic
        fun getPBKDF2(
            password: ByteArray, salt: ByteArray,
            iterations: Int, resultLen: Int
        ): ByteArray {
            val result = ByteArray(resultLen)
            val mac = initMac(password)
            var len = 64 + Math.max(32, salt.size + 4)
            val message = ByteArray(len)
            var macRes: ByteArray? = null
            var k = 1
            var offset = 0
            while (offset < resultLen) {
                for (i in 0 until iterations) {
                    if (i == 0) {
                        System.arraycopy(salt, 0, message, 0, salt.size)
                        INT_VH_BE.set(message, salt.size, k)
                        len = salt.size + 4
                    } else {
                        System.arraycopy(macRes!!, 0, message, 0, 32)
                        len = 32
                    }
                    mac.update(message, 0, len)
                    macRes = mac.doFinal()
                    var j = 0
                    while (j < 32 && j + offset < resultLen) {
                        result[j + offset] = (result[j + offset].toInt() xor macRes[j].toInt()).toByte()
                        j++
                    }
                }
                k++
                offset += 32
            }
            Arrays.fill(password, 0.toByte())
            return result
        }

        /**
         * Calculate the hash code for the given data.
         *
         * @param data the data to hash
         * @param nullData if the data should be filled with zeros after calculating
         *            the hash code
         * @return the hash code
         */
        @JvmStatic
        fun getHash(data: ByteArray, nullData: Boolean): ByteArray {
            val result: ByteArray
            try {
                result = MessageDigest.getInstance("SHA-256").digest(data)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
            if (nullData) {
                Arrays.fill(data, 0.toByte())
            }
            return result
        }
    }
}
