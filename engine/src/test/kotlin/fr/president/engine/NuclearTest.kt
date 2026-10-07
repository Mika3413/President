package fr.president.engine

import fr.president.engine.military.Capture
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.MilitarySetup
import fr.president.engine.military.NuclearService
import fr.president.engine.military.WarService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NuclearTest {

    @Test
    fun `la doctrine interdit la frappe sans agression contre le territoire`() {
        val s = TestData.newSession()
        assertNotNull(s.nuclear.strikeBlocker())
        s.diplomacy.declareWar("CHE")
        assertNotNull(s.nuclear.strikeBlocker(), "La guerre seule ne suffit pas")
    }

    @Test
    fun `l'ultime avertissement anéantit l'objectif et fait monter l'escalade`() {
        val s = TestData.newSession(seed = 5L)
        s.diplomacy.declareWar("CHE")
        val zone = s.military.zoneAt(6.1, 46.2)!!.let { z -> s.db.zones.ownedBy("FRA").minByOrNull { s.db.zones.distanceKm(it.id, z) }!!.id }
        val invader = MilitarySetup(s.context).createUnit("CHE", s.db.unitType("INFANTRY_BRIGADE"), zone, 0.8)
        Capture(s.context, Geopolitics(s.context)).take(zone, "CHE")
        assertNull(s.nuclear.strikeBlocker())
        val result = s.nuclear.warningStrike()
        assertTrue(result.isSuccess, result.toString())
        assertTrue(invader.destroyed)
        assertEquals(NuclearService.Rung.STRIKE, s.nuclear.rung())
        assertTrue(zone in s.state.military.nuclearZones)
    }

    @Test
    fun `une frappe ennemie impose une décision, et la riposte massive contre une puissance nucléaire met fin à tout`() {
        val s = TestData.newSession(seed = 6L)
        WarService(s.context).declare("RUS", "FRA", "test")
        s.nuclear.enemyStrike("RUS")
        assertTrue(s.nuclear.pendingDecision())
        assertTrue(fr.president.engine.readout.OfficeReadout(s.context).briefing().first().title.contains("nucléaire"))
        val r = s.nuclear.respond(NuclearService.Response.MASSIVE)
        assertTrue(r.isSuccess)
        assertNotNull(s.state.player.gameOver)
    }

    @Test
    fun `la retenue préserve le monde mais coûte en crédibilité`() {
        val s = TestData.newSession(seed = 7L)
        WarService(s.context).declare("RUS", "FRA", "test")
        val before = s.state.defense.credibility
        s.nuclear.enemyStrike("RUS")
        assertTrue(s.nuclear.respond(NuclearService.Response.RESTRAINT).isSuccess)
        assertNull(s.state.player.gameOver)
        assertTrue(s.state.defense.credibility < before)
        assertTrue(s.nuclear.respond(NuclearService.Response.RESTRAINT).isFailure, "Une seule décision")
    }
}
