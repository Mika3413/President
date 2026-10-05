package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiguresTest {
    @Test
    fun leadersAndFiguresHaveTheirNames() {
        val s = TestData.newSession()
        val usa = s.state.characters.getValue(s.state.countries.getValue("USA").leaderId)
        assertEquals("Donald Tromp", usa.fullName)
        val ita = s.state.characters.getValue(s.state.countries.getValue("ITA").leaderId)
        assertTrue(ita.female)
        val names = s.state.elections.candidates.map { s.state.characters.getValue(it.characterId).lastName }
        assertTrue("Bardelli" in names, names.toString())
    }
}
