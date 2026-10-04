package fr.president.game.ui.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.crisis.MeasureCommands
import fr.president.engine.session.GameSession
import fr.president.game.ui.Sfx
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Carte d'une mesure de crise (confinement, couvre-feu, plan ORSEC...) : coût, durée, effets au
 * lancement et chaque mois, effet sur les risques, et le bouton pour la décréter ou la lever.
 */
class MeasureCards(private val ui: Ui, private val session: GameSession, private val refresh: () -> Unit, private val onResult: (String) -> Unit) {
    /** Mesure lourde en attente de confirmation (une seule à la fois). */
    private var confirming: String? = null

    fun card(v: MeasureCommands.View, department: String?, color: Color = Theme.warning, compact: Boolean = false): Table {
        val def = v.def
        val active = v.active
        val card = Table().apply { setBackground(ui.skin.fill(if (active != null) ACTIVE_BG else Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(if (v.locked) "⊘" else def.icon, "value", if (v.blocker == null || active != null) color else Theme.textMuted)).width(ICON_WIDTH).left()
        val title = def.label + if (def.local && !def.label.contains("local", ignoreCase = true)) " · local" else ""
        head.add(ui.label(title, "bold", if (v.blocker == null || active != null) Theme.text else Theme.textMuted, wrap = true)).left().growX().minWidth(0f)
        val cost = Table()
        cost.add(ui.label(v.costText, "small", if (def.costBillions > 0 || def.costPerMonthBillions > 0) Theme.warning else Theme.good)).right().row()
        cost.add(ui.label(v.durationText, "muted")).right()
        head.add(cost).right().padLeft(6f)
        card.add(head).growX().row()
        if (!compact) card.add(ui.label(def.description, "muted", wrap = true)).growX().padTop(2f).row()
        // Ce que la mesure change sur les risques : la raison d'être de la prévention.
        v.impacts.forEach { card.add(ui.label("⇢ $it", "small", Theme.good, wrap = true)).growX().row() }
        val effects = v.startEffects + v.monthlyEffects
        if (effects.isNotEmpty()) card.add(ActionCards.chips(ui, effects)).growX().row()

        if (active != null) {
            val left = active.endsAt?.let { session.state.time.daysUntil(it) }
            val where = active.department?.let { code -> session.context.playerData.territory?.departments?.firstOrNull { it.code == code }?.name }
            val status = "● En vigueur" + (where?.let { " ($it)" } ?: "") + (left?.let { " — encore ${Math.max(1, Math.ceil(it).toInt())} j" } ?: " — jusqu'à levée")
            val foot = Table()
            foot.add(ui.label(status, "small", Theme.good, wrap = true)).left().growX().minWidth(0f)
            foot.add(ui.button("Lever", "flat") { onResult(session.measures.lift(def.id, active.department).fold({ it }, { it.message ?: "Impossible." })) }).right()
            card.add(foot).growX().padTop(3f).row()
            return card
        }
        val blocker = v.blocker
        val asking = confirming == def.id && blocker == null
        if (blocker == null && !asking) {
            card.add(ui.colorButton("▶ Décréter", color) {
                if (def.confirm) { confirming = def.id; refresh() } else launch(def.id, department)
            }).right().padTop(3f).row()
        }
        if (asking) {
            card.add(ui.label("Mesure lourde (${v.costText}, ${v.durationText}). Vous confirmez ?", "small", Theme.warning, wrap = true)).growX().padTop(4f).row()
            val buttons = Table().apply { defaults().padRight(4f) }
            buttons.add(ui.colorButton("✔ Confirmer", color) { confirming = null; launch(def.id, department) })
            buttons.add(ui.button("Annuler") { confirming = null; refresh() })
            card.add(buttons).left().padTop(2f).row()
        }
        if (blocker != null) card.add(ui.label((if (v.locked) "⊘ " else "↻ ") + blocker, "small", if (v.locked) Theme.textMuted else Theme.warning, wrap = true)).growX().padTop(3f).row()
        return card
    }

    private fun launch(id: String, department: String?) {
        Sfx.play(Sfx.Kind.DECISION)
        onResult(session.measures.activate(id, department).fold({ it }, { it.message ?: "Impossible." }))
    }

    private companion object {
        const val ICON_WIDTH = 26f
        val ACTIVE_BG: Color get() = Theme.good.cpy().mul(1f, 1f, 1f, 0.14f)
    }
}
