package fr.president.engine

import fr.president.engine.politics.Appearance
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresidentCreationTest {
    @Test
    fun thePlayerShapesTheirPresident() {
        val clock = TestData.FakeClock()
        val base = GameSession.newGame(TestData.db, NewGameOptions("normal", 3L, clock.now), clock)
        val s = GameSession.newGame(TestData.db, NewGameOptions("normal", 3L, clock.now, "Claire", "Durand", true,
            presidentAge = 41, careerId = "entrepreneur", presidentTraits = mapOf("charisma" to 0.9),
            appearance = Appearance(skin = 2, hairStyle = 1, glasses = true)), clock)
        val p = s.state.characters.getValue(s.state.player.presidentId)
        assertEquals("Claire Durand", p.fullName)
        assertEquals(s.state.time.toDateTime().year - 41, p.birthYear)
        assertTrue(p.traits.getValue("charisma") >= 0.9)
        assertEquals(true, p.appearance?.glasses)
        assertEquals("entrepreneur", s.state.player.career)
        assertTrue(s.state.playerCountry.economy.businessConfidence > base.state.playerCountry.economy.businessConfidence)
    }
}
