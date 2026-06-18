/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.util.HashMap

/**
 * Represents an optional BNF rule.
 */
class RuleOptional(private val rule: Rule) : Rule {

    private var mapSet = false

    override fun accept(visitor: BnfVisitor) {
        if (rule is RuleList) {
            if (rule.or) {
                visitor.visitRuleOptional(rule.list)
                return
            }
        }
        visitor.visitRuleOptional(rule)
    }

    override fun setLinks(ruleMap: HashMap<String, RuleHead>) {
        if (!mapSet) {
            rule.setLinks(ruleMap)
            mapSet = true
        }
    }

    override fun autoComplete(sentence: Sentence): Boolean {
        sentence.stopIfRequired()
        rule.autoComplete(sentence)
        return true
    }

    override fun toString(): String {
        return '[' + rule.toString() + ']'
    }

}
