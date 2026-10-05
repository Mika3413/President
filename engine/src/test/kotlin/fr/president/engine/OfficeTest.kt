package fr.president.engine

import fr.president.engine.legislation.Advisors
import fr.president.engine.legislation.LeverChange
import fr.president.engine.readout.ConsequenceReadout
import fr.president.engine.readout.OfficeReadout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficeTest {
    @Test
    fun theOfficeReportsWhatMatters() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.levers.apply(LeverChange("spend:police", 1.0, 0.4))
        clock.advanceWorldDays(200.0); s.advanceToNow()
        val office = OfficeReadout(s.context)
        val note = office.briefing()
        assertTrue(note.isNotEmpty() && note.size <= 5)
        assertTrue(note.any { "sous-effectif" in it.title.lowercase() || "insécurité" in it.title.lowercase() }, note.map { it.title }.toString())
        val reports = office.reports()
        assertTrue(reports.size >= 10)
        val interior = reports.first { it.ministryId == "interior" }
        assertTrue(interior.indicators.any { it.label == "Crédits de la police" && "40 %" in it.value }, interior.indicators.toString())
        assertTrue("grave" in interior.advice || "alerter" in interior.advice, interior.advice)
        assertTrue(office.services().isNotEmpty())
        assertTrue(office.hotspots().all { it.reasons.isNotEmpty() })
    }

    @Test
    fun theForecastDoesNotTouchTheRealCountry() {
        val s = TestData.newSession()
        val time = s.state.time
        val debt = s.state.playerCountry.economy.debtBillions
        val f = OfficeReadout(s.context).forecast(6)
        assertEquals(time, s.state.time)
        assertEquals(debt, s.state.playerCountry.economy.debtBillions)
        assertEquals(7, f.lines.size)
        assertTrue(f.lines.all { "→" in it.value })
    }

    @Test
    fun ministersGiveContrastingAdvice() {
        val s = TestData.newSession()
        val p = s.levers.preview("spend:health", 1.0, 1.5)
        val advice = Advisors(s.context).opinions("spend:health", p)
        assertTrue(advice.any { it.role == "Économie" && "entre" in it.text }, advice.toString())
        assertTrue(advice.any { it.role != "Économie" && it.stance == 1 }, "le ministre de la Santé est pour : $advice")
        val cut = Advisors(s.context).opinions("spend:police", s.levers.preview("spend:police", 1.0, 0.5))
        assertTrue(cut.any { it.stance == -1 && it.role != "Économie" }, "le ministre de l'Intérieur s'y oppose : $cut")
    }

    @Test
    fun thresholdsAreShownInPlainNumbers() {
        val s = TestData.newSession()
        assertEquals("RSA à 45 % du SMIC", ConsequenceReadout.format("derived.rsaToSmic", 0.452))
        assertEquals("40 % des crédits de départ", ConsequenceReadout.format("spending.police", 0.4))
        assertTrue(s.stats.why("inflation") != null && s.stats.why("debt") != null)
    }

    @Test
    fun dailyLifeReactsToDecisions() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val clock2 = TestData.FakeClock()
        val control = TestData.newSession(clock = clock2)
        s.levers.apply(LeverChange("param:housing_aid", 100.0, 250.0))
        s.levers.apply(LeverChange("param:family_allowance", 100.0, 250.0))
        clock.advanceWorldDays(730.0); s.advanceToNow()
        clock2.advanceWorldDays(730.0); control.advanceToNow()
        assertTrue(s.state.society.rentIndex > control.state.society.rentIndex + 0.03, "les aides au logement font monter les loyers")
        assertTrue(s.state.society.fertility > control.state.society.fertility + 0.03, "les allocations soutiennent la natalité")
        assertTrue(control.state.society.lifeExpectancy in 80.0..86.0)
    }
}
