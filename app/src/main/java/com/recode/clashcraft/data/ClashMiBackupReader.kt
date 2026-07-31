package com.recode.clashcraft.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

object ClashMiBackupReader {
    private const val MAX_CONFIG_BYTES = 64 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 2_048
    private const val MAX_ARCHIVE_PROFILES = 512
    private const val MAX_EXTRACTED_PROFILE_BYTES = 128L * 1024 * 1024

    fun read(input: InputStream): ConfigReadResult {
        val yamlFiles = linkedMapOf<String, String>()
        var profilesJson: String? = null
        var entryCount = 0
        var extractedProfileBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MAX_ARCHIVE_ENTRIES) { "备份文件条目过多，已停止读取" }
                if (!entry.isDirectory) {
                    val normalized = entry.name.replace('\\', '/').trimStart('/')
                    val profileId = normalized.substringAfterLast("profiles/", missingDelimiterValue = "")
                    when {
                        normalized.substringAfterLast('/') == "profiles.json" -> {
                            profilesJson = zip.readLimited(4 * 1024 * 1024, "ClashMi 配置索引过大")
                                .toString(StandardCharsets.UTF_8)
                        }
                        profileId.isNotEmpty() && '/' !in profileId &&
                            (profileId.endsWith(".yaml", true) || profileId.endsWith(".yml", true)) -> {
                            require(yamlFiles.size < MAX_ARCHIVE_PROFILES) { "ClashMi 备份中的配置超过 512 个" }
                            val bytes = zip.readLimited(MAX_CONFIG_BYTES, "单个配置超过 64 MB")
                            extractedProfileBytes += bytes.size
                            require(extractedProfileBytes <= MAX_EXTRACTED_PROFILE_BYTES) {
                                "ClashMi 备份中的配置总量超过 128 MB"
                            }
                            yamlFiles[profileId] = bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require(yamlFiles.isNotEmpty()) { "没有在 ClashMi 备份的 profiles 目录中找到 YAML 配置" }
        val remarks = parseProfileRemarks(profilesJson)
        return ConfigReadResult(
            profiles = yamlFiles.map { (id, text) ->
                ImportedProfile(
                    fileName = id,
                    displayName = remarks[id]?.takeIf(String::isNotBlank) ?: id,
                    text = text,
                )
            },
            isArchive = true,
        )
    }

    private fun parseProfileRemarks(json: String?): Map<String, String> = runCatching {
        val root = json?.let(MihomoYaml::parse).orEmpty()
        val profiles = root["profiles"] as? List<*> ?: return@runCatching emptyMap()
        profiles.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"]?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            id to map["remark"]?.toString().orEmpty()
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun InputStream.readLimited(limit: Int, errorMessage: String): ByteArray {
        val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, limit))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { errorMessage }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
