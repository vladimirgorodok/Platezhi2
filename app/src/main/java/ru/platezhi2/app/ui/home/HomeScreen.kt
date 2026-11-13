package ru.platezhi2.app.ui.home

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.ui.AppViewModelProvider
import ru.platezhi2.app.ui.components.PaymentIcon
import ru.platezhi2.app.ui.theme.LocalAppPalette
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddPayment: () -> Unit,
    onEditPayment: (Long) -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val overdue = state.rows.filter { !it.due.paid && it.due.overdue }.sortedBy { it.due.date }
    val upcoming = state.rows.filter { !it.due.paid && !it.due.overdue }.sortedBy { it.due.date }
    val paidRows = state.rows.filter { it.due.paid }
        .sortedByDescending { it.periodPayment?.paidAtEpochDay ?: it.due.date.toEpochDay() }

    // Раскрытая строка платежа: дата и сумма оплаты.
    var expandedCardId by remember { mutableStateOf<Long?>(null) }
    var editPaidAt by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var editAmount by remember { mutableStateOf("") }

    val renderRow: @Composable (CardRow) -> Unit = { row ->
        PaymentRow(
            row = row,
            expanded = expandedCardId == row.card.id,
            editPaidAt = editPaidAt,
            editAmount = editAmount,
            onToggleExpand = {
                if (expandedCardId == row.card.id) {
                    expandedCardId = null
                } else {
                    expandedCardId = row.card.id
                    editPaidAt = row.periodPayment?.paidAtEpochDay ?: LocalDate.now().toEpochDay()
                    editAmount = Format.amountInput(
                        row.periodPayment?.amountMinor ?: row.card.amountMinor
                    )
                }
            },
            onEditPaidAt = { editPaidAt = it },
            onEditAmount = { editAmount = it },
            onOpenCard = { onEditPayment(row.card.id) },
            onSavePayment = {
                viewModel.savePayment(
                    row = row,
                    paidAtEpochDay = editPaidAt,
                    amountMinor = Format.parseAmountToMinor(editAmount) ?: 0L,
                )
                expandedCardId = null
            },
            onClearPayment = {
                viewModel.clearPayment(row)
                expandedCardId = null
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Платежи", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    IconButton(onClick = onAddPayment) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить платёж")
                    }
                    IconButton(onClick = onOpenData) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Данные")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
    ) { padding ->
        if (state.cardCount == 0) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OverviewBanner(
                        month = state.month,
                        isCurrentMonth = state.isCurrentMonth,
                        totalUnpaid = state.totalUnpaid,
                        totalPaid = state.totalPaid,
                        unpaidCount = state.unpaidCount,
                        overdueCount = state.overdueCount,
                        onPreviousMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        onCurrentMonth = viewModel::currentMonth,
                    )
                }

                if (state.rows.isEmpty()) {
                    item { EmptyMonth(isCurrentMonth = state.isCurrentMonth) }
                }

                if (overdue.isNotEmpty()) {
                    item { SectionHeader("Просрочено", palette.danger) }
                    items(overdue, key = { it.card.id }) { row -> renderRow(row) }
                }

                if (upcoming.isNotEmpty()) {
                    item { SectionHeader("К оплате", palette.accent) }
                    items(upcoming, key = { it.card.id }) { row -> renderRow(row) }
                }

                if (paidRows.isNotEmpty()) {
                    item { SectionHeader("Оплачено", palette.success) }
                    items(paidRows, key = { it.card.id }) { row -> renderRow(row) }
                }
            }
        }
    }
}

/** Цвет состояния платежа: рамка строки и акценты. */
@Composable
private fun statusColor(row: CardRow): Color {
    val palette = LocalAppPalette.current
    return when {
        row.due.paid -> palette.success
        row.due.overdue -> palette.danger
        else -> palette.accent
    }
}

@Composable
private fun OverviewBanner(
    month: YearMonth,
    isCurrentMonth: Boolean,
    totalUnpaid: Long,
    totalPaid: Long,
    unpaidCount: Int,
    overdueCount: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
) {
    val palette = LocalAppPalette.current
    val total = totalUnpaid + totalPaid
    val progress = if (total > 0) totalPaid.toFloat() / total.toFloat() else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(palette.accent, palette.accentDark)))
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ---- Фильтр по месяцам ----
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Предыдущий месяц",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = Format.month(month),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Следующий месяц",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (!isCurrentMonth) {
                    IconButton(onClick = onCurrentMonth, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.Today,
                            contentDescription = "Текущий месяц",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // ---- Сумма справа от месяца ----
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Осталось оплатить",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = Format.money(totalUnpaid),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = palette.success,
                trackColor = Color.White.copy(alpha = 0.22f),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$unpaidCount к оплате",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (overdueCount > 0) {
                    Text(
                        text = "$overdueCount просрочено",
                        color = Color(0xFFFFD9D8),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = "Оплачено ${Format.money(totalPaid)}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun PaymentRow(
    row: CardRow,
    expanded: Boolean,
    editPaidAt: Long,
    editAmount: String,
    onToggleExpand: () -> Unit,
    onEditPaidAt: (Long) -> Unit,
    onEditAmount: (String) -> Unit,
    onOpenCard: () -> Unit,
    onSavePayment: () -> Unit,
    onClearPayment: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val card = row.card
    val info = row.due
    val accent = statusColor(row)
    val paidAt = row.periodPayment?.paidAtEpochDay ?: row.lastPayment?.paidAtEpochDay
    val dueText = if (info.paid && paidAt != null) {
        "оплачено ${Format.dayMonth(LocalDate.ofEpochDay(paidAt))}"
    } else {
        Format.relativeDue(info.daysLeft, info.paid)
    }
    val shape = RoundedCornerShape(18.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        // Индикация состояния оплаты — рамка вокруг платежа.
        border = BorderStroke(2.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaymentIcon(imagePath = card.imagePath, icon = card.icon, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title.ifBlank { "Без названия" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = card.recurrence.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${Format.dayMonth(info.date)} · $dueText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (info.overdue) palette.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (info.overdue) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = Format.money(card.amountMinor),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (info.paid) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                // Шестерёнка — открыть карточку платежа.
                IconButton(onClick = onOpenCard, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Открыть карточку платежа",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Дата",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                val current = LocalDate.ofEpochDay(editPaidAt)
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        onEditPaidAt(LocalDate.of(year, month + 1, day).toEpochDay())
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth,
                                ).show()
                            },
                        ) {
                            Text(Format.date(LocalDate.ofEpochDay(editPaidAt)))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Сумма",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = editAmount,
                            onValueChange = onEditAmount,
                            singleLine = true,
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(150.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (info.paid) {
                            TextButton(onClick = onClearPayment) {
                                Text("Отменить оплату", color = palette.danger)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(onClick = onSavePayment) {
                            Text(if (info.paid) "Сохранить" else "Оплатить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMonth(isCurrentMonth: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🗓", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "В этом месяце платежей нет",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isCurrentMonth) {
                "Добавьте платёж кнопкой «+» вверху экрана."
            } else {
                "Переключите месяц стрелками вверху."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "🧾", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Пока нет платежей",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Нажмите «+» вверху экрана, чтобы создать первый платёж и настроить напоминание.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
