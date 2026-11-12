package ru.platezhi2.app.ui.edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.AppIcons
import ru.platezhi2.app.data.model.Recurrence
import ru.platezhi2.app.ui.AppViewModelProvider
import ru.platezhi2.app.ui.components.PaymentIcon
import ru.platezhi2.app.ui.theme.Coral
import ru.platezhi2.app.ui.theme.Violet
import java.time.LocalDate

private val MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentEditScreen(
    onBack: () -> Unit,
    viewModel: PaymentEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddPayment by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentDate by remember { mutableLongStateOf(Format.todayEpochDay()) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) viewModel.importIcon(uri) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isNew) "Новый платёж" else "Карточка платежа",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = Coral)
                        }
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
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Иконка ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PaymentIcon(imagePath = state.imagePath, icon = state.icon, size = 72.dp)
                    Spacer(Modifier.height(14.dp))
                    IconGrid(
                        selected = if (state.imagePath == null) state.icon else null,
                        onSelect = viewModel::setIcon,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                            if (state.iconBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(
                                    Icons.Filled.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Вставить из файла")
                        }
                        if (state.imagePath != null) {
                            TextButton(onClick = { viewModel.clearImage() }) { Text("Убрать") }
                        }
                    }
                    state.iconMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- Основное ----
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Название") },
                placeholder = { Text("Например, Билайн") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text("Сумма") },
                placeholder = { Text("0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Срок оплаты ----
            FieldLabel("Число месяца, до которого нужно оплатить")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((1..31).toList()) { day ->
                    FilterChip(
                        selected = state.dueDay == day,
                        onClick = { viewModel.setDueDay(day) },
                        label = { Text(day.toString()) },
                    )
                }
            }

            // ---- Периодичность ----
            FieldLabel("Периодичность")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Recurrence.entries.toList()) { item ->
                    FilterChip(
                        selected = state.recurrence == item,
                        onClick = { viewModel.setRecurrence(item) },
                        label = { Text(item.title) },
                    )
                }
            }

            if (state.recurrence == Recurrence.YEARLY) {
                FieldLabel("Месяц")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MONTHS.withIndex().toList()) { (index, name) ->
                        FilterChip(
                            selected = state.dueMonth == index + 1,
                            onClick = { viewModel.setDueMonth(index + 1) },
                            label = { Text(name) },
                        )
                    }
                }
            }

            // ---- Заметка ----
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("Заметка") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- История оплат ----
            if (!viewModel.isNew) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("История оплат", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (state.payments.isEmpty()) "Оплат пока нет"
                                    else "Всего оплат: ${state.payments.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    paymentAmount = state.amountText
                                    paymentDate = Format.todayEpochDay()
                                    showAddPayment = true
                                },
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Оплата")
                            }
                        }
                        state.payments.sortedByDescending { it.paidAtEpochDay }.forEach { payment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Format.date(payment.paidAtEpochDay),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "за ${Format.dayMonth(LocalDate.ofEpochDay(payment.periodEpochDay))}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = Format.money(payment.amountMinor),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                IconButton(onClick = { viewModel.deletePayment(payment) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Удалить оплату", tint = Coral)
                                }
                            }
                        }
                    }
                }
            }

            // ---- Напоминание ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Напоминание", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Уведомление перед числом оплаты",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.reminderEnabled,
                            onCheckedChange = viewModel::setReminderEnabled,
                        )
                    }
                    if (state.reminderEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "За сколько дней",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            StepperButton("−") {
                                viewModel.setReminderDays(state.reminderDaysBefore - 1)
                            }
                            Text(
                                text = state.reminderDaysBefore.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .width(28.dp),
                            )
                            StepperButton("+") {
                                viewModel.setReminderDays(state.reminderDaysBefore + 1)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Время",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute -> viewModel.setReminderTime(hour, minute) },
                                        state.reminderHour,
                                        state.reminderMinute,
                                        true,
                                    ).show()
                                },
                            ) {
                                Text("%02d:%02d".format(state.reminderHour, state.reminderMinute))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Сохранить", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showAddPayment) {
        AlertDialog(
            onDismissRequest = { showAddPayment = false },
            title = { Text("Новая оплата") },
            text = {
                Column {
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text("Сумма") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val current = LocalDate.ofEpochDay(paymentDate)
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    paymentDate = LocalDate.of(year, month + 1, day).toEpochDay()
                                },
                                current.year,
                                current.monthValue - 1,
                                current.dayOfMonth,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Дата: ${Format.dayMonth(LocalDate.ofEpochDay(paymentDate))}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addPayment(Format.parseAmountToMinor(paymentAmount) ?: 0L, paymentDate)
                    showAddPayment = false
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPayment = false }) { Text("Отмена") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить платёж?") },
            text = { Text("Карточка и вся история её оплат будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onBack)
                }) { Text("Удалить", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun IconGrid(
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcons.all.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { icon ->
                    IconChoice(
                        icon = icon,
                        selected = selected == icon,
                        onClick = { onSelect(icon) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IconChoice(icon: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Violet else Color.Transparent
    val bg = if (selected) Violet.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = icon, fontSize = 19.sp)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Violet.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(symbol, style = MaterialTheme.typography.titleLarge, color = Violet)
        }
    }
}
