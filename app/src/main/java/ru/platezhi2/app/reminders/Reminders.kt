package ru.platezhi2.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.platezhi2.app.core.DueDates
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {

    const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания о платежах",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Уведомления о предстоящих и просроченных платежах" }
        manager.createNotificationChannel(channel)
    }

    fun schedule(context: Context, card: Card, payments: List<Payment>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, card)
        if (!card.reminderEnabled) {
            alarmManager.cancel(pi)
            return
        }
        val triggerAt = nextTriggerMillis(card, DueDates.paidPeriods(payments))
        if (triggerAt == null) {
            alarmManager.cancel(pi)
            return
        }
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, card: Card) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, card))
    }

    fun rescheduleAll(context: Context, cards: List<Card>, payments: List<Payment>) {
        val byCard = payments.groupBy { it.cardId }
        cards.forEach { card -> schedule(context, card, byCard[card.id].orEmpty()) }
    }

    private fun pendingIntent(context: Context, card: Card): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, card.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, card.title)
            putExtra(ReminderReceiver.EXTRA_AMOUNT_MINOR, card.amountMinor)
        }
        return PendingIntent.getBroadcast(
            context,
            card.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Ближайшее будущее время напоминания либо null. */
    private fun nextTriggerMillis(card: Card, paidPeriods: Set<Long>): Long? {
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(
            card.reminderHour.coerceIn(0, 23),
            card.reminderMinute.coerceIn(0, 59),
        )
        val today = LocalDate.now(zone)
        val daysBefore = card.reminderDaysBefore.toLong().coerceAtLeast(0L)
        val now = System.currentTimeMillis()

        if (card.recurrence == Recurrence.NONE) {
            val due = DueDates.currentPeriodDue(card.dueDay, card.dueMonth, card.recurrence, today)
            if (due.isBefore(today)) return null
            val trigger = triggerMillis(due.minusDays(daysBefore), time, zone)
            return if (trigger > now) trigger else null
        }

        var due = DueDates.nextDue(card.dueDay, card.dueMonth, card.recurrence, today)
        var guard = 0
        while (guard < 600) {
            if (due.toEpochDay() !in paidPeriods) {
                val trigger = triggerMillis(due.minusDays(daysBefore), time, zone)
                if (trigger > now) return trigger
            }
            due = when (card.recurrence) {
                Recurrence.MONTHLY -> due.plusMonths(1)
                Recurrence.YEARLY -> due.plusYears(1)
                Recurrence.NONE -> return null
            }
            guard++
        }
        return null
    }

    private fun triggerMillis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
}
