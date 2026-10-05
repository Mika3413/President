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

        // Textes en discussion et référendums convoqués.
        session.state.policy.proposals.filter { it.kind == PolicyKind.LAW && it.status == PolicyStatus.PENDING_VOTE }.forEach { p ->
            into.add(fr.president.game.ui.widgets.ProposalCard.build(ui, session, p) { message = it; nav.refresh() }).growX().padBottom(4f).row()
        }
        session.state.laws.referendums.forEach { r ->
            val law = laws.law(r.lawId) ?: return@forEach
            val chance = laws.referendumChance(r.lawId, r.option)
            into.add(ui.label("Référendum le ${Formats.date(r.at)} : ${law.title} — « ${law.options[r.option].label} » · sondage ${Math.round(chance * 100)} % de oui",
                "small", if (chance >= 0.5) Theme.good else Theme.bad, wrap = true)).growX().padBottom(4f).row()
        }

        laws.laws.filter { it.category == current }.forEach { into.add(card(it)).growX().padBottom(4f).row() }
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

    private fun card(law: LawDef): Table {
        val laws = session.laws
        val open = law.id in expanded
        val current = laws.current(law)
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(law.title + if (law.constitutional) " · Constitution" else "", "bold", wrap = true)).growX().minWidth(0f)
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right()
        head.onClick { if (!expanded.remove(law.id)) expanded += law.id; nav.refresh() }
        card.add(head).growX().row()
        card.add(ui.label("En vigueur : ${law.options[current].label}", "small", Theme.good, wrap = true)).growX().row()
        if (!open) return card
        card.add(ui.label(law.description, "muted", wrap = true)).growX().padTop(2f).row()
        law.options.forEachIndexed { i, o ->
            if (i == current) return@forEachIndexed
            val box = Table().apply { setBackground(ui.skin.fill(Theme.panel)); pad(5f, 6f, 5f, 6f); defaults().left() }
            box.add(ui.label("▶ ${o.label}", "bold", wrap = true)).growX().row()
            box.add(ui.label(o.description, "small", wrap = true)).growX().row()
            val effects = ActionPresenter(session.context).summarize(LocalActionDef(o.id, o.label, "", "", "", immediate = o.effects + o.longTerm))
            if (effects.isNotEmpty()) box.add(ActionCards.chips(ui, effects)).growX().row()
            val shifts = buildList {
                fun d(v: Double, base: Double, name: String) { val x = v - base; if (x != 0.0) add("$name ${if (x > 0) "+" else "−"}${Math.round(kotlin.math.abs(x))}") }
                d(o.liberty, law.options[0].liberty, "libertés")
                d(o.press, law.options[0].press, "presse")
                d(o.rule, law.options[0].rule, "État de droit")
            }
            if (shifts.isNotEmpty()) box.add(ui.label("Indices : " + shifts.joinToString(", "), "small", Theme.textMuted, wrap = true)).growX().row()
            val blocker = laws.blocker(law.id, i)
            val buttons = Table().apply { defaults().padRight(4f).padTop(3f) }
            val chance = laws.passChance(law.id, i)
            buttons.add(ui.colorButton("⌂ Au Parlement (${Math.round(chance * 100)} %)", Theme.accentDark) {
                message = laws.propose(law.id, i).fold({ "Texte déposé : vote dans un mois." }, { it.message ?: "Impossible." }); nav.refresh()
            }.also { it.isDisabled = blocker != null })
            if (law.constitutional) {
                val ref = laws.referendumChance(law.id, i)
                buttons.add(ui.button("✔ Référendum (${Math.round(ref * 100)} % de oui)", "flat") {
                    message = laws.referendum(law.id, i).fold({ it }, { it.message ?: "Impossible." }); nav.refresh()
                }.also { it.isDisabled = blocker != null })
            }
            box.add(buttons).left().row()
            blocker?.let { box.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().row() }
            card.add(box).growX().padTop(4f).row()
        }
        return card
    }

    private companion object {
        const val INDEX_LABEL = 140f
        const val BAR = 7f
        const val GOOD = 70.0
        const val FAIR = 50.0
    }
}
