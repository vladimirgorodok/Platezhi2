package ru.platezhi2.app.data.model

/** Базовый набор стандартных иконок платежа. */
object AppIcons {

    const val DEFAULT = "📄"

    val all: List<String> = listOf(
        "📄", "💡", "📶", "🏦", "📱", "🏠", "🚗", "💧",
        "🔥", "⚡", "🎵", "📺", "🛡", "🎓", "💊", "🛒",
        "🧾", "🎁", "✈️", "🚌", "🏥", "💳", "🐶", "🔔",
    )

    fun normalize(icon: String?): String = if (icon.isNullOrBlank()) DEFAULT else icon
}
