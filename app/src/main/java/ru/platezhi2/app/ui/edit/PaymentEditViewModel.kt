package ru.platezhi2.app.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.core.DueDates
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.AppIcons
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import ru.platezhi2.app.reminders.Reminders
import java.time.LocalDate

data class EditState(
    val id: Long = 0L,
    val title: String = "",
    val amountText: String = "",
    val dueDay: Int = LocalDate.now().dayOfMonth,
    val dueMonth: Int = LocalDate.now().monthValue,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val icon: String = AppIcons.DEFAULT,
    val imagePath: String? = null,
    val note: String = "",
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 1,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val createdAt: Long = 0L,
    val loaded: Boolean = false,
    val iconBusy: Boolean = false,
    val iconMessage: String? = null,
    val payments: List<Payment> = emptyList(),
)

class PaymentEditViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cardId: Long = savedStateHandle.get<Long>("id") ?: 0L
    val isNew: Boolean get() = cardId == 0L

    private val _state = MutableStateFlow(EditState(id = cardId))
    val state: StateFlow<EditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultDays = container.settingsRepository.settings.first().defaultReminderDaysBefore
            val existing = if (cardId != 0L) container.paymentRepository.getCard(cardId) else null
            val history = if (cardId != 0L) container.paymentRepository.getPaymentsFor(cardId) else emptyList()
            _state.update { current ->
                if (existing != null) current.fromCard(existing).copy(payments = history)
                else current.copy(reminderDaysBefore = defaultDays, loaded = true)
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }
    fun setAmount(value: String) = _state.update { it.copy(amountText = value) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setIcon(value: String) = _state.update { it.copy(icon = value, imagePath = null, iconMessage = null) }
    fun setRecurrence(value: Recurrence) = _state.update { it.copy(recurrence = value) }
    fun setDueDay(value: Int) = _state.update { it.copy(dueDay = value.coerceIn(1, 31)) }
    fun setDueMonth(value: Int) = _state.update { it.copy(dueMonth = value.coerceIn(1, 12)) }
    fun setReminderEnabled(value: Boolean) = _state.update { it.copy(reminderEnabled = value) }
    fun setReminderDays(days: Int) = _state.update { it.copy(reminderDaysBefore = days.coerceIn(0, 60)) }
    fun setReminderTime(hour: Int, minute: Int) =
        _state.update { it.copy(reminderHour = hour, reminderMinute = minute) }

    fun clearImage() = _state.update { it.copy(imagePath = null, iconMessage = null) }

    fun importIcon(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(iconBusy = true, iconMessage = null) }
            val path = container.imageStore.importImage(uri)
            _state.update {
                it.copy(
                    iconBusy = false,
                    imagePath = path ?: it.imagePath,
                    iconMessage = if (path == null) "Не удалось загрузить изображение" else "Иконка обновлена",
                )
            }
        }
    }

    // ---- Оплаты ----

    fun addPayment(amountMinor: Long, paidAtEpochDay: Long) {
        val s = _state.value
        if (s.id == 0L) return
        val card = s.toCard()
        val paidAt = LocalDate.ofEpochDay(paidAtEpochDay)
        val period = DueDates.periodForPaidDate(card, paidAt).toEpochDay()
        viewModelScope.launch {
            container.paymentRepository.addPayment(
                Payment(
                    cardId = s.id,
                    periodEpochDay = period,
                    paidAtEpochDay = paidAtEpochDay,
                    amountMinor = amountMinor,
                )
            )
            reloadPayments()
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            container.paymentRepository.deletePayment(payment)
            reloadPayments()
        }
    }

    private suspend fun reloadPayments() {
        val id = _state.value.id
        val history = container.paymentRepository.getPaymentsFor(id)
        _state.update { it.copy(payments = history) }
    }

    // ---- Сохранение ----

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val card = s.toCard()
        viewModelScope.launch {
            val newId = container.paymentRepository.saveCard(card)
            val saved = card.copy(id = if (newId != 0L) newId else s.id)
            val history = if (saved.id != 0L) container.paymentRepository.getPaymentsFor(saved.id) else emptyList()
            if (saved.reminderEnabled) {
                Reminders.schedule(container.appContext, saved, history)
            } else {
                Reminders.cancel(container.appContext, saved)
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.id
        if (id == 0L) {
            onDone()
            return
        }
        viewModelScope.launch {
            val card = container.paymentRepository.getCard(id)
            if (card != null) {
                Reminders.cancel(container.appContext, card)
                container.paymentRepository.deleteCard(card)
            }
            onDone()
        }
    }

    private fun EditState.toCard(): Card = Card(
        id = id,
        title = title.trim().ifBlank { "Без названия" },
        amountMinor = Format.parseAmountToMinor(amountText) ?: 0L,
        dueDay = dueDay,
        dueMonth = dueMonth,
        recurrence = recurrence,
        icon = AppIcons.normalize(icon),
        imagePath = imagePath,
        note = note.trim(),
        reminderEnabled = reminderEnabled,
        reminderDaysBefore = reminderDaysBefore,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        createdAt = createdAt,
    )

    private fun EditState.fromCard(card: Card): EditState = copy(
        id = card.id,
        title = card.title,
        amountText = Format.amountInput(card.amountMinor),
        dueDay = card.dueDay,
        dueMonth = card.dueMonth,
        recurrence = card.recurrence,
        icon = card.icon,
        imagePath = card.imagePath,
        note = card.note,
        reminderEnabled = card.reminderEnabled,
        reminderDaysBefore = card.reminderDaysBefore,
        reminderHour = card.reminderHour,
        reminderMinute = card.reminderMinute,
        createdAt = card.createdAt,
        loaded = true,
    )
}
