package ru.platezhi2.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import ru.platezhi2.app.reminders.Reminders
import java.time.LocalDate

/** Строка главного экрана: карточка, её срок и последняя оплата. */
data class CardRow(
    val card: Card,
    val due: DueInfo,
    val lastPayment: Payment?,
)

data class HomeState(
    val rows: List<CardRow> = emptyList(),
    val totalUnpaid: Long = 0L,
    val totalPaid: Long = 0L,
    val unpaidCount: Int = 0,
    val overdueCount: Int = 0,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        container.paymentRepository.observeCards(),
        container.paymentRepository.observePayments(),
    ) { cards, payments ->
        buildState(cards, payments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun buildState(cards: List<Card>, payments: List<Payment>): HomeState {
        val today = LocalDate.now()
        val byCard = payments.groupBy { it.cardId }

        val rows = cards.map { card ->
            val history = byCard[card.id].orEmpty()
            CardRow(
                card = card,
                due = DueDates.info(card, history, today),
                lastPayment = history.maxByOrNull { it.paidAtEpochDay },
            )
        }

        val unpaid = rows.filter { !it.due.paid }
        val paid = rows.filter { it.due.paid }
        val totalPaid = paid.sumOf { row ->
            val period = row.due.date.toEpochDay()
            byCard[row.card.id]?.firstOrNull { it.periodEpochDay == period }?.amountMinor
                ?: row.card.amountMinor
        }

        return HomeState(
            rows = rows,
            totalUnpaid = unpaid.sumOf { it.card.amountMinor },
            totalPaid = totalPaid,
            unpaidCount = unpaid.size,
            overdueCount = unpaid.count { it.due.overdue },
        )
    }

    /** Отметить оплату за текущий период или снять отметку. */
    fun togglePaid(row: CardRow) {
        viewModelScope.launch {
            val repo = container.paymentRepository
            if (row.due.paid) {
                repo.deletePaymentForPeriod(row.card.id, row.due.date.toEpochDay())
                Reminders.schedule(
                    container.appContext,
                    row.card,
                    repo.getPaymentsFor(row.card.id),
                )
            } else {
                repo.addPayment(DueDates.newPayment(row.card))
                Reminders.cancel(container.appContext, row.card)
            }
        }
    }

    fun delete(card: Card) {
        viewModelScope.launch {
            Reminders.cancel(container.appContext, card)
            container.paymentRepository.deleteCard(card)
        }
    }
}
