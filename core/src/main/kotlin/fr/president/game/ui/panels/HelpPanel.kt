package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Aide : comment jouer et glossaire des notions politiques, économiques et militaires. */
class HelpPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Aide"
    override val autoRefresh = false
    private var glossary = false

    override fun build(into: Table) {
        val tabs = Table().apply { defaults().padRight(4f) }
        tabs.add(ui.button("Comment jouer", "toggle") { glossary = false; nav.refresh() }.also { it.isChecked = !glossary })
        tabs.add(ui.button("Glossaire", "toggle") { glossary = true; nav.refresh() }.also { it.isChecked = glossary })
        into.add(tabs).left().padBottom(GAP).row()
        val help = nav.session.db.help
        if (glossary) {
            help.glossary.forEach { g ->
                into.add(ui.label(g.term, "bold", Theme.accent)).padTop(4f).row()
                into.add(ui.label(g.definition, "small", wrap = true)).growX().row()
            }
        } else {
            help.tutorial.forEach { t ->
                into.add(ui.label(t.subject, "bold")).padTop(6f).row()
                into.add(ui.label(t.body, "small", wrap = true)).growX().row()
            }
        }
    }
}
