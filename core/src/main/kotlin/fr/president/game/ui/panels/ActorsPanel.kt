package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.engine.politics.ActorService
import fr.president.engine.session.ActionPresenter
import fr.president.engine.territory.LocalActionDef
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.widgets.ActionCards

/**
 * Société civile : syndicats, patronat, cultes, lobbies, associations. Leur satisfaction, ce qui
 * la fait, leur influence ; les recevoir ou céder à une revendication (au risque d'en fâcher d'autres).
 */
class ActorsPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "⚒ Société civile"
    private val session get() = nav.session
    private var kind: String? = null
    private var message: String? = null

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        into.add(ui.label("Leur satisfaction suit vos lois, vos réformes et l'état du pays. Un acteur influent en colère mobilise : grèves, blocages, manifestations.", "muted", wrap = true)).growX().padBottom(4f).row()
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        chips.addActor(ui.button("Tous", "toggle") { kind = null; nav.refresh() }.also { it.isChecked = kind == null })
        session.actors.kinds.forEach { k -> chips.addActor(ui.button("${k.icon} ${k.label}", "toggle") { kind = k.id; nav.refresh() }.also { it.isChecked = kind == k.id }) }
        into.add(chips).growX().padBottom(GAP).row()
        session.actors.rows(kind).sortedBy { it.satisfaction }.forEach { into.add(card(it)).growX().padBottom(4f).row() }
    }

    private fun card(r: ActorService.Row): Table {
        val open = r.def.id in expanded
        val color = when { r.satisfaction >= 0.6 -> Theme.good; r.satisfaction >= 0.35 -> Theme.warning; else -> Theme.bad }
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(r.def.icon, "value", color)).width(ICON).left()
        head.add(ui.label(r.def.label, "bold", wrap = true)).growX().minWidth(0f)
        head.add(ui.label("${Math.round(r.satisfaction * 100)} %", "bold", color)).right()
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
        head.onClick { if (!expanded.remove(r.def.id)) expanded += r.def.id; nav.refresh() }
        card.add(head).growX().row()
        val gauge = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
        gauge.add(Table().apply { setBackground(ui.skin.fill(color)) }).width(Value.percentWidth(r.satisfaction.toFloat().coerceIn(0.02f, 1f), gauge)).height(BAR).left().expandX()
        card.add(gauge).growX().height(BAR).padTop(2f).row()
        card.add(ui.label("Influence : ${influence(r.def.influence)}" + if (r.satisfaction < 0.3 && r.def.mobilize.isNotEmpty()) " · risque de mobilisation" else "", "small",
            if (r.satisfaction < 0.3) Theme.bad else Theme.textMuted)).row()
        if (!open) return card
        card.add(ui.label(r.def.description, "muted", wrap = true)).growX().padTop(2f).row()
        r.reasons.take(MAX_REASONS).forEach { (label, w) -> card.add(ui.label("${if (w >= 0) "▲" else "▼"} $label", "small", if (w >= 0) Theme.good else Theme.bad, wrap = true)).growX().row() }
        val buttons = Table().apply { defaults().padRight(4f).padTop(3f) }
        buttons.add(ui.colorButton("☎ Recevoir à l'Élysée", Theme.accentDark) { message = session.actors.meet(r.def.id).fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }
            .also { it.isDisabled = r.meetBlocker != null })
        card.add(buttons).left().row()
        r.meetBlocker?.let { card.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().row() }
        if (r.def.demands.isNotEmpty()) card.add(ui.label("Revendications", "bold")).padTop(4f).row()
        r.def.demands.forEach { d ->
            val box = Table().apply { setBackground(ui.skin.fill(Theme.panel)); pad(4f, 6f, 4f, 6f); defaults().left() }
            box.add(ui.label(d.label, "small", wrap = true)).growX().row()
            val effects = ActionPresenter(session.context).summarize(LocalActionDef(d.id, d.label, "", "", "", costBillions = d.costBillions, immediate = d.effects))
            if (effects.isNotEmpty()) box.add(ActionCards.chips(ui, effects)).growX().row()
            val opposed = d.opposed.mapNotNull { o -> session.actors.rows().firstOrNull { it.def.id == o }?.def?.label }
            if (opposed.isNotEmpty()) box.add(ui.label("Mécontente : " + opposed.joinToString(", "), "small", Theme.warning, wrap = true)).growX().row()
            val blocker = session.actors.grantBlocker(r.def.id, d.id)
            box.add(ui.button((if (d.costBillions > 0) "Accorder (${Formatting.billions(d.costBillions)})" else "Accorder") + (blocker?.let { " — $it" } ?: ""), "flat") {
                message = session.actors.grant(r.def.id, d.id).fold({ it }, { it.message ?: "Impossible." }); nav.refresh()
            }.also { it.isDisabled = blocker != null }).left().row()
            card.add(box).growX().padTop(3f).row()
        }
        return card
    }

    private fun influence(v: Double) = when { v >= 0.7 -> "très forte"; v >= 0.5 -> "forte"; v >= 0.35 -> "moyenne"; else -> "faible" }

    private companion object {
        const val ICON = 26f
        const val BAR = 5f
        const val MAX_REASONS = 5
    }
}
