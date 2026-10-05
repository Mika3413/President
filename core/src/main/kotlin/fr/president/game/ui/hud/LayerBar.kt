package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Sélecteur de couches de la carte : un seul bouton (« Carte : Opinion ») ; le toucher ouvre
 * une grille de toutes les couches par-dessus l'écran, sans rien déplacer ni masquer d'autre.
 * Choisir une couche (ou toucher ailleurs) referme la grille.
 */
class LayerBar(private val ui: Ui, initial: ThematicLayer, private val onChange: (ThematicLayer) -> Unit) {
    val root: Table = Table()
    /** Calque plein écran à ajouter au-dessus de tout le reste de l'interface. */
    val popup: Table = Table().apply { setFillParent(true); top().left(); isVisible = false }
    private var current = initial
    private val toggle: TextButton = ui.colorButton(title(initial), Theme.catLocal) { setOpen(!popup.isVisible) }
    /** Hauteur réservée au-dessus de la grille (barre du haut + bouton « Carte »). */
    var topOffset: () -> Float = { 0f }
    var compact = false

    init {
        toggle.name = "layers"
        root.add(toggle).growX()
        popup.touchable = Touchable.enabled
        // Toucher en dehors de la grille : fermeture.
        popup.addListener(object : InputListener() {
            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (event.target == popup) { setOpen(false); return true }
                return false
            }
        })
    }

    private fun build() {
        popup.clearChildren()
        val grid = ui.panelTable().apply { pad(6f) }
        grid.add(ui.label("Que voulez-vous voir sur la carte ?", "bold")).colspan(columns()).left().padBottom(4f).row()
        ThematicLayer.entries.forEachIndexed { i, layer ->
            val b = ui.button(layer.label, "toggle") {
                current = layer
                onChange(layer)
                toggle.setText(title(layer))
                setOpen(false)
            }
            b.isChecked = layer == current
            b.name = "layer.${layer.name.lowercase()}"
            grid.add(b).growX().uniformX().pad(2f)
            if (i % columns() == columns() - 1) grid.row()
        }
        popup.add(grid).left().top().padTop(topOffset()).padLeft(6f)
    }

    private fun columns() = if (compact) 2 else 3

    private fun setOpen(value: Boolean) {
        if (value) build()
        popup.isVisible = value
        popup.toFront()
    }

    fun close() = setOpen(false)

    val isOpen: Boolean get() = popup.isVisible

    private fun title(layer: ThematicLayer) = "☰ Carte : ${layer.label}"
}
