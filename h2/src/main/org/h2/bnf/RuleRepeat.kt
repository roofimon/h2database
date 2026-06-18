/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.util.HashMap

/**
 * Represents a loop in a BNF object.
 */
class RuleRepeat(private val rule: Rule, private val comma: Boolean) : Rule {

    override fun accept(visitor: BnfVisitor) {
        visitor.visitRuleRepeat(comma, rule)
    }

    override fun setLinks(ruleMap: HashMap<String, RuleHead>) {
        // not required, because it's already linked
    }

    override fun autoComplete(sentence: Sentence): Boolean {
        sentence.stopIfRequired()
        while (rule.autoComplete(sentence)) {
            // nothing to do
        }
        var s = sentence.getQuery()!!
        while (Bnf.startWithSpace(s)) {
            s = s.substring(1)
        }
        sentence.setQuery(s)
        return true
    }

    override fun toString(): String {
        return if (comma) ", ..." else " ..."
    }

}
