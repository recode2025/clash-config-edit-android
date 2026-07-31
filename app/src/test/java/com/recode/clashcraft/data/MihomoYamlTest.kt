package com.recode.clashcraft.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoYamlTest {
    @Test
    fun parsesFlagEmojiAcrossReaderBufferBoundary() {
        // SnakeYAML 2.3 split this UTF-16 surrogate pair at its 1024-char reader boundary.
        val yaml = "value: \"${"a".repeat(1013)}🇭🇰\"\nrules:\n  - MATCH,DIRECT\n"

        val root = MihomoYaml.parse(yaml)

        assertEquals(1, MihomoYaml.summarize(root).ruleCount)
        assertEquals("${"a".repeat(1013)}🇭🇰", root["value"])
    }
}
