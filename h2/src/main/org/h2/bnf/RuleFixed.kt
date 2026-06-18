/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.util.HashMap

/**
 * Represents a hard coded terminal rule in a BNF object.
 */
class RuleFixed(private val type: Int) : Rule {

    override fun accept(visitor: BnfVisitor) {
        visitor.visitRuleFixed(type)
    }

    override fun setLinks(ruleMap: HashMap<String, RuleHead>) {
        // nothing to do
    }

    override fun autoComplete(sentence: Sentence): Boolean {
        sentence.stopIfRequired()
        val query = sentence.getQuery()!!
        var s = query
        var removeTrailingSpaces = false
        when (type) {
            YMD -> {
                while (s.length > 0 && "0123456789-".indexOf(s[0]) >= 0) {
                    s = s.substring(1)
                }
                if (s.length == 0) {
                    sentence.add("2006-01-01", "1", Sentence.KEYWORD)
                }
                // needed for timestamps
                removeTrailingSpaces = true
            }
            HMS -> {
                while (s.length > 0 && "0123456789:".indexOf(s[0]) >= 0) {
                    s = s.substring(1)
                }
                if (s.length == 0) {
                    sentence.add("12:00:00", "1", Sentence.KEYWORD)
                }
            }
            NANOS -> {
                while (s.length > 0 && Character.isDigit(s[0])) {
                    s = s.substring(1)
                }
                if (s.length == 0) {
                    sentence.add("nanoseconds", "0", Sentence.KEYWORD)
                }
                removeTrailingSpaces = true
            }
            ANY_EXCEPT_SINGLE_QUOTE -> {
                while (true) {
                    while (s.length > 0 && s[0] != '\'') {
                        s = s.substring(1)
                    }
                    if (s.startsWith("''")) {
                        s = s.substring(2)
                    } else {
                        break
                    }
                }
                if (s.length == 0) {
                    sentence.add("anything", "Hello World", Sentence.KEYWORD)
                    sentence.add("'", "'", Sentence.KEYWORD)
                }
            }
            ANY_EXCEPT_2_DOLLAR -> {
                while (s.length > 0 && !s.startsWith("$$")) {
                    s = s.substring(1)
                }
                if (s.length == 0) {
                    sentence.add("anything", "Hello World", Sentence.KEYWORD)
                    sentence.add("$$", "$$", Sentence.KEYWORD)
                }
            }
            ANY_EXCEPT_DOUBLE_QUOTE -> {
                while (true) {
                    while (s.length > 0 && s[0] != '\"') {
                        s = s.substring(1)
                    }
                    if (s.startsWith("\"\"")) {
                        s = s.substring(2)
                    } else {
                        break
                    }
                }
                if (s.length == 0) {
                    sentence.add("anything", "identifier", Sentence.KEYWORD)
                    sentence.add("\"", "\"", Sentence.KEYWORD)
                }
            }
            ANY_WORD, JSON_TEXT -> {
                while (s.length > 0 && !Bnf.startWithSpace(s)) {
                    s = s.substring(1)
                }
                if (s.length == 0) {
                    sentence.add("anything", "anything", Sentence.KEYWORD)
                }
            }
            HEX_START -> {
                if (s.startsWith("0X") || s.startsWith("0x")) {
                    s = s.substring(2)
                } else if ("0" == s) {
                    sentence.add("0x", "x", Sentence.KEYWORD)
                } else if (s.length == 0) {
                    sentence.add("0x", "0x", Sentence.KEYWORD)
                }
            }
            OCTAL_START -> {
                if (s.startsWith("0O") || s.startsWith("0o")) {
                    s = s.substring(2)
                } else if ("0" == s) {
                    sentence.add("0o", "o", Sentence.KEYWORD)
                } else if (s.length == 0) {
                    sentence.add("0o", "0o", Sentence.KEYWORD)
                }
            }
            BINARY_START -> {
                if (s.startsWith("0B") || s.startsWith("0b")) {
                    s = s.substring(2)
                } else if ("0" == s) {
                    sentence.add("0b", "b", Sentence.KEYWORD)
                } else if (s.length == 0) {
                    sentence.add("0b", "0b", Sentence.KEYWORD)
                }
            }
            CONCAT -> {
                if (s == "|") {
                    sentence.add("||", "|", Sentence.KEYWORD)
                } else if (s.startsWith("||")) {
                    s = s.substring(2)
                } else if (s.length == 0) {
                    sentence.add("||", "||", Sentence.KEYWORD)
                }
                removeTrailingSpaces = true
            }
            AZ_UNDERSCORE -> {
                if (s.length > 0 &&
                        (Character.isLetter(s[0]) || s[0] == '_')) {
                    s = s.substring(1)
                }
                if (s.length == 0) {
                    sentence.add("character", "A", Sentence.KEYWORD)
                }
            }
            AF -> {
                if (s.length > 0) {
                    val ch = Character.toUpperCase(s[0])
                    if (ch >= 'A' && ch <= 'F') {
                        s = s.substring(1)
                    }
                }
                if (s.length == 0) {
                    sentence.add("hex character", "0A", Sentence.KEYWORD)
                }
            }
            DIGIT -> {
                if (s.length > 0 && Character.isDigit(s[0])) {
                    s = s.substring(1)
                }
                if (s.length == 0) {
                    sentence.add("digit", "1", Sentence.KEYWORD)
                }
            }
            OPEN_BRACKET -> {
                if (s.length == 0) {
                    sentence.add("[", "[", Sentence.KEYWORD)
                } else if (s[0] == '[') {
                    s = s.substring(1)
                }
                removeTrailingSpaces = true
            }
            CLOSE_BRACKET -> {
                if (s.length == 0) {
                    sentence.add("]", "]", Sentence.KEYWORD)
                } else if (s[0] == ']') {
                    s = s.substring(1)
                }
                removeTrailingSpaces = true
            }
            // no autocomplete support for comments
            // (comments are not reachable in the bnf tree)
            ANY_UNTIL_EOL, ANY_UNTIL_END -> throw AssertionError("type=$type")
            else -> throw AssertionError("type=$type")
        }
        if (s != query) {
            // can not always remove spaces here, because a repeat
            // rule for a-z would remove multiple words
            // but we have to remove spaces after '||'
            // and after ']'
            if (removeTrailingSpaces) {
                while (Bnf.startWithSpace(s)) {
                    s = s.substring(1)
                }
            }
            sentence.setQuery(s)
            return true
        }
        return false
    }

