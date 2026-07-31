package com.recode.clashcraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRuleManagerTest {
    @Test
    fun duplicateMatcherReturnsExistingRuleEvenWhenTargetDiffers() {
        val rules = listOf("DOMAIN-SUFFIX,example.org,DIRECT", "MATCH,节点选择")

        val (unchanged, result) = CustomRuleManager.insert(
            rules,
            CustomRuleRequest("domain-suffix", "example.org", "节点选择"),
        )

        assertTrue(result.existed)
        assertEquals(0, result.index)
        assertEquals(rules, unchanged)
    }

    @Test
    fun newRuleIsInsertedBeforeMatch() {
        val rules = listOf("DOMAIN-SUFFIX,example.org,DIRECT", "MATCH,节点选择")

        val (updated, result) = CustomRuleManager.insert(
            rules,
            CustomRuleRequest("IP-CIDR", "10.0.0.0/8", "DIRECT", noResolve = true),
        )

        assertFalse(result.existed)
        assertEquals(1, result.index)
        assertEquals("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve", updated[1])
        assertEquals("MATCH,节点选择", updated.last())
    }
}
