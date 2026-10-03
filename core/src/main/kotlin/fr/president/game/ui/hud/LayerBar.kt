package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Ui

/** Sélecteur de couches thématiques de la carte. */
class LayerBar(ui: Ui, initial: ThematicLayer, onChange: (ThematicLayer) -> Unit) {
    val root: Table = ui.panelTable()

    init {
        root.pad(4f)
        val group = ButtonGroup<TextButton>().apply { setMaxCheckCount(1); setMinCheckCount(1) }
        ThematicLayer.entries.forEach { layer ->
            val b = ui.button(layer.label, "toggle") { onChange(layer) }
            group.add(b)
            if (layer == initial) b.isChecked = true
            root.add(b).growX().padBottom(2f).row()
        }
    }
}
