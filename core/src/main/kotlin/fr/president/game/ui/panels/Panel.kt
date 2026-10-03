package fr.president.game.ui.panels

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
    private val scroll = ScrollPane(content, ui.s).apply { setFadeScrollBars(false); setScrollingDisabled(true, false) }
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
    }

    abstract val title: String

    /** Remplit [into] avec le contenu courant. */
    protected abstract fun build(into: Table)

    fun refresh() {
        titleLabel.setText(title)
        val scrollY = scroll.scrollY
        content.clearChildren()
        build(content)
        scroll.layout()
        scroll.scrollY = scrollY
        scroll.updateVisualScroll()
    }

    /** Certains panneaux se rafraîchissent périodiquement (données vivantes). */
    open val autoRefresh: Boolean = true

    protected companion object {
        const val PAD = 12f
        const val GAP = 8f
    }
}
