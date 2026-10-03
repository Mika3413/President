package fr.president.engine.diplomacy

import fr.president.engine.politics.Traits
import fr.president.engine.simulation.SimulationContext

/**
 * Analyse d'une proposition par un gouvernement IA : besoins, ressources, relation,
 * confiance, prudence face à la durée, exigence du dirigeant.
 */
class ProposalEvaluator(private val ctx: SimulationContext) {

    data class Evaluation(
        val utility: Double,
        val threshold: Double,
        val reasons: List<String>,
        val breakdown: List<Pair<String, Double>>,
    ) {
        val acceptable: Boolean get() = utility >= threshold
        val gap: Double get() = threshold - utility
    }

    fun evaluate(evaluator: String, partner: String, clauses: List<Clause>, durationYears: Int): Evaluation {
        val p = ctx.db.diplomacy.evaluation
        val valuator = ClauseValuator(ctx)
        val relations = RelationCalculator(ctx)
        val leader = ctx.state.characters.getValue(ctx.state.countries.getValue(evaluator).leaderId)

        val values = clauses.map { it to valuator.value(evaluator, partner, it, durationYears) }
        val relationTerm = p.relationWeight * (relations.score(evaluator, partner) - NEUTRAL)
        val trustTerm = p.trustWeight * (relations.trust(evaluator, partner) - NEUTRAL)
        val cautionTerm = -p.durationCautionPerYear * durationYears * leader.trait(Traits.CAUTION)
        val utility = values.sumOf { it.second.value } + relationTerm + trustTerm + cautionTerm
        val threshold = p.baseThreshold + p.toughnessThreshold * (leader.trait(Traits.TOUGHNESS) - NEUTRAL)

        val reasons = values.filter { it.second.value < 0 || it.second.reason != null }
            .sortedBy { it.second.value }.mapNotNull { it.second.reason }.toMutableList()
        if (relationTerm < -REASON_MARGIN) reasons += "l'état de nos relations ne s'y prête guère"
        if (trustTerm < -REASON_MARGIN) reasons += "vos engagements passés n'ont pas toujours été tenus"
        if (cautionTerm < -REASON_MARGIN * 2) reasons += "une telle durée nous paraît imprudente"

        val breakdown = values.map { it.first.type to it.second.value } +
            listOf("relation" to relationTerm, "confiance" to trustTerm, "prudence" to cautionTerm)
        ctx.log(
            "ai.diplomacy",
            "$evaluator évalue ${clauses.joinToString { it.type }} de $partner : utilité %.3f / seuil %.3f %s".format(
                utility, threshold, breakdown.joinToString { "${it.first}=%.3f".format(it.second) },
            ),
        )
        return Evaluation(utility, threshold, reasons.distinct(), breakdown)
    }

    private companion object {
        const val NEUTRAL = 0.5
        const val REASON_MARGIN = 0.03
    }
}
