/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

/**
 * Represents the head of a BNF rule.
 */
class RuleHead constructor(
    private val section: String,
    private val topic: String,
    private var rule: Rule
) {

    fun getTopic(): String {
        return topic
    }

    fun getRule(): Rule {
        return rule
    }

    fun setRule(rule: Rule) {
        this.rule = rule
    }

    fun getSection(): String {
        return section
    }

}
