package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.dialogue.ConversationMood
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.panels.Navigator

/**
 * Bouton « S'entretenir avec… » et choix du sujet. Le compte rendu de l'entretien arrive dans la
 * messagerie ; un résumé s'affiche ici.
 */
class ConversationControls(private val ui: Ui, private val nav: Navigator) {
    private var openFor: String? = null
    private var result: Pair<String, ConversationMood>? = null
    private var resultFor: String? = null

    fun build(into: Table, characterId: String?, who: String) {
        val id = characterId ?: return
        val service = nav.session.conversations
        val character = nav.session.state.characters[id] ?: return
        if (resultFor == id) result?.let { (text, mood) ->
            val color = when (mood) { ConversationMood.WARM -> Theme.good; ConversationMood.NEUTRAL -> Theme.textMuted; ConversationMood.COLD -> Theme.bad }
            into.add(ui.label("${mood.label} — $text (compte rendu dans la messagerie)", "small", color, wrap = true)).growX().padTop(4f).row()
        }
        val blocker = service.blocker(id)
        if (blocker != null) {
            into.add(ui.label(blocker, "muted", wrap = true)).growX().padTop(2f).row()
            return
        }
        into.add(ui.button(if (openFor == id) "Annuler" else "S'entretenir avec $who…", "flat") {
            openFor = if (openFor == id) null else id
            nav.refresh()
        }).left().padTop(2f).row()
        if (openFor != id) return
        service.topicsFor(character).forEach { topic ->
            val row = Table()
            row.add(ui.button(topic.label, "default") {
                service.talk(id, topic).onSuccess { r -> result = r.summary to r.mood; resultFor = id }
                    .onFailure { e -> result = (e.message ?: "Impossible") to ConversationMood.COLD; resultFor = id }
                openFor = null
                nav.refresh()
            }).left().padRight(6f)
            row.add(ui.label(topic.hint, "muted", wrap = true)).growX().left()
            into.add(row).growX().padTop(2f).row()
        }
    }
}
