/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.ByteArrayOutputStream
import java.lang.ref.SoftReference
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.Arrays
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.function.IntPredicate

import org.h2.api.ErrorCode
import org.h2.engine.SysProperties
import org.h2.message.DbException

/**
 * A few String-related utility functions.
 */
class StringUtils private constructor() {
    // utility class

    companion object {

        private var softCache: SoftReference<Array<String>>? = null
        private var softCacheCreatedNs: Long = 0

        private val HEX = "0123456789abcdef".toCharArray()
        private val HEX_DECODE = kotlin.IntArray('f'.code + 1)

        // memory used by this cache:
        // 4 * 1024 * 2 (strings per pair) * 64 * 2 (bytes per char) = 0.5 MB
        private const val TO_UPPER_CACHE_LENGTH = 2 * 1024
        private const val TO_UPPER_CACHE_MAX_ENTRY_LENGTH = 64
        private val TO_UPPER_CACHE = AtomicReferenceArray<Array<String>>(
            TO_UPPER_CACHE_LENGTH
        )

        init {
            Arrays.fill(HEX_DECODE, -1)
            for (i in 0..9) {
                HEX_DECODE[i + '0'.code] = i
            }
            for (i in 0..5) {
                HEX_DECODE[i + 'A'.code] = i + 10
                HEX_DECODE[i + 'a'.code] = i + 10
            }
        }

        private fun getCache(): Array<String>? {
            var cache: Array<String>?
            if (softCache != null) {
                cache = softCache!!.get()
                if (cache != null) {
                    return cache
                }
            }
            // create a new cache at most every 5 seconds
            // so that out of memory exceptions are not delayed
            val time = System.nanoTime()
            if (softCacheCreatedNs != 0L && time - softCacheCreatedNs < TimeUnit.SECONDS.toNanos(5)) {
                return null
            }
            try {
                cache = arrayOfNulls<String>(SysProperties.OBJECT_CACHE_SIZE) as Array<String>
                softCache = SoftReference(cache)
                return cache
            } finally {
                softCacheCreatedNs = System.nanoTime()
            }
        }

        /**
         * Convert a string to uppercase using the English locale.
         *
         * @param s the test to convert
         * @return the uppercase text
         */
        @JvmStatic
        fun toUpperEnglish(s: String): String {
            if (s.length > TO_UPPER_CACHE_MAX_ENTRY_LENGTH) {
                return s.uppercase(Locale.ENGLISH)
            }
            val index = s.hashCode() and (TO_UPPER_CACHE_LENGTH - 1)
            val e = TO_UPPER_CACHE.get(index)
            if (e != null) {
                if (e[0] == s) {
                    return e[1]
                }
            }
            val s2 = s.uppercase(Locale.ENGLISH)
            TO_UPPER_CACHE.compareAndSet(index, e, arrayOf(s, s2))
            return s2
        }

        /**
         * Convert a string to lowercase using the English locale.
         *
         * @param s the text to convert
         * @return the lowercase text
         */
        @JvmStatic
        fun toLowerEnglish(s: String): String {
            return s.lowercase(Locale.ENGLISH)
        }

        /**
         * Convert a string to a SQL literal. Null is converted to NULL. The text is
         * enclosed in single quotes. If there are any special characters, the
         * Unicode character string literal is used.
         *
         * @param s the text to convert.
         * @return the SQL literal
         */
        @JvmStatic
        fun quoteStringSQL(s: String?): String {
            if (s == null) {
                return "NULL"
            }
            return quoteStringSQL(StringBuilder(s.length + 2), s).toString()
        }

        /**
         * Convert a string to a SQL character string literal. Null is converted to
         * NULL. If there are any special characters, the Unicode character string
         * literal is used.
         *
         * @param builder
         *            string builder to append result to
         * @param s the text to convert
         * @return the specified string builder
         */
        @JvmStatic
        fun quoteStringSQL(builder: StringBuilder, s: String?): StringBuilder {
            if (s == null) {
                return builder.append("NULL")
            }
            return quoteIdentifierOrLiteral(builder, s, '\'')
        }

        /**
         * Decodes a Unicode SQL string.
         *
         * @param s
         *            the string to decode
         * @param uencode
         *            the code point of UENCODE character, or '\\'
         * @return the decoded string
         * @throws DbException
         *             on format exception
         */
        @JvmStatic
        fun decodeUnicodeStringSQL(s: String, uencode: Int): String {
            val l = s.length
            val builder = StringBuilder(l)
            var i = 0
            while (i < l) {
                var cp = s.codePointAt(i)
                i += Character.charCount(cp)
                if (cp == uencode) {
                    if (i >= l) {
                        throw getFormatException(s, i)
                    }
                    cp = s.codePointAt(i)
                    if (cp == uencode) {
                        i += Character.charCount(cp)
                    } else {
                        if (i + 4 > l) {
                            throw getFormatException(s, i)
                        }
                        val ch = s[i]
                        try {
                            if (ch == '+') {
                                if (i + 7 > l) {
                                    throw getFormatException(s, i)
                                }
                                cp = Integer.parseUnsignedInt(s.substring(i + 1, (i + 7).also { i = it }), 16)
                            } else {
                                cp = Integer.parseUnsignedInt(s.substring(i, (i + 4).also { i = it }), 16)
                            }
                        } catch (e: NumberFormatException) {
                            throw getFormatException(s, i)
                        }
                    }
                }
                builder.appendCodePoint(cp)
            }
            return builder.toString()
        }

        /**
         * Convert a string to a Java literal using the correct escape sequences.
         * The literal is not enclosed in double quotes. The result can be used in
         * properties files or in Java source code.
         *
         * @param s the text to convert
         * @return the Java representation
         */
        @JvmStatic
        fun javaEncode(s: String): String {
            val buff = StringBuilder(s.length)
            javaEncode(s, buff, false)
            return buff.toString()
        }

        /**
         * Convert a string to a Java literal using the correct escape sequences.
         * The literal is not enclosed in double quotes. The result can be used in
         * properties files or in Java source code.
         *
         * @param s the text to convert
         * @param buff the Java representation to return
         * @param forSQL true if we embed this inside a STRINGDECODE SQL command
         */
        @JvmStatic
        fun javaEncode(s: String, buff: StringBuilder, forSQL: Boolean) {
            val length = s.length
            for (i in 0 until length) {
                val c = s[i]
                when (c) {
//            case '\b':
//                // BS backspace
//                // not supported in properties files
//                buff.append("\\b");
//                break;
                    '\t' ->
                        // HT horizontal tab
                        buff.append("\\t")
                    '\n' ->
                        // LF linefeed
                        buff.append("\\n")
                    '\u000c' ->
                        // FF form feed
                        buff.append("\\f")
                    '\r' ->
                        // CR carriage return
                        buff.append("\\r")
                    '"' ->
                        // double quote
                        buff.append("\\\"")
                    '\'' -> {
                        // quote:
                        if (forSQL) {
                            buff.append('\'')
                        }
                        buff.append('\'')
                    }
                    '\\' ->
                        // backslash
                        buff.append("\\\\")
                    else -> {
                        if (c >= ' ' && (c.code < 0x80)) {
                            buff.append(c)
                            // not supported in properties files
                            // } else if (c < 0xff) {
                            // buff.append("\\");
                            // // make sure it's three characters (0x200 is octal 1000)
                            // buff.append(Integer.toOctalString(0x200 | c).substring(1));
                        } else {
                            buff.append("\\u")
                                .append(HEX[c.code ushr 12])
                                .append(HEX[c.code ushr 8 and 0xf])
                                .append(HEX[c.code ushr 4 and 0xf])
                                .append(HEX[c.code and 0xf])
                        }
                    }
                }
            }
        }

        /**
         * Add an asterisk ('[*]') at the given position. This format is used to
         * show where parsing failed in a statement.
         *
         * @param s the text
         * @param index the position
         * @return the text with asterisk
         */
        @JvmStatic
        fun addAsterisk(s: String?, index: Int): String? {
            var s = s
            if (s != null) {
                val len = s.length
                val idx = Math.min(index, len)
                s = StringBuilder(len + 3).append(s, 0, idx).append("[*]").append(s, idx, len).toString()
            }
            return s
        }

        private fun getFormatException(s: String, i: Int): DbException {
            return DbException.get(ErrorCode.STRING_FORMAT_ERROR_1, addAsterisk(s, i))
        }

        /**
         * Decode a text that is encoded as a Java string literal. The Java
         * properties file format and Java source code format is supported.
         *
         * @param s the encoded string
         * @return the string
         */
        @JvmStatic
        fun javaDecode(s: String): String {
            val length = s.length
            val buff = StringBuilder(length)
            var i = 0
            while (i < length) {
                var c = s[i]
                if (c == '\\') {
                    if (i + 1 >= s.length) {
                        throw getFormatException(s, i)
                    }
                    c = s[++i]
                    when (c) {
                        't' -> buff.append('\t')
                        'r' -> buff.append('\r')
                        'n' -> buff.append('\n')
                        'b' -> buff.append('\b')
                        'f' -> buff.append('\u000c')
                        '#' ->
                            // for properties files
                            buff.append('#')
                        '=' ->
                            // for properties files
                            buff.append('=')
                        ':' ->
                            // for properties files
                            buff.append(':')
                        '"' -> buff.append('"')
                        '\\' -> buff.append('\\')
                        'u' -> {
                            if (i + 4 >= length) {
                                throw getFormatException(s, i)
                            }
                            try {
                                c = Integer.parseInt(s.substring(i + 1, i + 5), 16).toChar()
                            } catch (e: NumberFormatException) {
                                throw getFormatException(s, i)
                            }
                            i += 4
                            buff.append(c)
                        }
                        else -> {
                            if (c >= '0' && c <= '9' && i + 2 < length) {
                                try {
                                    c = Integer.parseInt(s.substring(i, i + 3), 8).toChar()
                                } catch (e: NumberFormatException) {
                                    throw getFormatException(s, i)
                                }
                                i += 2
                                buff.append(c)
                            } else {
                                throw getFormatException(s, i)
                            }
                        }
                    }
                } else {
                    buff.append(c)
                }
                i++
            }
            return buff.toString()
        }

        /**
         * Convert a string to the Java literal and enclose it with double quotes.
         * Null will result in "null" (without double quotes).
         *
         * @param s the text to convert
         * @return the Java representation
         */
        @JvmStatic
        fun quoteJavaString(s: String?): String {
            if (s == null) {
                return "null"
            }
            val builder = StringBuilder(s.length + 2).append('"')
            javaEncode(s, builder, false)
            return builder.append('"').toString()
        }

        /**
         * Convert a string array to the Java source code that represents this
         * array. Null will be converted to 'null'.
         *
         * @param array the string array
         * @return the Java source code (including new String[]{})
         */
        @JvmStatic
        fun quoteJavaStringArray(array: Array<String?>?): String {
            if (array == null) {
                return "null"
            }
            val buff = StringBuilder("new String[]{")
            for (i in array.indices) {
                if (i > 0) {
                    buff.append(", ")
                }
                buff.append(quoteJavaString(array[i]))
            }
            return buff.append('}').toString()
        }

        /**
         * Convert an int array to the Java source code that represents this array.
         * Null will be converted to 'null'.
         *
         * @param array the int array
         * @return the Java source code (including new int[]{})
         */
        @JvmStatic
        fun quoteJavaIntArray(array: kotlin.IntArray?): String {
            if (array == null) {
                return "null"
            }
            val builder = StringBuilder("new int[]{")
            for (i in array.indices) {
                if (i > 0) {
                    builder.append(", ")
                }
                builder.append(array[i])
            }
            return builder.append('}').toString()
        }

        /**
         * Encode the string as a URL.
         *
         * @param s the string to encode
         * @return the encoded string
         */
        @JvmStatic
        fun urlEncode(s: String): String {
            try {
                return URLEncoder.encode(s, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                // UnsupportedEncodingException
                throw DbException.convert(e)
            }
        }

        /**
         * Decode the URL to a string.
         *
         * @param encoded the encoded URL
         * @return the decoded string
         */
        @JvmStatic
        fun urlDecode(encoded: String): String {
            val length = encoded.length
            val buff = ByteArray(length)
            var j = 0
            var i = 0
            while (i < length) {
                val ch = encoded[i]
                if (ch == '+') {
                    buff[j++] = ' '.code.toByte()
                } else if (ch == '%') {
                    buff[j++] = Integer.parseInt(encoded.substring(i + 1, i + 3), 16).toByte()
                    i += 2
                } else if (ch.code <= 127 && ch >= ' ') {
                    buff[j++] = ch.code.toByte()
                } else {
                    throw IllegalArgumentException("Unexpected char " + ch.code + " decoding " + encoded)
                }
                i++
            }
            return String(buff, 0, j, StandardCharsets.UTF_8)
        }

        /**
         * Split a string into an array of strings using the given separator. A null
         * string will result in a null array, and an empty string in a zero element
         * array.
         *
         * @param s the string to split
         * @param separatorChar the separator character
         * @param trim whether each element should be trimmed
         * @return the array list
         */
        @JvmStatic
        fun arraySplit(s: String?, separatorChar: Char, trim: Boolean): Array<String>? {
            if (s == null) {
                return null
            }
            val length = s.length
            if (length == 0) {
                return arrayOf()
            }
            val list = Utils.newSmallArrayList<String>()
            val buff = StringBuilder(length)
            var i = 0
            while (i < length) {
                val c = s[i]
                if (c == separatorChar) {
                    val e = buff.toString()
                    list.add(if (trim) e.trim { it <= ' ' } else e)
                    buff.setLength(0)
                } else if (c == '\\' && i < length - 1) {
                    buff.append(s[++i])
                } else {
                    buff.append(c)
                }
                i++
            }
            val e = buff.toString()
            list.add(if (trim) e.trim { it <= ' ' } else e)
            return list.toTypedArray()
        }

        /**
         * Combine an array of strings to one array using the given separator
         * character. A backslash and the separator character and escaped using a
         * backslash.
         *
         * @param list the string array
         * @param separatorChar the separator character
         * @return the combined string
         */
        @JvmStatic
        fun arrayCombine(list: Array<String?>, separatorChar: Char): String {
            val builder = StringBuilder()
            for (i in list.indices) {
                if (i > 0) {
                    builder.append(separatorChar)
                }
                val s = list[i] ?: continue
                var j = 0
                val length = s.length
                while (j < length) {
                    val c = s[j]
                    if (c == '\\' || c == separatorChar) {
                        builder.append('\\')
                    }
                    builder.append(c)
                    j++
                }
            }
            return builder.toString()
        }

        /**
         * Creates an XML attribute of the form name="value".
         * A single space is prepended to the name,
         * so that multiple attributes can be concatenated.
         * @param name the attribute name
         * @param value the attribute value
         * @return the attribute
         */
        @JvmStatic
        fun xmlAttr(name: String, value: String): String {
            return " " + name + "=\"" + xmlText(value) + "\""
        }

        /**
         * Create an XML node with optional attributes and content.
         * The data is indented with 4 spaces if it contains a newline character.
         *
         * @param name the element name
         * @param attributes the attributes (might be null)
         * @param content the content (might be null)
         * @return the node
         */
        @JvmStatic
        fun xmlNode(name: String, attributes: String?, content: String?): String {
            return xmlNode(name, attributes, content, true)
        }

        /**
         * Create an XML node with optional attributes and content. The data is
         * indented with 4 spaces if it contains a newline character and the indent
         * parameter is set to true.
         *
         * @param name the element name
         * @param attributes the attributes (might be null)
         * @param content the content (might be null)
         * @param indent whether to indent the content if it contains a newline
         * @return the node
         */
        @JvmStatic
        fun xmlNode(
            name: String, attributes: String?,
            content: String?, indent: Boolean
        ): String {
            val builder = StringBuilder()
            builder.append('<').append(name)
            if (attributes != null) {
                builder.append(attributes)
            }
            if (content == null) {
                builder.append("/>\n")
                return builder.toString()
            }
            builder.append('>')
            if (indent && content.indexOf('\n') >= 0) {
                builder.append('\n')
                indent(builder, content, 4, true)
            } else {
                builder.append(content)
            }
            builder.append("</").append(name).append(">\n")
            return builder.toString()
        }

        /**
         * Indents a string with spaces and appends it to a specified builder.
         *
         * @param builder string builder to append to
         * @param s the string
         * @param spaces the number of spaces
         * @param newline append a newline if there is none
         * @return the specified string builder
         */
        @JvmStatic
        fun indent(builder: StringBuilder, s: String, spaces: Int, newline: Boolean): StringBuilder {
            var i = 0
            val length = s.length
            while (i < length) {
                for (j in 0 until spaces) {
                    builder.append(' ')
                }
                var n = s.indexOf('\n', i)
                n = if (n < 0) length else n + 1
                builder.append(s, i, n)
                i = n
            }
            if (newline && !s.endsWith("\n")) {
                builder.append('\n')
            }
            return builder
        }

        /**
         * Escapes a comment.
         * If the data contains '--', it is converted to '- -'.
         * The data is indented with 4 spaces if it contains a newline character.
         *
         * @param data the comment text
         * @return &lt;!-- data --&gt;
         */
        @JvmStatic
        fun xmlComment(data: String): String {
            var data = data
            var idx = 0
            while (true) {
                idx = data.indexOf("--", idx)
                if (idx < 0) {
                    break
                }
                data = data.substring(0, idx + 1) + " " + data.substring(idx + 1)
            }
            // must have a space at the beginning and at the end,
            // otherwise the data must not contain '-' as the first/last character
            if (data.indexOf('\n') >= 0) {
                val builder = StringBuilder(data.length + 18).append("<!--\n")
                return indent(builder, data, 4, true).append("-->\n").toString()
            }
            return "<!-- $data -->\n"
        }

        /**
         * Converts the data to a CDATA element.
         * If the data contains ']]&gt;', it is escaped as a text element.
         *
         * @param data the text data
         * @return &lt;![CDATA[data]]&gt;
         */
        @JvmStatic
        fun xmlCData(data: String): String {
            var data = data
            if (data.contains("]]>")) {
                return xmlText(data)
            }
            val newline = data.endsWith("\n")
            data = "<![CDATA[$data]]>"
            return if (newline) data + "\n" else data
        }

        /**
         * Returns &lt;?xml version="1.0"?&gt;
         * @return &lt;?xml version="1.0"?&gt;
         */
        @JvmStatic
        fun xmlStartDoc(): String {
            return "<?xml version=\"1.0\"?>\n"
        }

        /**
         * Escapes an XML text element.
         *
         * @param text the text data
         * @return the escaped text
         */
        @JvmStatic
        fun xmlText(text: String): String {
            return xmlText(text, false)
        }

        /**
         * Escapes an XML text element.
         *
         * @param text the text data
         * @param escapeNewline whether to escape newlines
         * @return the escaped text
         */
        @JvmStatic
        fun xmlText(text: String, escapeNewline: Boolean): String {
            val length = text.length
            val buff = StringBuilder(length)
            for (i in 0 until length) {
                val ch = text[i]
                when (ch) {
                    '<' -> buff.append("&lt;")
                    '>' -> buff.append("&gt;")
                    '&' -> buff.append("&amp;")
                    '\'' ->
                        // &apos; is not valid in HTML
                        buff.append("&#39;")
                    '\"' -> buff.append("&quot;")
                    '\r', '\n' -> {
                        if (escapeNewline) {
                            buff.append("&#x")
                                .append(Integer.toHexString(ch.code))
                                .append(';')
                        } else {
                            buff.append(ch)
                        }
                    }
                    '\t' -> buff.append(ch)
                    else -> {
                        if (ch < ' ' || ch.code > 127) {
                            buff.append("&#x")
                                .append(Integer.toHexString(ch.code))
                                .append(';')
                        } else {
                            buff.append(ch)
                        }
                    }
                }
            }
            return buff.toString()
        }

        /**
         * Replace all occurrences of the before string with the after string. Unlike
         * [String.replaceAll] this method reads `before`
         * and `after` arguments as plain strings and if `before` argument
         * is an empty string this method returns original string `s`.
         *
         * @param s the string
         * @param before the old text
         * @param after the new text
         * @return the string with the before string replaced
         */
        @JvmStatic
        fun replaceAll(s: String, before: String, after: String): String {
            var next = s.indexOf(before)
            if (next < 0 || before.isEmpty()) {
                return s
            }
            val buff = StringBuilder(
                s.length - before.length + after.length
            )
            var index = 0
            while (true) {
                buff.append(s, index, next).append(after)
                index = next + before.length
                next = s.indexOf(before, index)
                if (next < 0) {
                    buff.append(s, index, s.length)
                    break
                }
            }
            return buff.toString()
        }

        /**
         * Enclose a string with double quotes. A double quote inside the string is
         * escaped using a double quote.
         *
         * @param s the text
         * @return the double-quoted text
         */
        @JvmStatic
        fun quoteIdentifier(s: String): String {
            return quoteIdentifierOrLiteral(StringBuilder(s.length + 2), s, '"').toString()
        }

        /**
         * Enclose a string with double quotes and append it to the specified
         * string builder. A double quote inside the string is escaped using a
         * double quote.
         *
         * @param builder string builder to append to
         * @param s the text
         * @return the specified builder
         */
        @JvmStatic
        fun quoteIdentifier(builder: StringBuilder, s: String): StringBuilder {
            return quoteIdentifierOrLiteral(builder, s, '"')
        }

        private fun quoteIdentifierOrLiteral(builder: StringBuilder, s: String, q: Char): StringBuilder {
            val builderLength = builder.length
            builder.append(q)
            val l = s.length
            var i = 0
            while (i < l) {
                var cp = s.codePointAt(i)
                i += Character.charCount(cp)
                if (cp < ' '.code || cp > 127) {
                    // need to start from the beginning
                    builder.setLength(builderLength)
                    builder.append("U&").append(q)
                    i = 0
                    while (i < l) {
                        cp = s.codePointAt(i)
                        i += Character.charCount(cp)
                        if (cp >= ' '.code && cp < 127) {
                            val ch = cp.toChar()
                            if (ch == q || ch == '\\') {
                                builder.append(ch)
                            }
                            builder.append(ch)
                        } else if (cp <= 0xffff) {
                            appendHex(builder.append('\\'), cp.toLong(), 2)
                        } else {
                            appendHex(builder.append("\\+"), cp.toLong(), 3)
                        }
                    }
                    break
                }
                if (cp == q.code) {
                    builder.append(q)
                }
                builder.append(cp.toChar())
            }
            return builder.append(q)
        }

        /**
         * Check if a String is null or empty (the length is null).
         *
         * @param s the string to check
         * @return true if it is null or empty
         */
        @JvmStatic
        fun isNullOrEmpty(s: String?): Boolean {
            return s == null || s.isEmpty()
        }

        /**
         * Pad a string. This method is used for the SQL function RPAD and LPAD.
         *
         * @param string the original string
         * @param n the target length
         * @param padding the padding string
         * @param right true if the padding should be appended at the end
         * @return the padded string
         */
        @JvmStatic
        fun pad(string: String, n: Int, padding: String?, right: Boolean): String {
            var n = n
            if (n < 0) {
                n = 0
            }
            if (n < string.length) {
                return string.substring(0, n)
            } else if (n == string.length) {
                return string
            }
            val paddingChar: Int
            if (padding == null || padding.isEmpty()) {
                paddingChar = ' '.code
            } else {
                paddingChar = padding.codePointAt(0)
            }
            val buff = StringBuilder(n)
            n -= string.length
            if (Character.isSupplementaryCodePoint(paddingChar)) {
                n = n shr 1
            }
            if (right) {
                buff.append(string)
            }
            for (i in 0 until n) {
                buff.appendCodePoint(paddingChar)
            }
            if (!right) {
                buff.append(string)
            }
            return buff.toString()
        }

        /**
         * Create a new char array and copy all the data. If the size of the byte
         * array is zero, the same array is returned.
         *
         * @param chars the char array (might be null)
         * @return a new char array
         */
        @JvmStatic
        fun cloneCharArray(chars: CharArray?): CharArray? {
            if (chars == null) {
                return null
            }
            val len = chars.size
            if (len == 0) {
                return chars
            }
            return Arrays.copyOf(chars, len)
        }

        /**
         * Trim a character from a string.
         *
         * @param s the string
         * @param leading if leading characters should be removed
         * @param trailing if trailing characters should be removed
         * @param characters what to remove or `null` for a space
         * @return the trimmed string
         */
        @JvmStatic
        fun trim(s: String, leading: Boolean, trailing: Boolean, characters: String?): String {
            if (characters == null || characters.isEmpty()) {
                return trim(s, leading, trailing, ' ')
            }
            val length = characters.length
            if (length == 1) {
                return trim(s, leading, trailing, characters[0])
            }
            val test: IntPredicate
            val count = characters.codePointCount(0, length)
            if (count <= 2) {
                val cp = characters.codePointAt(0)
                if (count > 1) {
                    val cp2 = characters.codePointAt(Character.charCount(cp))
                    if (cp != cp2) {
                        test = IntPredicate { value -> value == cp || value == cp2 }
                        return trim(s, leading, trailing, test)
                    }
                }
                test = IntPredicate { value -> value == cp }
            } else {
                val set = HashSet<Int>()
                characters.codePoints().forEach { set.add(it) }
                test = IntPredicate { set.contains(it) }
            }
            return trim(s, leading, trailing, test)
        }

        private fun trim(s: String, leading: Boolean, trailing: Boolean, test: IntPredicate): String {
            var begin = 0
            var end = s.length
            if (leading) {
                var cp = 0
                while (begin < end && test.test(s.codePointAt(begin).also { cp = it })) {
                    begin += Character.charCount(cp)
                }
            }
            if (trailing) {
                var cp = 0
                while (end > begin && test.test(s.codePointBefore(end).also { cp = it })) {
                    end -= Character.charCount(cp)
                }
            }
            // substring() returns self if start == 0 && end == length()
            return s.substring(begin, end)
        }

        /**
         * Trim a character from a string.
         *
         * @param s the string
         * @param leading if leading characters should be removed
         * @param trailing if trailing characters should be removed
         * @param character what to remove
         * @return the trimmed string
         */
        @JvmStatic
        fun trim(s: String, leading: Boolean, trailing: Boolean, character: Char): String {
            var begin = 0
            var end = s.length
            if (leading) {
                while (begin < end && s[begin] == character) {
                    begin++
                }
            }
            if (trailing) {
                while (end > begin && s[end - 1] == character) {
                    end--
                }
            }
            // substring() returns self if start == 0 && end == length()
            return s.substring(begin, end)
        }

        /**
         * Trim a whitespace from a substring. Equivalent of
         * `substring(beginIndex).trim()`.
         *
         * @param s the string
         * @param beginIndex start index of substring (inclusive)
         * @return trimmed substring
         */
        @JvmStatic
        fun trimSubstring(s: String, beginIndex: Int): String {
            return trimSubstring(s, beginIndex, s.length)
        }

        /**
         * Trim a whitespace from a substring. Equivalent of
         * `substring(beginIndex, endIndex).trim()`.
         *
         * @param s the string
         * @param beginIndex start index of substring (inclusive)
         * @param endIndex end index of substring (exclusive)
         * @return trimmed substring
         */
        @JvmStatic
        fun trimSubstring(s: String, beginIndex: Int, endIndex: Int): String {
            var beginIndex = beginIndex
            var endIndex = endIndex
            while (beginIndex < endIndex && s[beginIndex] <= ' ') {
                beginIndex++
            }
            while (beginIndex < endIndex && s[endIndex - 1] <= ' ') {
                endIndex--
            }
            return s.substring(beginIndex, endIndex)
        }

        /**
         * Trim a whitespace from a substring and append it to a specified string
         * builder. Equivalent of
         * `builder.append(substring(beginIndex, endIndex).trim())`.
         *
         * @param builder string builder to append to
         * @param s the string
         * @param beginIndex start index of substring (inclusive)
         * @param endIndex end index of substring (exclusive)
         * @return the specified builder
         */
        @JvmStatic
        fun trimSubstring(builder: StringBuilder, s: String, beginIndex: Int, endIndex: Int): StringBuilder {
            var beginIndex = beginIndex
            var endIndex = endIndex
            while (beginIndex < endIndex && s[beginIndex] <= ' ') {
                beginIndex++
            }
            while (beginIndex < endIndex && s[endIndex - 1] <= ' ') {
                endIndex--
            }
            return builder.append(s, beginIndex, endIndex)
        }

        /**
         * Truncates the specified string to the specified length. This method,
         * unlike [String.substring], doesn't break Unicode code
         * points. If the specified length in characters breaks a valid pair of
         * surrogates, the whole pair is not included into result.
         *
         * @param s
         *            the string to truncate
         * @param maximumLength
         *            the maximum length in characters
         * @return the specified string if it isn't longer than the specified
         *         maximum length, and the truncated string otherwise
         */
        @JvmStatic
        fun truncateString(s: String, maximumLength: Int): String {
            var s = s
            if (s.length > maximumLength) {
                s = if (maximumLength > 0) s.substring(
                    0,
                    if (Character.isSurrogatePair(s[maximumLength - 1], s[maximumLength])) maximumLength - 1
                    else maximumLength
                )
                else ""
            }
            return s
        }

        /**
         * Get the string from the cache if possible. If the string has not been
         * found, it is added to the cache. If there is such a string in the cache,
         * that one is returned.
         *
         * @param s the original string
         * @return a string with the same content, if possible from the cache
         */
        @JvmStatic
        fun cache(s: String?): String? {
            if (!SysProperties.OBJECT_CACHE) {
                return s
            }
            if (s == null) {
                return s
            } else if (s.isEmpty()) {
                return ""
            }
            val cache = getCache()
            if (cache != null) {
                val hash = s.hashCode()
                val index = hash and (SysProperties.OBJECT_CACHE_SIZE - 1)
                val cached = cache[index]
                if (s == cached) {
                    return cached
                }
                cache[index] = s
            }
            return s
        }

        /**
         * Clear the cache. This method is used for testing.
         */
        @JvmStatic
        fun clearCache() {
            softCache = null
        }

        /**
         * Parses an unsigned 31-bit integer. Neither - nor + signs are allowed.
         *
         * @param s string to parse
         * @param start the beginning index, inclusive
         * @param end the ending index, exclusive
         * @return the unsigned `int` not greater than [Integer.MAX_VALUE].
         */
        @JvmStatic
        fun parseUInt31(s: String, start: Int, end: Int): Int {
            if (end > s.length || start < 0 || start > end) {
                throw IndexOutOfBoundsException()
            }
            if (start == end) {
                throw NumberFormatException("")
            }
            var result = 0
            for (i in start until end) {
                val ch = s[i]
                // Ensure that character is valid and that multiplication by 10 will
                // be performed without overflow
                if (ch < '0' || ch > '9' || result > 214_748_364) {
                    throw NumberFormatException(s.substring(start, end))
                }
                result = result * 10 + ch.code - '0'.code
                if (result < 0) {
                    // Overflow
                    throw NumberFormatException(s.substring(start, end))
                }
            }
            return result
        }

        /**
         * Convert a hex encoded string to a byte array.
         *
         * @param s the hex encoded string
         * @return the byte array
         */
        @JvmStatic
        fun convertHexToBytes(s: String): ByteArray {
            var len = s.length
            if (len % 2 != 0) {
                throw DbException.get(ErrorCode.HEX_STRING_ODD_1, s)
            }
            len /= 2
            val buff = ByteArray(len)
            var mask = 0
            val hex = HEX_DECODE
            try {
                for (i in 0 until len) {
                    val d = hex[s[i + i].code] shl 4 or hex[s[i + i + 1].code]
                    mask = mask or d
                    buff[i] = d.toByte()
                }
            } catch (e: ArrayIndexOutOfBoundsException) {
                throw DbException.get(ErrorCode.HEX_STRING_WRONG_1, s)
            }
            if ((mask and 255.inv()) != 0) {
                throw DbException.get(ErrorCode.HEX_STRING_WRONG_1, s)
            }
            return buff
        }

        /**
         * Parses a hex encoded string with possible space separators and appends
         * the decoded binary string to the specified output stream.
         *
         * @param baos the output stream, or `null`
         * @param s the hex encoded string
         * @param start the start index
         * @param end the end index, exclusive
         * @return the specified output stream or a new output stream
         */
        @JvmStatic
        fun convertHexWithSpacesToBytes(
            baos: ByteArrayOutputStream?, s: String, start: Int,
            end: Int
        ): ByteArrayOutputStream {
            var baos = baos
            if (baos == null) {
                baos = ByteArrayOutputStream((end - start) ushr 1)
            }
            var mask = 0
            val hex = HEX_DECODE
            try {
                var i = start
                loop@ while (true) {
                    var c1: Char
                    var c2: Char
                    do {
                        if (i >= end) {
                            break@loop
                        }
                        c1 = s[i++]
                    } while (c1 == ' ')
                    do {
                        if (i >= end) {
                            if (((mask or hex[c1.code]) and 255.inv()) != 0) {
                                throw getHexStringException(ErrorCode.HEX_STRING_WRONG_1, s, start, end)
                            }
                            throw getHexStringException(ErrorCode.HEX_STRING_ODD_1, s, start, end)
                        }
                        c2 = s[i++]
                    } while (c2 == ' ')
                    val d = hex[c1.code] shl 4 or hex[c2.code]
                    mask = mask or d
                    baos.write(d)
                }
            } catch (e: ArrayIndexOutOfBoundsException) {
                throw getHexStringException(ErrorCode.HEX_STRING_WRONG_1, s, start, end)
            }
            if ((mask and 255.inv()) != 0) {
                throw getHexStringException(ErrorCode.HEX_STRING_WRONG_1, s, start, end)
            }
            return baos
        }

        private fun getHexStringException(code: Int, s: String, start: Int, end: Int): DbException {
            return DbException.get(code, s.substring(start, end))
        }

        /**
         * Convert a byte array to a hex encoded string.
         *
         * @param value the byte array
         * @return the hex encoded string
         */
        @JvmStatic
        fun convertBytesToHex(value: ByteArray): String {
            return convertBytesToHex(value, value.size)
        }

        /**
         * Convert a byte array to a hex encoded string.
         *
         * @param value the byte array
         * @param len the number of bytes to encode
         * @return the hex encoded string
         */
        @JvmStatic
        fun convertBytesToHex(value: ByteArray, len: Int): String {
            val bytes = ByteArray(len * 2)
            val hex = HEX
            var i = 0
            var j = 0
            while (i < len) {
                val c = value[i].toInt() and 0xff
                bytes[j++] = hex[c shr 4].code.toByte()
                bytes[j++] = hex[c and 0xf].code.toByte()
                i++
            }
            return String(bytes, StandardCharsets.ISO_8859_1)
        }

        /**
         * Convert a byte array to a hex encoded string and appends it to a specified string builder.
         *
         * @param builder string builder to append to
         * @param value the byte array
         * @return the hex encoded string
         */
        @JvmStatic
        fun convertBytesToHex(builder: StringBuilder, value: ByteArray): StringBuilder {
            return convertBytesToHex(builder, value, value.size)
        }

        /**
         * Convert a byte array to a hex encoded string and appends it to a specified string builder.
         *
         * @param builder string builder to append to
         * @param value the byte array
         * @param len the number of bytes to encode
         * @return the hex encoded string
         */
        @JvmStatic
        fun convertBytesToHex(builder: StringBuilder, value: ByteArray, len: Int): StringBuilder {
            val hex = HEX
            for (i in 0 until len) {
                val c = value[i].toInt() and 0xff
                builder.append(hex[c ushr 4]).append(hex[c and 0xf])
            }
            return builder
        }

        /**
         * Appends specified number of trailing bytes from unsigned long value to a
         * specified string builder.
         *
         * @param builder
         *            string builder to append to
         * @param x
         *            value to append
         * @param bytes
         *            number of bytes to append
         * @return the specified string builder
         */
        @JvmStatic
        fun appendHex(builder: StringBuilder, x: Long, bytes: Int): StringBuilder {
            val hex = HEX
            var i = bytes * 8
            while (i > 0) {
                i -= 4
                builder.append(hex[(x shr i).toInt() and 0xf])
                i -= 4
                builder.append(hex[(x shr i).toInt() and 0xf])
            }
            return builder
        }

        /**
         * Check if this string is a decimal number.
         *
         * @param s the string
         * @return true if it is
         */
        @JvmStatic
        fun isNumber(s: String): Boolean {
            val l = s.length
            if (l == 0) {
                return false
            }
            for (i in 0 until l) {
                if (!Character.isDigit(s[i])) {
                    return false
                }
            }
            return true
        }

        /**
         * Check if the specified string is empty or contains only whitespace.
         *
         * @param s
         *            the string
         * @return whether the specified string is empty or contains only whitespace
         */
        @JvmStatic
        fun isWhitespaceOrEmpty(s: String): Boolean {
            var i = 0
            val l = s.length
            while (i < l) {
                if (s[i] > ' ') {
                    return false
                }
                i++
            }
            return true
        }

        /**
         * Append a zero-padded number from 00 to 99 to a string builder.
         *
         * @param builder the string builder
         * @param positiveValue the number to append
         * @return the specified string builder
         */
        @JvmStatic
        fun appendTwoDigits(builder: StringBuilder, positiveValue: Int): StringBuilder {
            if (positiveValue < 10) {
                builder.append('0')
            }
            return builder.append(positiveValue)
        }

        /**
         * Append a zero-padded number to a string builder.
         *
         * @param builder the string builder
         * @param length the number of characters to append
         * @param positiveValue the number to append
         * @return the specified string builder
         */
        @JvmStatic
        fun appendZeroPadded(builder: StringBuilder, length: Int, positiveValue: Int): StringBuilder {
            var length = length
            val s = Integer.toString(positiveValue)
            length -= s.length
            while (length > 0) {
                builder.append('0')
                length--
            }
            return builder.append(s)
        }

        /**
         * Appends the specified string or its part to the specified builder with
         * maximum builder length limit.
         *
         * @param builder the string builder
         * @param s the string to append
         * @param length the length limit
         * @return the specified string builder
         */
        @JvmStatic
        fun appendToLength(builder: StringBuilder, s: String, length: Int): StringBuilder {
            val builderLength = builder.length
            if (builderLength < length) {
                val need = length - builderLength
                if (need >= s.length) {
                    builder.append(s)
                } else {
                    builder.append(s, 0, need)
                }
            }
            return builder
        }

        /**
         * Escape table or schema patterns used for DatabaseMetaData functions.
         *
         * @param pattern the pattern
         * @return the escaped pattern
         */
        @JvmStatic
        fun escapeMetaDataPattern(pattern: String?): String? {
            if (pattern == null || pattern.isEmpty()) {
                return pattern
            }
            return replaceAll(pattern, "\\", "\\\\")
        }

        /**
         * Case-sensitive check if a {@param text} starts with a {@param prefix}.
         * It only calls `String.startsWith()` and is only here for API consistency
         *
         * @param text the full text starting with a prefix
         * @param prefix the full text starting with a prefix
         * @return TRUE only if text starts with the prefix
         */
        @JvmStatic
        fun startsWith(text: String, prefix: String): Boolean {
            return text.startsWith(prefix)
        }

        /**
         * Case-Insensitive check if a {@param text} starts with a {@param prefix}.
         *
         * @param text the full text starting with a prefix
         * @param prefix the full text starting with a prefix
         * @return TRUE only if text starts with the prefix
         */
        @JvmStatic
        fun startsWithIgnoringCase(text: String, prefix: String): Boolean {
            if (text.length < prefix.length) {
                return false
            } else {
                val collator = Collator.getInstance()
                collator.strength = Collator.PRIMARY
                return collator.equals(text.substring(0, prefix.length), prefix)
            }
        }
    }
}
