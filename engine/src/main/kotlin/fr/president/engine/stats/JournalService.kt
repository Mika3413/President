package fr.president.engine.stats

import fr.president.engine.readout.Tone
import fr.president.engine.simulation.SimulationContext

/** Inscrit les faits marquants du mandat (décisions, lois, élections, crises). */
class JournalService(private val ctx: SimulationContext) {
    fun add(kind: String, text: String, tone: Tone = Tone.NEUTRAL) {
        val journal = ctx.state.stats.journal
        journal += JournalEntry(ctx.now, kind, text, tone)
        while (journal.size > MAX_ENTRIES) journal.removeAt(0)
    }

    private companion object {
        const val MAX_ENTRIES = 600
    }
}
