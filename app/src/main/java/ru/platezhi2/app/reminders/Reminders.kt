package ru.platezhi2.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

    fun schedule(context: Context, payment: Payment) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, payment)
        if (!payment.reminderEnabled) {
            alarmManager.cancel(pi)
            return
        }
        val triggerAt = nextTriggerMillis(payment) ?: run {
            alarmManager.cancel(pi)
            return
        }
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, payment: Payment) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, payment))
    }

    fun rescheduleAll(context: Context, payments: List<Payment>) {
        payments.forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, payment: Payment): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, payment.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, payment.title)
            putExtra(ReminderReceiver.EXTRA_AMOUNT_MINOR, payment.amountMinor)
            putExtra(ReminderReceiver.EXTRA_CURRENCY, payment.currency)
        }
        return PendingIntent.getBroadcast(
            context,
            payment.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Ближайшее будущее время напоминания либо null, если оно уже прошло и платёж разовый. */
    private fun nextTriggerMillis(payment: Payment): Long? {
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(payment.reminderHour.coerceIn(0, 23), payment.reminderMinute.coerceIn(0, 59))
        var date = LocalDate.ofEpochDay(payment.dueDateEpochDay).minusDays(payment.reminderDaysBefore.toLong())
        var trigger = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (trigger > now) return trigger

        if (payment.recurrence == Recurrence.NONE) return null

        var guard = 0
        while (trigger <= now && guard < 600) {
            date = when (payment.recurrence) {
                Recurrence.WEEKLY -> date.plusWeeks(1)
                Recurrence.MONTHLY -> date.plusMonths(1)
                Recurrence.YEARLY -> date.plusYears(1)
                Recurrence.NONE -> return null
            }
            trigger = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
            guard++
        }
        return trigger
    }
}
