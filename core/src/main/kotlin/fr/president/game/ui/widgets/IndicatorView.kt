package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.Indicator
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Affiche une information simple (« Dette publique : EN HAUSSE ») avec son explication,
 * et un bouton « Détails » révélant les chiffres. La complexité est cachée, jamais supprimée.
 */
class IndicatorView(private val ui: Ui, private val indicator: Indicator, private val expanded: MutableSet<String>) : Table() {
    init {
        defaults().left()
        build()
    }

    private fun build() {
        clearChildren()
        val header = Table()
        header.add(ui.label(indicator.label, "bold")).left().expandX()
        header.add(ui.label(indicator.status, "bold", Theme.tone(indicator.tone))).right()
        add(header).growX().row()
        if (indicator.explanation.isNotBlank()) add(ui.label(indicator.explanation, "muted", wrap = true)).growX().padTop(2f).row()
        if (indicator.details.isNotEmpty()) {
            val open = indicator.label in expanded
            add(ui.button(if (open) "Masquer les détails" else "Détails", "flat") {
                if (open) expanded -= indicator.label else expanded += indicator.label
                build()
            }).left().padTop(2f).row()
            if (open) {
                val details = Table().apply { defaults().left().padRight(8f) }
                indicator.details.forEach { (k, v) ->
                    details.add(ui.label(k, "muted")).left().expandX()
                    details.add(ui.label(v, "small")).right().row()
                }
                add(details).growX().padLeft(6f).row()
            }
        }
    }
}
