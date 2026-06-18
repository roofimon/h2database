/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.util.HashMap

import org.h2.util.StringUtils.Companion.toLowerEnglish
import org.h2.util.StringUtils.Companion.toUpperEnglish

/**
 * A single terminal rule in a BNF object.
 */
class RuleElement(private val name: String, topic: String) : Rule {

    private val keyword: Boolean = name.length == 1 || name == toUpperEnglish(name)
    private var link: Rule? = null
    private val type: Int

    init {
        val t = toLowerEnglish(topic)
        this.type = if (t.startsWith("function")) Sentence.FUNCTION else Sentence.KEYWORD
    }

    override fun accept(visitor: BnfVisitor) {
        visitor.visitRuleElement(keyword, name, link)
    }

    override fun setLinks(ruleMap: HashMap<String, RuleHead>) {
        link?.setLinks(ruleMap)
        if (keyword) {
            return
        }
        val test = Bnf.getRuleMapKey(name)
        for (i in 0 until test.length) {
            val t = test.substring(i)
            val r = ruleMap[t]
            if (r != null) {
                link = r.getRule()
                return
            }
        }
        throw AssertionError("Unknown $name/$test")
    }

    override fun autoComplete(sentence: Sentence): Boolean {
        sentence.stopIfRequired()
        if (keyword) {
            var query = sentence.getQuery()!!
            val q = query.trim()
            val up = sentence.getQueryUpper()!!.trim()
            if (up.startsWith(name)) {
                query = query.substring(name.length)
                while ("_" != name && Bnf.startWithSpace(query)) {
                    query = query.substring(1)
                }
                sentence.setQuery(query)
                return true
            } else if (q.length == 0 || name.startsWith(up)) {
                if (q.length < name.length) {
                    sentence.add(name, name.substring(q.length), type)
                }
            }
            return false
        }
        return link!!.autoComplete(sentence)
    }

    override fun toString(): String {
        return name
    }

}
