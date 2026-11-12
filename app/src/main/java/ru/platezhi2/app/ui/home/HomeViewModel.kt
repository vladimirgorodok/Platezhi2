package ru.platezhi2.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.reminders.Reminders

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val payments: StateFlow<List<Payment>> = container.paymentRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePaid(payment: Payment) {
        viewModelScope.launch {
            val newPaid = !payment.paid
            container.paymentRepository.setPaid(payment.id, newPaid)
            if (newPaid) {
                Reminders.cancel(container.appContext, payment)
            } else {
                Reminders.schedule(container.appContext, payment.copy(paid = false))
            }
        }
    }

    fun delete(payment: Payment) {
        viewModelScope.launch {
            Reminders.cancel(container.appContext, payment)
            container.paymentRepository.delete(payment)
        }
    }
}
