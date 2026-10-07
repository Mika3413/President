package fr.president.engine

import fr.president.engine.military.FortificationService
import fr.president.engine.military.Terrain
import fr.president.engine.military.WarService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarfareTest {

    @Test
    fun `le relief des zones suit la géographie`() {
        val s = TestData.newSession()
        val terrain = Terrain(s.context)
        fun at(lon: Double, lat: Double) = terrain.of(s.military.zoneAt(lon, lat)!!)?.id
        assertEquals("URBAN", at(2.35, 48.86), "Paris")
        assertEquals("MOUNTAIN", at(10.5, 46.5), "Alpes")
        assertEquals("DESERT", at(5.0, 25.0), "Sahara")
        assertEquals("FOREST", at(28.0, 52.0), "Polésie")
        assertEquals("PLAINS", at(1.5, 47.8), "Beauce")
    }

    @Test
    fun `franchir le Rhin ou le Dniepr est détecté`() {
        val s = TestData.newSession()
        val terrain = Terrain(s.context)
        val west = s.military.zoneAt(7.0, 48.5)!!
        val east = s.military.zoneAt(8.6, 48.5)!!
        assertTrue(west != east)
        assertEquals("Rhin", terrain.riverBetween(west, east))
        assertEquals("Dniepr", terrain.riverBetween(s.military.zoneAt(29.5, 50.4)!!, s.military.zoneAt(31.5, 50.4)!!))
        assertNull(terrain.riverBetween(s.military.zoneAt(-4.0, 40.0)!!, s.military.zoneAt(-3.0, 40.0)!!), "Pas de grand fleuve dans la Meseta")
    }

    @Test
    fun `un chantier de fortification coûte, dure et s'achève`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val forts = FortificationService(s.context)
        val metz = s.military.zoneAt(4.0, 47.0)!!
        assertNull(forts.blocker("line", metz))
        val result = forts.build("line", metz)
        assertTrue(result.isSuccess, result.toString())
        assertNotNull(forts.blocker("line", metz), "Un seul chantier à la fois par ouvrage")
        assertEquals(0.0, forts.effect(metz, "line", "defense"))
        clock.advanceWorldDays(50.0)
        s.advanceToNow()
        assertEquals(1, forts.work(metz, "line")!!.level)
        assertTrue(forts.effect(metz, "line", "defense") > 0.15)
        // Une batterie côtière ne se bâtit pas à l'intérieur des terres.
        assertNotNull(forts.blocker("coastal", metz))
        // Pas de chantier chez les autres.
        assertNotNull(forts.blocker("line", s.military.zoneAt(13.4, 52.5)!!))
    }

    @Test
    fun `les lignes réelles existent au départ et passent à l'ennemi`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(1.0)
        s.advanceToNow()
        val forts = FortificationService(s.context)
        assertTrue(forts.of("UKR").count { it.type == "line" } >= 3, "Lignes ukrainiennes")
        assertTrue(forts.of("FRA").any { it.type == "air_defense" }, "Défense sol-air de Paris")
        val zone = forts.of("UKR").first { it.type == "line" }.zoneId
        WarService(s.context).declare("RUS", "UKR", "test")
        fr.president.engine.military.Capture(s.context, fr.president.engine.military.Geopolitics(s.context)).take(zone, "RUS")
        val w = forts.work(zone, "line")!!
        assertEquals("RUS", w.countryId)
        assertTrue(w.condition < 1.0)
    }

    @Test
    fun `les batailles laissent un rapport avec terrain et pertes`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(seed = 31L, clock = clock)
        WarService(s.context).declare("RUS", "UKR", "test")
        repeat(4) {
            clock.advanceWorldDays(10.0)
            s.advanceToNow()
        }
        val battles = s.state.military.battles
        assertTrue(battles.isNotEmpty(), "Des batailles doivent avoir eu lieu")
        assertTrue(battles.any { it.attackerLosses + it.defenderLosses > 0 })
        assertTrue(battles.any { it.modifiers.isNotEmpty() }, "Terrain, fortifications ou fleuve doivent apparaître")
        val view = s.warfare.battles().first()
        assertTrue(view.title.startsWith("Bataille"))
    }

    @Test
    fun `une défense sol-air intercepte une partie des missiles`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(1.0)
        s.advanceToNow()
        val forts = FortificationService(s.context)
        val paris = forts.of("FRA").first { it.type == "air_defense" }.zoneId
        assertTrue(forts.interception(paris, setOf("FRA")) > 0.2)
        assertEquals(0.0, forts.interception(paris, setOf("DEU")))
    }
}
