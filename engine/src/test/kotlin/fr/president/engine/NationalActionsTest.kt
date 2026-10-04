package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NationalActionsTest {
    @Test
    fun everyDecisionHasValidTargetsAndCategory() {
        val s = TestData.newSession()
        val db = s.db
        val data = db.country(s.state.player.countryId)
        val categories = s.nationalActions.categories.map { it.id }.toSet()
        val groups = data.socialGroups!!.groups.map { it.id }.toSet()
        val spending = data.economy.budget!!.spending.map { it.id }.toSet()
        val alliances = db.alliances.map { it.id }.toSet()
        assertTrue(s.nationalActions.definitions.size >= 40)
        for (def in s.nationalActions.definitions) {
            assertTrue(def.category in categories, "${def.id} : rubrique inconnue ${def.category}")
            for (spec in def.immediate + def.onCompletion) {
                val p = spec.target.split('.')
                val ok = when (p[0]) {
                    "opinion" -> p[1] == "national" || p.getOrNull(2) in groups
                    "spending" -> p[1] in spending
                    "alliance" -> p[1] in alliances
                    "memory" -> p[1] in s.state.countries
                    "quality" -> p[1] in s.state.playerCountry.services
                    "economy", "budget", "government", "military", "demography", "energy", "president", "war" -> true
                    else -> false
                }
                assertTrue(ok, "${def.id} : cible invalide ${spec.target}")
            }
            // Chaque décision annonce clairement au moins un effet.
            assertTrue(s.nationalActions.actions(def.category).first { it.def.id == def.id }.effects.isNotEmpty(), def.id)
        }
    }

    @Test
    fun everyDecisionCanBeTakenOnceThenWaits() {
        val s = TestData.newSession()
        for (def in s.nationalActions.definitions) {
            if (s.nationalActions.actions(def.category).first { it.def.id == def.id }.locked) {
                assertTrue(s.nationalActions.perform(def.id).isFailure, "${def.id} verrouillée")
                continue
            }
            val r = s.nationalActions.perform(def.id)
            assertTrue(r.isSuccess, "${def.id} : ${r.exceptionOrNull()?.message}")
            assertNotNull(s.nationalActions.actions(def.category).first { it.def.id == def.id }.blocker, def.id)
            assertTrue(s.nationalActions.perform(def.id).isFailure, def.id)
        }
    }

    @Test
    fun planRunsThenProducesItsEffects() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val groups = s.state.opinion.groups
        val young = groups.getValue("young").shock
        assertTrue(s.nationalActions.perform("youth_jobs").isSuccess)
        assertTrue(groups.getValue("young").shock > young)
        assertEquals(1, s.nationalActions.running().size)
        clock.advanceWorldDays(370.0)
        s.advanceToNow()
        assertEquals(0, s.nationalActions.running().size)
    }

    @Test
    fun crisisDecisionUnlocksWithTheSituation() {
        val s = TestData.newSession()
        val view = { s.nationalActions.actions("social").first { it.def.id == "jobs_emergency" } }
        s.state.playerCountry.economy.unemployment = 0.07
        assertTrue(view().locked)
        assertTrue(s.nationalActions.perform("jobs_emergency").isFailure)
        s.state.playerCountry.economy.unemployment = 0.10
        assertTrue(!view().locked && view().blocker == null)
        assertTrue(s.nationalActions.situational().any { it.def.id == "jobs_emergency" })
        assertTrue(view().needsConfirmation)
        assertTrue(view().forecast.any { it.text.startsWith("Déficit") })
        assertTrue(s.nationalActions.perform("jobs_emergency").isSuccess)
    }

    @Test
    fun briefingSummarisesAnAbsence() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val from = s.state.time
        clock.advanceWorldDays(30.0)
        s.advanceToNow()
        val b = s.briefing.since(from)
        assertTrue(b.days > 29 && b.changes.isNotEmpty() && b.upcoming.isNotEmpty())
    }
}
