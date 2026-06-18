/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value

import java.nio.charset.Charset
import java.text.Collator
import java.util.Locale
import java.util.Objects

import org.h2.util.StringUtils

/**
 * Instances of this class can compare strings. Case sensitive and case
 * insensitive comparison is supported, and comparison using a collator.
 */
open class CompareMode protected constructor(private val name: String?, private val strength: Int) {

    /**
     * Compare two characters in a string.
     *
     * @param a the first string
     * @param ai the character index in the first string
     * @param b the second string
     * @param bi the character index in the second string
     * @param ignoreCase true if a case-insensitive comparison should be made
     * @return true if the characters are equals
     */
    open fun equalsChars(a: String, ai: Int, b: String, bi: Int, ignoreCase: Boolean): Boolean {
        val ca = a[ai]
        val cb = b[bi]
        if (ca == cb) {
            return true
        }
        if (ignoreCase) {
            if (Character.toUpperCase(ca) == Character.toUpperCase(cb)
                || Character.toLowerCase(ca) == Character.toLowerCase(cb)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Compare two strings.
     *
     * @param a the first string
     * @param b the second string
     * @param ignoreCase true if a case-insensitive comparison should be made
     * @return -1 if the first string is 'smaller', 1 if the second string is
     *         smaller, and 0 if they are equal
     */
    open fun compareString(a: String, b: String, ignoreCase: Boolean): Int {
        if (ignoreCase) {
            return a.compareTo(b, ignoreCase = true)
        }
        return a.compareTo(b)
    }

    fun getName(): String {
        return name ?: OFF
    }

    fun getStrength(): Int {
        return strength
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        } else if (obj !is CompareMode) {
            return false
        }
        val o = obj
        if (getName() != o.getName()) {
            return false
        }
        if (strength != o.strength) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + getName().hashCode()
        result = 31 * result + strength
        return result
    }

    companion object {

        /**
         * This constant means there is no collator set, and the default string
         * comparison is to be used.
         */
        const val OFF = "OFF"

        /**
         * This constant means the default collator should be used, even if ICU4J is
         * in the classpath.
         */
        const val DEFAULT = "DEFAULT_"

        /**
         * This constant means ICU4J should be used (this will fail if it is not in
         * the classpath).
         */
        const val ICU4J = "ICU4J_"

        /**
         * This constant means the charset specified should be used.
         * This will fail if the specified charset does not exist.
         */
        const val CHARSET = "CHARSET_"

        private var LOCALES: Array<Locale>? = null

        @Volatile
        private var lastUsed: CompareMode? = null

        private val CAN_USE_ICU4J: Boolean

        init {
            var b = false
            try {
                Class.forName("com.ibm.icu.text.Collator")
                b = true
            } catch (e: Exception) {
                // ignore
            }
            CAN_USE_ICU4J = b
        }

        /**
         * Create a new compare mode with the given collator and strength. If
         * required, a new CompareMode is created, or if possible the last one is
         * returned. A cache is used to speed up comparison when using a collator;
         * CollationKey objects are cached.
         *
         * @param name the collation name or null
         * @param strength the collation strength
         * @return the compare mode
         */
        @JvmStatic
        fun getInstance(name: String?, strength: Int): CompareMode {
            var name = name
            var last = lastUsed
            if (last != null && Objects.equals(last.name, name) && last.strength == strength) {
                return last
            }
            if (name == null || name == OFF) {
                last = CompareMode(name, strength)
            } else {
                val useICU4J: Boolean
                if (name.startsWith(ICU4J)) {
                    useICU4J = true
                    name = name.substring(ICU4J.length)
                } else if (name.startsWith(DEFAULT)) {
                    useICU4J = false
                    name = name.substring(DEFAULT.length)
                } else if (name.startsWith(CHARSET)) {
                    useICU4J = false
                } else {
                    useICU4J = CAN_USE_ICU4J
                }
                last = if (useICU4J) {
                    CompareModeIcu4J(name, strength)
                } else {
                    CompareModeDefault(name, strength)
                }
            }
            lastUsed = last
            return last
        }

        /**
         * Returns available locales for collations.
         *
         * @param onlyIfInitialized
         *            if {@code true}, returns {@code null} when locales are not yet
         *            initialized
         * @return available locales for collations.
         */
        @JvmStatic
        fun getCollationLocales(onlyIfInitialized: Boolean): Array<Locale>? {
            var locales = LOCALES
            if (locales == null && !onlyIfInitialized) {
                locales = Collator.getAvailableLocales()
                LOCALES = locales
            }
            return locales
        }

        /**
         * Get the collation name.
         *
         * @param l the locale
         * @return the name of the collation
         */
        @JvmStatic
        fun getName(l: Locale): String {
            val english = Locale.ENGLISH
            var name = l.getDisplayLanguage(english) + ' ' +
                l.getDisplayCountry(english) + ' ' + l.variant
            name = StringUtils.toUpperEnglish(name.trim().replace(' ', '_'))
            return name
        }

        /**
         * Compare name of the locale with the given name. The case of the name
         * is ignored.
         *
         * @param locale the locale
         * @param name the name
         * @return true if they match
         */
        @JvmStatic
        internal fun compareLocaleNames(locale: Locale, name: String): Boolean {
            return name.equals(locale.toString(), ignoreCase = true) ||
                name.equals(locale.toLanguageTag(), ignoreCase = true) ||
                name.equals(getName(locale), ignoreCase = true)
        }

        /**
         * Get the collator object for the given language name or language / country
         * combination.
         *
         * @param name the language name
         * @return the collator
         */
        @JvmStatic
        fun getCollator(name: String): Collator? {
            var name = name
            var result: Collator? = null
            if (name.startsWith(ICU4J)) {
                name = name.substring(ICU4J.length)
            } else if (name.startsWith(DEFAULT)) {
                name = name.substring(DEFAULT.length)
            } else if (name.startsWith(CHARSET)) {
                return CharsetCollator(Charset.forName(name.substring(CHARSET.length)))
            }
            val length = name.length
            if (length == 2) {
                val locale = Locale(StringUtils.toLowerEnglish(name), "")
                if (compareLocaleNames(locale, name)) {
                    result = Collator.getInstance(locale)
                }
            } else if (length == 5) {
                // LL_CC (language_country)
                val idx = name.indexOf('_')
                if (idx >= 0) {
                    val language = StringUtils.toLowerEnglish(name.substring(0, idx))
                    val country = name.substring(idx + 1)
                    val locale = Locale(language, country)
                    if (compareLocaleNames(locale, name)) {
                        result = Collator.getInstance(locale)
                    }
                }
            } else if (name.indexOf('-') > 0) {
                val locale = Locale.forLanguageTag(name)
                if (!locale.language.isEmpty()) {
                    return Collator.getInstance(locale)
                }
            }
            if (result == null) {
                for (locale in getCollationLocales(false)!!) {
                    if (compareLocaleNames(locale, name)) {
                        result = Collator.getInstance(locale)
                        break
                    }
                }
            }
            return result
        }
    }
}
