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
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("profiles.json"))
                zip.write("""{"current_id":"123.yaml","profiles":[{"id":"123.yaml","remark":"主配置"}]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("profiles/123.yaml"))
                zip.write("rules:\n  - MATCH,DIRECT\n".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val result = ClashMiBackupReader.read(ByteArrayInputStream(bytes))

        assertTrue(result.isArchive)
        assertEquals("主配置", result.profiles.single().displayName)
        assertEquals(1, MihomoYaml.summarize(MihomoYaml.parse(result.profiles.single().text)).ruleCount)
    }
}
