package ru.platezhi2.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.ui.AppViewModelProvider
import ru.platezhi2.app.ui.components.PaymentIcon
import ru.platezhi2.app.ui.theme.Coral
import ru.platezhi2.app.ui.theme.Mint
import ru.platezhi2.app.ui.theme.Violet
import ru.platezhi2.app.ui.theme.VioletDark
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
    val overdue = state.rows.filter { !it.due.paid && it.due.overdue }.sortedBy { it.due.date }
    val upcoming = state.rows.filter { !it.due.paid && !it.due.overdue }.sortedBy { it.due.date }
    val paidRows = state.rows.filter { it.due.paid }
        .sortedByDescending { it.periodPayment?.paidAtEpochDay ?: it.due.date.toEpochDay() }

    var cardToDelete by remember { mutableStateOf<Card?>(null) }

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
                    item { SectionHeader("Просрочено", Coral) }
                    items(overdue, key = { it.card.id }) { row ->
                        PaymentRow(
                            row = row,
                            onClick = { onEditPayment(row.card.id) },
                            onTogglePaid = { viewModel.togglePaid(row) },
                            onDelete = { cardToDelete = row.card },
                        )
                    }
                }

                if (upcoming.isNotEmpty()) {
                    item { SectionHeader("К оплате", Violet) }
                    items(upcoming, key = { it.card.id }) { row ->
                        PaymentRow(
                            row = row,
                            onClick = { onEditPayment(row.card.id) },
                            onTogglePaid = { viewModel.togglePaid(row) },
                            onDelete = { cardToDelete = row.card },
                        )
                    }
                }

                if (paidRows.isNotEmpty()) {
                    item { SectionHeader("Оплачено", Mint) }
                    items(paidRows, key = { it.card.id }) { row ->
                        PaymentRow(
                            row = row,
                            onClick = { onEditPayment(row.card.id) },
                            onTogglePaid = { viewModel.togglePaid(row) },
                            onDelete = { cardToDelete = row.card },
                        )
                    }
                }
            }
        }
    }

    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("Удалить платёж?") },
            text = {
                Text(
                    "Карточка «${card.title.ifBlank { "Без названия" }}» и вся история её оплат " +
                        "будут удалены без возможности восстановления."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(card)
                    cardToDelete = null
                }) { Text("Удалить", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) { Text("Отмена") }
            },
        )
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
    val total = totalUnpaid + totalPaid
    val progress = if (total > 0) totalPaid.toFloat() / total.toFloat() else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Violet, VioletDark)))
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
    ) {
        Column {
            // ---- Фильтр по месяцам ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Предыдущий месяц",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = Format.month(month),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Следующий месяц",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!isCurrentMonth) {
                    TextButton(
                        onClick = onCurrentMonth,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "Текущий",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = "Осталось оплатить",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Format.money(totalUnpaid),
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Mint,
                    trackColor = Color.White.copy(alpha = 0.22f),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$unpaidCount к оплате",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (overdueCount > 0) {
                        Text(
                            text = "$overdueCount просрочено",
                            color = Color(0xFFFFD9D8),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            text = "Оплачено ${Format.money(totalPaid)}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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
            .padding(top = 10.dp, bottom = 2.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentRow(
    row: CardRow,
    onClick: () -> Unit,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val card = row.card
    val info = row.due
    val paidAt = row.periodPayment?.paidAtEpochDay ?: row.lastPayment?.paidAtEpochDay
    val dueText = if (info.paid && paidAt != null) {
        "оплачено ${Format.dayMonth(LocalDate.ofEpochDay(paidAt))}"
    } else {
        Format.relativeDue(info.daysLeft, info.paid)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaymentIcon(imagePath = card.imagePath, icon = card.icon, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = card.recurrence.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${Format.dayMonth(info.date)} · $dueText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (info.overdue) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (info.overdue) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Format.money(card.amountMinor),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (info.paid) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePaid, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (info.paid) Icons.Filled.CheckCircle
                            else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (info.paid) "Снять отметку об оплате" else "Отметить оплату",
                            tint = if (info.paid) Mint else MaterialTheme.colorScheme.outline,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Ещё",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Изменить") },
                                onClick = { menuOpen = false; onClick() },
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                onClick = { menuOpen = false; onDelete() },
                            )
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
