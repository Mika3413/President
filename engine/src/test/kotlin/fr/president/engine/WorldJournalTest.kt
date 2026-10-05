package fr.president.engine

import fr.president.engine.ai.WorldLife
import kotlin.test.Test
import kotlin.test.assertTrue

class WorldJournalTest {
    @Test
    fun theWorldLivesWithoutUs() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(180.0); s.advanceToNow()
        val entries = s.state.world.entries
        println("WORLD ${entries.size} " + entries.groupingBy { it.category }.eachCount())
        entries.takeLast(15).forEach { println("WORLD · ${it.headline} | ${it.detail}") }
        assertTrue(entries.size >= 30, "au moins une trentaine de nouvelles en six mois : ${entries.size}")
        assertTrue(entries.any { it.countries.size == 2 }, "des événements entre pays")
        assertTrue(entries.all { e -> e.countries.all { it in s.state.countries } && "{" !in e.headline && "{" !in e.detail })
        assertTrue(entries.none { s.state.player.countryId in it.countries })
    }

    @Test
    fun everyWorldEventAppliesAndACoupChangesTheLeader() {
        val s = TestData.newSession()
        val life = WorldLife(s.context)
        val defs = s.db.worldEvents!!.events
        assertTrue(defs.size >= 50)
        for (d in defs) life.happen(d, "ITA", if (d.bilateral) "ESP" else null)
        val leader = s.state.countries.getValue("TUR").leaderId
        life.happen(defs.first { it.id == "coup_attempt" }, "TUR", null)
        assertTrue(s.state.countries.getValue("TUR").leaderId != leader, "un coup d'État change le dirigeant")
        assertTrue(s.state.world.entries.size >= defs.size)
    }
}
