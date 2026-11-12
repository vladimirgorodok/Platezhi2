package ru.platezhi2.app.data.model

/** Периодичность платежа. */
enum class Recurrence(val key: String, val title: String) {
    NONE("NONE", "Разовый"),
    WEEKLY("WEEKLY", "Каждую неделю"),
    MONTHLY("MONTHLY", "Каждый месяц"),
    YEARLY("YEARLY", "Каждый год");

    companion object {
        fun fromKey(k: String?): Recurrence = entries.firstOrNull { it.key == k } ?: NONE
    }
}

/** Категория платежа с эмодзи-маркером и акцентным цветом (ARGB). */
data class Category(
    val key: String,
    val title: String,
    val emoji: String,
    val color: Long,
)

object Categories {
    val all: List<Category> = listOf(
        Category("utilities", "Коммунальные", "💡", 0xFFF2B705),
        Category("internet", "Связь и интернет", "📶", 0xFF4A90D9),
        Category("credit", "Кредиты", "🏦", 0xFF6C5CE7),
        Category("subscription", "Подписки", "🔁", 0xFF00B894),
        Category("transport", "Транспорт", "🚗", 0xFFE17055),
        Category("home", "Дом и аренда", "🏠", 0xFFB2652A),
        Category("kids", "Дети и учёба", "🧸", 0xFFD63384),
        Category("health", "Здоровье", "💊", 0xFF00A8A8),
        Category("other", "Другое", "📦", 0xFF7A7A8C),
    )

    fun byKey(key: String?): Category = all.firstOrNull { it.key == key } ?: all.last()
}
