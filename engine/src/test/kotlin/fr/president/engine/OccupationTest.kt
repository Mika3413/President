package fr.president.engine

import fr.president.engine.military.Capture
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.OccupationService
import fr.president.engine.military.WarService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OccupationTest {

    @Test
    fun `une zone occupée sans garnison voit naître une résistance qui finit par la libérer`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        WarService(s.context).declare("RUS", "UKR", "test")
        val zone = s.db.zones.ownedBy("UKR").first { z -> s.state.military.units.values.none { it.zoneId == z.id } }.id
        Capture(s.context, Geopolitics(s.context)).take(zone, "RUS")
        val system = fr.president.engine.military.OccupationSystem()
        repeat(3) { system.run(s.context) }
        val o = s.state.military.occupation[zone]
        assertNotNull(o)
        assertTrue(o.resistance > 0.0, "La résistance doit naître")
        o.resistance = 0.95
        system.run(s.context)
        assertNull(s.state.military.occupied[zone], "Sans garnison, l'insurrection libère la zone")
    }

    @Test
    fun `l'occupant peut administrer ou ratisser, avec un délai`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.diplomacy.declareWar("CHE")
        val zone = s.db.zones.ownedBy("CHE").first().id
        Capture(s.context, Geopolitics(s.context)).take(zone, "FRA")
        clock.advanceWorldDays(1.0)
        s.advanceToNow()
        val service = OccupationService(s.context)
        val before = service.state(zone)!!.morale
        assertTrue(service.administer(zone).isSuccess)
        assertTrue(service.state(zone)!!.morale > before)
        assertTrue(service.sweep(zone).isFailure, "Une seule action tous les 20 jours")
        assertTrue(service.supportResistance(zone).isFailure)
        val view = s.warfare.occupation(zone)!!
        assertEquals(true, view.weOccupy)
        assertEquals(2, view.actions.size)
    }
}
