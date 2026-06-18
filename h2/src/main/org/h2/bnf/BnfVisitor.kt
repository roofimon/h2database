/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf

import java.util.ArrayList

/**
 * The visitor interface for BNF rules.
 */
interface BnfVisitor {

    /**
     * Visit a rule element.
     *
     * @param keyword whether this is a keyword
     * @param name the element name
     * @param link the linked rule if it's not a keyword
     */
    fun visitRuleElement(keyword: Boolean, name: String, link: Rule?)

    /**
     * Visit a repeat rule.
     *
     * @param comma whether the comma is repeated as well
     * @param rule the element to repeat
     */
    fun visitRuleRepeat(comma: Boolean, rule: Rule)

    /**
     * Visit a fixed rule.
     *
     * @param type the type
     */
    fun visitRuleFixed(type: Int)

    /**
     * Visit a rule list.
     *
     * @param or true for OR, false for AND
     * @param list the rules
     */
    fun visitRuleList(or: Boolean, list: ArrayList<Rule>)

    /**
     * Visit an optional rule.
     *
     * @param rule the rule
     */
    fun visitRuleOptional(rule: Rule)

    /**
     * Visit an OR list of optional rules.
     *
     * @param list the optional rules
     */
    fun visitRuleOptional(list: ArrayList<Rule>)

    /**
     * Visit a rule with non-standard extension.
     *
     * @param rule the rule
     * @param compatibility whether this rule exists for compatibility only
     */
    fun visitRuleExtension(rule: Rule, compatibility: Boolean)

}
