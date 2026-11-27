package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.arka.app.data.PlayItem
import tv.arka.app.data.Progress
import tv.arka.app.data.ProgressRules

class ProgressRulesTest {
    private fun item(key: String) = PlayItem(key, "Titre", "", "url", live = false)

    @Test
    fun recordsAPositionAndPutsItFirst() {
        val existing = listOf(Progress(item("a"), 10_000, 100_000, 1))
        val updated = ProgressRules.update(existing, item("b"), 30_000, 100_000, 2)
        assertEquals(listOf("b", "a"), updated.map { it.item.key })
        assertEquals(30_000L, updated[0].position)
    }

    @Test
    fun replacesThePreviousPositionOfTheSameContent() {
        val existing = listOf(Progress(item("a"), 10_000, 100_000, 1))
        val updated = ProgressRules.update(existing, item("a"), 50_000, 100_000, 2)
        assertEquals(1, updated.size)
        assertEquals(50_000L, updated[0].position)
    }

    @Test
    fun forgetsFinishedAndBarelyStartedContent() {
        val existing = listOf(Progress(item("a"), 10_000, 100_000, 1))
        assertTrue(ProgressRules.update(existing, item("a"), 96_000, 100_000, 2).isEmpty())
        assertTrue(ProgressRules.update(existing, item("a"), 2_000, 100_000, 2).isEmpty())
    }

    @Test
    fun keepsTheListBounded() {
        var list = emptyList<Progress>()
        repeat(ProgressRules.MAX_ENTRIES + 10) { i ->
            list = ProgressRules.update(list, item("k$i"), 20_000, 100_000, i.toLong())
        }
        assertEquals(ProgressRules.MAX_ENTRIES, list.size)
    }
}
