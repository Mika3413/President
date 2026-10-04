package fr.president.engine.elections

import fr.president.engine.data.ElectionsDefinition
import fr.president.engine.data.SocialGroupsDefinition
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.clamp01
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Modèle de vote par groupe social : participation, proximité idéologique,
 * jugement du bilan du sortant (opinion de chaque groupe), dynamique de campagne.
 * Pas de règle « popularité > 50 % = victoire ».
 */
class ElectionSimulator(private val ctx: SimulationContext) {
    private val elections: ElectionsDefinition = ctx.playerData.elections!!
    private val groups: SocialGroupsDefinition = ctx.playerData.socialGroups!!

    fun simulate(candidates: List<Candidate>, noise: Double = 0.0): RoundResult {
        val votes = mutableMapOf<String, Double>()
        val incumbent = candidates.firstOrNull { it.incumbent }
        var turnoutSum = 0.0
        for (partition in groups.partitions) {
            for (group in groups.groups.filter { it.partition == partition.id }) {
                val approval = ctx.state.opinion.groups[group.id]?.effective ?: group.baseApproval
                val turnout = turnout(group.baseTurnout, approval)
                turnoutSum += group.populationShare * turnout
                val promiseEffect = promises.electoralEffect(group.id)
                // Diversité interne du groupe : des électeurs plus à gauche, plus à droite, plus ou moins conservateurs.
                for ((dx, dy, w) in subVoters()) {
                    val econ = group.economicLeaning + dx
                    val social = group.socialLeaning + dy
                    val subApproval = (approval + loyalty(group.economicLeaning, group.socialLeaning, econ, social, incumbent)).clamp01()
                    val prefs = preferences(econ, social, subApproval, candidates, promiseEffect)
                    prefs.forEach { (id, share) -> votes.merge(id, group.populationShare * turnout * share * w, Double::plus) }
                }
            }
        }
        if (noise > 0) votes.replaceAll { _, v -> (v * (1.0 + ctx.rng.nextGaussian() * noise)).coerceAtLeast(0.0) }
        val total = votes.values.sum()
        return RoundResult(
            turnout = turnoutSum / groups.partitions.size,
            shares = votes.mapValues { it.value / total },
        )
    }

    /** Poids des scandales : chaque affaire pèse moins à mesure qu'elle s'éloigne (demi-vie). */
    private fun scandalWeight(characterId: String): Double {
        val c = ctx.state.characters[characterId] ?: return 0.0
        if (c.scandalDates.isEmpty()) return c.scandals * LEGACY_SCANDAL_WEIGHT
        return c.scandalDates.sumOf { Math.pow(HALF, (ctx.now.seconds - it) / SECONDS_PER_DAY / SCANDAL_HALF_LIFE_DAYS) }
    }

    /** Grille 3×3 de positions autour du centre du groupe, pondérée (le centre compte davantage). */
    private fun subVoters(): List<Triple<Double, Double, Double>> {
        val spread = elections.voterSpread
        if (spread <= 0.0) return listOf(Triple(0.0, 0.0, 1.0))
        val steps = listOf(-1.0 to SIDE_WEIGHT, 0.0 to CENTER_WEIGHT, 1.0 to SIDE_WEIGHT)
        return steps.flatMap { (sx, wx) -> steps.map { (sy, wy) -> Triple(sx * spread, sy * spread, wx * wy) } }
    }

    /** Les électeurs proches du sortant jugent son bilan avec plus d'indulgence (et inversement). */
    private fun loyalty(groupEcon: Double, groupSocial: Double, econ: Double, social: Double, incumbent: Candidate?): Double {
        incumbent ?: return 0.0
        val center = hypot(groupEcon - incumbent.economicPosition, groupSocial - incumbent.socialPosition)
        val here = hypot(econ - incumbent.economicPosition, social - incumbent.socialPosition)
        return elections.partisanLoyalty * (center - here)
    }

    private fun turnout(base: Double, approval: Double): Double =
        (base + elections.turnoutDiscontentWeight * (NEUTRAL - approval).coerceAtLeast(0.0) +
            elections.turnoutEnthusiasmWeight * (approval - NEUTRAL).coerceAtLeast(0.0)).clamp01()

    private val promises = PromiseEvaluator(ctx)

    private fun preferences(econ: Double, social: Double, approval: Double, candidates: List<Candidate>, promiseEffect: Double): Map<String, Double> {
        val utilities = candidates.associate { c ->
            val family = elections.families.first { it.id == c.familyId }
            val distance = hypot(econ - c.economicPosition, social - c.socialPosition) / MAX_DISTANCE
            // Au pouvoir, le bilan du président pèse plus que l'implantation structurelle de son parti.
            val strength = if (c.incumbent) family.baseStrength * INCUMBENT_STRENGTH_SHARE else family.baseStrength
            var u = elections.affinityWeight * (1.0 - distance) + strength + c.momentum
            if (c.incumbent) {
                u += elections.incumbentRecordWeight * (approval - NEUTRAL) + elections.incumbentBonus
                u -= elections.scandalPenalty * scandalWeight(c.characterId)
                u += promiseEffect
            }
            c.characterId to u
        }
        val max = utilities.values.maxOrNull() ?: 0.0
        val weights = utilities.mapValues { exp((it.value - max) / elections.choiceTemperature) }
        val sum = weights.values.sum()
        return weights.mapValues { it.value / sum }
    }

    /** Part du sortant par département au second tour, déduite de l'opinion locale. */
    fun incumbentShareByDepartment(nationalShare: Double): Map<String, Double> {
        val national = ctx.state.opinion.nationalApproval
        return ctx.state.territory.departments.mapValues { (_, d) ->
            (nationalShare + LOCAL_SWING * (d.approval - national)).clamp01()
        }
    }

    private companion object {
        const val NEUTRAL = 0.5
        const val INCUMBENT_STRENGTH_SHARE = 0.5
        const val SIDE_WEIGHT = 0.25
        const val HALF = 0.5
        const val SECONDS_PER_DAY = 86_400.0
        const val SCANDAL_HALF_LIFE_DAYS = 500.0
        const val LEGACY_SCANDAL_WEIGHT = 0.5
        const val CENTER_WEIGHT = 0.5
        /** Distance maximale entre deux positions dans l'espace [-1,1]². */
        const val MAX_DISTANCE = 2.8284271247
        const val LOCAL_SWING = 1.2
    }
}
