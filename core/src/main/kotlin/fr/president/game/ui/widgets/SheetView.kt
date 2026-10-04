package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.Indicator
import fr.president.engine.readout.LocalReadouts
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.hint

/** Fiche d'un territoire ou équipement : indicateurs, élus, problèmes, projets. */
class SheetView(ui: Ui, sheet: LocalReadouts.Sheet, expanded: MutableSet<String>, compact: Boolean = false) : Table() {
    init {
        defaults().left().growX().padBottom(6f)
        add(ui.label(sheet.subtitle, "muted", wrap = true)).row()
        if (compact) add(KpiGrid(ui, sheet.indicators)).row()
        else sheet.indicators.forEach { add(IndicatorView(ui, it, expanded)).row() }
        if (sheet.problems.isNotEmpty()) {
            add(ui.label("⚠ Problèmes principaux", "bold", Theme.warning)).padTop(6f).row()
            sheet.problems.forEach { add(ui.label("• $it", "small", Theme.warning, wrap = true)).row() }
        }
        if (sheet.projects.isNotEmpty()) {
            add(ui.label("Projets en cours", "bold")).padTop(6f).row()
            sheet.projects.forEach { add(ui.label("• $it", "small", wrap = true)).row() }
        }
        if (sheet.people.isNotEmpty()) {
            add(ui.label("Élus et représentants", "bold")).padTop(6f).row()
            sheet.people.forEach { (title, who) ->
                add(ui.label(title, "muted")).padBottom(0f).row()
                add(ui.label(who, "small", wrap = true)).row()
            }
        }
    }
}

/**
 * Chiffres clés en tuiles, deux par ligne : le libellé, la valeur en grand et en couleur,
 * l'appréciation dessous. On lit l'état d'un territoire d'un coup d'œil.
 */
class KpiGrid(ui: Ui, indicators: List<Indicator>) : Table() {
    init {
        defaults().growX().uniformX().pad(2f)
        indicators.forEachIndexed { i, ind ->
            val color = Theme.tone(ind.tone)
            val tile = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 6f, 4f, 6f); defaults().left() }
            tile.add(Table().apply { setBackground(ui.skin.fill(color)) }).width(STRIPE).growY().padRight(6f)
            val text = Table().apply { defaults().left(); left() }
            text.add(ui.label(ind.label, "muted")).row()
            val line = Table()
            line.add(ui.label(ind.value.ifEmpty { ind.status }, if (ind.value.isEmpty()) "bold" else "value", color)).left()
            if (ind.trend != 0) {
                val good = (ind.trend > 0) == ind.higherIsBetter
                line.add(ui.label(if (ind.trend > 0) " ▲" else " ▼", "small", if (good) Theme.good else Theme.bad))
            }
            text.add(line).left().row()
            if (ind.value.isNotEmpty()) text.add(ui.label(ind.status.lowercase().replaceFirstChar { it.uppercase() }, "muted", wrap = true)).growX().row()
            tile.add(text).growX().minWidth(0f)
            tile.hint(ui, listOf(ind.explanation, ind.details.joinToString(" · ") { "${it.first} : ${it.second}" }).filter { it.isNotBlank() }.joinToString("\n"))
            add(tile).fill()
            if (i % 2 == 1) row()
        }
    }

    private companion object {
        const val STRIPE = 3f
    }
}
