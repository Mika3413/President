package fr.president.engine

import fr.president.engine.military.OrderService
import fr.president.engine.military.UnitOrder
import fr.president.engine.military.WarService
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationsTest {
    @Test
    fun cyberAttackHasCooldown() {
        val s = TestData.newSession()
        assertTrue(s.military.operations.cyber("RUS").isSuccess)
        assertNotNull(s.military.operations.cyberBlocker("RUS"))
        assertTrue(s.military.operations.cyber("RUS").isFailure)
        assertTrue(s.stats.journal().any { it.kind == "Opération" })
    }

    @Test
    fun strikesNeedAWarThenHitTheEnemy() {
        val s = TestData.newSession()
        assertNotNull(s.military.operations.strikeBlocker("DEU"))
        WarService(s.context).declare("FRA", "DEU", "test")
        assertNull(s.military.operations.strikeBlocker("DEU"))
        val target = s.military.operations.strikeTarget("DEU")!!
        val before = s.state.military.units.values.filter { it.zoneId == target && it.countryId == "DEU" }.sumOf { it.strength }
        assertTrue(s.military.operations.strike("DEU").isSuccess)
        val after = s.state.military.units.values.filter { it.zoneId == target && it.countryId == "DEU" }.sumOf { it.strength }
        assertTrue(after < before)
    }

    @Test
    fun airborneAndAmphibiousOperations() {
        val s = TestData.newSession()
        val zones = s.db.zones
        val para = s.military.ownUnits().first { it.type == "AIRBORNE_BRIGADE" }
        val drop = zones.zones.values.first { !it.sea && it.owner == "FRA" && zones.distanceKm(para.zoneId, it.id) in 300.0..900.0 }
        assertTrue(s.military.order(para.id, UnitOrder.AIRBORNE, drop.id) is OrderService.Outcome.Ok)
        assertTrue(para.zoneId == drop.id)
        assertTrue(s.military.order(para.id, UnitOrder.AIRBORNE, para.homeZoneId) is OrderService.Outcome.Refused, "délai entre deux opérations")

        val marines = s.military.ownUnits().first { it.type == "MARINE_BRIGADE" }
        val ships = s.military.ownUnits().filter { s.db.unitType(it.type).domain == fr.president.engine.data.Domain.SEA }
        val beach = zones.zones.values.firstOrNull { z -> z.coastal && !z.sea && z.owner == "FRA" && z.id != marines.zoneId &&
            ships.any { zones.distanceKm(it.zoneId, z.id) <= 600.0 } }
        if (beach != null) {
            val r = s.military.order(marines.id, UnitOrder.AMPHIBIOUS, beach.id)
            assertTrue(r is OrderService.Outcome.Ok || (r as OrderService.Outcome.Refused).reason.isNotBlank())
        }
    }

    @Test
    fun foreignCountriesStrikeTheirEnemiesAndWarnThePlayer() {
        val s = TestData.newSession()
        WarService(s.context).declare("DEU", "FRA", "test")
        val ops = s.military.operations
        val struck = ops.aiStrike("DEU", "FRA")
        if (struck) {
            assertTrue(s.state.notifications.feed.any { it.title.contains("frappe nos forces") })
            assertTrue(!ops.aiStrike("DEU", "FRA"), "délai entre deux frappes")
        }
        assertTrue(ops.aiCyber("DEU", "FRA"))
        assertTrue(s.stats.journal().any { it.text.contains("Cyberattaque") })
    }

    @Test
    fun orderPreviewGivesTimeAndOddsWithoutMovingTheUnit() {
        val s = TestData.newSession()
        WarService(s.context).declare("FRA", "DEU", "test")
        val unit = s.state.military.units.getValue("u_2bb")
        val zoneBefore = unit.zoneId
        val target = s.military.zoneAt(9.2, 48.9)!!
        val p = assertNotNull(s.military.preview.preview(unit.id, target))
        assertTrue(p.hostile)
        val attack = p.options.first { it.order == UnitOrder.ATTACK }
        assertTrue(attack.available && (attack.etaHours ?: 0.0) > 0 && attack.odds != null)
        assertTrue(unit.zoneId == zoneBefore && unit.path.isEmpty(), "l'aperçu ne déplace rien")
    }
}
