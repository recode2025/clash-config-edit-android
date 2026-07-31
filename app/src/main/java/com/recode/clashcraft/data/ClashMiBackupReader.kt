package com.recode.clashcraft.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object ClashMiBackupReader {
    private const val MAX_CONFIG_BYTES = 64 * 1024 * 1024
    private const val MAX_INDEX_BYTES = 4 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 2_048
    private const val MAX_ARCHIVE_YAML_FILES = 1_024
    private const val MAX_ARCHIVE_PROFILES = 512
    private const val MAX_EXTRACTED_YAML_BYTES = 128L * 1024 * 1024

    private data class YamlEntry(
        val path: String,
        val fileName: String,
        val text: String,
    ) {
        val isInProfilesDirectory: Boolean
            get() = path.split('/').dropLast(1).any { it.equals("profiles", ignoreCase = true) }
    }

    private data class ProfileMetadata(val id: String, val remark: String)

    fun read(input: InputStream): ConfigReadResult = try {
        readArchive(input)
    } catch (error: ZipException) {
        throw IllegalArgumentException("ClashMi 备份 ZIP 已损坏、被加密或格式不受支持：${error.detail()}", error)
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("读取 ClashMi 备份失败：${error.detail()}", error)
    }

    private fun readArchive(input: InputStream): ConfigReadResult {
        val yamlEntries = mutableListOf<YamlEntry>()
        val entryNames = mutableListOf<String>()
        var profilesJson: String? = null
        var entryCount = 0
        var extractedYamlBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MAX_ARCHIVE_ENTRIES) { "备份文件条目过多，已停止读取" }
                val normalized = entry.name.normalizeZipPath()
                if (entryNames.size < 12 && normalized.isNotBlank()) entryNames += normalized
                if (!entry.isDirectory) {
                    val fileName = normalized.substringAfterLast('/')
                    when {
                        fileName.equals("profiles.json", ignoreCase = true) -> {
                            profilesJson = zip.readLimited(MAX_INDEX_BYTES, "ClashMi 配置索引超过 4 MB")
                                .toString(StandardCharsets.UTF_8)
                                .removePrefix("\uFEFF")
                        }
                        fileName.hasYamlExtension() -> {
                            require(yamlEntries.size < MAX_ARCHIVE_YAML_FILES) {
                                "备份中的 YAML 文件超过 $MAX_ARCHIVE_YAML_FILES 个"
                            }
                            val bytes = zip.readLimited(MAX_CONFIG_BYTES, "单个配置超过 64 MB")
                            extractedYamlBytes += bytes.size
                            require(extractedYamlBytes <= MAX_EXTRACTED_YAML_BYTES) {
                                "备份中的 YAML 文件总量超过 128 MB"
                            }
                            yamlEntries += YamlEntry(
                                path = normalized,
                                fileName = fileName,
                                text = bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF"),
                            )
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        require(entryCount > 0) { "ZIP 中没有任何文件，请在 ClashMi 中重新执行“备份与同步 → 导出”" }
        val metadata = parseProfileMetadata(profilesJson)
        val metadataById = metadata.associateBy { it.id.substringAfterLast('/').lowercase(Locale.ROOT) }
        val indexedEntries = metadata.mapNotNull { profile ->
            val fileName = profile.id.substringAfterLast('/')
            yamlEntries.firstOrNull {
                it.isInProfilesDirectory && it.fileName.equals(fileName, ignoreCase = true)
            } ?: yamlEntries.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
        }
        val directoryEntries = yamlEntries.filter(YamlEntry::isInProfilesDirectory)
        val selected = buildList {
            addAll(indexedEntries)
            directoryEntries.filterTo(this) { candidate ->
                none { it.path.equals(candidate.path, ignoreCase = true) }
            }
        }.ifEmpty {
            yamlEntries.filterNot { it.isClashMiAuxiliaryYaml() }
                .takeIf { it.size == 1 }
                .orEmpty()
        }

        require(selected.isNotEmpty()) {
            val preview = entryNames.joinToString(limit = 8, truncated = "…")
            "未在备份中找到 ClashMi 配置。已看到的条目：${preview.ifBlank { "无" }}"
        }
        require(selected.size <= MAX_ARCHIVE_PROFILES) { "ClashMi 备份中的配置超过 512 个" }

        return ConfigReadResult(
            profiles = selected.map { entry ->
                val profile = metadataById[entry.fileName.lowercase(Locale.ROOT)]
                ImportedProfile(
                    fileName = entry.fileName,
                    displayName = profile?.remark?.takeIf(String::isNotBlank) ?: entry.fileName,
                    text = entry.text,
                )
            },
            isArchive = true,
        )
    }

    private fun parseProfileMetadata(json: String?): List<ProfileMetadata> = runCatching {
        val root = json?.let(MihomoYaml::parse).orEmpty()
        val profiles = root["profiles"] as? List<*> ?: return@runCatching emptyList()
        profiles.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"]?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            ProfileMetadata(id.normalizeZipPath(), map["remark"]?.toString().orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun YamlEntry.isClashMiAuxiliaryYaml(): Boolean {
        val lowerPath = path.lowercase(Locale.ROOT)
        val lowerName = fileName.lowercase(Locale.ROOT)
        return lowerPath.split('/').any { it in setOf("profilepatchs", "patches") } ||
            lowerName.startsWith("service_core_") ||
            lowerName in setOf("runtime.yaml", "config_patch.yaml")
    }

    private fun String.normalizeZipPath(): String = replace('\\', '/').trim().trimStart('/')

    private fun String.hasYamlExtension(): Boolean = endsWith(".yaml", true) || endsWith(".yml", true)

    private fun Throwable.detail(): String = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

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
