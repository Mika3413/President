package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.diplomacy.EuRule
import fr.president.engine.diplomacy.EuVote
import fr.president.engine.session.ActionPresenter
import fr.president.engine.territory.LocalActionDef
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.widgets.ActionCards

/**
 * Union européenne : le texte en discussion au Conseil, la position de la France, le pronostic
 * du vote État par État, les partenaires à rallier, l'amendement, puis l'historique des votes et
 * nos alliés et adversaires à Bruxelles.
 */
class EuPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "★ Union européenne"
    private val session get() = nav.session
    private var message: String? = null

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        val eu = session.eu
        val current = eu.current()
        if (current == null) {
            val next = session.state.eu.nextProposal
            into.add(ui.label("Aucun texte en discussion au Conseil." + (next?.let { " Prochaine proposition de la Commission vers le ${Formats.date(it)}." } ?: ""), "muted", wrap = true)).growX().padBottom(GAP).row()
        } else {
            val (proc, t) = current
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            card.add(ui.label(t.title, "bold", wrap = true)).growX().row()
            card.add(ui.label(t.description, "muted", wrap = true)).growX().padTop(2f).row()
            card.add(ui.label("Pour la France : ${t.interest}", "small", wrap = true)).growX().padTop(2f).row()
            val effects = ActionPresenter(session.context).summarize(LocalActionDef(t.id, t.title, "", "", "", immediate = t.effects))
            if (effects.isNotEmpty()) card.add(ActionCards.chips(ui, effects)).growX().row()
            card.add(ui.label("Vote du Conseil le ${Formats.date(proc.voteAt)} · ${t.rule.label}", "small", Theme.warning, wrap = true)).growX().padTop(3f).row()
            if (proc.amended) card.add(ui.label("✔ Amendement français adopté : ce que la France y perd est réduit de moitié.", "small", Theme.good, wrap = true)).growX().row()
            into.add(card).growX().padBottom(GAP).row()

            into.add(ui.label("Position de la France", "bold")).row()
            val positions = Table().apply { defaults().padRight(4f) }
            EuVote.entries.forEach { v ->
                positions.add(ui.button(v.label, "toggle") { eu.setPosition(v); message = null; nav.refresh() }.also { it.isChecked = proc.francePosition == v })
            }
            into.add(positions).left().padBottom(4f).row()
            val amend = eu.amendBlocker()
            into.add(ui.button("✎ Proposer un amendement" + (amend?.let { " — $it" } ?: ""), "flat") {
                message = eu.amend().fold({ it }, { it.message ?: "Impossible." }); nav.refresh()
            }.also { it.isDisabled = amend != null }).left().padBottom(GAP).row()

            eu.outlook()?.let { o ->
                val verdict = if (o.adopted) "Adoption probable" else "Rejet probable"
                val detail = if (t.rule == EuRule.QMV) " · ${Math.round(o.yesStates * 100)} % des États, ${Math.round(o.yesPopulation * 100)} % de la population pour (seuils : 55 % et 65 %)"
                    else if (o.blockers.isNotEmpty()) " · veto possible : ${o.blockers.joinToString { eu.name(it) }}" else " · aucun veto en vue"
                into.add(ui.label("Pronostic : $verdict$detail", "small", if (o.adopted) Theme.good else Theme.bad, wrap = true)).growX().padBottom(4f).row()
                into.add(ui.label("Les États membres (pronostic, avant le jour du vote). Appelez un dirigeant pour le rallier à la position française.", "muted", wrap = true)).growX().padBottom(4f).row()
                o.forecasts.forEach { f ->
                    val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(3f, 8f, 3f, 8f) }
                    row.add(ui.label(f.name + if (f.lobbied) " ✔" else "", "default")).left().growX().minWidth(0f)
                    val color = when (f.vote) { EuVote.YES -> Theme.good; EuVote.NO -> Theme.bad; EuVote.ABSTAIN -> Theme.textMuted }
                    row.add(ui.label(f.vote.label, "bold", color)).right().padRight(6f)
                    val blocker = eu.lobbyBlocker(f.country)
                    row.add(ui.button("☎ Rallier", "flat") { message = eu.lobby(f.country).fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }
                        .also { it.isDisabled = blocker != null }).right()
                    into.add(row).growX().padBottom(2f).row()
                }
                into.add(ui.label("Autres États membres (15, non simulés) : ${Math.round(o.othersYesShare * 100)} % favorables", "small", Theme.textMuted, wrap = true)).growX().padTop(2f).row()
            }
        }

        val allies = eu.alignment()
        if (allies.isNotEmpty()) {
            into.add(ui.label("Alliés et adversaires au Conseil", "bold")).padTop(GAP).row()
            into.add(ui.label("Part des votes identiques à ceux de la France.", "muted")).row()
            allies.forEach { (name, share) ->
                val row = Table()
                row.add(ui.label(name, "small")).left().growX()
                row.add(ui.label("${Math.round(share * 100)} %", "small", if (share >= 0.6) Theme.good else if (share < 0.4) Theme.bad else Theme.textMuted)).right()
                into.add(row).growX().row()
            }
        }
        val results = session.state.eu.results.asReversed().take(MAX_RESULTS)
        if (results.isNotEmpty()) {
            into.add(ui.label("Derniers votes", "bold")).padTop(GAP).row()
            results.forEach { r ->
                val t = eu.text(r.textId) ?: return@forEach
                val text = "${if (r.adopted) "✔ Adopté" else "✕ Rejeté"} · ${t.title} · France : ${r.france.label.lowercase()}" + (r.vetoBy?.let { " · veto : ${eu.name(it)}" } ?: "")
                into.add(ui.label(text, "small", if (r.adopted) Theme.good else Theme.textMuted, wrap = true)).growX().padBottom(2f).row()
            }
        }
    }

    private companion object {
        const val MAX_RESULTS = 8
    }
}
