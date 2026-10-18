package fr.president.engine

import fr.president.engine.readout.HistoryBookReadout
import fr.president.engine.stats.BigNewsService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StagingTest {

    @Test
    fun `une guerre fait un flash spécial, montré une seule fois`() {
        val s = TestData.newSession()
        val news = BigNewsService(s.context)
        assertNull(news.pending())
        fr.president.engine.military.WarService(s.context).declare("DEU", "FRA", "test")
        val n = news.pending()
        assertNotNull(n)
        assertTrue(n.flash && n.picture == "WAR")
        news.seen()
        assertNull(news.pending())
    }

    @Test
    fun `le livre raconte chaque année du mandat`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        repeat(14) { clock.advanceWorldDays(30.0); s.advanceToNow() }
        val book = HistoryBookReadout(s.context).book()
        assertEquals(2, book.chapters.size)
        assertTrue(book.chapters.all { it.title.isNotBlank() && it.paragraphs.isNotEmpty() && it.figures.isNotEmpty() })
        assertTrue(book.epilogue.contains("/ 20"))
    }
}
