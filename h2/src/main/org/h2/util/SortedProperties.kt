/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.LineNumberReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.Enumeration
import java.util.Properties
import java.util.TreeMap
import org.h2.store.fs.FileUtils

/**
 * Sorted properties file.
 * This implementation requires that store() internally calls keys().
 */
class SortedProperties : Properties() {

    @Synchronized
    override fun keys(): Enumeration<Any> {
        val v = ArrayList<Any>()
        for (o in keys) {
            v.add(o.toString())
        }
        v.sortWith(compareBy { it.toString() })
        return Collections.enumeration(v)
    }

    /**
     * Store a properties file. The header and the date are not written.
     *
     * @param fileName the target file name
     * @throws IOException on failure
     */
    @Synchronized
    @Throws(IOException::class)
    fun store(fileName: String) {
        val out = ByteArrayOutputStream()
        store(out, null)
        val `in` = ByteArrayInputStream(out.toByteArray())
        val reader = InputStreamReader(`in`, StandardCharsets.ISO_8859_1)
        val r = LineNumberReader(reader)
        val w: Writer
        try {
            w = OutputStreamWriter(FileUtils.newOutputStream(fileName, false), StandardCharsets.ISO_8859_1)
        } catch (e: Exception) {
            throw IOException(e.toString(), e)
        }
        PrintWriter(BufferedWriter(w)).use { writer ->
            while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith("#")) {
                    writer.print(line + "\n")
                }
            }
        }
    }

    /**
     * Convert the map to a list of line in the form key=value.
     *
     * @return the lines
     */
    @Synchronized
    fun toLines(): String {
        val buff = StringBuilder()
        for (e in TreeMap<Any, Any>(this).entries) {
            buff.append(e.key).append('=').append(e.value).append('\n')
        }
        return buff.toString()
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Get a boolean property value from a properties object.
         *
         * @param prop the properties object
         * @param key the key
         * @param def the default value
         * @return the value if set, or the default value if not
         */
        @JvmStatic
        fun getBooleanProperty(prop: Properties, key: String, def: Boolean): Boolean {
            return try {
                Utils.parseBoolean(prop.getProperty(key, null), def, true)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                def
            }
        }

        /**
         * Get an int property value from a properties object.
         *
         * @param prop the properties object
         * @param key the key
         * @param def the default value
         * @return the value if set, or the default value if not
         */
        @JvmStatic
        fun getIntProperty(prop: Properties, key: String, def: Int): Int {
            val value = prop.getProperty(key, Integer.toString(def))
            return try {
                Integer.decode(value)
            } catch (e: Exception) {
                e.printStackTrace()
                def
            }
        }

        /**
         * Get a string property value from a properties object.
         *
         * @param prop the properties object
         * @param key the key
         * @param def the default value
         * @return the value if set, or the default value if not
         */
        @JvmStatic
        fun getStringProperty(prop: Properties, key: String, def: String?): String? {
            return prop.getProperty(key, def)
        }

        /**
         * Load a properties object from a file.
         *
         * @param fileName the name of the properties file
         * @return the properties object
         * @throws IOException on failure
         */
        @JvmStatic
        @Synchronized
        @Throws(IOException::class)
        fun loadProperties(fileName: String): SortedProperties {
            val prop = SortedProperties()
            if (FileUtils.exists(fileName)) {
                FileUtils.newInputStream(fileName).use { `in` ->
                    prop.load(InputStreamReader(`in`, StandardCharsets.ISO_8859_1))
                }
            }
            return prop
        }

        /**
         * Convert a String to a map.
         *
         * @param s the string
         * @return the map
         */
        @JvmStatic
        fun fromLines(s: String): SortedProperties {
            val p = SortedProperties()
            for (line in StringUtils.arraySplit(s, '\n', true)!!) {
                val idx = line.indexOf('=')
                if (idx > 0) {
                    p[line.substring(0, idx)] = line.substring(idx + 1)
                }
            }
            return p
        }
    }
}
