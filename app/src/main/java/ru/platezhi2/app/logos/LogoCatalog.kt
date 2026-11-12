package ru.platezhi2.app.logos

/**
 * Небольшой словарь популярных брендов: помогает построить точный поисковый
 * запрос для онлайн-поиска логотипа (например, «Билайн» → «Beeline logo»).
 */
object LogoCatalog {

    private data class Brand(val query: String, val aliases: List<String>)

    private val brands = listOf(
        Brand("Beeline logo", listOf("билайн", "beeline")),
        Brand("MTS logo", listOf("мтс", "mts", "мтс банк", "mts bank")),
        Brand("MegaFon logo", listOf("мегафон", "megafon")),
        Brand("Tele2 logo", listOf("теле2", "tele2", "t2")),
        Brand("Rostelecom logo", listOf("ростелеком", "rostelecom")),
        Brand("Yandex logo", listOf("яндекс", "yandex")),
        Brand("VK logo", listOf("вк", "vk", "вконтакте", "vkontakte")),
        Brand("Odnoklassniki logo", listOf("одноклассники", "ok", "odnoklassniki")),
        Brand("Sberbank logo", listOf("сбер", "сбербанк", "sber", "sberbank")),
        Brand("VTB logo", listOf("втб", "vtb")),
        Brand("T-Bank logo", listOf("тинькофф", "tinkoff", "т-банк", "tbank")),
        Brand("Alfa-Bank logo", listOf("альфа", "альфа-банк", "alfa", "alfabank")),
        Brand("Gazprombank logo", listOf("газпромбанк", "gazprombank")),
        Brand("Rosselkhozbank logo", listOf("россельхозбанк", "rshb")),
        Brand("Raiffeisenbank logo", listOf("райффайзен", "raiffeisen")),
        Brand("Sovcombank logo", listOf("совкомбанк", "sovcombank")),
        Brand("Lukoil logo", listOf("лукойл", "lukoil")),
        Brand("Gazprom logo", listOf("газпром", "gazprom")),
        Brand("Rosneft logo", listOf("роснефть", "rosneft")),
        Brand("Magnit logo", listOf("магнит", "magnit")),
        Brand("Pyaterochka logo", listOf("пятёрочка", "пятерочка", "pyaterochka")),
        Brand("Perekrestok logo", listOf("перекрёсток", "перекресток", "perekrestok")),
        Brand("Wildberries logo", listOf("вайлдберриз", "wildberries", "вб", "wb")),
        Brand("Ozon logo", listOf("озон", "ozon")),
        Brand("Avito logo", listOf("авито", "avito")),
        Brand("Lenta logo", listOf("лента", "lenta")),
        Brand("Yota logo", listOf("йота", "yota")),
        Brand("Russian Post logo", listOf("почта", "почта россии", "pochta")),
        Brand("Russian Railways logo", listOf("ржд", "rzd")),
        Brand("Citilink logo", listOf("ситилинк", "citilink")),
        Brand("DNS logo", listOf("днс", "dns")),
        Brand("M.Video logo", listOf("м.видео", "мвидео", "m.video")),
        Brand("Gosuslugi logo", listOf("госуслуги", "gosuslugi")),
        Brand("Aeroflot logo", listOf("аэрофлот", "aeroflot")),
        Brand("S7 logo", listOf("s7", "сибирь")),
        Brand("Netflix logo", listOf("netflix", "нетфликс")),
        Brand("Spotify logo", listOf("spotify", "спотифай")),
        Brand("YouTube logo", listOf("youtube", "ютуб")),
        Brand("Telegram logo", listOf("telegram", "телеграм")),
        Brand("Google logo", listOf("google", "гугл")),
        Brand("Apple logo", listOf("apple", "эпл")),
        Brand("Microsoft logo", listOf("microsoft", "майкрософт")),
    )

    fun searchQuery(name: String): String {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return name
        for (b in brands) {
            if (b.aliases.any { alias -> n.contains(alias) || alias.contains(n) }) return b.query
        }
        return "$name logo"
    }
}
