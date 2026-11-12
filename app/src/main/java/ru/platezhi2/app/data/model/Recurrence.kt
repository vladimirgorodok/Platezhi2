package ru.platezhi2.app.data.model

/** Периодичность платежа. */
enum class Recurrence(val key: String, val title: String) {
    NONE("NONE", "Разовый"),
    MONTHLY("MONTHLY", "Ежемесячно"),
    YEARLY("YEARLY", "Ежегодно");

    companion object {
        fun fromKey(key: String?): Recurrence = entries.firstOrNull { it.key == key } ?: MONTHLY
    }
}
