/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.nio.charset.Charset
import java.text.CollationKey
import java.text.Collator
import java.util.Arrays
import java.util.Locale

/**
 * The charset collator sorts strings according to the order in the given charset.
 */
class CharsetCollator(private val charset: Charset) : Collator() {

    fun getCharset(): Charset {
        return charset
    }

    override fun compare(source: String, target: String): Int {
        return Arrays.compare(toBytes(source), toBytes(target))
    }

    /**
     * Convert the source to bytes, using the character set.
     *
     * @param source the source
     * @return the bytes
     */
    fun toBytes(source: String): ByteArray {
        var source = source
        if (strength <= Collator.SECONDARY) {
            // TODO perform case-insensitive comparison properly
            source = source.uppercase(Locale.ROOT)
        }
        return source.toByteArray(charset)
    }

    override fun getCollationKey(source: String): CollationKey {
        return CharsetCollationKey(source)
    }

    override fun hashCode(): Int {
        return 255
    }

    private inner class CharsetCollationKey(source: String) : CollationKey(source) {

        private val bytes: ByteArray = toBytes(source)

        override fun compareTo(target: CollationKey): Int {
            return Arrays.compare(bytes, target.toByteArray())
        }

        override fun toByteArray(): ByteArray {
            return bytes
        }
    }
}
