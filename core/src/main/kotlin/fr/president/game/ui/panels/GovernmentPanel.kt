package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.government.MinistryEffectiveness
import fr.president.engine.government.Priority
import fr.president.engine.politics.Character
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Composition du gouvernement : Premier ministre, ministres, priorités, remplacements. */
class GovernmentPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Gouvernement"
    private var replacing: String? = null
    private var message: String? = null
    private val session get() = nav.session

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        into.add(com.badlogic.gdx.scenes.scene2d.ui.Table().also { t ->
            val ind = session.national.parliament()
            t.add(fr.president.game.ui.widgets.IndicatorView(ui, ind, expanded)).growX()
        }).row()
        val def = session.db.country(session.state.player.countryId).government!!
        val effectiveness = MinistryEffectiveness(session.context)
        for (ministry in def.ministries) {
            val minister = effectiveness.ministerOf(ministry.id)
            val box = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(Theme.panelAlt)) }
            box.add(ui.label(ministry.title, "muted", wrap = true)).growX().row()
            box.add(ui.label(minister?.fullName ?: "Vacant — intérim", "bold", if (minister == null) Theme.warning else null)).row()
            minister?.let { m ->
                val summary = session.characters.summary(m).filter { it.first in SHOWN }.joinToString(" · ") { "${it.first} : ${it.second}" }
                box.add(ui.label(summary, "small", wrap = true)).growX().row()
                val traits = session.characters.knownTraits(m)
                if (traits.isNotEmpty()) box.add(ui.label("Tempérament : " + traits.joinToString(", "), "muted", wrap = true)).growX().row()
            }
            if (!ministry.isPrimeMinister) box.add(priorityRow(ministry.id)).left().padTop(4f).row()
            box.add(ui.button(if (replacing == ministry.id) "Annuler" else if (minister == null) "Nommer…" else "Remplacer…", "flat") {
                replacing = if (replacing == ministry.id) null else ministry.id
                nav.refresh()
            }).left().row()
            if (replacing == ministry.id) candidates(box, ministry.id)
            into.add(box).growX().padBottom(GAP).row()
        }
    }

    private fun priorityRow(ministryId: String): Table {
        val row = Table().apply { defaults().padRight(4f) }
        row.add(ui.label("Priorité", "muted")).padRight(6f)
        val current = session.state.government.priorities[ministryId] ?: Priority.NORMAL
        Priority.entries.forEach { p ->
            val b = ui.button(p.label, "toggle") {
                message = session.government.setPriority(ministryId, p).fold({ null }, { it.message })
                nav.refresh()
            }
            b.isChecked = p == current
            row.add(b)
        }
        return row
    }

    private fun candidates(box: Table, ministryId: String) {
        session.government.candidates(ministryId).forEach { c: Character ->
            val t = Table().apply { defaults().left(); pad(4f); setBackground(ui.skin.fill(Theme.panel)) }
            t.add(ui.label(c.fullName, "bold")).row()
            t.add(ui.label(session.characters.summary(c).filter { it.first in CANDIDATE_SHOWN }.joinToString(" · ") { "${it.first} : ${it.second}" }, "small", wrap = true)).growX().row()
            t.add(ui.button("Nommer", "accent") {
                message = session.government.appoint(ministryId, c.id).fold({ "${c.fullName} est nommé(e)." }, { it.message })
                replacing = null
                nav.refresh()
            }).left()
            box.add(t).growX().padTop(4f).row()
        }
    }

    private companion object {
        val SHOWN = setOf("Compétence", "Loyauté", "Popularité")
        val CANDIDATE_SHOWN = setOf("Âge", "Compétence", "Gestion", "Expérience", "Popularité", "Sensibilité")
    }
}
