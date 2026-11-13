package ru.platezhi2.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.core.DueDates
import ru.platezhi2.app.core.DueInfo
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import ru.platezhi2.app.reminders.Reminders
import java.time.LocalDate
import java.time.YearMonth

/** Строка главного экрана: карточка, её срок в выбранном месяце и оплата за этот период. */
data class CardRow(
    val card: Card,
    val due: DueInfo,
    val periodPayment: Payment?,
    val lastPayment: Payment?,
)

data class HomeState(
    val month: YearMonth = YearMonth.now(),
    val cardCount: Int = 0,
    val rows: List<CardRow> = emptyList(),
    val totalUnpaid: Long = 0L,
    val totalPaid: Long = 0L,
    val unpaidCount: Int = 0,
    val overdueCount: Int = 0,
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.now()
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** Выбранный месяц для фильтра и агрегации в верхнем поле. */
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HomeState> = combine(
        container.paymentRepository.observeCards(),
        container.paymentRepository.observePayments(),
        selectedMonth,
    ) { cards, payments, month ->
        buildState(cards, payments, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ---- Выбор месяца ----

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun currentMonth() {
        selectedMonth.value = YearMonth.now()
    }

    private fun buildState(cards: List<Card>, payments: List<Payment>, month: YearMonth): HomeState {
        val today = LocalDate.now()
        val byCard = payments.groupBy { it.cardId }

        val rows = cards.mapNotNull { card ->
            val history = byCard[card.id].orEmpty()
            val period = periodInMonth(card, history, month) ?: return@mapNotNull null
            val periodPayment = history.firstOrNull { it.periodEpochDay == period.toEpochDay() }
            val paid = if (card.recurrence == Recurrence.NONE) {
                periodPayment != null || history.isNotEmpty()
            } else {
                periodPayment != null
            }
            CardRow(
                card = card,
                due = DueInfo(
                    date = period,
                    paid = paid,
                    overdue = !paid && period.isBefore(today),
                    daysLeft = period.toEpochDay() - today.toEpochDay(),
                ),
                periodPayment = periodPayment,
                lastPayment = history.maxByOrNull { it.paidAtEpochDay },
            )
        }

        val unpaid = rows.filter { !it.due.paid }
        val paid = rows.filter { it.due.paid }

        return HomeState(
            month = month,
            cardCount = cards.size,
            rows = rows,
            totalUnpaid = unpaid.sumOf { it.card.amountMinor },
            totalPaid = paid.sumOf { it.periodPayment?.amountMinor ?: it.card.amountMinor },
            unpaidCount = unpaid.size,
            overdueCount = unpaid.count { it.due.overdue },
        )
    }

    /** Срок оплаты карточки внутри выбранного месяца (null — в этом месяце платежа нет). */
    private fun periodInMonth(card: Card, history: List<Payment>, month: YearMonth): LocalDate? {
        if (card.recurrence != Recurrence.NONE) return DueDates.dueInMonth(card, month)
        // Разовый платёж: показываем в месяце его периода, а если оплат ещё нет — в текущем месяце.
        val periods = history.map { LocalDate.ofEpochDay(it.periodEpochDay) }
        periods.firstOrNull { YearMonth.from(it) == month }?.let { return it }
        if (periods.isEmpty() && month == YearMonth.now()) {
            return DueDates.dueInMonth(card, month)
        }
        return null
    }

    /** Оплатить период строки (в выбранном месяце) или сохранить изменённую дату/сумму. */
    fun savePayment(row: CardRow, paidAtEpochDay: Long, amountMinor: Long) {
        viewModelScope.launch {
            val repo = container.paymentRepository
            val existing = row.periodPayment
            repo.savePayment(
                Payment(
                    id = existing?.id ?: 0L,
                    cardId = row.card.id,
                    periodEpochDay = row.due.date.toEpochDay(),
                    paidAtEpochDay = paidAtEpochDay,
                    amountMinor = amountMinor,
                    createdAt = existing?.createdAt ?: 0L,
                )
            )
            Reminders.cancel(container.appContext, row.card)
        }
    }

    /** Снять отметку об оплате за период строки. */
    fun clearPayment(row: CardRow) {
        viewModelScope.launch {
            val repo = container.paymentRepository
            repo.deletePaymentForPeriod(row.card.id, row.due.date.toEpochDay())
            Reminders.schedule(
                container.appContext,
                row.card,
                repo.getPaymentsFor(row.card.id),
            )
        }
    }

    fun delete(card: Card) {
        viewModelScope.launch {
            Reminders.cancel(container.appContext, card)
            container.paymentRepository.deleteCard(card)
        }
    }
}
