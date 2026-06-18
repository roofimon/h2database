/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.sql.SQLException
import java.util.StringTokenizer
import org.h2.bnf.context.DbContextRule
import org.h2.command.dml.Help
import org.h2.tools.Csv
import org.h2.util.StringUtils.Companion.cache
import org.h2.util.StringUtils.Companion.replaceAll
import org.h2.util.StringUtils.Companion.toLowerEnglish
import org.h2.util.Utils

/**
 * This class can read a file that is similar to BNF (Backus-Naur form).
 * It is made specially to support SQL grammar.
 */
class Bnf {

    /**
     * The rule map. The key is lowercase, and all spaces
     * are replaces with underscore.
     */
    private val ruleMap = HashMap<String, RuleHead>()
    private var syntax: String? = null
    private var currentToken: String? = null
    private var tokens: Array<String> = arrayOf()
    private var firstChar = '\u0000'
    private var index = 0
    private var lastRepeat: Rule? = null
    private var statements: ArrayList<RuleHead>? = null
    private var currentTopic: String? = null

    /**
     * Add an alias for a rule.
     *
     * @param name for example "procedure"
     * @param replacement for example "@func@"
     */
    fun addAlias(name: String, replacement: String) {
        val head = ruleMap[replacement]!!
        ruleMap[name] = head
    }

    private fun addFixedRule(name: String, fixedType: Int) {
        val rule: Rule = RuleFixed(fixedType)
        addRule(name, "Fixed", rule)
    }

    private fun addRule(topic: String, section: String, rule: Rule?): RuleHead {
        val head = RuleHead(section, topic, rule!!)
        val key = toLowerEnglish(topic.trim().replace(' ', '_'))
        if (ruleMap.putIfAbsent(key, head) != null) {
            throw AssertionError("already exists: $topic")
        }
        return head
    }

    @Throws(SQLException::class, IOException::class)
    private fun parse(reader: Reader) {
        var functions: Rule? = null
        statements = ArrayList()
        val csv = Csv()
        csv.setLineCommentCharacter('#')
        val rs = csv.read(reader, null)
        while (rs.next()) {
            val section = rs.getString("SECTION").trim()
            if (section.startsWith("System")) {
                continue
            }
            val topic = rs.getString("TOPIC")
            syntax = Help.stripAnnotationsFromSyntax(rs.getString("SYNTAX"))
            currentTopic = section
            tokens = tokenize()
            index = 0
            var rule = parseRule()
            if (section.startsWith("Command")) {
                rule = RuleList(rule, RuleElement(";\n\n", currentTopic!!), false)
            }
            val head = addRule(topic, section, rule)
            if (section.startsWith("Function")) {
                functions = if (functions == null) {
                    rule
                } else {
                    RuleList(rule, functions, true)
                }
            } else if (section.startsWith("Commands")) {
                statements!!.add(head)
            }
        }
        addRule("@func@", "Function", functions)
        addFixedRule("@ymd@", RuleFixed.YMD)
        addFixedRule("@hms@", RuleFixed.HMS)
        addFixedRule("@nanos@", RuleFixed.NANOS)
        addFixedRule("anything_except_single_quote", RuleFixed.ANY_EXCEPT_SINGLE_QUOTE)
        addFixedRule("single_character", RuleFixed.ANY_EXCEPT_SINGLE_QUOTE)
        addFixedRule("anything_except_double_quote", RuleFixed.ANY_EXCEPT_DOUBLE_QUOTE)
        addFixedRule("anything_until_end_of_line", RuleFixed.ANY_UNTIL_EOL)
        addFixedRule("anything_until_comment_start_or_end", RuleFixed.ANY_UNTIL_END)
        addFixedRule("anything_except_two_dollar_signs", RuleFixed.ANY_EXCEPT_2_DOLLAR)
        addFixedRule("anything", RuleFixed.ANY_WORD)
        addFixedRule("@hex_start@", RuleFixed.HEX_START)
        addFixedRule("@octal_start@", RuleFixed.OCTAL_START)
        addFixedRule("@binary_start@", RuleFixed.BINARY_START)
        addFixedRule("@concat@", RuleFixed.CONCAT)
        addFixedRule("@az_@", RuleFixed.AZ_UNDERSCORE)
        addFixedRule("@af@", RuleFixed.AF)
        addFixedRule("@digit@", RuleFixed.DIGIT)
        addFixedRule("@open_bracket@", RuleFixed.OPEN_BRACKET)
        addFixedRule("@close_bracket@", RuleFixed.CLOSE_BRACKET)
        addFixedRule("json_text", RuleFixed.JSON_TEXT)
        val digit = ruleMap["digit"]!!.getRule()
        ruleMap["number"]!!.setRule(RuleList(digit, RuleOptional(RuleRepeat(digit, false)), false))
    }

