package com.recode.clashcraft

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.recode.clashcraft.data.ClashShareRequest
import com.recode.clashcraft.ui.ClashCraftApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.open(uri)
        } else if (intent?.action == Intent.ACTION_SEND) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf(String::isNotBlank)
                ?.let(viewModel::openSharedText)
        }
    }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/yaml"),
    ) { uri -> uri?.let(viewModel::saveAs) }

    private val createDocumentForShare = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/yaml"),
    ) { uri -> uri?.let(viewModel::saveAsAndShare) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIncomingIntent(intent)
        setContent {
            LaunchedEffect(viewModel) {
                viewModel.shareRequests.collect { request -> openInClash(request) }
            }
            ClashCraftApp(
                viewModel = viewModel,
                onOpen = {
                    openDocument.launch(
                        arrayOf("application/yaml", "text/yaml", "text/x-yaml", "text/plain", "application/octet-stream"),
                    )
                },
                onImportClashMi = {
                    openDocument.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
                onSaveAs = { suggestedName -> createDocument.launch(suggestedName.withYamlExtension()) },
                onSaveAndShare = { suggestedName ->
                    if (viewModel.state.value.uri == null) {
                        createDocumentForShare.launch(suggestedName.withYamlExtension())
                    } else {
                        viewModel.saveAndShare()
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIncomingIntent(intent)
    }

    private fun consumeIncomingIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            else -> null
        }
        if (uri != null) {
            viewModel.open(uri)
        } else if (intent?.action == Intent.ACTION_SEND) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?.let(viewModel::openSharedText)
        }
    }

    private fun openInClash(request: ClashShareRequest) {
        val targets = listOfNotNull(
            clashMiIntent(request).takeIf { it.resolveActivity(packageManager) != null },
            clashMetaIntent(request, CLASH_META_PACKAGE, "clashmeta")
                .takeIf { it.resolveActivity(packageManager) != null },
            clashMetaIntent(request, CLASH_FOR_ANDROID_PACKAGE, "clash")
                .takeIf { it.resolveActivity(packageManager) != null },
        )

        targets.forEach { target ->
            target.`package`?.let { packageName ->
                grantUriPermission(packageName, request.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val launchIntent = when (targets.size) {
            0 -> genericShareIntent(request)
            1 -> targets.single()
            else -> Intent.createChooser(targets.first(), "导入到 Clash").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
            }
        }
        try {
            startActivity(launchIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "未找到可接收配置的 Clash 客户端", Toast.LENGTH_LONG).show()
        }
    }

    private fun clashMiIntent(request: ClashShareRequest) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage(CLASH_MI_PACKAGE)
        putExtra(Intent.EXTRA_STREAM, request.uri)
        clipData = ClipData.newRawUri(request.fileName, request.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun clashMetaIntent(
        request: ClashShareRequest,
        packageName: String,
        scheme: String,
    ): Intent {
        val importUri = Uri.Builder()
            .scheme(scheme)
            .authority("install-config")
            .appendQueryParameter("url", request.uri.toString())
            .appendQueryParameter("type", "file")
            .appendQueryParameter("name", request.fileName.substringBeforeLast('.'))
            .build()
        return Intent(Intent.ACTION_VIEW, importUri).apply {
            setPackage(packageName)
            clipData = ClipData.newRawUri(request.fileName, request.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun genericShareIntent(request: ClashShareRequest): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, request.uri)
            clipData = ClipData.newRawUri(request.fileName, request.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "分享到 Clash")
    }

    private fun String.withYamlExtension(): String {
        val clean = if (isBlank() || this == "未打开配置") "config.yaml" else this
        return if (clean.endsWith(".yaml", true) || clean.endsWith(".yml", true)) clean else "$clean.yaml"
    }

    private companion object {
        const val CLASH_MI_PACKAGE = "com.nebula.clashmi"
        const val CLASH_META_PACKAGE = "com.github.metacubex.clash.meta"
        const val CLASH_FOR_ANDROID_PACKAGE = "com.github.kr328.clash"
    }
}
