package fr.president.engine.stats

import fr.president.engine.readout.Tone
import fr.president.engine.time.WorldTime
import fr.president.engine.util.History
import kotlinx.serialization.Serializable

/**
 * Statistiques du mandat : séries hebdomadaires (courbes de l'interface) et journal des faits
 * marquants. Les séries sont identifiées par une clé : « approval », « group.retirees »,
 * « relation.DEU »...
 */
@Serializable
class StatsState(
    val series: MutableMap<String, History> = mutableMapOf(),
    val journal: MutableList<JournalEntry> = mutableListOf(),
    var lastRecordDay: Long = Long.MIN_VALUE,
    /** Relecture du mandat : une image de la carte par semaine (voir [ReplayFrame]). */
    val replay: MutableList<ReplayFrame> = mutableListOf(),
    /** Ordre des départements dans [ReplayFrame.approval]. */
    val replayDepartments: MutableList<String> = mutableListOf(),
)

/**
 * Image hebdomadaire de la carte : popularité de chaque département (un caractère par
 * département, de « 0 » à « o » : 64 crans), zones occupées (« zone=pays ») et pays en guerre contre nous.
 */
@Serializable
class ReplayFrame(
    val time: WorldTime,
    val approval: String = "",
    val occupied: List<String> = emptyList(),
    val enemies: List<String> = emptyList(),
) {
    fun approvalAt(index: Int): Double? = approval.getOrNull(index)?.let { (it - BASE).toDouble() / STEPS }

    companion object {
        const val BASE = '0'
        const val STEPS = 63

        fun encode(v: Double): Char = BASE + (v.coerceIn(0.0, 1.0) * STEPS).toInt()
    }
}

/** Fait marquant du mandat, daté. */
@Serializable
data class JournalEntry(
    val time: WorldTime,
    /** Rubrique libre : « décision », « loi », « élection », « crise »... */
    val kind: String,
    val text: String,
    val tone: Tone = Tone.NEUTRAL,
)
