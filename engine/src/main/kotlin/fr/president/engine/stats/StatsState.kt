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
)

/** Fait marquant du mandat, daté. */
@Serializable
data class JournalEntry(
    val time: WorldTime,
    /** Rubrique libre : « décision », « loi », « élection », « crise »... */
    val kind: String,
    val text: String,
    val tone: Tone = Tone.NEUTRAL,
)
