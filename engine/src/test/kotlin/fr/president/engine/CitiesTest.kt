package fr.president.engine

import fr.president.engine.military.CitiesSystem
import fr.president.engine.military.WarService
import fr.president.engine.military.WorldCities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CitiesTest {
    @Test
    fun everyCityHasAZoneAndASheet() {
        val s = TestData.newSession()
        assertTrue(s.db.worldCities.size >= 200)
        s.db.worldCities.forEach { c ->
            assertNotNull(WorldCities(s.context).zoneOf(c), c.id)
            assertNotNull(s.worldCities.sheet(c.id), c.id)
        }
        assertEquals("DEU", s.worldCities.holder("deu-berlin"))
    }

    @Test
    fun capturingACityIsAnnounced() {
        val s = TestData.newSession()
        WarService(s.context).declare("FRA", "DEU", "test")
        val berlin = s.db.worldCities.first { it.id == "deu-berlin" }
        val zone = WorldCities(s.context).zoneOf(berlin)!!
        s.state.military.occupied[zone] = "FRA"
        CitiesSystem().run(s.context)
        assertEquals("FRA", s.worldCities.holder("deu-berlin"))
        assertTrue(s.state.notifications.feed.any { it.title == "Nos troupes prennent Berlin" })
        assertTrue(s.stats.journal().any { it.text.startsWith("Prise de Berlin") })
    }
}
