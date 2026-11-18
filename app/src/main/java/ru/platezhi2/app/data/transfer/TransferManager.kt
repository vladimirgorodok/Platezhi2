package ru.platezhi2.app.data.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.platezhi2.app.core.DueDates
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.media.ImageScaling
import ru.platezhi2.app.media.ImageStore
import java.time.LocalDate
import java.util.Base64

/**
 * Копии данных: текст для облака и архива на устройстве плюс импорт из текста.
 *
 * Данные состоят из двух частей: карточки платежей и история оплат по ним.
 * Поддерживается импорт:
 *  - JSON собственного формата (карточки + вложенная история оплат);
 *  - JSON предыдущей версии (плоский список карточек);
 *  - CSV собственного формата и CSV старого приложения «Платежи» ([ImportParser]).
 */
class TransferManager(
    private val repo: PaymentRepository,
    private val images: ImageStore,
) {

    companion object {
        private const val BOM = "\uFEFF"

        /** Больше этой картинки в копию не встраиваем (иначе копия станет огромной). */
        private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
    }

    // ------------------------------------------------------------------
    // Экспорт
    // ------------------------------------------------------------------

    /**
     * Текст копии для облака.
     *
     * Значки-картинки в него не вшиваются: для них есть отдельный архив значков.
     * Так файл данных остаётся совсем маленьким и уходит на Диск мгновенно даже
     * на слабом мобильном интернете.
     */
    suspend fun snapshotJson(): String = buildJsonText(includeImages = false)

    /**
     * Текст копии для архива на устройстве. Значки вшиваются: копия должна быть
     * полной, чтобы данные вернулись даже без Яндекс Диска.
     */
    suspend fun archiveJson(): String = buildJsonText(includeImages = true)

    private suspend fun buildJsonText(includeImages: Boolean): String {
        val cards = repo.getCards()
        val byCard = repo.getPayments().groupBy { it.cardId }
        val root = JSONObject()
        root.put("version", 3)
        root.put(
            "cards",
            JSONArray().apply {
                cards.forEach { card -> put(cardJson(card, byCard[card.id].orEmpty(), includeImages)) }
            },
        )
        return root.toString(2)
    }

    private suspend fun cardJson(
        card: Card,
        history: List<Payment>,
        includeImages: Boolean,
    ): JSONObject = JSONObject().apply {
        put("title", card.title)
        put("amountMinor", card.amountMinor)
        put("dueDay", card.dueDay)
        put("dueMonth", card.dueMonth)
        put("recurrence", card.recurrence.key)
        put("icon", card.icon)
        put("note", card.note)
        put("reminderEnabled", card.reminderEnabled)
        put("reminderDaysBefore", card.reminderDaysBefore)
        put("reminderHour", card.reminderHour)
        put("reminderMinute", card.reminderMinute)
        // В копию на устройстве значок-картинку вшиваем: иначе он не перенёсся бы
        // на другое устройство (там нет файла по этому пути). В облачную копию —
        // нет: значки идут отдельным архивом.
        if (includeImages) {
            images.readBytes(card.imagePath)
                ?.let { bytes -> ImageScaling.shrink(bytes) ?: bytes }
                ?.takeIf { it.size <= MAX_IMAGE_BYTES }
                ?.let { bytes -> put("image", Base64.getEncoder().encodeToString(bytes)) }
        }
        put(
            "payments",
            JSONArray().apply {
                history.sortedBy { it.paidAtEpochDay }.forEach { payment ->
                    put(
                        JSONObject().apply {
                            put("period", LocalDate.ofEpochDay(payment.periodEpochDay).toString())
                            put("paidAt", LocalDate.ofEpochDay(payment.paidAtEpochDay).toString())
                            put("amountMinor", payment.amountMinor)
                        }
                    )
                }
            },
        )
    }

    // ------------------------------------------------------------------
    // Импорт
    // ------------------------------------------------------------------

    /** Импорт из готового текста копии (данные из облака или из архива на устройстве). */
    suspend fun importFromText(raw: String, replaceAll: Boolean): Int = withContext(Dispatchers.IO) {
        val text = raw.removePrefix(BOM).trim()
        if (text.isEmpty()) error("Файл пуст")

        val parsed = if (text.startsWith("[") || text.startsWith("{")) {
            parseJson(text)
        } else {
            ImportParser.parseCsv(text)
        }

        val meaningful = parsed.filter {
            it.card.title.isNotBlank() || it.card.amountMinor != 0L || it.payments.isNotEmpty()
        }
        if (meaningful.isEmpty()) error("Не удалось распознать формат файла")

        // Защита от повторного импорта одного и того же файла. При замене
        // отбрасываем только полностью одинаковые карточки, чтобы не потерять
        // разные платежи с похожими названиями.
        val seen = if (replaceAll) HashSet() else repo.getCards().mapTo(HashSet()) { CardKeys.loose(it) }
        val toInsert = ArrayList<ImportParser.ParsedCard>()
        meaningful.forEach { imported ->
            val key = if (replaceAll) CardKeys.full(imported) else CardKeys.loose(imported.card)
            if (seen.add(key)) toInsert.add(imported)
        }

        val buildPayments: (List<Long>) -> List<Payment> = { ids ->
            val payments = ArrayList<Payment>()
            toInsert.forEachIndexed { index, imported ->
                val cardId = ids.getOrNull(index) ?: return@forEachIndexed
                imported.payments.forEach { payment -> payments += payment.copy(cardId = cardId) }
            }
            payments
        }

        // Очистка/вставка идут одной транзакцией: если запись сорвётся,
        // прежние данные останутся на месте.
        val cards = toInsert.map { it.card }
        if (replaceAll) repo.replaceAll(cards, buildPayments) else repo.appendAll(cards, buildPayments)
    }

    private suspend fun parseJson(text: String): List<ImportParser.ParsedCard> {
        val array = if (text.startsWith("{")) {
            val root = JSONObject(text)
            root.optJSONArray("cards") ?: JSONArray().put(root)
        } else {
            JSONArray(text)
        }

        val result = ArrayList<ImportParser.ParsedCard>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val dueDay = o.optInt("dueDay", 0)
            val dueMonth = o.optInt("dueMonth", 0)
            var card = Card(
                title = o.optString("title").takeIf { it.isNotBlank() }
                    ?: o.optString("name").takeIf { it.isNotBlank() }
                    ?: "",
                amountMinor = if (o.has("amountMinor")) o.optLong("amountMinor") else amountToMinor(o.optString("amount")),
                dueDay = dueDay.coerceIn(1, 31),
                dueMonth = if (dueMonth in 1..12) dueMonth else 1,
                recurrence = ImportParser.recurrenceFrom(o.optString("recurrence")),
                icon = ImportParser.normalizeIcon(o.optString("icon")),
                note = o.optString("note"),
                reminderEnabled = o.optBoolean("reminderEnabled", false),
                reminderDaysBefore = o.optInt("reminderDaysBefore", 1).coerceIn(0, 60),
                reminderHour = o.optInt("reminderHour", 9).coerceIn(0, 23),
                reminderMinute = o.optInt("reminderMinute", 0).coerceIn(0, 59),
            )

            // Значок-картинка из копии: сохраняем файлом на этом устройстве.
            o.optString("image").takeIf { it.isNotBlank() }?.let { encoded ->
                runCatching { Base64.getDecoder().decode(encoded) }
                    .getOrNull()
                    ?.let { bytes -> images.saveBytes(bytes) }
                    ?.let { path -> card = card.copy(imagePath = path) }
            }

            val payments = ArrayList<Payment>()
            val arrayPayments = o.optJSONArray("payments")
            if (arrayPayments != null) {
                for (j in 0 until arrayPayments.length()) {
                    val p = arrayPayments.optJSONObject(j) ?: continue
                    val period = ImportParser.parseDateOrNull(p.optString("period"))
                    val paidAt = ImportParser.parseDateOrNull(p.optString("paidAt"))
                        ?: ImportParser.parseDateOrNull(p.optString("paidDate"))
                        ?: period
                        ?: continue
                    payments += Payment(
                        periodEpochDay = (period ?: paidAt).toEpochDay(),
                        paidAtEpochDay = paidAt.toEpochDay(),
                        amountMinor = if (p.has("amountMinor")) p.optLong("amountMinor") else card.amountMinor,
                    )
                }
            } else {
                // Формат предыдущей версии: единый флаг «оплачено до».
                val paidUntil = o.optLong("paidUntil", 0L)
                if (paidUntil > 0L) {
                    payments += Payment(
                        periodEpochDay = paidUntil,
                        paidAtEpochDay = paidUntil,
                        amountMinor = card.amountMinor,
                    )
                } else if (o.optBoolean("paid", false)) {
                    val paidAt = LocalDate.now()
                    payments += Payment(
                        periodEpochDay = DueDates.periodForPaidDate(card, paidAt).toEpochDay(),
                        paidAtEpochDay = paidAt.toEpochDay(),
                        amountMinor = card.amountMinor,
                    )
                }
            }

            result += ImportParser.ParsedCard(card, ImportParser.dedupePayments(payments))
        }
        return result
    }

    private fun amountToMinor(text: String): Long = Format.parseAmountToMinor(text) ?: 0L
}
