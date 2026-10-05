package fr.president.engine

import fr.president.engine.data.DetailLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataLoadingTest {
    @Test
    fun `les données du snapshot se chargent et sont cohérentes`() {
        val db = TestData.db
        val france = db.country("FRA")
        assertEquals(DetailLevel.FULL, france.definition.detail)
        assertEquals(18, france.territory!!.regions.size)
        assertEquals(101, france.territory!!.departments.size)
        assertTrue(france.territory!!.cities.size >= 40)
        assertTrue(db.countries.size >= 5)
        assertTrue(db.events.isNotEmpty())
        assertEquals(7, db.config.paces.size)
    }

    @Test
    fun `chaque événement à message référence un modèle existant`() {
        val db = TestData.db
        db.events.mapNotNull { it.message }.forEach {
            assertTrue(it.template in db.dialogue, "Modèle manquant ${it.template}")
        }
    }
}
