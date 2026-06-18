/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.util.ArrayList
import java.util.HashMap

import org.h2.util.Utils.Companion.newSmallArrayList

/**
 * Represents a sequence of BNF rules, or a list of alternative rules.
 */
class RuleList(first: Rule, next: Rule, or: Boolean) : Rule {

    internal val or: Boolean
    internal val list: ArrayList<Rule>
    private var mapSet = false

    init {
        list = newSmallArrayList()
        if (first is RuleList && first.or == or) {
            list.addAll(first.list)
        } else {
            list.add(first)
        }
        if (next is RuleList && next.or == or) {
            list.addAll(next.list)
        } else {
            list.add(next)
        }
        this.or = or
    }

    override fun accept(visitor: BnfVisitor) {
        visitor.visitRuleList(or, list)
    }

    override fun setLinks(ruleMap: HashMap<String, RuleHead>) {
        if (!mapSet) {
            for (r in list) {
                r.setLinks(ruleMap)
            }
            mapSet = true
        }
    }

    override fun autoComplete(sentence: Sentence): Boolean {
        sentence.stopIfRequired()
        val old = sentence.getQuery()!!
        if (or) {
            for (r in list) {
                sentence.setQuery(old)
                if (r.autoComplete(sentence)) {
                    return true
                }
            }
            return false
        }
        for (r in list) {
            if (!r.autoComplete(sentence)) {
                sentence.setQuery(old)
                return false
            }
        }
        return true
    }

    override fun toString(): String {
        val builder = StringBuilder()
        var i = 0
        val l = list.size
        while (i < l) {
            if (i > 0) {
                if (or) {
                    builder.append(" | ")
                } else {
                    builder.append(' ')
                }
            }
            builder.append(list[i].toString())
            i++
        }
        return builder.toString()
    }

}
