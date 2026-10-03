package fr.president.engine

import fr.president.engine.diplomacy.Clause
import fr.president.engine.military.OrderService
import fr.president.engine.military.UnitOrder
import fr.president.engine.military.WarService
import fr.president.engine.military.WarStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MilitaryTest {

    @Test
    fun `les forces sont positionnées et les pays étrangers ont une armée`() {
        val s = TestData.newSession()
        val units = s.state.military.units.values
        assertTrue(units.filter { it.countryId == "FRA" }.all { it.zoneId.isNotBlank() })
        assertTrue(units.count { it.countryId == "RUS" } > 20)
        assertTrue(units.count { it.countryId == "DEU" } > 5)
    }

    @Test
    fun `une brigade rejoint sa destination en quelques jours`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val unit = s.state.military.units.getValue("u_2bb")
        val lyon = s.military.zoneAt(4.84, 45.76)!!
        val result = s.military.order(unit.id, UnitOrder.MOVE, lyon)
        assertEquals(OrderService.Outcome.Ok, result)
        clock.advanceWorldDays(8.0)
        s.advanceToNow()
        assertEquals(lyon, unit.zoneId)
        assertTrue(unit.fuel < 1.0)
    }

    @Test
    fun `une unité ne peut pas entrer chez un voisin sans droit de passage`() {
        val s = TestData.newSession()
        val madrid = s.military.zoneAt(-3.7, 40.4)!!
        val r = s.military.order("u_6blb", UnitOrder.MOVE, madrid)
        // L'Espagne est alliée (OTAN/UE) : passage autorisé ; la Suisse, neutre, ne l'est pas.
        assertEquals(OrderService.Outcome.Ok, r)
        val berne = s.military.zoneAt(7.45, 46.95)!!
        assertTrue(s.military.order("u_27bim", UnitOrder.MOVE, berne) is OrderService.Outcome.Refused)
    }

    @Test
    fun `une guerre entre pays IA produit combats et pertes puis évolue`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(seed = 31L, clock = clock)
        val war = WarService(s.context).declare("RUS", "UKR", "test")
        repeat(6) {
            clock.advanceWorldDays(15.0)
            s.advanceToNow()
        }
        assertTrue((war.casualties["RUS"] ?: 0) + (war.casualties["UKR"] ?: 0) > 0, "Des pertes doivent survenir")
        assertTrue(war.weariness.values.any { it > 0.0 })
        assertTrue(s.state.diplomacy.sanctions.any { it.target == "RUS" }, "L'agresseur doit être sanctionné")
    }

    @Test
    fun `l'agression d'un membre de l'OTAN appelle la France à le défendre`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(seed = 32L, clock = clock)
        WarService(s.context).declare("BLR", "POL", "test")
        clock.advanceWorldDays(2.0)
        s.advanceToNow()
        assertTrue(s.state.inbox.messages.any { it.origin == fr.president.engine.inbox.MessageOrigin.ALLIANCE_CALL })
    }

    @Test
    fun `la France en guerre combat et peut signer la paix`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(seed = 33L, clock = clock)
        s.state.diplomacy.relation("CHE", "FRA").memories.clear()
        val war = s.diplomacy.declareWar("CHE")
        val geneva = s.military.zoneAt(7.45, 46.95)!!
        val r = s.military.order("u_27bim", UnitOrder.ATTACK, geneva)
        assertEquals(OrderService.Outcome.Ok, r, r.toString())
        clock.advanceWorldDays(20.0)
        s.advanceToNow()
        assertTrue(s.state.military.warWeariness > 0.0)
        s.diplomacy.propose("CHE", listOf(Clause("PEACE_TREATY", "FRA", mapOf("keepOccupied" to 0.0))), 1)
        // Force l'acceptation : la Suisse est épuisée.
        war.weariness["CHE"] = 1.0
        clock.advanceWorldDays(6.0)
        s.advanceToNow()
        assertEquals(WarStatus.ENDED, war.status)
    }

    @Test
    fun `la production livre une nouvelle unité`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val before = s.military.ownUnits().size
        s.military.production.order("INFANTRY_BRIGADE").getOrThrow()
        clock.advanceWorldDays(370.0)
        s.advanceToNow()
        assertTrue(s.military.ownUnits().size > before)
    }
}
