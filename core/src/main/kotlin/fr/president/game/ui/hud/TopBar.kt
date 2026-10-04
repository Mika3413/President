package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.Indicator
import fr.president.engine.session.GameSession
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.hint
import fr.president.game.ui.panels.PanelId

/**
 * Barre supérieure, comme un tableau de bord : chaque pastille montre un chiffre clé, sa couleur
 * (vert = bien, orange = à surveiller, rouge = danger) et une flèche d'évolution.
 */
class TopBar(private val ui: Ui, private val session: GameSession, private val open: (PanelId, String?) -> Unit) {
    val root: Table = ui.panelTable()
    private val date = ui.label("", "bold")
    private val pace = ui.label("", "muted")
    private val chips = Table()
    /** Dernière valeur affichée par indicateur : un changement fait clignoter la pastille. */
    private val lastValue = HashMap<String, String>()
    private val flashStart = HashMap<String, Long>()
    private val left = Table()
    /** Écran étroit : sans la date ni les libellés, pour que tout tienne. */
    var compact = false
        set(v) {
            if (field == v) return
            field = v
            root.clearChildren()
            if (!v) root.add(left).left()
            root.add(chips).expandX().apply { if (v) center() else right() }
        }

    init {
        root.pad(4f, 8f, 4f, 8f)
        left.add(date).left().row()
        left.add(pace).left()
        root.add(left).left()
        root.add(chips).expandX().right()
    }

    fun refresh() {
        date.setText(Formats.dateTime(session.state.time))
        val paceDef = session.db.config.paces.firstOrNull { it.id == session.state.meta.clock.paceId }
        pace.setText("Rythme ${paceDef?.label?.lowercase()}")
        chips.clearChildren()
        val national = session.national
        // Toucher un chiffre ouvre sa courbe et ses causes.
        chip("♥", "Popularité", national.approval(), "approval", "Part des Français qui vous approuvent. En dessous de 40 %, la réélection devient difficile. Touchez pour voir la courbe et ses causes.")
        chip("⚒", "Chômage", national.unemployment(), "unemployment", "Part des actifs sans emploi. C'est la première préoccupation de nombreux groupes.")
        chip("▲", "Croissance", national.growth(), "growth", "Progression de la production sur un an. Elle crée des emplois et des recettes fiscales.")
        chip("€", "Budget", national.deficit(), "deficit", "Solde du budget de l'État en % du PIB. Bruxelles demande moins de 3 % de déficit.")
        chip("⚖", "Dette", national.debt(), "debt", "Dette publique en % du PIB. Plus elle monte, plus les intérêts coûtent cher et plus les marchés s'inquiètent.")
        val days = session.state.time.daysUntil(session.state.elections.nextElection).toInt()
        val tone = if (days < WARNING_DAYS) Theme.warning else Theme.accent
        val (wrapper, box) = pill(tone)
        box.add(ui.label("✔ J-$days", "value", tone)).row()
        if (!compact) box.add(ui.label("Élection", "muted"))
        wrapper.onClick { open(PanelId.ELECTIONS, null) }
        wrapper.hint(ui, "Jours avant la prochaine élection présidentielle. Perdue, elle met fin à la partie.")
        chips.add(wrapper).padLeft(CHIP_GAP)
    }

    private fun chip(icon: String, label: String, indicator: Indicator, series: String, help: String) {
        val color = Theme.tone(indicator.tone)
        val (wrapper, box) = pill(color)
        val line = Table()
        if (!compact) line.add(ui.label("$icon ", "small", color))
        val value = indicator.value.ifEmpty { indicator.status }
        line.add(ui.label(if (compact) value.replace(" PIB", "") else value, "value", color))
        arrow(indicator)?.let { (glyph, c) -> line.add(ui.label(" $glyph", "small", c)) }
        box.add(line).row()
        if (!compact) box.add(ui.label(label, "muted"))
        wrapper.onClick { open(PanelId.STATS, series) }
        wrapper.name = "chip.$series"
        wrapper.hint(ui, help)
        val shown = indicator.value + indicator.status
        val previous = lastValue.put(label, shown)
        val now = System.currentTimeMillis()
        if (previous != null && previous != shown) flashStart[label] = now
        // La pastille est reconstruite à chaque rafraîchissement : la teinte dépend du temps écoulé.
        flashStart[label]?.let { start ->
            val progress = (now - start) / FLASH_MILLIS
            if (progress < 1f) wrapper.color.set(Theme.highlight).lerp(Color.WHITE, progress) else flashStart.remove(label)
        }
        chips.add(wrapper).padLeft(if (compact) 2f else CHIP_GAP)
    }

    /** Pastille sombre soulignée de la couleur de l'état. */
    private fun pill(color: Color): Pair<Table, Table> {
        val side = if (compact) 4f else 8f
        val box = Table().apply { pad(3f, side, 3f, side); setBackground(ui.skin.fill(Theme.panelAlt)) }
        val wrapper = Table()
        wrapper.add(box).growX().row()
        wrapper.add(Table().apply { setBackground(ui.skin.fill(color)) }).height(UNDERLINE).growX()
        return wrapper to box
    }

    /** Flèche verte si l'évolution est bonne, rouge sinon. */
    private fun arrow(i: Indicator): Pair<String, Color>? {
        if (i.trend == 0) return null
        val good = (i.trend > 0) == i.higherIsBetter
        return (if (i.trend > 0) "▲" else "▼") to (if (good) Theme.good else Theme.bad)
    }

    private companion object {
        const val WARNING_DAYS = 120
        const val FLASH_MILLIS = 2500f
        const val CHIP_GAP = 5f
        const val UNDERLINE = 3f
    }
}
