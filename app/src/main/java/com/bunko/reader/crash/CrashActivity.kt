package com.bunko.reader.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bunko.reader.MainActivity
import com.bunko.reader.ui.theme.BunkoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CrashActivity : ComponentActivity() {
    private var reportFile by mutableStateOf<File?>(null)
    private var preparingReport by mutableStateOf(false)
    private var reportFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
        }
        prepareReport()
        setContent {
            BunkoTheme {
                val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                SideEffect {
                    val bars = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { dark }
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                CrashScreen()
            }
        }
    }

    private fun prepareReport() {
        if (preparingReport) return
        preparingReport = true
        reportFailed = false
        lifecycleScope.launch {
            try {
                reportFile = withContext(Dispatchers.IO) {
                    CrashReportStore.complete(applicationContext, intent)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                reportFailed = true
            } finally {
                preparingReport = false
            }
        }
    }

    private fun copyReport() {
        val report = reportFile ?: return
        lifecycleScope.launch {
            try {
                if (report.length() > 512 * 1024) {
                    Toast.makeText(this@CrashActivity, "Report is too large to copy directly, please share it instead", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val text = withContext(Dispatchers.IO) { report.readText() }
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Bunko Crash Report", text))
                Toast.makeText(this@CrashActivity, "Crash report copied to clipboard", Toast.LENGTH_SHORT).show()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Toast.makeText(this@CrashActivity, "Failed to copy report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareReport() {
        val report = reportFile ?: return
        try {
            shareReportFile(this, report)
        } catch (_: Exception) {
            Toast.makeText(this, "Failed to share report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartApp() {
        val restartIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(restartIntent)
        finishAffinity()
    }

    @Composable
    private fun CrashScreen() {
        val throwableClass = remember { intent.getStringExtra(CrashReportStore.EXTRA_THROWABLE_CLASS) ?: "Unknown Error" }
        val errorMessage = remember { intent.getStringExtra(CrashReportStore.EXTRA_ERROR_MESSAGE) }
        val reportId = remember { intent.getStringExtra(CrashReportStore.EXTRA_REPORT_ID) }
        var showDetails by remember { mutableStateOf(false) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Text(
                        text = "Bunko Crashed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "An unexpected error caused Bunko to stop working.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = throwableClass,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (!errorMessage.isNullOrBlank()) {
                                    Text(
                                        text = errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!reportId.isNullOrBlank()) {
                                    Text(
                                        text = "Report ID: $reportId",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = ::restartApp,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restart Bunko")
                        }
                        OutlinedButton(
                            onClick = { finishAffinity() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                        ) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Close")
                        }
                    }

                    if (preparingReport) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            text = "Preparing crash report...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (reportFailed) {
                        Text(
                            text = "Failed to collect full report details.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = ::prepareReport) { Text("Retry") }
                    }

                    OutlinedButton(
                        onClick = ::shareReport,
                        enabled = reportFile != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Crash Report")
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { showDetails = true },
                            enabled = reportFile != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.BugReport, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View Details")
                        }
                        TextButton(
                            onClick = ::copyReport,
                            enabled = reportFile != null
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy All")
                        }
                    }
                }
            }
        }

        val report = reportFile
        if (showDetails && report != null) {
            CrashDetailsDialog(report = report, onDismiss = { showDetails = false })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CrashDetailsDialog(
        report: File,
        onDismiss: () -> Unit
    ) {
        var lines by remember(report) { mutableStateOf<List<String>?>(null) }
        var failed by remember(report) { mutableStateOf(false) }

        LaunchedEffect(report) {
            try {
                lines = withContext(Dispatchers.IO) { report.readLines() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed = true
            }
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Crash Details & Logs") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                            }
                        },
                        actions = {
                            IconButton(onClick = ::copyReport) {
                                Icon(Icons.Filled.ContentCopy, "Copy All")
                            }
                            IconButton(onClick = ::shareReport) {
                                Icon(Icons.Filled.Share, "Share Report")
                            }
                        }
                    )
                }
            ) { padding ->
                val reportLines = lines
                if (failed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Could not read crash report file", color = MaterialTheme.colorScheme.error)
                    }
                } else if (reportLines == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(0.6f))
                    }
                } else {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(reportLines) { line ->
                                Text(
                                    text = line.ifEmpty { " " },
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun shareReportFile(
            context: Context,
            file: File,
            authority: String = "${context.packageName}.provider"
        ) {
            val uri = FileProvider.getUriForFile(context, authority, file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Bunko crash report", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Bunko crash report"))
        }
    }
}
