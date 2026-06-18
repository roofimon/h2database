/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf.context

import java.util.HashMap
import java.util.HashSet
import org.h2.bnf.Bnf
import org.h2.bnf.BnfVisitor
import org.h2.bnf.Rule
import org.h2.bnf.RuleElement
import org.h2.bnf.RuleHead
import org.h2.bnf.RuleList
import org.h2.bnf.Sentence
import org.h2.message.DbException
import org.h2.util.ParserUtil.Companion.isKeyword
import org.h2.util.StringUtils.Companion.quoteIdentifier
import org.h2.util.StringUtils.Companion.startsWith
import org.h2.util.StringUtils.Companion.startsWithIgnoringCase
import org.h2.util.StringUtils.Companion.toUpperEnglish
import org.h2.util.StringUtils.Companion.trimSubstring

/**
 * A BNF terminal rule that is linked to the database context information.
 * This class is used by the H2 Console, to support auto-complete.
 */
class DbContextRule
/**
 * BNF terminal rule Constructor
 * @param contents Extract rule from this component
 * @param type Rule type, one of
 * [DbContextRule.COLUMN],
 * [DbContextRule.TABLE],
 * [DbContextRule.TABLE_ALIAS],
 * [DbContextRule.NEW_TABLE_ALIAS],
 * [DbContextRule.COLUMN_ALIAS],
 * [DbContextRule.SCHEMA]
 */
