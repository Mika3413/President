package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Sélecteur de couches de la carte, replié par défaut en un seul bouton (« Carte : Opinion »)
 * pour laisser toute la place à la carte.
 */
class LayerBar(ui: Ui, initial: ThematicLayer, onChange: (ThematicLayer) -> Unit) {
    val root: Table = Table()
    private val list: Table = ui.panelTable()
    private var open = false
    private val toggle: TextButton = ui.colorButton(title(initial), Theme.catLocal) { setOpen(!open) }

    init {
        toggle.name = "layers"
        list.pad(4f)
        val group = ButtonGroup<TextButton>().apply { setMaxCheckCount(1); setMinCheckCount(1) }
        ThematicLayer.entries.forEach { layer ->
            val b = ui.button(layer.label, "toggle") {
                onChange(layer)
                toggle.setText(title(layer))
                setOpen(false)
            }
            group.add(b)
            if (layer == initial) b.isChecked = true
            list.add(b).growX().padBottom(2f).row()
        }
        setOpen(false)
    }

    private fun setOpen(value: Boolean) {
        open = value
        root.clearChildren()
        root.add(toggle).growX().row()
        if (open) root.add(list).growX().padTop(2f).row()
    }

    private fun title(layer: ThematicLayer) = "☰ Carte : ${layer.label}"
}
