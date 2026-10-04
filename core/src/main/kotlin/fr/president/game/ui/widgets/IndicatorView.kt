package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.Indicator
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Carte d'information : une bande de couleur (vert = bien, orange = à surveiller, rouge = danger),
 * le chiffre clé en grand avec sa tendance, une phrase d'explication, et les détails sur demande.
 */
class IndicatorView(private val ui: Ui, private val indicator: Indicator, private val expanded: MutableSet<String>) : Table() {
    init {
        build()
    }

    private fun build() {
        clearChildren()
        val color = Theme.tone(indicator.tone)
        add(Table().apply { setBackground(ui.skin.fill(color)) }).width(STRIPE).growY()
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val header = Table()
        header.add(ui.label(indicator.label, "bold")).left().expandX().minWidth(0f)
        if (indicator.value.isNotEmpty()) {
            header.add(ui.label(indicator.value, "value", color)).right().padLeft(6f)
            if (indicator.trend != 0) {
                val good = (indicator.trend > 0) == indicator.higherIsBetter
                header.add(ui.label(if (indicator.trend > 0) " ▲" else " ▼", "small", if (good) Theme.good else Theme.bad)).right()
            }
        } else {
            header.add(ui.label(indicator.status, "value", color)).right().padLeft(6f)
        }
        card.add(header).growX().row()
        val sub = buildString {
            if (indicator.value.isNotEmpty()) append(indicator.status.lowercase().replaceFirstChar { it.uppercase() })
            if (indicator.explanation.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(indicator.explanation)
            }
        }
        if (sub.isNotBlank()) card.add(ui.label(sub, "muted", wrap = true)).growX().padTop(2f).row()
        if (indicator.details.isNotEmpty()) {
            val open = indicator.label in expanded
            card.add(ui.button(if (open) "▲ Masquer" else "▼ Détails", "flat") {
                if (open) expanded -= indicator.label else expanded += indicator.label
                build()
            }).left().padTop(2f).row()
            if (open) {
                val details = Table().apply { defaults().left().padRight(8f) }
                indicator.details.forEach { (k, v) ->
                    details.add(ui.label(k, "muted")).left().expandX()
                    details.add(ui.label(v, "small")).right().row()
                }
                card.add(details).growX().padLeft(6f).row()
            }
        }
        add(card).growX()
    }

    private companion object {
        const val STRIPE = 4f
    }
}
