package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Actions possibles dans un département, présentées comme des cartes : ce que ça coûte,
 * combien de temps ça prend, et ce que ça change (en vert ce qui s'améliore, en rouge le reste).
 */
class LocalActionList(private val ui: Ui, private val session: GameSession, private val onResult: (String) -> Unit) {

    fun build(into: Table, departmentCode: String) {
        val actions = session.localActions.actionsFor(departmentCode)
        if (actions.isEmpty()) return
        into.add(ui.label("Agir ici", "title", Theme.catLocal)).padTop(4f).row()
        actions.forEach { a ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(a.def.icon, "value", Theme.catLocal)).width(ICON_WIDTH).left()
            head.add(ui.label(a.def.label, "bold")).left().expandX().minWidth(0f)
            head.add(ui.label("${a.costText} · ${a.durationText}", "muted")).right()
            card.add(head).growX().row()
            card.add(ui.label(a.def.description, "muted", wrap = true)).growX().padTop(2f).row()
            // Les effets passent à la ligne au lieu d'élargir la fiche.
            val chips = com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(3f); padTop(3f) }
            a.effects.forEach { e ->
                val chip = Table().apply { setBackground(ui.skin.fill(if (e.good) GOOD_BG else BAD_BG)); pad(1f, 5f, 1f, 5f) }
                chip.add(ui.label(e.text, "small", if (e.good) Theme.good else Theme.bad))
                chips.addActor(chip)
            }
            card.add(chips).growX().left().row()
            val blocker = a.blocker
            if (blocker == null) {
                card.add(ui.colorButton("Lancer", Theme.catLocal) {
                    onResult(session.localActions.perform(departmentCode, a.def.id).fold({ it }, { it.message ?: "Impossible." }))
                }).left().padTop(4f).row()
            } else {
                card.add(ui.label("↻ $blocker", "small", Theme.warning)).left().padTop(4f).row()
            }
            into.add(card).growX().padBottom(4f).row()
        }
    }

    private companion object {
        const val ICON_WIDTH = 26f
        val GOOD_BG = Theme.good.cpy().mul(1f, 1f, 1f, 0.18f)
        val BAD_BG = Theme.bad.cpy().mul(1f, 1f, 1f, 0.18f)
    }
}
