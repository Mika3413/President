package fr.president.engine.diplomacy

import fr.president.engine.simulation.SimulationContext

/**
 * Construit une contre-proposition en ajustant pas à pas les paramètres des clauses
 * (volumes, prix, montants, durée) jusqu'à rendre l'accord acceptable pour l'IA.
 */
class CounterProposalBuilder(private val ctx: SimulationContext) {

    data class Counter(val clauses: List<Clause>, val durationYears: Int)

    fun build(evaluator: String, partner: String, clauses: List<Clause>, durationYears: Int): Counter? {
        val p = ctx.db.diplomacy.evaluation
        val evaluatorEngine = ProposalEvaluator(ctx)
        var current = Counter(clauses, durationYears)
        var evaluation = evaluatorEngine.evaluate(evaluator, partner, current.clauses, current.durationYears)
        if (evaluation.gap > p.counterReachableGap) return null
        repeat(p.maxCounterSteps) {
            if (evaluation.acceptable) return current
            val best = neighbours(current).map { candidate ->
                candidate to evaluatorEngine.evaluate(evaluator, partner, candidate.clauses, candidate.durationYears)
            }.maxByOrNull { it.second.utility } ?: return null
            if (best.second.utility <= evaluation.utility) return null
            current = best.first
            evaluation = best.second
        }
        return current.takeIf { evaluation.acceptable }
    }

    /** Toutes les variantes obtenues en modifiant un seul paramètre d'un cran. */
    private fun neighbours(counter: Counter): List<Counter> {
        val result = mutableListOf<Counter>()
        counter.clauses.forEachIndexed { index, clause ->
            val def = ctx.db.diplomacy.clause(clause.type)
            for (param in def.params) {
                val value = clause.params[param.id] ?: param.default
                for (direction in listOf(-1.0, 1.0)) {
                    val next = (value + direction * param.step).coerceIn(param.min, param.max)
                    if (next == value) continue
                    val updated = clause.copy(params = clause.params + (param.id to next))
                    result += counter.copy(clauses = counter.clauses.toMutableList().also { it[index] = updated })
                }
            }
        }
        if (counter.durationYears > MIN_DURATION) result += counter.copy(durationYears = counter.durationYears - 1)
        return result
    }

    private companion object {
        const val MIN_DURATION = 1
    }
}