    /**
     * Parse the syntax and let the rule call the visitor.
     *
     * @param visitor the visitor
     * @param s the syntax to parse
     */
    fun visit(visitor: BnfVisitor, s: String) {
        this.syntax = s
        tokens = tokenize()
        index = 0
        val rule = parseRule()
        rule.setLinks(ruleMap)
        rule.accept(visitor)
    }

    /**
     * Get the rule head for the given title.
     *
     * @param title the title
     * @return the rule head, or null
     */
    fun getRuleHead(title: String): RuleHead? {
        return ruleMap[title]
    }

    private fun parseRule(): Rule {
        read()
        return parseOr()
    }

    private fun parseOr(): Rule {
        var r = parseList()
        if (firstChar == '|') {
            read()
            r = RuleList(r, parseOr(), true)
        }
        lastRepeat = r
        return r
    }

    private fun parseList(): Rule {
        var r = parseToken()
        if (firstChar != '|' && firstChar != ']' && firstChar != '}'
                && firstChar != '\u0000') {
            r = RuleList(r, parseList(), false)
        }
        lastRepeat = r
        return r
    }

    private fun parseExtension(compatibility: Boolean): RuleExtension {
        read()
        var r: Rule
        if (firstChar == '[') {
            read()
            r = parseOr()
            r = RuleOptional(r)
            if (firstChar != ']') {
                throw AssertionError("expected ], got $currentToken syntax:$syntax")
            }
        } else if (firstChar == '{') {
            read()
            r = parseOr()
            if (firstChar != '}') {
                throw AssertionError("expected }, got $currentToken syntax:$syntax")
            }
        } else {
            r = parseOr()
        }
        return RuleExtension(r, compatibility)
    }

    private fun parseToken(): Rule {
        var r: Rule
        if ((firstChar >= 'A' && firstChar <= 'Z')
                || (firstChar >= 'a' && firstChar <= 'z')) {
            // r = new RuleElement(currentToken+ " syntax:" + syntax);
            r = RuleElement(currentToken!!, currentTopic!!)
        } else if (firstChar == '[') {
            read()
            r = parseOr()
            r = RuleOptional(r)
            if (firstChar != ']') {
                throw AssertionError("expected ], got $currentToken syntax:$syntax")
            }
        } else if (firstChar == '{') {
            read()
            r = parseOr()
            if (firstChar != '}') {
                throw AssertionError("expected }, got $currentToken syntax:$syntax")
            }
        } else if (firstChar == '@') {
            if ("@commaDots@" == currentToken) {
                r = RuleList(RuleElement(",", currentTopic!!), lastRepeat!!, false)
                r = RuleRepeat(r, true)
            } else if ("@dots@" == currentToken) {
                r = RuleRepeat(lastRepeat!!, false)
            } else if ("@c@" == currentToken) {
                r = parseExtension(true)
            } else if ("@h2@" == currentToken) {
                r = parseExtension(false)
            } else {
                r = RuleElement(currentToken!!, currentTopic!!)
            }
        } else {
            r = RuleElement(currentToken!!, currentTopic!!)
        }
        lastRepeat = r
        read()
        return r
    }

