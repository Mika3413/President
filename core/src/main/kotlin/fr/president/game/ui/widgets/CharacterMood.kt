package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.politics.Character
import fr.president.engine.politics.CharacterLife
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Ce qu'un personnage pense de vous et ce dont il se souvient, en quelques lignes colorées. */
object CharacterMood {
    fun build(ui: Ui, session: GameSession, c: Character, minister: Boolean = false, compact: Boolean = false): Table {
        val t = Table().apply { defaults().left() }
        val lines = CharacterLife(session.context).sheet(c, minister).let { if (compact) it.take(1) else it }
        lines.forEach { (text, tone) ->
            val color = when { tone > 0 -> Theme.good; tone < 0 -> Theme.bad; else -> Theme.textMuted }
            t.add(ui.label(text, "small", color, wrap = true)).growX().row()
        }
        return t
    }
}
