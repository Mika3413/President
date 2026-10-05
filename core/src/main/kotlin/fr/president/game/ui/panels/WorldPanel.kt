package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.Tone
import fr.president.engine.util.Formatting
import fr.president.game.map.MapSelection
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/**
 * Journal du monde : ce qui se passe dans les autres pays et entre eux (élections, crises,
 * accords, guerres, catastrophes, économie), du plus récent au plus ancien, avec ce que cela
 * change pour la France. Filtres par rubrique et par pays ; toucher une nouvelle ouvre le pays.
 */
class WorldPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "◎ Journal du monde"
    private val session get() = nav.session
    private var category: String? = null
    private var country: String? = null
    private var shown = PAGE

    override fun applyArgument(argument: String) {
        country = argument.takeIf { it in session.state.countries }
        shown = PAGE
    }

    override fun build(into: Table) {
        val all = session.state.world.entries
        into.add(ui.label("Ce qui se passe hors de nos frontières. Le monde vit sans vous : chaque événement a ses conséquences, et certaines arrivent jusqu'en France.", "muted", wrap = true)).growX().padBottom(GAP).row()
        val bar = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        (listOf(null) + CATEGORIES.keys).forEach { c ->
            bar.addActor(ui.button(c?.let { "${CATEGORIES[it]} ${it.replaceFirstChar { ch -> ch.uppercase() }}" } ?: "Tout", "toggle") {
                category = c; shown = PAGE; nav.refresh()
            }.also { it.isChecked = category == c })
        }
        into.add(bar).growX().left().row()
        // Pays les plus actifs récemment : un filtre en un geste.
        val active = all.takeLast(RECENT).flatMap { it.countries }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(MAX_CHIPS).map { it.key }
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(3f); wrapSpace(3f) }
        country?.let { c -> chips.addActor(ui.button("✕ ${name(c)}", "toggle") { country = null; nav.refresh() }.also { it.isChecked = true }) }
        active.filter { it != country }.forEach { c -> chips.addActor(ui.button(name(c), "flat") { country = c; shown = PAGE; nav.refresh() }) }
        into.add(chips).growX().left().padTop(2f).padBottom(GAP).row()

        val list = all.asReversed().filter { (category == null || it.category == category) && (country == null || country in it.countries) }
        if (list.isEmpty()) into.add(ui.label("Rien pour l'instant : le monde bouge chaque mois, revenez bientôt.", "bold")).left().row()
        var lastDay = ""
        list.take(shown).forEach { n ->
            val day = Formatting.date(n.time)
            if (day != lastDay) { into.add(ui.label(day, "small", Theme.textMuted)).left().padTop(4f).row(); lastDay = day }
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            card.add(ui.label("${CATEGORIES[n.category] ?: "•"} ${n.headline}", "bold", color(n.tone), wrap = true)).growX().row()
            if (n.detail.isNotEmpty()) card.add(ui.label(n.detail, "small", wrap = true)).growX().row()
            if (n.france.isNotEmpty()) card.add(ui.label("Pour la France : ${n.france}", "small", Theme.accent, wrap = true)).growX().padTop(2f).row()
            n.countries.firstOrNull()?.let { c -> card.onClick { nav.select(MapSelection.Country(c)) } }
            into.add(card).growX().padBottom(3f).row()
        }
        if (list.size > shown) into.add(ui.button("Plus ancien…", "flat") { shown += PAGE; nav.refresh() }).left().padTop(GAP).row()
    }

    private fun name(c: String) = session.db.countries[c]?.definition?.name ?: c

    private fun color(t: Tone): Color = when (t) { Tone.GOOD -> Theme.good; Tone.BAD -> Theme.bad; Tone.WARNING -> Theme.warning; Tone.NEUTRAL -> Theme.text }

    private companion object {
        val CATEGORIES = linkedMapOf("conflit" to "⚔", "diplomatie" to "☎", "politique" to "⚖", "économie" to "€", "société" to "⚑", "catastrophe" to "⚠")
        const val PAGE = 40
        const val RECENT = 120
        const val MAX_CHIPS = 8
    }
}
