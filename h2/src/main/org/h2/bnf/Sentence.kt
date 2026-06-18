/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.util.Objects
import org.h2.bnf.context.DbSchema
import org.h2.bnf.context.DbTableOrView
import org.h2.util.StringUtils.Companion.toUpperEnglish

/**
 * A query context object. It contains the list of table and alias objects.
 * Used for autocomplete.
 */
class Sentence {

    /**
     * The map of next tokens in the form type#tokenName token.
     */
    private val next = HashMap<String, String>()

    /**
     * The complete query string.
     */
    private var query: String? = null

    /**
     * The uppercase version of the query string.
     */
    private var queryUpper: String? = null

    private var stopAtNs: Long = 0
    private var lastMatchedSchema: DbSchema? = null
    private var lastMatchedTable: DbTableOrView? = null
    private var lastTable: DbTableOrView? = null
    private var tables: HashSet<DbTableOrView>? = null
    private var aliases: HashMap<String, DbTableOrView>? = null

    /**
     * Start the timer to make sure processing doesn't take too long.
     */
    fun start() {
        stopAtNs = System.nanoTime() + MAX_PROCESSING_TIME * 1_000_000L
    }

    /**
     * Check if it's time to stop processing.
     * Processing auto-complete shouldn't take more than a few milliseconds.
     * If processing is stopped, this methods throws an IllegalStateException
     */
    fun stopIfRequired() {
        if (System.nanoTime() - stopAtNs > 0L) {
            throw IllegalStateException()
        }
    }

    /**
     * Add a word to the set of next tokens.
     *
     * @param n the token name
     * @param string an example text
     * @param type the token type
     */
    fun add(n: String, string: String, type: Int) {
        next["$type#$n"] = string
    }

    /**
     * Add an alias name and object
     *
     * @param alias the alias name
     * @param table the alias table
     */
    fun addAlias(alias: String, table: DbTableOrView) {
        if (aliases == null) {
            aliases = HashMap()
        }
        aliases!![alias] = table
    }

    /**
     * Add a table.
     *
     * @param table the table
     */
    fun addTable(table: DbTableOrView) {
        lastTable = table
        if (tables == null) {
            tables = HashSet()
        }
        tables!!.add(table)
    }

    /**
     * Get the set of tables.
     *
     * @return the set of tables
     */
    fun getTables(): HashSet<DbTableOrView>? {
        return tables
    }

    /**
     * Get the alias map.
     *
     * @return the alias map
     */
    fun getAliases(): HashMap<String, DbTableOrView>? {
        return aliases
    }

    /**
     * Get the last added table.
     *
     * @return the last table
     */
    fun getLastTable(): DbTableOrView? {
        return lastTable
    }

    /**
     * Get the last matched schema if the last match was a schema.
     *
     * @return the last schema or null
     */
    fun getLastMatchedSchema(): DbSchema? {
        return lastMatchedSchema
    }

    /**
     * Set the last matched schema if the last match was a schema,
     * or null if it was not.
     *
     * @param schema the last matched schema or null
     */
    fun setLastMatchedSchema(schema: DbSchema?) {
        this.lastMatchedSchema = schema
    }

    /**
     * Set the last matched table if the last match was a table.
     *
     * @param table the last matched table or null
     */
    fun setLastMatchedTable(table: DbTableOrView?) {
        this.lastMatchedTable = table
    }

    /**
     * Get the last matched table if the last match was a table.
     *
     * @return the last table or null
     */
    fun getLastMatchedTable(): DbTableOrView? {
        return lastMatchedTable
    }

    /**
     * Set the query string.
     *
     * @param query the query string
     */
    fun setQuery(query: String?) {
        if (!Objects.equals(this.query, query)) {
            this.query = query
            this.queryUpper = toUpperEnglish(query!!)
        }
    }

    /**
     * Get the query string.
     *
     * @return the query
     */
    fun getQuery(): String? {
        return query
    }

    /**
     * Get the uppercase version of the query string.
     *
     * @return the uppercase query
     */
    fun getQueryUpper(): String? {
        return queryUpper
    }

    /**
     * Get the map of next tokens.
     *
     * @return the next token map
     */
    fun getNext(): HashMap<String, String> {
        return next
    }

    companion object {
        /**
         * This token type means the possible choices of the item depend on the
         * context. For example the item represents a table name of the current
         * database.
         */
        const val CONTEXT = 0

        /**
         * The token type for a keyword.
         */
        const val KEYWORD = 1

        /**
         * The token type for a function name.
         */
        const val FUNCTION = 2

        private const val MAX_PROCESSING_TIME = 100
    }
}
