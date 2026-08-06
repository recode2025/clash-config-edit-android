package com.recode.clashcraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleFilterTest {
    @Test
    fun emptyLockedKeepsNothing() {
        val rules = listOf<Any?>("DOMAIN-SUFFIX,a.com,DIRECT", "MATCH,节点选择")
        assertEquals(emptyList<Any?>(), RuleFilter.keepLocked(rules, emptySet()))
    }

    @Test
    fun fullyLockedKeepsAllInOrder() {
        val rules = listOf<Any?>("DOMAIN-SUFFIX,a.com,DIRECT", "MATCH,节点选择")
        val locked = setOf("DOMAIN-SUFFIX,a.com,DIRECT", "MATCH,节点选择")
        assertEquals(rules, RuleFilter.keepLocked(rules, locked))
    }

    @Test
    fun partiallyLockedKeepsOnlyMatchesInOrder() {
        val rules = listOf<Any?>(
            "DOMAIN-SUFFIX,a.com,DIRECT",
            "PROCESS-NAME,org.example,AUTO",
            "MATCH,节点选择",
        )
        val locked = setOf("PROCESS-NAME,org.example,AUTO")
        assertEquals(
            listOf<Any?>("PROCESS-NAME,org.example,AUTO"),
            RuleFilter.keepLocked(rules, locked),
        )
    }

    @Test
    fun duplicateRuleTextIsKeptTogether() {
        val rules = listOf<Any?>("DOMAIN-SUFFIX,a.com,DIRECT", "DOMAIN-SUFFIX,a.com,DIRECT")
        val locked = setOf("DOMAIN-SUFFIX,a.com,DIRECT")
        val kept = RuleFilter.keepLocked(rules, locked)
        assertEquals(2, kept.size)
        assertTrue(kept.all { it == "DOMAIN-SUFFIX,a.com,DIRECT" })
    }

    @Test
    fun danglingLockedEntriesNotInRulesAreHarmless() {
        val rules = listOf<Any?>("DOMAIN-SUFFIX,a.com,DIRECT")
        val locked = setOf("DOMAIN-SUFFIX,a.com,DIRECT", "PROCESS-NAME,missing,AUTO")
        assertEquals(
            listOf<Any?>("DOMAIN-SUFFIX,a.com,DIRECT"),
            RuleFilter.keepLocked(rules, locked),
        )
    }
}
