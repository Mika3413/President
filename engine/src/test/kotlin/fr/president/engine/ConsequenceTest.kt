package fr.president.engine

import fr.president.engine.consequences.ConsequenceService
import fr.president.engine.legislation.BillStatus
import fr.president.engine.legislation.LeverChange
import fr.president.engine.legislation.LeverSource
import fr.president.engine.legislation.MeasureConfig
import fr.president.engine.politics.MovementPhase
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsequenceTest {
    @Test
    fun nothingIsTriggeredAtTheStart() {
        val s = TestData.newSession()
        val service = ConsequenceService(s.context)
        val rules = s.db.consequences!!.rules
        assertTrue(rules.size >= 40, "${rules.size} règles")
        rules.forEach { r ->
            assertTrue(service.value(r) != null || r.variable == "intel.capacity", "${r.id} : variable inconnue ${r.variable}")
            assertEquals(0.0, service.severity(r), "${r.id} ne doit pas être déclenchée au départ (valeur ${service.value(r)})")
        }
    }

    @Test
    fun cuttingThePoliceHasConsequencesEverywhere() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val control = TestData.newSession(clock = TestData.FakeClock())
        val crime = s.state.territory.departments.values.sumOf { it.crime }
        val preview = s.levers.preview("spend:police", 1.0, 0.4)
        assertTrue(preview.warnings.any { "sous-effectif" in it }, "l'aperçu prévient du seuil : ${preview.warnings}")
        assertTrue(preview.warnings.any { "insécurité" in it.lowercase() && "à terme" in it }, "et de la conséquence à terme")
        s.levers.apply(LeverChange("spend:police", 1.0, 0.4))
        clock.advanceWorldDays(240.0); s.advanceToNow()
        val active = ConsequenceService(s.context).active().map { it.first.id }
        assertTrue("cut_police" in active, "$active")
        assertTrue("security_collapse" in active, "la sécurité s'effondre : ${s.state.playerCountry.services["security"]}")
        assertTrue(s.state.territory.departments.values.sumOf { it.crime } > crime * 1.05, "la criminalité monte sur la carte")
        assertTrue(s.state.notifications.feed.any { "Pourquoi" in it.body }, "le joueur sait pourquoi")
        assertTrue(ConsequenceService.eventFactor(s.context, "urban_riots") > 1.2, "émeutes plus probables")
        assertTrue(control.state.consequences.current.isEmpty())
    }

    @Test
    fun anRsaAboveTheMinimumWageKeepsPeopleOutOfWork() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val clock2 = TestData.FakeClock()
        val control = TestData.newSession(clock = clock2)
        assertTrue(s.levers.preview("param:rsa_amount", 646.0, 1938.0).warnings.any { "travail ne paie plus" in it })
        s.levers.apply(LeverChange("param:rsa_amount", 646.0, 1938.0))
        clock.advanceWorldDays(365.0); s.advanceToNow()
        clock2.advanceWorldDays(365.0); control.advanceToNow()
        assertTrue(ConsequenceService(s.context).active().any { it.first.id == "work_does_not_pay" })
        val u = s.state.playerCountry.economy.unemployment
        val u0 = control.state.playerCountry.economy.unemployment
        assertTrue(u > u0 + 0.004, "chômage $u contre $u0")
        assertTrue(s.state.playerCountry.economy.deficitRatio > control.state.playerCountry.economy.deficitRatio, "et le déficit se creuse")
    }

    @Test
    fun damageHealsSlowlyOnceTheCauseIsGone() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.levers.apply(LeverChange("param:rsa_amount", 646.0, 2500.0))
        clock.advanceWorldDays(300.0); s.advanceToNow()
        val a = s.state.consequences.current["work_does_not_pay"]!!
        val applied = a.applied["economy.naturalUnemployment"] ?: 0.0
        assertTrue(applied > 0)
        s.levers.apply(LeverChange("param:rsa_amount", 2500.0, 646.0))
        clock.advanceWorldDays(95.0); s.advanceToNow()
        val left = s.state.consequences.current["work_does_not_pay"]
        assertTrue(left == null || !left.active && (left.applied["economy.naturalUnemployment"] ?: 0.0) < applied, "les dégâts se résorbent")
    }

    @Test
    fun everyLeverHasConsequences() {
        val s = TestData.newSession()
        val silent = mutableListOf<String>()
        for (lever in s.levers.all().filter { it.source != LeverSource.MEASURE }) {
            val current = s.levers.current(lever.id)
            val targets = if (lever.numeric) listOf(lever.min, lever.max) else lever.options.indices.map { it.toDouble() }
            val any = targets.filter { abs(it - current) > 1e-9 }.any { to ->
                val p = s.levers.preview(lever.id, current, to)
                abs(p.revenue) + abs(p.spending) > 1e-6 || p.groups.values.any { abs(it) > 1e-6 } || p.quality.values.any { abs(it) > 1e-6 } ||
                    p.sectors.isNotEmpty() || p.lines.isNotEmpty() || p.events.isNotEmpty() || abs(p.liberty) > 1e-6
            }
            if (!any && targets.any { abs(it - current) > 1e-9 }) silent += lever.id
        }
        assertTrue(silent.isEmpty(), "leviers sans conséquence : $silent")
    }

    @Test
    fun everyBuilderMeasureHasConsequences() {
        val s = TestData.newSession()
        val builder = fr.president.engine.legislation.BuilderModel(s.context)
        val file = s.context.playerData.legislation!!.builder!!
        for (action in file.actions) for (target in builder.targets(action)) {
            val (min, max, _) = builder.range(action, target)
            val v = if (action.model == fr.president.engine.legislation.MeasureModel.PRICE_CAP) min else maxOf(action.default.coerceIn(min, max), min)
            val i = builder.evaluate(MeasureConfig(action.id, target.id), if (action.model == fr.president.engine.legislation.MeasureModel.BAN) 1.0 else v)
            val any = abs(i.revenue) + abs(i.spending) > 1e-6 || i.groups.isNotEmpty() || i.quality.isNotEmpty() || i.economy.isNotEmpty() || i.sectors.isNotEmpty()
            assertTrue(any, "${action.id} × ${target.id} sans effet")
        }
    }

    @Test
    fun jobCutsSaveMoneyButBreakTheService() {
        val s = TestData.newSession()
        val builder = fr.president.engine.legislation.BuilderModel(s.context)
        val small = builder.evaluate(MeasureConfig("job_cuts", "police"), 5000.0)
        val big = builder.evaluate(MeasureConfig("job_cuts", "police"), 80000.0)
        assertTrue(big.spending < small.spending && small.spending < 0, "des économies")
        assertTrue((big.quality["security"] ?: 0.0) < (small.quality["security"] ?: 0.0) * 10, "au-delà d'un quart, le service s'effondre")
        assertTrue(big.events.isNotEmpty() && big.groups.values.any { it < 0 })
    }

    @Test
    fun article16OnlyWhenTheNationIsInDanger() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.agenda.entries.clear()
        assertTrue(s.nationalActions.perform("article_16").isFailure, "pas sans péril grave")
        s.unrest.spark(s.unrest.cause("anger")!!, 1.5)
        s.state.unrest.movements.single().phase = MovementPhase.INSURRECTION
        val liberty = s.laws.indices().liberty
        val r = s.nationalActions.perform("article_16")
        assertTrue(r.isSuccess, r.exceptionOrNull()?.message)
        assertTrue(s.legislation.article16Active())
        assertTrue(s.laws.indices().liberty < liberty, "les libertés reculent")
        s.state.government.parliamentSupport = 0.1
        s.legislation.addToLaw("param:speed_limit", 90.0, null)
        val bill = s.legislation.depositLaw().getOrThrow()
        assertEquals(90.0, s.levers.current("param:speed_limit"), "appliqué sans vote")
        assertTrue(s.state.legislation.bills.any { it.id == bill.id && it.status == BillStatus.ARTICLE_16 })
        clock.advanceWorldDays(95.0); s.advanceToNow()
        assertTrue(!s.legislation.article16Active(), "les pleins pouvoirs prennent fin")
    }
}
