package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import fr.president.engine.diplomacy.UnDraft
import fr.president.engine.diplomacy.UnService
import fr.president.engine.diplomacy.UnVote
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Conseil de sécurité de l'ONU : les quinze membres, les projets en discussion avec le vote
 * prévu de chacun, le vote (ou le veto) de la France, les propositions françaises, l'historique.
 */
class UnPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "◎ ONU : Conseil de sécurité"
    private val session get() = nav.session
    private val un get() = session.un
    private var tab = Tab.COUNCIL
    private var message: String? = null

    private enum class Tab(val label: String) { COUNCIL("Projets en discussion"), PROPOSE("Proposer une résolution"), HISTORY("Votes passés") }

    override fun build(into: Table) {
        val tabs = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t -> tabs.addActor(ui.button(t.label, "toggle") { tab = t; message = null; nav.refresh() }.also { it.isChecked = t == tab }) }
        into.add(tabs).growX().left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        if (!un.available) { into.add(ui.label("Données indisponibles.", "muted")).row(); return }
        when (tab) {
            Tab.COUNCIL -> council(into)
            Tab.PROPOSE -> propose(into)
            Tab.HISTORY -> history(into)
        }
    }

    private fun run(r: Result<String>) { message = r.fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }

    private fun TextButton.wide() = apply { label.setWrap(true); label.setAlignment(Align.left) }

    private fun color(v: UnVote): Color = when (v) { UnVote.YES -> Theme.good; UnVote.NO -> Theme.warning; UnVote.VETO -> Theme.bad; UnVote.ABSTAIN -> Theme.textMuted }

    private fun council(into: Table) {
        val s = un.state
        into.add(ui.label("15 membres : 5 permanents (★, droit de veto) et 10 élus pour deux ans. Une résolution passe avec 9 voix et sans aucun veto. La France est membre permanent.", "muted", wrap = true)).growX().padBottom(4f).row()
        into.add(ui.label("France : ${s.frenchAdopted} résolution(s) française(s) adoptée(s) · ${s.vetoes} veto(s) opposé(s)", "small", wrap = true)).growX().padBottom(GAP).row()
        if (s.drafts.isEmpty()) {
            into.add(ui.label("Aucun projet en discussion pour l'instant.", "bold")).left().row()
            val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(3f); wrapSpace(3f) }
            un.members().forEach { m -> chips.addActor(ui.label((if (un.isPermanent(m)) "★ " else "") + un.name(m) + "  ", "small")) }
            into.add(chips).growX().padTop(4f).row()
        }
        s.drafts.forEach { into.add(draft(it)).growX().padBottom(GAP).row() }
    }

    private fun draft(d: UnDraft): Table {
        val t = un.template(d.template)
        val o = un.outlook(d)
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        card.add(ui.label(d.title, "value", wrap = true)).growX().row()
        val days = session.context.now.daysUntil(d.voteAt).coerceAtLeast(0.0).toInt()
        card.add(ui.label("Proposé par ${un.name(d.sponsor)} · vote dans $days j" + if (d.amended) " · amendé" else "", "small", Theme.textMuted, wrap = true)).growX().row()
        t?.description?.takeIf { it.isNotEmpty() }?.let { card.add(ui.label(it, "small", wrap = true)).growX().padTop(2f).row() }
        t?.interest?.takeIf { it.isNotEmpty() }?.let { card.add(ui.label("Pour la France : $it", "small", Theme.accent, wrap = true)).growX().row() }
        val verdict = when {
            o.adopted -> "Prévision : adoptée (${o.yes} voix sur ${UnService.MAJORITY} nécessaires)"
            o.vetoBy.isNotEmpty() -> "Prévision : veto ${o.vetoBy.joinToString { un.name(it) }} (${o.yes} voix pour)"
            else -> "Prévision : rejetée, ${o.yes} voix sur ${UnService.MAJORITY} nécessaires"
        }
        card.add(ui.label(verdict, "bold", if (o.adopted) Theme.good else Theme.bad, wrap = true)).growX().padTop(4f).row()
        // Les membres et leur vote prévu ; un appel peut faire basculer ceux qui ne sont pas acquis.
        un.members().forEach { m ->
            val v = o.votes[m] ?: UnVote.ABSTAIN
            val row = Table()
            row.add(ui.label((if (un.isPermanent(m)) "★ " else "") + un.name(m), "small")).growX().left().minWidth(0f)
            row.add(ui.label(v.label, "small", color(v))).right().padLeft(4f)
            if (m != session.state.player.countryId && v != UnVote.YES) {
                val blocker = un.lobbyBlocker(d, m)
                row.add(ui.button(if (m in d.lobbied) "sollicité" else "Convaincre", "flat") { run(un.lobby(d.id, m)) }.also { it.isDisabled = blocker != null }).right().padLeft(4f)
            }
            card.add(row).growX().row()
        }
        if (d.sponsor == session.state.player.countryId) {
            if (!d.amended) card.add(ui.button("Amender : adoucir le texte pour rallier des voix (effets réduits)", "flat") { run(un.amend(d.id)) }.wide()).growX().padTop(4f).row()
        } else {
            card.add(ui.label("Vote de la France", "bold")).padTop(4f).row()
            val row = Table().apply { defaults().padRight(4f) }
            listOf(UnVote.YES, UnVote.ABSTAIN, UnVote.VETO).forEach { v ->
                row.add(ui.button(if (v == UnVote.VETO) "Veto" else v.label, "toggle") { un.setFranceVote(d.id, v); message = "La France votera : ${v.label.lowercase()}."; nav.refresh() }.also { it.isChecked = d.france == v })
            }
            card.add(row).left().row()
        }
        return card
    }

    private fun propose(into: Table) {
        val blocker = un.frenchBlocker()
        into.add(ui.label("Porter un texte à New York vous coûte une journée d'agenda. Les textes sur une guerre en cours peuvent condamner l'agresseur, imposer un cessez-le-feu, des sanctions, des Casques bleus, ou autoriser la force.", "muted", wrap = true)).growX().padBottom(4f).row()
        blocker?.let { into.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().padBottom(4f).row() }
        un.proposals().forEach { p ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            card.add(ui.label(p.title, "bold", wrap = true)).growX().row()
            p.template.interest.takeIf { it.isNotEmpty() }?.let { card.add(ui.label(it, "small", Theme.textMuted, wrap = true)).growX().row() }
            card.add(ui.colorButton("Déposer ce projet", Theme.accentDark) { run(un.propose(p.template.id, p.aggressor, p.victim)) }.also { it.isDisabled = blocker != null }).left().padTop(2f).row()
            into.add(card).growX().padBottom(4f).row()
        }
    }

    private fun history(into: Table) {
        val results = un.state.results.takeLast(MAX_HISTORY).reversed()
        if (results.isEmpty()) into.add(ui.label("Aucun vote pour l'instant.", "muted")).left().row()
        results.forEach { r ->
            val yes = r.votes.values.count { it == UnVote.YES }
            val outcome = when {
                r.adopted -> "adoptée"
                r.vetoBy.isNotEmpty() -> "veto ${r.vetoBy.joinToString { un.name(it) }}"
                else -> "rejetée"
            }
            into.add(ui.label("${Formatting.date(r.time)} · ${r.title}", "small", wrap = true)).growX().row()
            into.add(ui.label("   $outcome · $yes voix pour · France : ${r.france.label.lowercase()} · proposé par ${un.name(r.sponsor)}", "small", if (r.adopted) Theme.good else Theme.bad, wrap = true)).growX().padBottom(3f).row()
        }
    }

    private companion object {
        const val MAX_HISTORY = 15
    }
}
