package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.engine.government.LawDef
import fr.president.engine.government.PolicyKind
import fr.president.engine.government.PolicyStatus
import fr.president.engine.session.ActionPresenter
import fr.president.engine.territory.LocalActionDef
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.widgets.ActionCards

/**
 * Lois et Constitution : la société que vous voulez. Chaque loi montre ce qui est en vigueur et
 * les alternatives, leurs effets, leurs chances au Parlement ; les questions constitutionnelles
 * peuvent aussi être tranchées par référendum. En tête, les trois indices des libertés.
 */
class LawsPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "⚖ Lois et Constitution"
    private val session get() = nav.session
    private var category: String? = null
    private var message: String? = null

    override fun applyArgument(argument: String) {
        category = argument
    }

    override fun build(into: Table) {
        val laws = session.laws
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        indices(into)

        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        val current = category ?: laws.categories.firstOrNull()?.id
        category = current
        laws.categories.forEach { c ->
            chips.addActor(ui.button("${c.icon} ${c.label}", "toggle") { category = c.id; message = null; nav.refresh() }.also { it.isChecked = c.id == current })
        }
        into.add(chips).growX().padBottom(GAP).row()
        laws.categories.firstOrNull { it.id == current }?.let { into.add(ui.label(it.description, "muted", wrap = true)).growX().padBottom(4f).row() }

        // Le projet de loi en préparation : tous les changements choisis ici y sont réunis.
        val leg = session.legislation
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val n = leg.lawChanges().size
        box.add(ui.label(if (n == 0) "Choisissez une nouvelle valeur : elle rejoint votre projet de loi, que vous déposez ensuite au Parlement (ou soumettez au référendum)."
            else "Projet « ${leg.lawName()} » : $n changement(s).", "small", wrap = true)).growX().row()
        box.add(ui.colorButton("⚖ Ouvrir le projet de loi", Theme.accentDark) { nav.open(PanelId.LEGISLATION, "law") }).left().padTop(3f).row()
        into.add(box).growX().padBottom(GAP).row()

        // Textes en discussion et référendums convoqués.
        leg.pendingBills().filter { p -> p.changes.any { it.lever.startsWith("law:") } }.forEach { p ->
            into.add(fr.president.game.ui.widgets.ProposalCard.build(ui, session, p) { message = it; nav.refresh() }).growX().padBottom(4f).row()
        }
        session.state.laws.referendums.forEach { r ->
            val law = laws.law(r.lawId) ?: return@forEach
            val chance = laws.referendumChance(r.lawId, r.option)
            into.add(ui.label("Référendum le ${Formats.date(r.at)} : ${law.title} — « ${law.options[r.option].label} » · sondage ${Math.round(chance * 100)} % de oui",
                "small", if (chance >= 0.5) Theme.good else Theme.bad, wrap = true)).growX().padBottom(4f).row()
        }

        val c = fr.president.game.ui.widgets.LeverCards.Context(ui, session, expanded, mutableMapOf(), { message = it }, { nav.refresh() })
        laws.laws.filter { it.category == current }.forEach { l ->
            session.levers.lever("law:${l.id}")?.let { into.add(fr.president.game.ui.widgets.LeverCards.build(c, it, fr.president.game.ui.widgets.LeverCards.Mode.LAW)).growX().padBottom(4f).row() }
        }
        // Les réglages chiffrés du même domaine (âge de la retraite...).
        session.levers.all().filter { it.source == fr.president.engine.legislation.LeverSource.PARAM && it.domain == current }.forEach { l ->
            into.add(fr.president.game.ui.widgets.LeverCards.build(c, l, if (l.channel == fr.president.engine.legislation.Channel.DECREE) fr.president.game.ui.widgets.LeverCards.Mode.DECREE else fr.president.game.ui.widgets.LeverCards.Mode.LAW)).growX().padBottom(4f).row()
        }
    }

    private fun indices(into: Table) {
        val i = session.laws.indices()
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        listOf("Libertés publiques" to i.liberty, "Liberté de la presse" to i.press, "État de droit" to i.rule).forEach { (label, v) ->
            val row = Table()
            row.add(ui.label(label, "small")).width(INDEX_LABEL).left()
            val gauge = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
            val color = when { v >= GOOD -> Theme.good; v >= FAIR -> Theme.warning; else -> Theme.bad }
            gauge.add(Table().apply { setBackground(ui.skin.fill(color)) }).width(Value.percentWidth((v / 100).toFloat().coerceIn(0.02f, 1f), gauge)).height(BAR).left().expandX()
            row.add(gauge).growX().height(BAR).padLeft(6f).padRight(6f)
            row.add(ui.label("${Math.round(v)}/100", "small", color)).right()
            box.add(row).growX().padBottom(2f).row()
        }
        box.add(ui.label("Les reculs inquiètent l'Union européenne et les investisseurs ; une presse libre révèle plus d'affaires.", "muted", wrap = true)).growX().row()
        into.add(box).growX().padBottom(GAP).row()
    }

    private companion object {
        const val INDEX_LABEL = 140f
        const val BAR = 7f
        const val GOOD = 70.0
        const val FAIR = 50.0
    }
}
