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
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import ru.platezhi2.app.reminders.Reminders

data class EditState(
    val id: Long = 0L,
    val title: String = "",
    val amountText: String = "",
    val dueDateEpochDay: Long = Format.todayEpochDay(),
    val recurrence: Recurrence = Recurrence.NONE,
    val category: String = "other",
    val logoPath: String? = null,
    val note: String = "",
    val paid: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 1,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val createdAt: Long = 0L,
    val loaded: Boolean = false,
    val logoBusy: Boolean = false,
    val logoMessage: String? = null,
)

class PaymentEditViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val paymentId: Long = savedStateHandle.get<Long>("id") ?: 0L
    val isNew: Boolean get() = paymentId == 0L

    private val _state = MutableStateFlow(EditState(id = paymentId))
    val state: StateFlow<EditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultDays = container.settingsRepository.settings.first().defaultReminderDaysBefore
            val existing = if (paymentId != 0L) container.paymentRepository.get(paymentId) else null
            _state.update { current ->
                if (existing != null) current.fromPayment(existing)
                else current.copy(reminderDaysBefore = defaultDays, loaded = true)
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }
    fun setAmount(value: String) = _state.update { it.copy(amountText = value) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setCategory(key: String) = _state.update { it.copy(category = key) }
    fun setRecurrence(value: Recurrence) = _state.update { it.copy(recurrence = value) }
    fun setPaid(value: Boolean) = _state.update { it.copy(paid = value) }
    fun setDueDate(epochDay: Long) = _state.update { it.copy(dueDateEpochDay = epochDay) }
    fun setReminderEnabled(value: Boolean) = _state.update { it.copy(reminderEnabled = value) }
    fun setReminderDays(days: Int) = _state.update { it.copy(reminderDaysBefore = days.coerceIn(0, 60)) }
    fun setReminderTime(hour: Int, minute: Int) =
        _state.update { it.copy(reminderHour = hour, reminderMinute = minute) }

    fun clearLogo() = _state.update { it.copy(logoPath = null, logoMessage = null) }

    fun importLogo(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(logoBusy = true, logoMessage = null) }
            val path = container.logoRepository.importImage(uri)
            _state.update {
                it.copy(
                    logoBusy = false,
                    logoPath = path ?: it.logoPath,
                    logoMessage = if (path == null) "Не удалось загрузить изображение" else "Логотип обновлён",
                )
            }
        }
    }

    /** Ищет логотип в интернете; при неудаче подставляет монограмму. */
    fun searchLogo() {
        val name = _state.value.title.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(logoMessage = "Сначала введите название платежа") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(logoBusy = true, logoMessage = "Ищу логотип…") }
            val online = container.logoRepository.searchOnline(name)
            if (online != null) {
                _state.update { it.copy(logoBusy = false, logoPath = online, logoMessage = "Логотип найден") }
            } else {
                val monogram = container.logoRepository.monogram(name)
                _state.update {
                    it.copy(
                        logoBusy = false,
                        logoPath = monogram,
                        logoMessage = "Логотип не найден — добавлена монограмма",
                    )
                }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val amountMinor = Format.parseAmountToMinor(s.amountText) ?: 0L
        val payment = Payment(
            id = s.id,
            title = s.title.trim().ifBlank { "Без названия" },
            amountMinor = amountMinor,
            currency = "RUB",
            dueDateEpochDay = s.dueDateEpochDay,
            recurrence = s.recurrence,
            category = s.category,
            logoPath = s.logoPath,
            note = s.note.trim(),
            paid = s.paid,
            reminderEnabled = s.reminderEnabled,
            reminderDaysBefore = s.reminderDaysBefore,
            reminderHour = s.reminderHour,
            reminderMinute = s.reminderMinute,
            createdAt = s.createdAt,
        )
        viewModelScope.launch {
            val newId = container.paymentRepository.save(payment)
            val saved = payment.copy(id = if (newId != 0L) newId else s.id)
            if (saved.reminderEnabled && !saved.paid) {
                Reminders.schedule(container.appContext, saved)
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
            val payment = container.paymentRepository.get(id)
            if (payment != null) {
                Reminders.cancel(container.appContext, payment)
                container.paymentRepository.delete(payment)
            }
            onDone()
        }
    }

    private fun EditState.fromPayment(p: Payment): EditState = copy(
        id = p.id,
        title = p.title,
        amountText = amountToText(p.amountMinor),
        dueDateEpochDay = p.dueDateEpochDay,
        recurrence = p.recurrence,
        category = p.category,
        logoPath = p.logoPath,
        note = p.note,
        paid = p.paid,
        reminderEnabled = p.reminderEnabled,
        reminderDaysBefore = p.reminderDaysBefore,
        reminderHour = p.reminderHour,
        reminderMinute = p.reminderMinute,
        createdAt = p.createdAt,
        loaded = true,
    )

    private fun amountToText(minor: Long): String {
        if (minor == 0L) return ""
        val rub = minor / 100
        val kop = minor % 100
        return if (kop == 0L) rub.toString() else "$rub,${kop.toString().padStart(2, '0')}"
    }
}
