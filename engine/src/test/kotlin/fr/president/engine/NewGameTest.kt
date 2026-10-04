package fr.president.engine

import fr.president.engine.politics.CharacterRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewGameTest {
    @Test
    fun `une nouvelle partie est entièrement initialisée`() {
        val session = TestData.newSession()
        val s = session.state
        assertEquals("FRA", s.player.countryId)
        assertEquals(CharacterRole.PRESIDENT, s.characters.getValue(s.player.presidentId).role)
        assertNotNull(s.government.primeMinisterId)
        assertEquals(11, s.government.ministers.size)
        assertEquals(101, s.territory.departments.size)
        assertTrue(s.territory.cities.values.all { it.mayorId != null })
        assertTrue(s.infrastructure.isNotEmpty())
        assertTrue(s.military.units.isNotEmpty())
        assertTrue(s.inbox.messages.isNotEmpty(), "Message de bienvenue attendu")
        assertTrue(s.elections.candidates.size >= 5)
    }

    @Test
    fun `le budget initial reflète un déficit réaliste`() {
        val e = TestData.newSession().state.playerCountry.economy
        assertTrue(e.deficitRatio in 0.03..0.07, "Déficit initial ${e.deficitRatio}")
        assertTrue(e.debtRatio in 1.0..1.3, "Dette initiale ${e.debtRatio}")
    }

    @Test
    fun `la France est exportatrice nette d'électricité au départ`() {
        val energy = TestData.newSession().state.energy
        assertTrue(energy.netExportTWh > 0, "Export net ${energy.netExportTWh}")
        assertTrue(energy.margin > 0)
    }

    @Test
    fun `aucun dirigeant ne porte un nom vide`() {
        TestData.newSession().state.characters.values.forEach {
            assertTrue(it.firstName.isNotBlank() && it.lastName.isNotBlank())
        }
    }
}
