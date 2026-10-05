package fr.president.engine

import fr.president.engine.government.PolicyKind
import fr.president.engine.government.PolicyStatus
import fr.president.engine.legislation.BillStatus
import fr.president.engine.legislation.Channel
import fr.president.engine.legislation.MeasureConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegislationTest {
    @Test
    fun everyLeverHasOneValueAndAChannel() {
        val s = TestData.newSession()
        val all = s.levers.all()
        assertEquals(all.size, all.map { it.id }.toSet().size, "un identifiant par levier")
        assertTrue(all.any { it.id == "tax:vat" && it.channel == Channel.BUDGET })
        assertTrue(all.any { it.id == "param:pension_age" && it.channel == Channel.LAW })
        assertTrue(all.any { it.id == "param:smic_boost" && it.channel == Channel.DECREE })
        assertTrue(all.none { it.id == "reform:pension_age_65" }, "la réforme est remplacée par le réglage chiffré")
        assertEquals(20.0, s.levers.current("tax:vat"))
        assertEquals(64.0, s.levers.current("param:pension_age"))
        assertEquals(35.0, s.levers.current("law:work_week"))
    }

    @Test
    fun theAnnualBudgetCollectsTaxChanges() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.government.parliamentSupport = 0.9
        clock.advanceWorldDays(1.0); s.advanceToNow()
        val plf = s.legislation.openPlf
        assertNotNull(plf, "la loi de finances est déposée à l'automne")
        assertNull(s.legislation.setBudget("tax:vat", 21.0))
        assertNull(s.legislation.setBudget("fiscal:wealth", 0.5))
        assertEquals(2, plf.changes.size, "les réglages rejoignent la loi de finances en discussion")
        assertEquals(21.0, s.legislation.budgetTarget("tax:vat"))
        assertEquals(20.0, s.levers.current("tax:vat"), "rien ne change avant le vote")
        assertTrue(s.legislation.setBudget("law:cannabis", 1.0) != null, "une loi ne passe pas par le budget")
        clock.advanceWorldDays(80.0); s.advanceToNow()
        assertTrue(plf.status == PolicyStatus.ADOPTED || plf.status == PolicyStatus.REJECTED)
        if (plf.status == PolicyStatus.ADOPTED) assertEquals(21.0, s.levers.current("tax:vat"))
        assertTrue(s.state.legislation.bills.any { it.title.startsWith("Loi de finances pour") })
    }

    @Test
    fun aCorrectiveBudgetCanBeVotedAnyTime() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.government.parliamentSupport = 0.95
        clock.advanceWorldDays(90.0); s.advanceToNow()
        s.legislation.setBudget("spend:health", 1.1)
        val p = s.legislation.depositCorrective().getOrThrow()
        assertEquals(PolicyKind.BUDGET_BILL, p.kind)
        assertTrue(s.state.legislation.budgetDraft.isEmpty())
        clock.advanceWorldDays(20.0); s.advanceToNow()
        if (p.status == PolicyStatus.ADOPTED) assertEquals(1.1, s.levers.current("spend:health"), 1e-9)
    }

    @Test
    fun aLawBillGroupsSeveralChangesWithAProposedName() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.government.parliamentSupport = 0.95
        assertNull(s.legislation.addToLaw("law:work_week", 37.0))
        assertNull(s.legislation.addToLaw("param:pension_age", 64.5))
        assertNull(s.legislation.addToLaw("law:sunday_work", 1.0))
        assertTrue(s.legislation.addToLaw("tax:vat", 21.0) != null, "un impôt passe par le budget")
        assertTrue(s.legislation.lawName().startsWith("Loi"))
        val preview = s.legislation.preview(s.legislation.lawChanges())
        assertTrue(preview.spending < 0, "reculer l'âge de la retraite fait des économies")
        s.legislation.setLawName("Loi pour le travail")
        val p = s.legislation.depositLaw().getOrThrow()
        assertEquals("Loi pour le travail", p.title)
        assertNotNull(s.legislation.pendingFor("law:work_week"))
        clock.advanceWorldDays(40.0); s.advanceToNow()
        if (p.status == PolicyStatus.ADOPTED) {
            assertEquals(37.0, s.levers.current("law:work_week"))
            assertEquals(64.5, s.levers.current("param:pension_age"))
            assertTrue("pension_age_65" in s.state.policy.adoptedReforms, "reculer l'âge vaut réforme des retraites")
            val record = s.state.legislation.bills.last { it.id == p.id }
            assertEquals(BillStatus.ADOPTED, record.status)
            assertTrue(s.legislation.abrogate(record.id).isSuccess)
            assertTrue(s.legislation.lawChanges().any { it.lever == "param:pension_age" && it.to == 64.0 })
        }
    }

    @Test
    fun decreesApplyAtOnce() {
        val s = TestData.newSession()
        assertTrue(s.legislation.decree("param:smic_boost", 3.0).isSuccess)
        assertEquals(3.0, s.levers.current("param:smic_boost"))
        assertTrue("minimum_wage" in s.state.policy.adoptedReforms)
        assertTrue(s.legislation.decree("param:smic_boost", 4.0).isFailure, "un délai entre deux décrets")
        assertTrue(s.legislation.decree("param:pension_age", 63.0).isFailure, "l'âge de la retraite demande une loi")
        assertTrue(s.levers.lever("param:smic_boost")!!.min >= 3.0, "le SMIC ne se baisse pas")
    }

    @Test
    fun theBuilderComputesRealisticMeasures() {
        val s = TestData.newSession()
        val cfg = MeasureConfig("surtax", "doctors", threshold = 120_000.0, exemptRural = true)
        val lever = s.levers.measureLever(cfg)!!
        assertEquals(Channel.BUDGET, lever.channel)
        val five = s.legislation.preview(listOf(fr.president.engine.legislation.LeverChange(cfg.key, 0.0, 5.0, measure = cfg)))
        val six = s.legislation.preview(listOf(fr.president.engine.legislation.LeverChange(cfg.key, 0.0, 6.0, measure = cfg)))
        assertTrue(five.revenue > 0 && six.revenue > five.revenue, "6 % rapporte plus que 5 %")
        assertTrue((six.groups["self_employed"] ?: 0.0) < (five.groups["self_employed"] ?: 0.0), "et fâche davantage")
        assertTrue(five.censure > 0, "taxer une seule profession : risque de rupture d'égalité")
        // Effet Laffer : une surtaxe énorme rapporte moins qu'une surtaxe forte.
        val huge = s.legislation.preview(listOf(fr.president.engine.legislation.LeverChange(cfg.key, 0.0, 30.0, measure = cfg)))
        val strong = s.legislation.preview(listOf(fr.president.engine.legislation.LeverChange(cfg.key, 0.0, 12.0, measure = cfg)))
        assertTrue(huge.revenue < strong.revenue * 2.5)
        // Dans le budget, appliqué au vote.
        assertNull(s.legislation.setBudget(cfg.key, 5.0, cfg))
        val revenue = s.state.playerCountry.economy.revenueBillions
        s.levers.apply(fr.president.engine.legislation.LeverChange(cfg.key, 0.0, 5.0, measure = cfg))
        assertTrue(s.state.playerCountry.economy.revenueBillions > revenue)
        assertEquals(5.0, s.levers.current(cfg.key))
        // Même règle, même cible : la nouvelle valeur remplace l'ancienne.
        s.levers.apply(fr.president.engine.legislation.LeverChange(cfg.key, 5.0, 6.0, measure = cfg))
        assertEquals(1, s.state.legislation.measures.size)
        // Une interdiction passe par la loi.
        val ban = MeasureConfig("ban", "single_plastic")
        assertNull(s.legislation.addToLaw(ban.key, 1.0, ban))
        assertEquals(1, s.legislation.lawChanges().size)
    }

    @Test
    fun theConstitutionalCouncilCanStrikeDownArticles() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.government.parliamentSupport = 0.99
        val cfg = MeasureConfig("surtax", "athletes")
        s.legislation.setBudget(cfg.key, 30.0, cfg)
        var censured = false
        repeat(1) {
            val p = s.legislation.depositCorrective().getOrThrow()
            clock.advanceWorldDays(20.0); s.advanceToNow()
            censured = p.changes.any { it.censured } || p.status == PolicyStatus.REJECTED
        }
        val preview = s.legislation.levers.preview(cfg.key, 0.0, 30.0)
        assertTrue(preview.censure > 0.5, "une surtaxe confiscatoire a de fortes chances d'être censurée")
        assertTrue(censured || s.levers.current(cfg.key) == 30.0)
    }

    @Test
    fun theOldCommandsStillWork() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.government.parliamentSupport = 0.95
        val p = s.policy.proposeReform("pension_age_65").getOrThrow()
        assertEquals(PolicyKind.LAW_BILL, p.kind)
        assertNotNull(s.policy.reformBlocker("pension_age_65"))
        clock.advanceWorldDays(60.0); s.advanceToNow()
        if (p.status == PolicyStatus.ADOPTED) assertEquals(65.0, s.levers.current("param:pension_age"))
    }
}
