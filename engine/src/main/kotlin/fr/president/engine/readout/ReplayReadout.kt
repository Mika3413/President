package fr.president.engine.readout

import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalEntry
import fr.president.engine.stats.ReplayFrame
import fr.president.engine.util.Formatting

/**
 * Relecture accélérée du mandat : semaine après semaine, la carte de popularité, les zones
 * occupées, les chiffres clés et les grands titres du moment. Les courbes et les images de carte
 * sont relevées ensemble chaque semaine ; une ancienne partie peut avoir moins d'images que de points.
 */
class ReplayReadout(private val ctx: SimulationContext) {
    data class Metric(val key: String, val label: String, val value: String, val delta: String, val tone: Tone, val history: List<Double>)
    data class Step(
        val index: Int,
        val date: String,
        val metrics: List<Metric>,
        val headlines: List<JournalEntry>,
        val enemies: List<String>,
        val enemyIds: List<String>,
        val approval: Map<String, Double>,
        val occupied: Map<String, String>,
    )

    private val stats get() = ctx.state.stats
    val frames: List<ReplayFrame> get() = stats.replay
    val count: Int get() = frames.size

    /** Faits marquants du mandat entier (pour les repères de la frise). */
    fun milestones(): List<Pair<Int, JournalEntry>> {
        val f = frames
        if (f.isEmpty()) return emptyList()
        return stats.journal.filter { e ->
            e.kind in MILESTONES || e.kind == "Société" && e.tone == Tone.GOOD || e.kind in WAR_KINDS && (e.tone == Tone.BAD || e.tone == Tone.WARNING)
        }
            .mapNotNull { e -> indexAt(e.time.seconds)?.let { it to e } }
    }

    fun step(i: Int): Step? {
        val f = frames.getOrNull(i) ?: return null
        val next = frames.getOrNull(i + 1)
        val order = stats.replayDepartments
        val approval = order.indices.mapNotNull { k -> f.approvalAt(k)?.let { order[k] to it } }.toMap()
        val occupied = f.occupied.mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val from = f.time.seconds
        val to = next?.time?.seconds ?: Long.MAX_VALUE
        // Titres de la semaine : d'abord ce qui a marqué (bon ou mauvais), au plus trois.
        val headlines = stats.journal.filter { it.time.seconds in from until to }
            .sortedBy { when (it.tone) { Tone.BAD -> 0; Tone.GOOD -> 1; Tone.WARNING -> 2; Tone.NEUTRAL -> 3 } }.take(MAX_HEADLINES)
        val enemies = f.enemies.map { ctx.db.countries[it]?.definition?.name ?: it }
        return Step(i, Formatting.date(f.time), METRICS.mapNotNull { metric(it, i) }, headlines, enemies, f.enemies, approval, occupied)
    }

    private fun metric(def: MetricDef, i: Int): Metric? {
        val all = stats.series[def.key]?.all() ?: return null
        // Aligne les séries sur les images (même relevé hebdomadaire, alignées par la fin).
        val offset = all.size - count
        val k = i + offset
        if (k !in all.indices) return null
        val v = all[k]
        val start = all[maxOf(0, offset)]
        val d = v - start
        val better = if (def.higherIsBetter) d > def.epsilon else d < -def.epsilon
        val worse = if (def.higherIsBetter) d < -def.epsilon else d > def.epsilon
        val tone = if (better) Tone.GOOD else if (worse) Tone.BAD else Tone.NEUTRAL
        val history = all.subList(maxOf(0, offset), k + 1)
        return Metric(def.key, def.label, def.format(v), (if (d >= 0) "+" else "−") + def.format(Math.abs(d)).removeSuffix(" %").let { if (def.percent) "$it pt" else it }, tone, history)
    }

    private fun indexAt(seconds: Long): Int? {
        val f = frames
        if (f.isEmpty() || seconds < f.first().time.seconds) return null
        val idx = f.indexOfLast { it.time.seconds <= seconds }
        return idx.takeIf { it >= 0 }
    }

    private class MetricDef(val key: String, val label: String, val higherIsBetter: Boolean, val percent: Boolean = true, val epsilon: Double = 0.002, val format: (Double) -> String = Formatting::percent)

    private companion object {
        const val MAX_HEADLINES = 3
        val MILESTONES = setOf("Élections", "Guerre", "Loi", "Président", "Article 16", "Gouvernement")
        val WAR_KINDS = setOf("Défense", "Militaire", "Opération")
        val METRICS = listOf(
            MetricDef("approval", "Popularité", higherIsBetter = true),
            MetricDef("unemployment", "Chômage", higherIsBetter = false),
            MetricDef("growth", "Croissance", higherIsBetter = true),
            MetricDef("debt", "Dette publique", higherIsBetter = false, epsilon = 0.005),
            MetricDef("inflation", "Inflation", higherIsBetter = false),
        )
    }
}
