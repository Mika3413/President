package fr.president.engine.readout

import fr.president.engine.media.FrontPage
import fr.president.engine.media.InstituteDef
import fr.president.engine.media.PaperDef
import fr.president.engine.media.PollRelease
import fr.president.engine.simulation.SimulationContext

/** Presse du jour, sondages et préoccupations des Français, pour l'interface. */
class MediaReadout(private val ctx: SimulationContext) {

    data class Concern(val label: String, val score: Double)

    /** Dernière une de chaque journal. */
    fun frontPages(): List<Pair<PaperDef, FrontPage>> {
        val def = ctx.db.media ?: return emptyList()
        val pages = ctx.state.media.frontPages
        return def.papers.mapNotNull { p -> pages.lastOrNull { it.paperId == p.id }?.let { p to it } }
    }

    /** Une principale (journal de référence) : pour le bilan au retour et la barre du haut. */
    fun headline(): FrontPage? = frontPages().firstOrNull()?.second

    /** Sondages, du plus récent au plus ancien. */
    fun polls(): List<Pair<InstituteDef, PollRelease>> {
        val def = ctx.db.media ?: return emptyList()
        return ctx.state.media.polls.asReversed().mapNotNull { r -> def.institutes.firstOrNull { it.id == r.instituteId }?.let { it to r } }
    }

    /** Ce qui préoccupe le plus les Français (facteurs d'opinion les plus négatifs). */
    fun concerns(limit: Int = MAX_CONCERNS): List<Concern> {
        val factors = ctx.playerData.socialGroups?.factors.orEmpty()
        return factors.map { Concern(it.label, ctx.state.opinion.factorScores[it.id] ?: 0.0) }.sortedBy { it.score }.take(limit)
    }

    private companion object {
        const val MAX_CONCERNS = 6
    }
}
