package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.hud.AcademyCoach
import fr.president.game.ui.hud.AcademyCourse

/** L'Académie : la liste des modules, leur progression, et le bouton pour en commencer un. */
class AcademyPanel(ui: Ui, private val nav: Navigator, private val coach: () -> AcademyCoach, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "✎ Académie du président"
    private val session get() = nav.session

    override fun build(into: Table) {
        val player = session.state.player
        val done = player.academyDone
        into.add(ui.label("Apprenez à vous servir du simulateur en profondeur, module par module. Chaque leçon explique un mécanisme puis vous le fait pratiquer dans votre partie ; l'étape se valide quand vous l'avez vraiment fait.", "muted", wrap = true)).growX().padBottom(4f).row()
        into.add(ui.label("Progression : ${done.size} / ${AcademyCourse.modules.size} modules", "bold", if (done.size == AcademyCourse.modules.size) Theme.good else Theme.highlight)).padBottom(GAP).row()
        AcademyCourse.modules.forEach { m ->
            val finished = m.id in done
            val running = player.academyModule == m.id
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(if (finished) "✔" else m.icon, "value", if (finished) Theme.good else Theme.highlight)).width(ICON).left()
            head.add(ui.label(m.title, "bold", wrap = true)).left().growX().minWidth(0f)
            head.add(ui.label("${m.steps.size} étapes", "muted")).right()
            card.add(head).growX().row()
            card.add(ui.label(m.goal, "small", wrap = true)).growX().padTop(2f).row()
            val label = when {
                running -> "En cours · étape ${player.academyStep + 1}/${m.steps.size}"
                finished -> "↻ Recommencer"
                else -> "▶ Commencer"
            }
            card.add(ui.colorButton(label, if (finished) Theme.panelBorder else Theme.accentDark) {
                if (!running) coach().startModule(m.id)
                // Le module commence sur la carte : on referme les panneaux.
                nav.closePanels()
            }.also { it.name = "academy.${m.id}"; it.isDisabled = running }).right().padTop(4f).row()
            into.add(card).growX().padBottom(4f).row()
        }
        if (player.academyModule != null) {
            into.add(ui.button("Arrêter le module en cours", "flat") { coach().stop(); nav.refresh() }).left().padTop(4f).row()
        }
    }

    private companion object {
        const val ICON = 26f
    }
}
