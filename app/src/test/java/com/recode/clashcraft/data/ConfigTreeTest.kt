package com.recode.clashcraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTreeTest {
    @Test
    fun updatesOnlyTheChangedPathAndKeepsOtherLargeBranches() {
        val rules = (1..50_000).map { "DOMAIN-SUFFIX,example$it.com,DIRECT" }
        val dns = linkedMapOf<String, Any?>("enable" to true, "ipv6" to false)
        val root = linkedMapOf<String, Any?>("dns" to dns, "rules" to rules)

        val updated = ConfigTree.set(root, ConfigPath.Root.key("dns").key("ipv6"), true)

        assertEquals(true, (updated["dns"] as Map<*, *>)["ipv6"])
        assertSame("未修改的大型列表应复用原对象", rules, updated["rules"])
    }

    @Test
    fun supportsAddingRenamingRemovingAndReorderingGuiValues() {
        var root = linkedMapOf<String, Any?>("rules" to mutableListOf("a", "b"))
        root = ConfigTree.addMapEntry(root, ConfigPath.Root, "dns", ConfigValueType.MAP)
        root = ConfigTree.addMapEntry(root, ConfigPath.Root.key("dns"), "enable", ConfigValueType.BOOLEAN)
        root = ConfigTree.renameKey(root, ConfigPath.Root.key("dns"), "enable", "ipv6")
        root = ConfigTree.moveListItem(root, ConfigPath.Root.key("rules"), 1, -1)
        root = ConfigTree.remove(root, ConfigPath.Root.key("dns").key("ipv6"))

        assertEquals(listOf("b", "a"), root["rules"])
        assertTrue((root["dns"] as Map<*, *>).isEmpty())
    }

    @Test
    fun largeRuleListRoundTripsAndSummarizes() {
        val root = linkedMapOf<String, Any?>(
            "rules" to (1..20_000).map { "DOMAIN-SUFFIX,example$it.com,DIRECT" },
            "proxy-groups" to listOf(mapOf("name" to "节点选择", "type" to "select", "proxies" to listOf("DIRECT"))),
        )

        val parsed = MihomoYaml.parse(MihomoYaml.dump(root))
        val summary = MihomoYaml.summarize(parsed)

        assertEquals(20_000, summary.ruleCount)
        assertEquals(1, summary.groupCount)
    }
}