    override fun toString(): String {
        return "#" + type
    }

    companion object {
        const val YMD = 0
        const val HMS = YMD + 1
        const val NANOS = HMS + 1
        const val ANY_EXCEPT_SINGLE_QUOTE = NANOS + 1
        const val ANY_EXCEPT_DOUBLE_QUOTE = ANY_EXCEPT_SINGLE_QUOTE + 1
        const val ANY_UNTIL_EOL = ANY_EXCEPT_DOUBLE_QUOTE + 1
        const val ANY_UNTIL_END = ANY_UNTIL_EOL + 1
        const val ANY_WORD = ANY_UNTIL_END + 1
        const val ANY_EXCEPT_2_DOLLAR = ANY_WORD + 1
        const val HEX_START = ANY_EXCEPT_2_DOLLAR + 1
        const val OCTAL_START = HEX_START + 1
        const val BINARY_START = OCTAL_START + 1
        const val CONCAT = BINARY_START + 1
        const val AZ_UNDERSCORE = CONCAT + 1
        const val AF = AZ_UNDERSCORE + 1
        const val DIGIT = AF + 1
        const val OPEN_BRACKET = DIGIT + 1
        const val CLOSE_BRACKET = OPEN_BRACKET + 1
        const val JSON_TEXT = CLOSE_BRACKET + 1
    }

}
