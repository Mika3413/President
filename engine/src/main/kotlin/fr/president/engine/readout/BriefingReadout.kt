package fr.president.engine.readout

import fr.president.engine.government.PolicyStatus
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalEntry
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting

/**
 * Bilan au retour du joueur : ce qui a changé pendant son absence et ce qui l'attend.
 * Une page, lisible en quelques secondes.
 */
class BriefingReadout(private val ctx: SimulationContext) {

    data class Change(val label: String, val before: String, val after: String, val good: Boolean?)

    data class Briefing(
        val days: Double,
        val changes: List<Change>,
        val facts: List<JournalEntry>,
        val pendingDecisions: Int,
        val upcoming: List<String>,
    )

    fun since(from: WorldTime): Briefing {
        val days = from.daysUntil(ctx.now)
        val steps = Math.round(days / DAYS_PER_POINT).toInt()
        val stats = ctx.state.stats.series
        fun change(key: String, label: String, higherIsBetter: Boolean, format: (Double) -> String): Change? {
            val history = stats[key] ?: return null
            val now = history.last() ?: return null
            val before = history.ago(steps.coerceAtMost(history.size - 1)) ?: return null
            val diff = now - before
            val good = if (kotlin.math.abs(diff) < EPSILON) null else (diff > 0) == higherIsBetter
            return Change(label, format(before), format(now), good)
        }
        val changes = listOfNotNull(
            change("approval", "Popularité", true) { Formatting.wholePercent(it) },
            change("unemployment", "Chômage", false) { Formatting.percent(it) },
            change("growth", "Croissance", true) { Formatting.signedPercent(it) },
            change("deficit", "Déficit", false) { Formatting.percent(it) },
            change("parliament", "Soutien à l'Assemblée", true) { Formatting.wholePercent(it) },
        )
        val facts = ctx.state.stats.journal.filter { it.time > from }.asReversed().take(MAX_FACTS)
        val upcoming = mutableListOf<String>()
        val election = ctx.now.daysUntil(ctx.state.elections.nextElection)
        upcoming += "Prochaine élection présidentielle dans ${election.toInt()} jours"
        ctx.state.policy.proposals.filter { it.status == PolicyStatus.PENDING_VOTE }.sortedBy { it.voteAt }.take(MAX_UPCOMING).forEach {
            upcoming += "Vote au Parlement le ${Formatting.date(it.voteAt)}"
        }
        ctx.state.projects.filter { it.status == fr.president.engine.territory.ProjectStatus.IN_PROGRESS }
            .sortedBy { it.completesAt }.take(MAX_UPCOMING).forEach {
                upcoming += "${it.name} : fin le ${Formatting.date(it.completesAt)}"
            }
        return Briefing(days, changes, facts, ctx.state.inbox.messages.count { it.awaitingAnswer }, upcoming)
    }

    private companion object {
        const val DAYS_PER_POINT = 7.0
        const val EPSILON = 1e-4
        const val MAX_FACTS = 8
        const val MAX_UPCOMING = 3
    }
}
