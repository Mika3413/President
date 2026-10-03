package fr.president.engine.setup

import fr.president.engine.data.CountryData
import fr.president.engine.government.Priority
import fr.president.engine.politics.Character
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.CharacterSpec
import fr.president.engine.simulation.SimulationContext

/** Génère le personnel politique fictif : gouvernement, viviers de ministrables, élus locaux. */
class PoliticalSetup(private val ctx: SimulationContext) {
    private val year = ctx.now.toDateTime().year

    fun createGovernment(country: CountryData, president: Character) {
        val def = country.government ?: return
        val gov = ctx.state.government
        for (ministry in def.ministries) {
            val role = if (ministry.isPrimeMinister) CharacterRole.PRIME_MINISTER else CharacterRole.MINISTER
            val minister = person(country.id, role, ministry.id, president.economicLeaning, GOVERNMENT_SPREAD)
            minister.relationWithPlayer = (minister.relationWithPlayer + LOYALTY_BONUS).coerceAtMost(1.0)
            if (ministry.isPrimeMinister) gov.primeMinisterId = minister.id else gov.ministers[ministry.id] = minister.id
            gov.priorities[ministry.id] = Priority.NORMAL
            refreshCandidates(country, ministry.id, president.economicLeaning)
        }
        gov.parliamentSupport = def.parliament.baseSupport
    }

    /** Vivier de personnalités nommables, de sensibilités variées. */
    fun refreshCandidates(country: CountryData, ministryId: String, centre: Double) {
        val def = country.government ?: return
        val pool = ctx.state.government.candidates.getOrPut(ministryId) { mutableListOf() }
        pool.removeAll { id -> ctx.state.characters[id]?.role != CharacterRole.MINISTER_CANDIDATE }
        while (pool.size < def.candidatesPerMinistry) {
            pool += person(country.id, CharacterRole.MINISTER_CANDIDATE, ministryId, centre, CANDIDATE_SPREAD).id
        }
    }

    fun createLocalActors(country: CountryData) {
        val territory = ctx.state.territory
        for (region in territory.regions.values) {
            region.presidentId = person(country.id, CharacterRole.REGION_PRESIDENT, region.code, 0.0, LOCAL_SPREAD).id
            region.prefectId = person(country.id, CharacterRole.PREFECT, region.code, 0.0, PREFECT_SPREAD).id
        }
        for (dept in territory.departments.values) {
            dept.presidentId = person(country.id, CharacterRole.DEPARTMENT_PRESIDENT, dept.code, 0.0, LOCAL_SPREAD).id
        }
        for (city in territory.cities.values) {
            city.mayorId = person(country.id, CharacterRole.MAYOR, city.id, 0.0, LOCAL_SPREAD).id
        }
    }

    fun person(countryId: String, role: CharacterRole, ref: String?, leaning: Double, spread: Double): Character {
        val c = ctx.characters.generate(
            ctx.state.newId("chr"),
            CharacterSpec(countryId, role, ref, year, economicLeaning = leaning, leaningSpread = spread),
            ctx.rng,
        )
        ctx.state.characters[c.id] = c
        return c
    }

    private companion object {
        const val GOVERNMENT_SPREAD = 0.2
        const val CANDIDATE_SPREAD = 0.5
        const val LOCAL_SPREAD = 0.6
        const val PREFECT_SPREAD = 0.15
        const val LOYALTY_BONUS = 0.15
    }
}
