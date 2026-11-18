package ru.platezhi2.app.ui.adapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Границы альбомного (планшетного) вида. */
class AdaptiveTest {

    @Test
    fun `телефон в портрете — узкий экран и обычные отступы`() {
        assertFalse(isWideWidth(411))
        assertEquals(16, sidePaddingDp(411))
    }

    @Test
    fun `планшет — широкий экран, колонка по центру`() {
        assertTrue(isWideWidth(1280))
        // (1280 - 720) / 2 — содержимое стоит по центру.
        assertEquals(280, sidePaddingDp(1280))
    }

    @Test
    fun `широкий вид начинается с 600 точек`() {
        assertFalse(isWideWidth(599))
        assertTrue(isWideWidth(600))
    }

    @Test
    fun `отступы никогда не меньше обычных`() {
        assertEquals(16, sidePaddingDp(360))
        assertEquals(16, sidePaddingDp(720))
        assertEquals(40, sidePaddingDp(800))
    }
}
