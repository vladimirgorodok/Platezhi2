package ru.platezhi2.app.ui.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.platezhi2.app.ui.AppViewModelProvider
import ru.platezhi2.app.ui.theme.LocalAppPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    onBack: () -> Unit,
    viewModel: DataViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val cloudFolder by viewModel.cloudFolderUri.collectAsState()
    val backups by viewModel.backups.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportJson) }

    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImport = uri }

    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setCloudFolder) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Данные", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfoCard(
                title = "Экспорт в файл",
                subtitle = "Сохраните все платежи в файл на устройстве",
                icon = Icons.Filled.Download,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { createJson.launch("platezhi2-backup.json") },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("JSON") }
                    OutlinedButton(
                        onClick = { createCsv.launch("platezhi2-backup.csv") },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("CSV") }
                }
            }

            InfoCard(
                title = "Импорт из файла",
                subtitle = "Загрузите платежи из файла JSON или CSV",
                icon = Icons.Filled.Upload,
            ) {
                Button(
                    onClick = {
                        openDocument.launch(
                            arrayOf(
                                "application/json",
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/octet-stream",
                                "*/*",
                            )
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Выбрать файл") }
            }

            InfoCard(
                title = "Облачная папка",
                subtitle = cloudFolder?.let { "Папка: ${prettyFolder(it)}" }
                    ?: "Выберите папку — файлы будут сохраняться в неё (Google Drive, Яндекс.Диск и др.)",
                icon = Icons.Filled.CloudUpload,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { openTree.launch(null) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (cloudFolder == null) "Выбрать папку" else "Изменить папку")
                    }
                    if (cloudFolder != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.exportToCloud(json = true) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("Копия JSON") }
                            Button(
                                onClick = { viewModel.exportToCloud(json = false) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("Копия CSV") }
                        }
                        TextButton(onClick = { viewModel.clearCloudFolder() }) {
                            Text("Отвязать папку")
                        }
                    }
                }
            }

            InfoCard(
                title = "Импорт из облака",
                subtitle = if (cloudFolder == null) {
                    "Сначала выберите облачную папку с резервными копиями"
                } else {
                    "Выберите резервную копию из облачной папки"
                },
                icon = Icons.Filled.Download,
            ) {
                Button(
                    onClick = { viewModel.loadBackups() },
                    enabled = !busy && cloudFolder != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Показать копии") }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (backups.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBackups() },
            title = { Text("Резервные копии") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    backups.forEach { backup ->
                        TextButton(
                            onClick = {
                                viewModel.dismissBackups()
                                pendingImport = backup.uri
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(backup.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissBackups() }) { Text("Отмена") }
            },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Импорт платежей") },
            text = { Text("Заменить все текущие платежи данными из файла или добавить их к существующим?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFile(uri, replaceAll = true)
                    pendingImport = null
                }) { Text("Заменить", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.importFile(uri, replaceAll = false)
                        pendingImport = null
                    }) { Text("Добавить") }
                    TextButton(onClick = { pendingImport = null }) { Text("Отмена") }
                }
            },
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = LocalAppPalette.current.accent)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

private fun prettyFolder(uriString: String): String {
    val uri = Uri.parse(uriString)
    val last = uri.lastPathSegment ?: return uriString
    return Uri.decode(last).substringAfterLast(':').substringAfterLast('/')
}
