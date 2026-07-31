package com.recode.clashcraft.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashMiBackupReaderTest {
    @Test
    fun extractsProfilesAndUsesClashMiRemarks() {
        val bytes = zipOf(
            "profiles.json" to
                """{"current_id":"123.yaml","profiles":[{"id":"123.yaml","remark":"主配置"}]}""",
            "profiles/123.yaml" to "rules:\n  - MATCH,DIRECT\n",
        )

        val result = ClashMiBackupReader.read(ByteArrayInputStream(bytes))

        assertTrue(result.isArchive)
        assertEquals("主配置", result.profiles.single().displayName)
        assertEquals(1, MihomoYaml.summarize(MihomoYaml.parse(result.profiles.single().text)).ruleCount)
    }

    @Test
    fun acceptsBackupWrappedInAnExtraDirectory() {
        val bytes = zipOf(
            "ClashMi-backup/profiles.json" to
                """{"profiles":[{"id":"abc.yaml","remark":"大配置"}]}""",
            "ClashMi-backup/profiles/abc.yaml" to "port: 7890\nrules:\n  - MATCH,DIRECT\n",
        )

        val result = ClashMiBackupReader.read(ByteArrayInputStream(bytes))

        assertEquals("大配置", result.profiles.single().displayName)
        assertTrue(result.profiles.single().text.contains("MATCH,DIRECT"))
    }

    @Test
    fun matchesProfileIdWhenYamlIsAtArchiveRoot() {
        val bytes = zipOf(
            "profiles.json" to """{"profiles":[{"id":"abc.yaml","remark":"根目录配置"}]}""",
            "abc.yaml" to "port: 7890\nmode: Rule\n",
            "service_core_patch.yaml" to "dns:\n  enable: true\n",
        )

        val result = ClashMiBackupReader.read(ByteArrayInputStream(bytes))

        assertEquals(1, result.profiles.size)
        assertEquals("根目录配置", result.profiles.single().displayName)
        assertEquals("abc.yaml", result.profiles.single().fileName)
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
