package ru.platezhi2.app.ui.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import ru.platezhi2.app.data.archive.ArchiveFiles
import ru.platezhi2.app.data.cloud.CloudSyncCoordinator
import ru.platezhi2.app.data.settings.AppSettings
import ru.platezhi2.app.ui.AppViewModelProvider
import ru.platezhi2.app.ui.theme.LocalAppPalette
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val syncStampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    onBack: () -> Unit,
    onPickCloudFolder: () -> Unit,
    viewModel: DataViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val archiveInfo by viewModel.archiveInfo.collectAsState()
    val archivePreviousInfo by viewModel.archivePreviousInfo.collectAsState()
    val cloudSettings by viewModel.cloudSettings.collectAsState()
    val cloudStatus by viewModel.cloudStatus.collectAsState()
    val login by viewModel.login.collectAsState()
    val password by viewModel.password.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmCloudPull by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmRestorePrevious by remember { mutableStateOf(false) }

    // Копия могла обновиться при запуске приложения — показываем свежие сведения.
    LaunchedEffect(Unit) { viewModel.refreshArchiveInfo() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(cloudStatus.message) {
        cloudStatus.message?.let { snackbarHostState.showSnackbar(it) }
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
            CloudCard(
                settings = cloudSettings,
                login = login,
                password = password,
                status = cloudStatus,
                busy = busy,
                onLogin = viewModel::setLogin,
                onPassword = viewModel::setPassword,
                onCheck = viewModel::checkCloudConnection,
                onPickFolder = {
                    viewModel.flushCredentials()
                    onPickCloudFolder()
                },
                onAutoSync = viewModel::setAutoSync,
                onKeepCopies = viewModel::setKeepCopies,
                onPush = viewModel::pushToCloud,
                onPull = { confirmCloudPull = true },
                onIconsPush = viewModel::pushIconsToCloud,
                onIconsRestore = viewModel::restoreIconsFromCloud,
            )

            ArchiveCard(
                info = archiveInfo,
                previousInfo = archivePreviousInfo,
                busy = busy,
                onCreate = viewModel::createArchive,
                onRestore = { confirmRestore = true },
                onRestorePrevious = { confirmRestorePrevious = true },
            )

            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmCloudPull) {
        AlertDialog(
            onDismissRequest = { confirmCloudPull = false },
            title = { Text("Загрузка из облака") },
            text = {
                Text(
                    "Текущие платежи на этом устройстве будут заменены самой свежей копией " +
                        "из Яндекс Диска. Значки-картинки подтянутся автоматически.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloudPull = false
                    viewModel.pullFromCloud()
                }) { Text("Загрузить", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloudPull = false }) { Text("Отмена") }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Вернуть из копии") },
            text = {
                Text(
                    "Текущие платежи и значки на этом устройстве будут заменены копией" +
                        (archiveInfo?.let { " от ${formatArchiveStamp(it.at)}" } ?: "") +
                        ". Состояние до восстановления останется на устройстве.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    viewModel.restoreArchive()
                }) { Text("Вернуть", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Отмена") }
            },
        )
    }

    if (confirmRestorePrevious) {
        AlertDialog(
            onDismissRequest = { confirmRestorePrevious = false },
            title = { Text("Вернуть состояние до восстановления") },
            text = { Text("Вернуть данные, которые были на устройстве перед последним восстановлением из копии.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestorePrevious = false
                    viewModel.restorePreviousArchive()
                }) { Text("Вернуть", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestorePrevious = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun CloudCard(
    settings: AppSettings,
    login: String,
    password: String,
    status: CloudSyncCoordinator.Status,
    busy: Boolean,
    onLogin: (String) -> Unit,
    onPassword: (String) -> Unit,
    onCheck: () -> Unit,
    onPickFolder: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onKeepCopies: (Int) -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onIconsPush: () -> Unit,
    onIconsRestore: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val working = busy || status.busy

    InfoCard(
        title = "Яндекс Диск",
        subtitle = "Одно и то же на всех ваших устройствах: платежи и значки-картинки",
        icon = Icons.Filled.Cloud,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = login,
                onValueChange = onLogin,
                label = { Text("Логин Яндекс") },
                placeholder = { Text("например, ivan.ivanov") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPassword,
                label = { Text("Пароль приложения") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Пароль приложения — не обычный пароль от Яндекс ID. Тип — " +
                    "«WebDAV-клиент для Яндекс Диска». Новый пароль начинает работать " +
                    "через 2–3 часа.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { openUrl(context, "https://id.yandex.ru/security/app-passwords") },
                    modifier = Modifier.weight(1f),
                ) { Text("Создать пароль") }
                Button(
                    onClick = onCheck,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) { Text("Проверить") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Папка на Диске", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = settings.cloudFolder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onPickFolder, enabled = !working) { Text("Выбрать") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Автосинхронизация", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "При запуске — свежие данные из облака, изменения уходят " +
                            "в облако сами. Без интернета — при следующем запуске.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.autoSync, onCheckedChange = onAutoSync, enabled = !working)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Хранить копий в облаке",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onKeepCopies(settings.cloudKeepCopies - 1) },
                    enabled = !working && settings.cloudKeepCopies > 1,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Меньше копий")
                }
                Text("${settings.cloudKeepCopies}", fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { onKeepCopies(settings.cloudKeepCopies + 1) },
                    enabled = !working && settings.cloudKeepCopies < 20,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Больше копий")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPush,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) { Text("Выгрузить") }
                OutlinedButton(
                    onClick = onPull,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) { Text("Загрузить") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Отдельный архив значков",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Значки-картинки переносятся отдельным архивом, поэтому файл " +
                    "с платежами остаётся лёгким и уходит на Диск мгновенно.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onIconsPush,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) { Text("Сохранить") }
                OutlinedButton(
                    onClick = onIconsRestore,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) { Text("Подтянуть") }
            }

            Text(
                text = if (settings.cloudLastSyncAt > 0L) {
                    "Последний обмен: ${formatSyncStamp(settings.cloudLastSyncAt)}"
                } else {
                    "Обмен ещё не выполнялся"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.cloudDirty) {
                Text(
                    text = "Есть невыгруженные изменения — уйдут в облако при следующем обмене.",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent,
                )
            }
            status.message?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status.failed) palette.danger else palette.success,
                )
            }
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Обмен с Яндекс Диском…", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ArchiveCard(
    info: ArchiveFiles.Info?,
    previousInfo: ArchiveFiles.Info?,
    busy: Boolean,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
    onRestorePrevious: () -> Unit,
) {
    InfoCard(
        title = "Архив на устройстве",
        subtitle = "Копия платежей и значков на этом телефоне",
        icon = Icons.Filled.SettingsBackupRestore,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Копия обновляется сама при запуске приложения и перед загрузкой " +
                    "из облака. Если что-то пойдёт не так, данные можно вернуть из неё — " +
                    "даже без интернета.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = info?.let { "Копия: ${formatArchiveSize(it.bytes)}, ${formatArchiveStamp(it.at)}" }
                    ?: "Копии пока нет",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCreate,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Сделать копию") }
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !busy && info != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Вернуть из копии") }
            }
            if (previousInfo != null) {
                TextButton(onClick = onRestorePrevious, enabled = !busy) {
                    Text("Вернуть состояние до восстановления")
                }
            }
        }
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

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun formatSyncStamp(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        .format(syncStampFormat)

private fun formatArchiveStamp(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        .format(syncStampFormat)

private fun formatArchiveSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f МБ", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} КБ"
    else -> "$bytes Б"
}
