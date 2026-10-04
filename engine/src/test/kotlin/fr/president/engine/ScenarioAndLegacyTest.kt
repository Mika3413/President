package fr.president.engine

import fr.president.engine.save.SaveRepository
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioAndLegacyTest {
    private fun start(scenario: String?, clock: TestData.FakeClock = TestData.FakeClock()) =
        GameSession.newGame(TestData.db, NewGameOptions("normal", 7L, clock.now, scenarioId = scenario), clock)

    @Test
    fun scenariosChangeTheStartingSituation() {
        val standard = start(null)
        val crash = start("financial_crisis")
        assertEquals("financial_crisis", crash.state.player.scenario)
        assertTrue(crash.state.playerCountry.economy.businessConfidence < standard.state.playerCountry.economy.businessConfidence)
        assertTrue(crash.state.scheduler.actions.any { it is fr.president.engine.simulation.ScheduledAction.EventLaunch && it.definitionId == "bank_fragility" })

        val war = start("war_europe")
        assertTrue(fr.president.engine.military.Geopolitics(war.context).atWar("RUS", "POL"))
        assertTrue(start("hung_parliament").state.government.parliamentSupport < 0.4)
        TestData.db.scenarios.forEach { sc ->
            val clock = TestData.FakeClock()
            val s = start(sc.id, clock)
            clock.advanceWorldDays(40.0); s.advanceToNow()
        }
    }

    @Test
    fun historyGradesThePresidency() {
        val clock = TestData.FakeClock()
        val s = start(null, clock)
        clock.advanceWorldDays(200.0); s.advanceToNow()
        val v = fr.president.engine.readout.LegacyReadout(s.context).verdict()
        assertTrue(v.grade in 0.0..20.0)
        assertTrue(v.lines.size >= 5)
        assertTrue(v.title.isNotBlank())
    }

    @Test
    fun severalGamesAreKeptSideBySide() {
        val dir = kotlin.io.path.createTempDirectory("slots").toFile()
        val repo = SaveRepository(dir)
        val a = start(null)
        repo.write(a.toSaveFile("test"))
        repo.writeMeta(repo.activeSlot, SaveRepository.SlotMeta("Première", "détail", 1L))
        repo.activeSlot = repo.newSlotId()
        val b = start("pandemic")
        repo.write(b.toSaveFile("test"))
        repo.writeMeta(repo.activeSlot, SaveRepository.SlotMeta("Seconde", "détail", 2L))
        assertEquals(2, repo.slots().size)
        assertEquals("Seconde", repo.slots().first().meta.title)
        assertEquals("pandemic", repo.read().state.player.scenario)
        repo.activeSlot = repo.slots().last().id
        assertEquals(null, repo.read().state.player.scenario)
        repo.delete(repo.slots().first().id)
        assertEquals(1, repo.slots().size)
    }
}
