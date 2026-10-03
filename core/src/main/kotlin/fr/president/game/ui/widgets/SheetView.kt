package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.LocalReadouts
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Fiche d'un territoire ou équipement : indicateurs, élus, problèmes, projets. */
class SheetView(ui: Ui, sheet: LocalReadouts.Sheet, expanded: MutableSet<String>) : Table() {
    init {
        defaults().left().growX().padBottom(6f)
        add(ui.label(sheet.subtitle, "muted", wrap = true)).row()
        sheet.indicators.forEach { add(IndicatorView(ui, it, expanded)).row() }
        if (sheet.problems.isNotEmpty()) {
            add(ui.label("Problèmes principaux", "bold")).padTop(6f).row()
            sheet.problems.forEach { add(ui.label("• $it", "small", Theme.warning, wrap = true)).row() }
        }
        if (sheet.people.isNotEmpty()) {
            add(ui.label("Élus et représentants", "bold")).padTop(6f).row()
            sheet.people.forEach { (title, who) ->
                add(ui.label(title, "muted")).padBottom(0f).row()
                add(ui.label(who, "small", wrap = true)).row()
            }
        }
        if (sheet.projects.isNotEmpty()) {
            add(ui.label("Projets en cours", "bold")).padTop(6f).row()
            sheet.projects.forEach { add(ui.label("• $it", "small", wrap = true)).row() }
        }
    }
}
