package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Légende de la couche thématique active (dégradé et libellés). */
class Legend(private val ui: Ui) {
    val root: Table = ui.panelTable().apply { pad(6f) }
    private var current: ThematicLayer? = null

    fun update(layer: ThematicLayer) {
        if (layer == current) return
        current = layer
        root.clearChildren()
        root.isVisible = layer.heatmap || layer in SYMBOL_LAYERS
        if (layer.heatmap) {
            root.add(ui.label(layer.label, "bold")).colspan(3).left().row()
            root.add(ui.label(layer.legendLow, "muted")).padRight(4f)
            root.add(Gradient(layer)).width(GRADIENT_WIDTH).height(GRADIENT_HEIGHT)
            root.add(ui.label(layer.legendHigh, "muted")).padLeft(4f).row()
            if (layer.figures.isNotEmpty()) root.add(ui.label(layer.figures, "muted")).colspan(3).left().padTop(2f)
        } else {
            root.add(ui.label(layer.label, "bold")).left().row()
            SYMBOLS[layer]?.forEach { root.add(ui.label(it, "small")).left().row() }
        }
    }

    private inner class Gradient(private val layer: ThematicLayer) : Actor() {
        private val c = Color()
        override fun draw(batch: Batch, parentAlpha: Float) {
            val steps = STEPS
            for (i in 0 until steps) {
                val t = i / (steps - 1f)
                when (layer) {
                    ThematicLayer.POPULATION -> c.set(Theme.france).lerp(Theme.accent, t)
                    ThematicLayer.INDUSTRY -> c.set(Theme.france).lerp(Color.valueOf("b08968"), t)
                    ThematicLayer.AGRICULTURE -> c.set(Theme.france).lerp(Color.valueOf("8fbf5a"), t)
                    else -> Theme.heat(t, c)
                }
                batch.color = c
                batch.draw(ui.skin.white, x + width * i / steps, y, width / steps + 1, height)
            }
            batch.color = Color.WHITE
        }
    }

    private companion object {
        const val GRADIENT_WIDTH = 120f
        const val GRADIENT_HEIGHT = 10f
        const val STEPS = 24
        val SYMBOL_LAYERS = setOf(ThematicLayer.ENERGY, ThematicLayer.TRANSPORT, ThematicLayer.MILITARY, ThematicLayer.EVENTS)
        val SYMBOLS = mapOf(
            ThematicLayer.ENERGY to listOf("■ jaune : centrale (rouge = à l'arrêt)", "■ brun : raffinerie"),
            ThematicLayer.TRANSPORT to listOf("— violet : ligne à grande vitesse", "— orange : autoroute", "◆ bleu : port ou aéroport"),
            ThematicLayer.MILITARY to listOf("▲ vert : base militaire", "Pions : bleu = France, vert = alliés,", "rouge = ennemis, gris = neutres", "Carrés colorés : zones occupées"),
            ThematicLayer.EVENTS to listOf("● rouge pulsant : crise récente", "◔ orange : chantier en cours"),
        )
    }
}
