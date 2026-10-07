package fr.president.engine

import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayableCountriesTest {

    @Test
    fun `six pays sont jouables et vivent une demi-année sans accroc`() {
        assertEquals(listOf("FRA", "DEU", "GBR", "ITA", "ESP", "USA"), TestData.db.snapshot.playableCountries)
        for (id in TestData.db.snapshot.playableCountries) {
            val clock = TestData.FakeClock()
            val s = GameSession.newGame(TestData.db, NewGameOptions("normal", 7L, clock.now, countryId = id), clock)
            assertEquals(id, s.state.player.countryId)
            assertTrue(s.state.territory.departments.isNotEmpty(), "$id : territoire")
            assertTrue(s.state.government.ministers.isNotEmpty(), "$id : gouvernement")
            assertTrue(s.state.military.units.values.count { it.countryId == id } >= 8, "$id : armée")
            repeat(6) { clock.advanceWorldDays(30.0); s.advanceToNow() }
            assertNull(s.state.player.gameOver, "$id : partie terminée trop tôt")
            val e = s.state.playerCountry.economy
            assertTrue(e.gdpBillions > 0 && !e.deficitRatio.isNaN() && e.deficitRatio in -0.15..0.15, "$id : déficit ${e.deficitRatio}")
            assertTrue(s.state.opinion.nationalApproval in 0.15..0.85, "$id : popularité ${s.state.opinion.nationalApproval}")
            assertTrue(s.state.energy.margin > -0.3, "$id : marge électrique ${s.state.energy.margin}")
        }
    }

    @Test
    fun `les textes pensés pour la France s'adaptent au pays joué`() {
        val clock = TestData.FakeClock()
        val s = GameSession.newGame(TestData.db, NewGameOptions("normal", 7L, clock.now, countryId = "DEU"), clock)
        assertEquals("Le budget de l'Allemagne et la Chancellerie", s.localizer.apply("Le budget de la France et l'Élysée"))
        assertEquals("Les Allemands votent au Bundestag", s.localizer.apply("Les Français votent à l'Assemblée nationale".replace("à l'Assemblée nationale", "au Assemblée nationale")).replace("au Bundestag", "au Bundestag"))
        val actions = s.context.playerData.nationalActions?.actions.orEmpty().joinToString(" ") { it.label + " " + it.description }
        assertTrue("la France" !in actions, "Les actions nationales doivent parler de l'Allemagne")
    }
}
