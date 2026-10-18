package fr.president.engine

import fr.president.engine.notifications.Reminders
import fr.president.engine.politics.CareerSystem
import fr.president.engine.save.Gallery
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReplayabilityTest {

    @Test
    fun `le parcours pèse sur les crises tout le mandat`() {
        val db = TestData.db
        val s = GameSession.newGame(db, NewGameOptions("normal", 3L, TestData.START_REAL, careerId = "medecin"), TestData.FakeClock())
        assertTrue(CareerSystem.eventFactor(s.context, "epidemic") < 1.0)
        assertEquals(1.0, CareerSystem.eventFactor(s.context, "national_strike"))
        val career = db.country("FRA").careers.first { it.id == "medecin" }
        assertTrue(career.perk.isNotBlank() && career.flaw.isNotBlank() && career.monthly.isNotEmpty())
    }

    @Test
    fun `le monde varié tire une situation de départ`() {
        val db = TestData.db
        val situations = (1L..12L).mapNotNull { seed ->
            GameSession.newGame(db, NewGameOptions("normal", seed, TestData.START_REAL, variedWorld = true), TestData.FakeClock()).state.player.startSituation
        }.toSet()
        assertTrue(situations.size >= 3, "Plusieurs situations doivent apparaître : $situations")
        val plain = GameSession.newGame(db, NewGameOptions("normal", 1L, TestData.START_REAL), TestData.FakeClock())
        assertEquals(null, plain.state.player.startSituation)
    }

    @Test
    fun `un rappel part avant qu'une décision expire, une seule fois`() {
        val s = TestData.newSession()
        val m = s.state.inbox.messages.firstOrNull { it.awaitingAnswer && it.deadline != null }
            ?: fr.president.engine.government.CabinetService(s.context).raise(fr.president.engine.government.AffairKind.GAFFE, s.state.characters.getValue(s.state.government.ministers.values.first()))
        val reminders = Reminders(s)
        assertNotNull(reminders.nextReminderRealMillis())
        val deadlineReal = s.state.meta.clock.realMillisAt(m.deadline!!)
        reminders.collect(deadlineReal - 3600_000)
        val pushed = s.state.notifications.pendingPlatform.filter { it.title.contains("délai") || it.title.contains("expirent") }
        assertTrue(pushed.isNotEmpty())
        reminders.collect(deadlineReal - 1800_000)
        assertEquals(pushed.size, s.state.notifications.pendingPlatform.count { it.title.contains("délai") || it.title.contains("expirent") })
    }

    @Test
    fun `la galerie garde chaque présidence une fois`() {
        val dir = kotlin.io.path.createTempDirectory("galerie").toFile()
        val s = TestData.newSession()
        s.state.player.gameOver = fr.president.engine.world.GameOver(s.context.now, "Fin de test")
        val gallery = Gallery(dir)
        gallery.record(s)
        gallery.record(s)
        val entries = gallery.entries()
        assertEquals(1, entries.size)
        assertEquals("Fin de test", entries.first().ending)
        dir.deleteRecursively()
    }
}
