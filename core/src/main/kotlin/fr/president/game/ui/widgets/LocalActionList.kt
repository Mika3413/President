package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.session.ActionPresenter
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.hint

/**
 * Carte d'action, commune aux actions locales et aux décisions nationales : une ligne de titre
 * (icône, nom, coût et durée), une phrase d'explication, les effets en pastilles (vert = gain,
 * rouge = perte, gris = neutre) et le bouton pour agir, ou la raison pour laquelle on ne peut pas.
 */
object ActionCards {
    /** Décision en attente de confirmation (une seule à la fois). */
    private var confirming: String? = null

    fun card(ui: Ui, a: ActionPresenter.ActionView, color: com.badlogic.gdx.graphics.Color, refresh: () -> Unit, onLaunch: () -> Unit): Table {
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        val icon = if (a.locked) "⊘" else a.def.icon
        head.add(ui.label(icon, "value", if (a.blocker == null) color else Theme.textMuted)).width(ICON_WIDTH).left()
        head.add(ui.label(a.def.label, "bold", if (a.blocker == null) Theme.text else Theme.textMuted, wrap = true)).left().growX().minWidth(0f)
        val cost = Table()
        cost.add(ui.label(a.costText, "small", if (a.def.costBillions > 0) Theme.warning else Theme.good)).right().row()
        cost.add(ui.label(a.durationText, "muted")).right()
        head.add(cost).right().padLeft(6f)
        card.add(head).growX().row()
        card.add(ui.label(a.def.description, "muted", wrap = true)).growX().padTop(2f).row()
        // Effets à gauche, bouton à droite : une ligne de moins par carte.
        val foot = Table()
        foot.add(chips(ui, a.effects)).growX().left().bottom().minWidth(0f)
        val blocker = a.blocker
        val asking = confirming == a.def.id && blocker == null
        if (blocker == null && !asking) {
            foot.add(ui.colorButton("▶ Lancer", color) {
                if (a.needsConfirmation) { confirming = a.def.id; refresh() } else onLaunch()
            }).right().bottom().padLeft(6f)
        }
        card.add(foot).growX().row()
        // Avant / après : ce que la décision change sur vos chiffres.
        if (a.forecast.isNotEmpty() && blocker == null) {
            val line = Table()
            line.add(ui.label("Prévision ", "muted")).left().top()
            line.add(chips(ui, a.forecast)).growX().left().minWidth(0f)
            card.add(line).growX().padTop(2f).row()
        }
        if (asking) {
            card.add(ui.label("Décision lourde (${a.costText}, ${a.durationText}). Vous confirmez ?", "small", Theme.warning, wrap = true)).growX().padTop(4f).row()
            val buttons = Table().apply { defaults().padRight(4f) }
            buttons.add(ui.colorButton("✔ Confirmer", color) { confirming = null; onLaunch() })
            buttons.add(ui.button("Annuler") { confirming = null; refresh() })
            card.add(buttons).left().padTop(2f).row()
        }
        if (blocker != null) card.add(ui.label((if (a.locked) "⊘ " else "↻ ") + blocker, "small", if (a.locked) Theme.textMuted else Theme.warning, wrap = true)).growX().padTop(3f).row()
        return card
    }

    /** Effets en pastilles qui passent à la ligne au lieu d'élargir la fiche. */
    fun chips(ui: Ui, effects: List<ActionPresenter.EffectLine>): HorizontalGroup {
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(3f); padTop(3f) }
        effects.forEach { e ->
            val (bg, fg) = when {
                e.neutral -> NEUTRAL_BG to Theme.neutral
                e.good -> GOOD_BG to Theme.good
                else -> BAD_BG to Theme.bad
            }
            val chip = Table().apply { setBackground(ui.skin.fill(bg)); pad(1f, 5f, 1f, 5f) }
            chip.add(ui.label(e.text, "small", fg))
            chip.hint(ui, e.hint)
            chips.addActor(chip)
        }
        return chips
    }

    private const val ICON_WIDTH = 26f
    private val GOOD_BG = Theme.good.cpy().mul(1f, 1f, 1f, 0.18f)
    private val BAD_BG = Theme.bad.cpy().mul(1f, 1f, 1f, 0.18f)
    private val NEUTRAL_BG = Theme.neutral.cpy().mul(1f, 1f, 1f, 0.12f)
}

/** Actions possibles dans un département : ce que ça coûte, combien de temps, ce que ça change. */
class LocalActionList(
    private val ui: Ui,
    private val session: GameSession,
    private val refresh: () -> Unit,
    private val onResult: (String) -> Unit,
) {
    /** Thème affiché (null = tous). */
    private var filter: String? = null

    fun build(into: Table, departmentCode: String) {
        val actions = session.localActions.actionsFor(departmentCode)
        if (actions.isEmpty()) return
        val categories = session.localActions.categories
        if (categories.isNotEmpty()) {
            val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
            chips.addActor(filterButton("Tout (${actions.count { it.blocker == null }})", null))
            categories.forEach { c ->
                val n = actions.count { it.def.category == c.id && it.blocker == null }
                chips.addActor(filterButton("${c.icon} ${c.label}" + if (n > 0) " ($n)" else "", c.id))
            }
            into.add(chips).growX().left().padBottom(6f).row()
        }
        // Les actions possibles d'abord, celles en attente ensuite.
        actions.filter { filter == null || it.def.category == filter }.sortedBy { it.blocker != null }.forEach { a ->
            into.add(ActionCards.card(ui, a, Theme.catLocal, refresh) {
                onResult(session.localActions.perform(departmentCode, a.def.id).fold({ it }, { it.message ?: "Impossible." }))
            }).growX().padBottom(4f).row()
        }
    }

    private fun filterButton(text: String, id: String?) =
        ui.button(text, "toggle") { filter = id; refresh() }.also { it.isChecked = filter == id }

    fun availableCount(departmentCode: String) = session.localActions.actionsFor(departmentCode).count { it.blocker == null }
}
