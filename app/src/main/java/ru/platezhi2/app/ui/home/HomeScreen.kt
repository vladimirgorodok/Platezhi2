package ru.platezhi2.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.Categories
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.ui.AppViewModelProvider
import ru.platezhi2.app.ui.components.LogoAvatar
import ru.platezhi2.app.ui.theme.Coral
import ru.platezhi2.app.ui.theme.Mint
import ru.platezhi2.app.ui.theme.Violet
import ru.platezhi2.app.ui.theme.VioletDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddPayment: () -> Unit,
    onEditPayment: (Long) -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val payments by viewModel.payments.collectAsState()

    val unpaid = payments.filter { !it.paid }
    val paid = payments.filter { it.paid }
    val totalUnpaid = unpaid.sumOf { it.amountMinor }
    val totalPaid = paid.sumOf { it.amountMinor }
    val overdue = unpaid.filter { Format.daysUntil(it.dueDateEpochDay) < 0 }
        .sortedBy { it.dueDateEpochDay }
    val upcoming = unpaid.filter { Format.daysUntil(it.dueDateEpochDay) >= 0 }
        .sortedBy { it.dueDateEpochDay }
    val paidSorted = paid.sortedByDescending { it.dueDateEpochDay }

    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }

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
                    IconButton(onClick = onOpenData) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Данные")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPayment,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Добавить", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        if (payments.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OverviewBanner(
                        totalUnpaid = totalUnpaid,
                        totalPaid = totalPaid,
                        unpaidCount = unpaid.size,
                        overdueCount = overdue.size,
                    )
                }

                if (overdue.isNotEmpty()) {
                    item { SectionHeader("Просрочено", Coral) }
                    items(overdue, key = { it.id }) { payment ->
                        PaymentRow(
                            payment = payment,
                            onClick = { onEditPayment(payment.id) },
                            onTogglePaid = { viewModel.togglePaid(payment) },
                            onDelete = { paymentToDelete = payment },
                        )
                    }
                }

                if (upcoming.isNotEmpty()) {
                    item { SectionHeader("К оплате", Violet) }
                    items(upcoming, key = { it.id }) { payment ->
                        PaymentRow(
                            payment = payment,
                            onClick = { onEditPayment(payment.id) },
                            onTogglePaid = { viewModel.togglePaid(payment) },
                            onDelete = { paymentToDelete = payment },
                        )
                    }
                }

                if (paidSorted.isNotEmpty()) {
                    item { SectionHeader("Оплачено", Mint) }
                    items(paidSorted, key = { it.id }) { payment ->
                        PaymentRow(
                            payment = payment,
                            onClick = { onEditPayment(payment.id) },
                            onTogglePaid = { viewModel.togglePaid(payment) },
                            onDelete = { paymentToDelete = payment },
                        )
                    }
                }
            }
        }
    }

    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("Удалить платёж?") },
            text = { Text("«${payment.title}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(payment)
                    paymentToDelete = null
                }) { Text("Удалить", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun OverviewBanner(
    totalUnpaid: Long,
    totalPaid: Long,
    unpaidCount: Int,
    overdueCount: Int,
) {
    val total = totalUnpaid + totalPaid
    val progress = if (total > 0) totalPaid.toFloat() / total.toFloat() else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Violet, VioletDark)))
            .padding(20.dp),
    ) {
        Column {
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
    payment: Payment,
    onClick: () -> Unit,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val category = Categories.byKey(payment.category)
    val dueText = Format.relativeDue(payment.dueDateEpochDay, payment.paid)
    val overdue = !payment.paid && Format.daysUntil(payment.dueDateEpochDay) < 0

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
            LogoAvatar(
                path = payment.logoPath,
                name = payment.title,
                category = category,
                size = 46.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payment.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${category.emoji} ${category.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${Format.dayMonth(payment.dueDateEpochDay)} · $dueText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Format.money(payment.amountMinor, payment.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (payment.paid) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePaid, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (payment.paid) Icons.Filled.CheckCircle
                            else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (payment.paid) "Отметить неоплаченным" else "Отметить оплаченным",
                            tint = if (payment.paid) Mint else MaterialTheme.colorScheme.outline,
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
            text = "Нажмите «Добавить», чтобы создать первый платёж и настроить напоминание.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
