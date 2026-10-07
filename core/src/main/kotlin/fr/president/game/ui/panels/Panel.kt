package fr.president.game.ui.panels

import fr.president.game.ui.tolerant
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.game.ui.Ui

/**
 * Panneau latéral générique : titre, bouton de fermeture et contenu défilant.
 * Le contenu est reconstruit à la demande ([refresh]) à partir de l'état de la simulation.
 */
abstract class Panel(protected val ui: Ui, private val onClose: () -> Unit) {
    val root: Table = ui.panelTable()
    private val content = Table()
    private val scroll = ScrollPane(content, ui.s).apply { setFadeScrollBars(false); setScrollingDisabled(true, false) }.tolerant()
    private val titleLabel = ui.label("", "title", wrap = true)
    protected val expanded = mutableSetOf<String>()

    /** Argument d'ouverture (onglet, cible...) transmis par le navigateur. */
    open fun applyArgument(argument: String) {}

    init {
        root.pad(PAD)
        val header = Table()
        header.add(titleLabel).left().growX().minWidth(0f)
        header.add(ui.button("✕", "flat") { onClose() }).right()
        root.add(header).growX().row()
        root.add(ui.separator()).growX().height(1f).padTop(4f).padBottom(6f).row()
        root.add(scroll).grow()
        content.top().left().defaults().left().growX()
        // Toute interaction avec le défilement repousse la mise à jour automatique du contenu.
        scroll.addCaptureListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
            override fun touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                lastInteraction = System.currentTimeMillis(); return false
            }
            override fun touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int) {
                lastInteraction = System.currentTimeMillis()
            }
            override fun scrolled(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                lastInteraction = System.currentTimeMillis(); return false
            }
        })
    }

    private var lastInteraction = 0L

    /** Le joueur fait défiler ou vient de le faire : ne pas reconstruire le contenu sous son doigt. */
    val busy: Boolean
        get() = scroll.isDragging || scroll.isFlinging || scroll.isPanning ||
            System.currentTimeMillis() - lastInteraction < IDLE_MILLIS

    /** Mise à jour périodique : seulement si le joueur ne fait pas défiler le panneau. */
    fun refreshIfIdle() {
        if (!busy) refresh()
    }

    abstract val title: String

    /** Remplit [into] avec le contenu courant. */
    protected abstract fun build(into: Table)

    fun refresh() {
        titleLabel.setText(ui.localize(title))
        val scrollY = scroll.scrollY
        content.clearChildren()
        build(content)
        // Deux passes : les textes qui passent à la ligne ne connaissent leur hauteur qu'après
        // la première. Sans cela, la hauteur est sous-estimée et la position remonte.
        content.invalidateHierarchy()
        scroll.validate()
        scroll.layout()
        scroll.scrollY = scrollY.coerceIn(0f, scroll.maxY.coerceAtLeast(0f))
        scroll.updateVisualScroll()
    }

    /** Certains panneaux se rafraîchissent périodiquement (données vivantes). */
    open val autoRefresh: Boolean = true

    protected companion object {
        const val PAD = 12f
        const val GAP = 8f
        const val IDLE_MILLIS = 4000L
    }
}