    private fun read() {
        if (index < tokens.size) {
            currentToken = tokens[index++]
            firstChar = currentToken!![0]
        } else {
            currentToken = ""
            firstChar = '\u0000'
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for (i in 0 until index) {
            builder.append(tokens[i]).append(' ')
        }
        builder.append("[*]")
        for (i in index until tokens.size) {
            builder.append(' ').append(tokens[i])
        }
        return builder.toString()
    }

    private fun tokenize(): Array<String> {
        val list = ArrayList<String>()
        syntax = replaceAll(syntax!!, "yyyy-MM-dd", "@ymd@")
        syntax = replaceAll(syntax!!, "hh:mm:ss", "@hms@")
        syntax = replaceAll(syntax!!, "hh:mm", "@hms@")
        syntax = replaceAll(syntax!!, "mm:ss", "@hms@")
        syntax = replaceAll(syntax!!, "nnnnnnnnn", "@nanos@")
        syntax = replaceAll(syntax!!, "function", "@func@")
        syntax = replaceAll(syntax!!, "0x", "@hexStart@")
        syntax = replaceAll(syntax!!, "0o", "@octalStart@")
        syntax = replaceAll(syntax!!, "0b", "@binaryStart@")
        syntax = replaceAll(syntax!!, ",...", "@commaDots@")
        syntax = replaceAll(syntax!!, "...", "@dots@")
        syntax = replaceAll(syntax!!, "||", "@concat@")
        syntax = replaceAll(syntax!!, "a-z|_", "@az_@")
        syntax = replaceAll(syntax!!, "A-Z|_", "@az_@")
        syntax = replaceAll(syntax!!, "A-F", "@af@")
        syntax = replaceAll(syntax!!, "0-9", "@digit@")
        syntax = replaceAll(syntax!!, "'['", "@openBracket@")
        syntax = replaceAll(syntax!!, "']'", "@closeBracket@")
        val tokenizer = getTokenizer(syntax!!)
        while (tokenizer.hasMoreTokens()) {
            var s = tokenizer.nextToken()
            // avoid duplicate strings
            s = cache(s)!!
            if (s.length == 1) {
                if (" \r\n".indexOf(s[0]) >= 0) {
                    continue
                }
            }
            list.add(s)
        }
        return list.toTypedArray()
    }

    /**
     * Get the list of tokens that can follow.
     * This is the main autocomplete method.
     * The returned map for the query 'S' may look like this:
     * <pre>
     * key: 1#SELECT, value: ELECT
     * key: 1#SET, value: ET
     * </pre>
     *
     * @param query the start of the statement
     * @return the map of possible token types / tokens
     */
    fun getNextTokenList(query: String): HashMap<String, String> {
        val sentence = Sentence()
        sentence.setQuery(query)
        try {
            for (head in statements!!) {
                if (!head.getSection().startsWith("Commands")) {
                    continue
                }
                sentence.start()
                if (head.getRule().autoComplete(sentence)) {
                    break
                }
            }
        } catch (e: IllegalStateException) {
            // ignore
        }
        return sentence.getNext()
    }

    /**
     * Cross-link all statements with each other.
     * This method is called after updating the topics.
     */
    fun linkStatements() {
        for (r in ruleMap.values) {
            r.getRule().setLinks(ruleMap)
        }
    }

    /**
     * Update a topic with a context specific rule.
     * This is used for autocomplete support.
     *
     * @param topic the topic
     * @param rule the database context rule
     */
    fun updateTopic(topic: String, rule: DbContextRule) {
        val key = toLowerEnglish(topic)
        var head = ruleMap[key]
        if (head == null) {
            head = RuleHead("db", key, rule)
            ruleMap[key] = head
            statements!!.add(head)
        } else {
            head.setRule(rule)
        }
    }

    /**
     * Get the list of possible statements.
     *
     * @return the list of statements
     */
    fun getStatements(): ArrayList<RuleHead>? {
        return statements
    }

    companion object {
        /**
         * Create an instance using the grammar specified in the CSV file.
         *
         * @param csv if not specified, the help.csv is used
         * @return a new instance
         * @throws SQLException on failure
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(SQLException::class, IOException::class)
        fun getInstance(csv: Reader?): Bnf {
            var csv = csv
            val bnf = Bnf()
            if (csv == null) {
                val data = Utils.getResource("/org/h2/res/help.csv")
                csv = InputStreamReader(ByteArrayInputStream(data), StandardCharsets.UTF_8)
            }
            bnf.parse(csv)
            return bnf
        }

        /**
         * Check whether the statement starts with a whitespace.
         *
         * @param s the statement
         * @return if the statement is not empty and starts with a whitespace
         */
        @JvmStatic
        fun startWithSpace(s: String): Boolean {
            return s.length > 0 && Character.isWhitespace(s[0])
        }

        /**
         * Convert convert ruleLink to rule_link.
         *
         * @param token the token
         * @return the rule map key
         */
        @JvmStatic
        fun getRuleMapKey(token: String): String {
            val buff = StringBuilder()
            var i = 0
            val l = token.length
            while (i < l) {
                val ch = token[i]
                if (Character.isUpperCase(ch)) {
                    buff.append('_').append(Character.toLowerCase(ch))
                } else {
                    buff.append(ch)
                }
                i++
            }
            return buff.toString()
        }

        /**
         * Get the tokenizer for the given syntax.
         *
         * @param s the syntax
         * @return the tokenizer
         */
        @JvmStatic
        fun getTokenizer(s: String): StringTokenizer {
            return StringTokenizer(s, " [](){}|.,\r\n<>:-+*/=\"!'$", true)
        }
    }
}
