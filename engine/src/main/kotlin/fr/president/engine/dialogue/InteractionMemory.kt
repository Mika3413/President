package fr.president.engine.dialogue

import fr.president.engine.events.InteractionOutcome
import fr.president.engine.simulation.SimulationContext

/** Mémoire des échanges entre le président et chaque personnage. */
class InteractionMemory(private val ctx: SimulationContext) {

    fun record(characterId: String, topic: String, outcome: InteractionOutcome, label: String? = null) {
        val records = ctx.state.dialogue.interactions.getOrPut(characterId) { mutableListOf() }
        records.add(InteractionRecord(topic, outcome, ctx.now, label))
        val retention = ctx.db.config.simulation.memoryRetentionDays.toLong()
        val cutoff = ctx.now.plusDays(-retention)
        records.removeAll { it.time < cutoff }
    }

    fun lastRecord(characterId: String): InteractionRecord? = records(characterId).lastOrNull()

    fun records(characterId: String): List<InteractionRecord> =
        ctx.state.dialogue.interactions[characterId].orEmpty()

    /** Étiquettes d'historique exploitées par les modèles de dialogue. */
    fun historyTags(characterId: String?): Set<String> {
        if (characterId == null) return setOf("history:none")
        val records = records(characterId)
        if (records.isEmpty()) return setOf("history:none")
        val tags = mutableSetOf<String>()
        val refusals = records.count { it.outcome == InteractionOutcome.REFUSED }
        val accepted = records.count { it.outcome == InteractionOutcome.ACCEPTED || it.outcome == InteractionOutcome.PARTIAL }
        when (records.last().outcome) {
            InteractionOutcome.REFUSED -> tags += "history:refused"
            InteractionOutcome.ACCEPTED, InteractionOutcome.PARTIAL -> tags += "history:cooperated"
            InteractionOutcome.POSTPONED -> tags += "history:postponed"
            InteractionOutcome.NEUTRAL -> tags += "history:known"
        }
        if (refusals >= REPEATED) tags += "history:refused_many"
        if (accepted >= REPEATED) tags += "history:cooperated_many"
        return tags
    }

    private companion object {
        const val REPEATED = 2
    }
}