(
    private val contents: DbContents,
    private val type: Int,
) : Rule {

    private var columnType: String? = null

    /**
     * @param columnType COLUMN Auto completion can be filtered by column type
     */
    fun setColumnType(columnType: String?) {
        this.columnType = columnType
    }

    override fun setLinks(ruleMap: HashMap<String, RuleHead>) {
        // nothing to do
    }

    override fun accept(visitor: BnfVisitor) {
        // nothing to do
    }

    override fun autoComplete(sentence: Sentence): Boolean {
        val query = sentence.getQuery()!!
        var s = query
        when (type) {
            SCHEMA -> {
                val schemas = contents.getSchemas()
                var best: String? = null
                var bestSchema: DbSchema? = null
                for (schema in schemas!!) {
                    val name = schema.name
                    val quotedName = quoteIdentifier(name!!)
                    if (startsWithIgnoringCase(query, name)) {
                        if (best == null || name!!.length > best.length) {
                            best = name
                            bestSchema = schema
                        }
                    } else if (startsWith(query, quotedName)) {
                        if (best == null || name!!.length > best.length) {
                            best = quotedName
                            bestSchema = schema
                        }
                    } else if (s.isEmpty() || startsWithIgnoringCase(name, query) ||
                        startsWithIgnoringCase(quotedName, query)
                    ) {
                        if (s.length < name!!.length) {
                            sentence.add(name, name.substring(s.length), type)
                            sentence.add(
                                schema.quotedName + ".",
                                schema.quotedName!!.substring(s.length) + ".",
                                Sentence.CONTEXT
                            )
                        }
                    }
                }
                if (best != null) {
                    sentence.setLastMatchedSchema(bestSchema)
                    s = s.substring(best.length)
                }
            }
            TABLE -> {
                var schema = sentence.getLastMatchedSchema()
                if (schema == null) {
                    schema = contents.getDefaultSchema()
                }
                val tables = schema!!.getTables()
                var best: String? = null
                var bestTable: DbTableOrView? = null
                for (table in tables!!) {
                    val name = table.getName()
                    val quotedName = quoteIdentifier(name!!)
                    if (startsWithIgnoringCase(query, name) ||
                        startsWithIgnoringCase("\"" + query, quotedName)
                    ) {
                        if (best == null || name!!.length > best.length) {
                            best = name
                            bestTable = table
                        }
                    } else if (s.isEmpty() || startsWithIgnoringCase(name, query) ||
                        startsWithIgnoringCase(quotedName, query)
                    ) {
                        if (s.length < name!!.length) {
                            sentence.add(
                                table.getQuotedName()!!,
                                table.getQuotedName()!!.substring(s.length),
                                Sentence.CONTEXT
                            )
                        }
                    }
                }
                if (best != null) {
                    sentence.setLastMatchedTable(bestTable)
                    sentence.addTable(bestTable!!)
                    s = s.substring(best.length)
                }
            }
            NEW_TABLE_ALIAS -> s = autoCompleteTableAlias(sentence, true)
            TABLE_ALIAS -> s = autoCompleteTableAlias(sentence, false)
            COLUMN_ALIAS -> {
                var i = 0
                if (query.indexOf(' ') < 0) {
                    return finish(sentence, query, s)
                }
                val l = query.length
                var cp = query.codePointAt(i)
                if (!Character.isJavaIdentifierStart(cp) || cp == '$'.code) {
                    return finish(sentence, query, s)
                }
                i += Character.charCount(cp)
                while (i < l && Character.isJavaIdentifierPart(query.codePointAt(i).also { cp = it })) {
                    i += Character.charCount(cp)
                }
                val alias = query.substring(0, i)
                if (isKeyword(alias, false)) {
                    return finish(sentence, query, s)
                }
                s = s.substring(alias.length)
            }
            COLUMN -> {
                val set = sentence.getTables()
                var best: String? = null
                val last = sentence.getLastMatchedTable()
                if (last != null && last.getColumns() != null) {
                    for (column in last.getColumns()!!) {
                        var compare = query
                        var name = column.getName()
                        if (column.getQuotedName()!!.length > name!!.length) {
                            name = column.getQuotedName()
                            compare = query
                        }
                        if (startsWithIgnoringCase(compare, name!!) && testColumnType(column)) {
                            val b = s.substring(name.length)
                            if (best == null || b.length < best.length) {
                                best = b
                            } else if (s.isEmpty() || startsWithIgnoringCase(name, compare)) {
                                if (s.length < name.length) {
                                    sentence.add(
                                        column.getName()!!,
                                        column.getName()!!.substring(s.length),
                                        Sentence.CONTEXT
                                    )
                                }
                            }
                        }
                    }
                }
                for (schema in contents.getSchemas()!!) {
                    for (table in schema.getTables()!!) {
                        if (table !== last && set != null && !set.contains(table)) {
                            continue
                        }
                        if (table.getColumns() == null) {
                            continue
                        }
                        for (column in table.getColumns()!!) {
                            val name = column.getName()
                            if (testColumnType(column)) {
                                if (startsWithIgnoringCase(query, name!!)) {
                                    val b = s.substring(name.length)
                                    if (best == null || b.length < best.length) {
                                        best = b
                                    }
                                } else if (s.isEmpty() || startsWithIgnoringCase(name, query)) {
                                    if (s.length < name!!.length) {
                                        sentence.add(
                                            column.getName()!!,
                                            column.getName()!!.substring(s.length),
                                            Sentence.CONTEXT
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (best != null) {
                    s = best
                }
            }
            PROCEDURE -> autoCompleteProcedure(sentence)
            else -> throw DbException.getInternalError("type=$type")
        }
        return finish(sentence, query, s)
    }

    private fun finish(sentence: Sentence, query: String, s: String): Boolean {
        var s = s
        if (s != query) {
            while (Bnf.startWithSpace(s)) {
                s = s.substring(1)
            }
            sentence.setQuery(s)
            return true
        }
        return false
    }

    private fun testColumnType(column: DbColumn): Boolean {
        val columnType = this.columnType ?: return true
        val type = column.getDataType()!!
        if (columnType.contains("CHAR") || columnType.contains("CLOB")) {
            return type.contains("CHAR") || type.contains("CLOB")
        }
        if (columnType.contains("BINARY") || columnType.contains("BLOB")) {
            return type.contains("BINARY") || type.contains("BLOB")
        }
        return type.contains(columnType)
    }

    private fun autoCompleteProcedure(sentence: Sentence) {
        var schema = sentence.getLastMatchedSchema()
        if (schema == null) {
            schema = contents.getDefaultSchema()
        }
        val incompleteSentence = sentence.getQueryUpper()!!
        var incompleteFunctionName = incompleteSentence
        val bracketIndex = incompleteSentence.indexOf('(')
        if (bracketIndex != -1) {
            incompleteFunctionName = trimSubstring(incompleteSentence, 0, bracketIndex)
        }

        // Common elements
        val openBracket = RuleElement("(", "Function")
        val closeBracket = RuleElement(")", "Function")
        val comma = RuleElement(",", "Function")

        // Fetch all elements
        for (procedure in schema!!.getProcedures()!!) {
            val procName = procedure.getName()!!
            if (procName.startsWith(incompleteFunctionName)) {
                // That's it, build a RuleList from this function
                val procedureElement = RuleElement(procName, "Function")
                var rl: RuleList = RuleList(procedureElement, openBracket, false)
                // Go further only if the user use open bracket
                if (incompleteSentence.contains("(")) {
                    for (parameter in procedure.getParameters()!!) {
                        if (parameter!!.getPosition() > 1) {
                            rl = RuleList(rl, comma, false)
                        }
                        val columnRule = DbContextRule(contents, COLUMN)
                        var parameterType = parameter.getDataType()!!
                        // Remove precision
                        if (parameterType.contains("(")) {
                            parameterType = parameterType.substring(0, parameterType.indexOf('('))
                        }
                        columnRule.setColumnType(parameterType)
                        rl = RuleList(rl, columnRule, false)
                    }
                    rl = RuleList(rl, closeBracket, false)
                }
                rl.autoComplete(sentence)
            }
        }
    }

    companion object {
        const val COLUMN = 0
        const val TABLE = 1
        const val TABLE_ALIAS = 2
        const val NEW_TABLE_ALIAS = 3
        const val COLUMN_ALIAS = 4
        const val SCHEMA = 5
        const val PROCEDURE = 6

        private fun autoCompleteTableAlias(sentence: Sentence, newAlias: Boolean): String {
            var s = sentence.getQuery()!!
            val up = sentence.getQueryUpper()!!
            var i = 0
            while (i < up.length) {
                val ch = up[i]
                if (ch != '_' && !Character.isLetterOrDigit(ch)) {
                    break
                }
                i++
            }
            if (i == 0) {
                return s
            }
            val alias = up.substring(0, i)
            if ("SET" == alias || isKeyword(alias, false)) {
                return s
            }
            if (newAlias) {
                sentence.addAlias(alias, sentence.getLastTable()!!)
            }
            val map = sentence.getAliases()
            if ((map != null && map.containsKey(alias)) || sentence.getLastTable() == null) {
                if (newAlias && s.length == alias.length) {
                    return s
                }
                s = s.substring(alias.length)
                if (s.isEmpty()) {
                    sentence.add("$alias.", ".", Sentence.CONTEXT)
                }
                return s
            }
            val tables = sentence.getTables()
            if (tables != null) {
                var best: String? = null
                for (table in tables) {
                    val tableName = toUpperEnglish(table.getName()!!)
                    if (alias.startsWith(tableName) &&
                        (best == null || tableName.length > best.length)
                    ) {
                        sentence.setLastMatchedTable(table)
                        best = tableName
                    } else if (s.isEmpty() || tableName.startsWith(alias)) {
                        sentence.add(
                            "$tableName.",
                            tableName.substring(s.length) + ".",
                            Sentence.CONTEXT
                        )
                    }
                }
                if (best != null) {
                    s = s.substring(best.length)
                    if (s.isEmpty()) {
                        sentence.add("$alias.", ".", Sentence.CONTEXT)
                    }
                    return s
                }
            }
            return s
        }
    }
}
