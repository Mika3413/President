package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.elections.RoundResult
import fr.president.engine.util.Formatting
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Échéance électorale, sondages et résultats passés. Perdre l'élection met fin à la partie. */
class ElectionPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Élection présidentielle"
    private val session get() = nav.session

    override fun build(into: Table) {
        val e = session.state.elections
        val days = session.state.time.daysUntil(e.nextElection).toInt()
        into.add(ui.label("Prochain scrutin : ${Formats.date(e.nextElection)}", "large")).row()
        into.add(ui.label("Dans $days jours · mandat n°${session.state.player.termNumber}", "muted")).row()
        into.add(ui.label("Le vote dépend de votre bilan tel que le perçoit chaque catégorie de Français : emploi, prix, services publics, sécurité, impôts, crises récentes.", "muted", wrap = true)).growX().padTop(4f).row()
        e.latestPoll?.let {
            into.add(ui.label("Sondage — premier tour", "bold")).padTop(GAP).row()
            shares(into, it)
        }
        e.latestRunoffPoll?.let {
            into.add(ui.label("Sondage — second tour", "bold")).padTop(GAP).row()
            shares(into, it)
        }
        e.lastPollAt?.let { into.add(ui.label("Réalisé le ${Formats.date(it)} (marge d'erreur ±3 points)", "muted")).row() }
        e.results.asReversed().forEach { r ->
            into.add(ui.label("Résultat du ${Formats.date(r.time)}", "bold")).padTop(GAP).row()
            (r.secondRound ?: r.firstRound).let { shares(into, it) }
        }
    }

    private fun shares(into: Table, r: RoundResult) {
        val families = session.db.country(session.state.player.countryId).elections!!.families.associateBy { it.id }
        val candidates = session.state.elections.candidates.associateBy { it.characterId }
        r.shares.entries.sortedByDescending { it.value }.forEach { (id, share) ->
            val c = session.state.characters[id]
            val family = candidates[id]?.familyId?.let { families[it]?.name } ?: ""
            val you = id == session.state.player.presidentId
            val row = Table()
            row.add(ui.label((if (you) "★ " else "") + (c?.fullName ?: id), if (you) "bold" else "small", if (you) Theme.highlight else null)).left().expandX()
            row.add(ui.label(Formatting.percent(share), "small")).right().row()
            if (family.isNotBlank()) row.add(ui.label(family, "muted")).left().row()
            into.add(row).growX().row()
        }
        into.add(ui.label("Participation : ${Formatting.percent(r.turnout)}", "muted")).row()
    }
}
