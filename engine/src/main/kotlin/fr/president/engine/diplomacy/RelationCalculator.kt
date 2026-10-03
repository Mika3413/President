package fr.president.engine.diplomacy

import fr.president.engine.simulation.SimulationContext
import fr.president.engine.time.WorldTime
import kotlin.math.abs

/**
 * Relation d'un pays envers un autre, calculée à partir de sa mémoire diplomatique.
 * Le joueur ne voit jamais le score brut : seulement un libellé et les facteurs principaux.
 */
class RelationCalculator(private val ctx: SimulationContext) {

    data class Factor(val label: String, val weight: Double)

    fun score(observer: String, target: String): Double =
        (NEUTRAL + currentWeights(observer, target).sumOf { it.second }).coerceIn(0.0, 1.0)

    fun trust(observer: String, target: String): Double =
        (NEUTRAL + currentWeights(observer, target)
            .filter { ctx.db.diplomacy.memoryKind(it.first.kind)?.affectsTrust == true }
            .sumOf { it.second } * TRUST_AMPLIFICATION).coerceIn(0.0, 1.0)

    fun label(score: Double): String =
        ctx.db.diplomacy.relationLabels.firstOrNull { score <= it.upTo }?.label
            ?: ctx.db.diplomacy.relationLabels.last().label

    /** Facteurs expliquant la relation, regroupés par type de souvenir, du plus au moins important. */
    fun factors(observer: String, target: String, limit: Int = MAX_FACTORS): List<Factor> =
        currentWeights(observer, target)
            .groupBy { it.first.kind to (it.second >= 0) }
            .map { (key, entries) ->
                val kind = ctx.db.diplomacy.memoryKind(key.first)
                val weight = entries.sumOf { it.second }
                val detail = entries.maxByOrNull { abs(it.second) }?.first?.detail.orEmpty()
                val base = if (weight >= 0) kind?.positiveLabel else kind?.negativeLabel
                Factor(listOfNotNull(base ?: key.first, detail.ifBlank { null }).joinToString(" : "), weight)
            }
            .filter { abs(it.weight) >= VISIBLE_WEIGHT }
            .sortedByDescending { abs(it.weight) }
            .take(limit)

    private fun currentWeights(observer: String, target: String): List<Pair<DiplomaticMemory, Double>> {
        val relation = ctx.state.diplomacy.relations[DiplomacyState.key(observer, target)] ?: return emptyList()
        return relation.memories.map { it to decayed(it, ctx.now) }
    }

    private fun decayed(memory: DiplomaticMemory, now: WorldTime): Double {
        val halfLife = ctx.db.diplomacy.memoryKind(memory.kind)?.halfLifeDays ?: DEFAULT_HALF_LIFE
        if (halfLife <= 0) return memory.weight
        return memory.weight * Math.pow(HALF, memory.time.daysUntil(now) / halfLife)
    }

    /** Oublie les souvenirs devenus négligeables. */
    fun prune(observer: String, target: String) {
        val relation = ctx.state.diplomacy.relations[DiplomacyState.key(observer, target)] ?: return
        relation.memories.removeAll { abs(decayed(it, ctx.now)) < FORGET_WEIGHT }
    }

    private companion object {
        const val NEUTRAL = 0.5
        const val HALF = 0.5
        const val DEFAULT_HALF_LIFE = 365.0
        const val TRUST_AMPLIFICATION = 2.0
        const val MAX_FACTORS = 5
        const val VISIBLE_WEIGHT = 0.01
        const val FORGET_WEIGHT = 0.002
    }
}
