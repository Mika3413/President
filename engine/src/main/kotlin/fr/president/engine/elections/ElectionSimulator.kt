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
        var turnoutSum = 0.0
        for (partition in groups.partitions) {
            for (group in groups.groups.filter { it.partition == partition.id }) {
                val approval = ctx.state.opinion.groups[group.id]?.effective ?: group.baseApproval
                val turnout = turnout(group.baseTurnout, approval)
                turnoutSum += group.populationShare * turnout
                val prefs = preferences(group.economicLeaning, group.socialLeaning, approval, candidates, promises.electoralEffect(group.id))
                prefs.forEach { (id, share) -> votes.merge(id, group.populationShare * turnout * share, Double::plus) }
            }
        }
        if (noise > 0) votes.replaceAll { _, v -> (v * (1.0 + ctx.rng.nextGaussian() * noise)).coerceAtLeast(0.0) }
        val total = votes.values.sum()
        return RoundResult(
            turnout = turnoutSum / groups.partitions.size,
            shares = votes.mapValues { it.value / total },
        )
    }

    private fun turnout(base: Double, approval: Double): Double =
        (base + elections.turnoutDiscontentWeight * (NEUTRAL - approval).coerceAtLeast(0.0) +
            elections.turnoutEnthusiasmWeight * (approval - NEUTRAL).coerceAtLeast(0.0)).clamp01()

    private val promises = PromiseEvaluator(ctx)

    private fun preferences(econ: Double, social: Double, approval: Double, candidates: List<Candidate>, promiseEffect: Double): Map<String, Double> {
        val utilities = candidates.associate { c ->
            val family = elections.families.first { it.id == c.familyId }
            val distance = hypot(econ - c.economicPosition, social - c.socialPosition) / MAX_DISTANCE
            var u = elections.affinityWeight * (1.0 - distance) + family.baseStrength + c.momentum
            if (c.incumbent) {
                u += elections.incumbentRecordWeight * (approval - NEUTRAL)
                u -= elections.scandalPenalty * (ctx.state.characters[c.characterId]?.scandals ?: 0)
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
        /** Distance maximale entre deux positions dans l'espace [-1,1]². */
        const val MAX_DISTANCE = 2.8284271247
        const val LOCAL_SWING = 1.2
    }
}
