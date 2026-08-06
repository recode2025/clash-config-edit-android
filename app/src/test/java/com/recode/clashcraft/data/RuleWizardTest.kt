package com.recode.clashcraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleWizardTest {
    private val source = """
        proxies:
          - name: HK-01
            type: ss
            server: example.com
            port: 443
            cipher: aes-128-gcm
            password: test
          - name: JP-01
            type: ss
            server: example.net
            port: 443
            cipher: aes-128-gcm
            password: test
        proxy-groups:
          - name: 节点选择
            type: select
            proxies:
              - HK-01
              - JP-01
              - DIRECT
        rules:
          - DOMAIN-SUFFIX,example.org,DIRECT
          - MATCH,节点选择
        experimental-field:
          future-key: preserved
    """.trimIndent()

    @Test
    fun insertsPackageAndDomainRulesBeforeMatchAndPreservesUnknownKeys() {
        val root = RuleWizard.apply(
            MihomoYaml.parse(source),
            RuleWizardRequest(
                packageNames = listOf("org.telegram.messenger"),
                domains = listOf("https://telegram.org/path"),
                groupName = "Telegram 自动",
                parentGroupName = "节点选择",
                selectedProxies = listOf("HK-01", "JP-01"),
                includeParentProviders = false,
                providerFilter = "",
                testUrl = "https://www.gstatic.com/generate_204",
            ),
        )

        val rules = root["rules"].asStringList()
        assertTrue(rules.indexOf("PROCESS-NAME,org.telegram.messenger,Telegram 自动") < rules.indexOf("MATCH,节点选择"))
        assertTrue(rules.contains("DOMAIN-SUFFIX,telegram.org,Telegram 自动"))
        assertEquals("preserved", ((root["experimental-field"] as Map<*, *>)["future-key"]))

        val newGroup = root["proxy-groups"].asMapList().first { it["name"] == "Telegram 自动" }
        assertEquals("url-test", newGroup["type"])
        assertEquals(listOf("HK-01", "JP-01"), newGroup["proxies"])
        assertEquals("always", root["find-process-mode"])
    }
}

