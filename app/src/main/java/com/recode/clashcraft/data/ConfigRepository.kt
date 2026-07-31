package com.recode.clashcraft.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ConfigRepository(private val context: Context) {
    companion object {
        private const val MAX_CONFIG_BYTES = 64 * 1024 * 1024
    }

    suspend fun read(uri: Uri): ConfigReadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        persistPermission(resolver, uri)
        val fileName = queryDisplayName(resolver, uri)
        resolver.openInputStream(uri)?.use { rawInput ->
            val input = BufferedInputStream(rawInput)
            input.mark(4)
            val signature = ByteArray(4)
            val signatureSize = input.read(signature)
            input.reset()
            val isZip = fileName.endsWith(".zip", true) ||
                (signatureSize == 4 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte())
            if (isZip) ClashMiBackupReader.read(input) else {
                val text = input.readLimited(MAX_CONFIG_BYTES, "配置文件超过 64 MB，已停止读取")
                    .toString(StandardCharsets.UTF_8)
                    .removePrefix("\uFEFF")
                ConfigReadResult(
                    profiles = listOf(ImportedProfile(fileName, fileName, text)),
                    isArchive = false,
                )
            }
        } ?: error("无法打开此文件")
    }

    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_CONFIG_BYTES) {
            "配置文件超过 64 MB，已停止写入"
        }
        context.contentResolver.openOutputStream(uri, "rwt")?.bufferedWriter(StandardCharsets.UTF_8)?.use {
            it.write(text)
        } ?: error("此位置不允许写入")
    }

    suspend fun createShareCopy(text: String, displayName: String): Uri = withContext(Dispatchers.IO) {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_CONFIG_BYTES) {
            "配置文件超过 64 MB，已停止分享"
        }
        val directory = File(context.cacheDir, "shared-configs").apply {
            check(exists() || mkdirs()) { "无法创建分享缓存" }
        }
        val file = File(directory, "${System.currentTimeMillis()}-${displayName.safeYamlName()}")
        file.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { it.write(text) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun persistPermission(resolver: ContentResolver, uri: Uri) {
        val readWrite = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, readWrite) }
            .recoverCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
    }

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

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true && !cursor.isNull(0)) {
                cursor.getString(0)?.takeIf(String::isNotBlank) ?: "config.yaml"
            } else {
                uri.lastPathSegment?.substringAfterLast('/') ?: "config.yaml"
            }
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/') ?: "config.yaml"
        } finally {
            cursor?.close()
        }
    }

    private fun String.safeYamlName(): String {
        val base = substringAfterLast('/').ifBlank { "config.yaml" }
        val clean = base.replace(Regex("[\\\\/:*?\"<>|\\p{Cc}]"), "_").take(120).ifBlank { "config.yaml" }
        return if (clean.endsWith(".yaml", true) || clean.endsWith(".yml", true)) clean else "$clean.yaml"
    }
}
