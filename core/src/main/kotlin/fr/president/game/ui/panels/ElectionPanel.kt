package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.elections.RoundResult
import fr.president.engine.util.Formatting
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Échéance électorale, sondages et résultats passés. Perdre l'élection met fin à la partie. */
class ElectionPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Élections"
    private val session get() = nav.session

    override fun build(into: Table) {
        val e = session.state.elections
        val days = session.state.time.daysUntil(e.nextElection).toInt()
        into.add(ui.label("Présidentielle : ${Formats.date(e.nextElection)}", "large")).row()
        into.add(ui.label("Dans $days jours · mandat n°${session.state.player.termNumber}", "muted")).row()
        into.add(ui.label("Le vote dépend de votre bilan tel que le perçoit chaque catégorie de Français : emploi, prix, services publics, sécurité, impôts, crises récentes.", "muted", wrap = true)).growX().padTop(4f).row()
        val promises = fr.president.engine.elections.PromiseEvaluator(session.context)
        if (promises.chosen().isNotEmpty()) {
            into.add(ui.label("Vos promesses de campagne", "bold")).padTop(GAP).row()
            promises.chosen().forEach { p ->
                val st = promises.status(p)
                val color = when (st) {
                    fr.president.engine.elections.PromiseStatus.KEPT, fr.president.engine.elections.PromiseStatus.ON_TRACK -> Theme.good
                    fr.president.engine.elections.PromiseStatus.AT_RISK -> Theme.warning
                    fr.president.engine.elections.PromiseStatus.BROKEN -> Theme.bad
                }
                val row = Table()
                row.add(ui.label(p.label, "small", wrap = true)).growX().left()
                row.add(ui.label(st.label, "small", color)).right()
                into.add(row).growX().row()
            }
        }
        e.latestPoll?.let {
            into.add(ui.label("Sondage — premier tour", "bold")).padTop(GAP).row()
            shares(into, it)
        }
        e.latestRunoffPoll?.let {
            into.add(ui.label("Sondage — second tour", "bold")).padTop(GAP).row()
            shares(into, it)
        }
        e.lastPollAt?.let { into.add(ui.label("Réalisé le ${Formats.date(it)} (marge d'erreur ±3 points)", "muted")).row() }
        legislative(into)
        e.results.asReversed().forEach { r ->
            into.add(ui.label("Résultat du ${Formats.date(r.time)}", "bold")).padTop(GAP).row()
            (r.secondRound ?: r.firstRound).let { shares(into, it) }
        }
    }

    /** Législatives et référendums : résultats en voix et en sièges. */
    private fun legislative(into: Table) {
        val parliament = session.state.parliament
        val families = session.parliament.families.associateBy { it.id }
        parliament.legislativeResults.lastOrNull()?.let { r ->
            into.add(ui.label("Législatives du ${Formats.date(r.time)}" + if (r.afterDissolution) " (après dissolution)" else "", "bold")).left().padTop(GAP).row()
            r.seats.entries.sortedByDescending { it.value }.forEach { (id, seats) ->
                val row = Table()
                row.add(ui.label(families[id]?.name ?: id, "small")).left().expandX()
                row.add(ui.label(Formatting.percent(r.votes[id] ?: 0.0), "muted")).right().padRight(8f)
                row.add(ui.label("$seats sièges", "small")).right()
                into.add(row).growX().row()
            }
            parliament.nextLegislative?.let { into.add(ui.label("Prochaines législatives : ${Formats.date(it)}", "muted")).left().row() }
        }
        parliament.referendumResults.asReversed().forEach { r ->
            val title = session.policy.reforms().firstOrNull { it.id == r.reformId }?.title ?: r.reformId
            into.add(ui.label("Référendum du ${Formats.date(r.time)}", "bold")).left().padTop(GAP).row()
            into.add(ui.label(title, "small", wrap = true)).growX().row()
            into.add(ui.label((if (r.yesShare > 0.5) "Oui " else "Non ") + Formatting.percent(if (r.yesShare > 0.5) r.yesShare else 1 - r.yesShare),
                "small", if (r.yesShare > 0.5) Theme.good else Theme.bad)).left().row()
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
