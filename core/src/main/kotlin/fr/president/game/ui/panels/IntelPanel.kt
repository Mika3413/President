package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Value
import com.badlogic.gdx.utils.Align
import fr.president.engine.military.SecretService
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/**
 * Renseignement : opérations de la DGSE à l'étranger (pays par pays), groupes armés et
 * terroristes, contre-espionnage et moyens de la DGSI.
 */
class IntelPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "⊘ Renseignement"
    private val session get() = nav.session
    private val intel get() = session.intel
    private var tab = Tab.ABROAD
    private var target: String? = null
    private var message: String? = null

    private enum class Tab(val label: String) { ABROAD("Opérations à l'étranger"), GROUPS("Groupes armés"), HOME("Contre-espionnage") }

    override fun build(into: Table) {
        val tabs = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t -> tabs.addActor(ui.button(t.label, "toggle") { tab = t; message = null; nav.refresh() }.also { it.isChecked = t == tab }) }
        into.add(tabs).growX().left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        if (!intel.available) { into.add(ui.label("Données indisponibles.", "muted")).row(); return }
        val s = intel.state
        into.add(ui.label("Capacité des services : ${Math.round(s.capacity * 100)} % · opérations en cours : ${s.operations.size} / ${SecretService.MAX_OPERATIONS}", "bold", wrap = true)).growX().padBottom(4f).row()
        when (tab) {
            Tab.ABROAD -> abroad(into)
            Tab.GROUPS -> groups(into)
            Tab.HOME -> home(into)
        }
        val history = s.history.takeLast(MAX_HISTORY).reversed()
        if (history.isNotEmpty()) {
            into.add(ui.label("Derniers comptes rendus", "bold")).padTop(GAP).row()
            history.forEach { r ->
                val color = when { r.success -> Theme.good; r.exposed -> Theme.bad; else -> Theme.textMuted }
                into.add(ui.label("${Formatting.date(r.time)} · ${r.text}", "small", color, wrap = true)).growX().padBottom(2f).row()
            }
        }
    }

    private fun run(r: Result<String>) { message = r.fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }

    private fun TextButton.wide() = apply { label.setWrap(true); label.setAlignment(Align.left) }

    private fun name(id: String) = session.context.db.countries[id]?.definition?.name ?: id

    // ---- À l'étranger ----

    private fun abroad(into: Table) {
        into.add(ui.label("Choisissez un pays. Un échec peut éclater au grand jour : expulsions, scandale, colère de l'opinion. Les opérations les plus offensives sont réservées aux pays hostiles.", "muted", wrap = true)).growX().padBottom(4f).row()
        val player = session.state.player.countryId
        val relations = fr.president.engine.diplomacy.RelationCalculator(session.context)
        val countries = session.state.countries.keys.filter { it != player }.sortedBy { relations.score(it, player) }
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(3f); wrapSpace(3f) }
        countries.forEach { c -> chips.addActor(ui.button(name(c), "toggle") { target = c; message = null; nav.refresh() }.also { it.isChecked = target == c }) }
        into.add(chips).growX().padBottom(GAP).row()
        val c = target ?: return
        val country = session.state.countries.getValue(c)
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val leader = session.state.characters[country.leaderId]
        val dossier = intel.dossier(c)
        box.add(ui.label(name(c), "bold")).row()
        box.add(ui.label("Dirigeant : ${leader?.fullName ?: "?"} · popularité ${Math.round(country.leaderApproval * 100)} % · relation : ${relations.label(relations.score(c, player)).lowercase()}", "small", wrap = true)).growX().row()
        box.add(ui.label(if (dossier > 0) "Dossier de la DGSE : ${Math.round(dossier * 100)} % à jour" else "Aucun dossier récent sur ce pays.", "small", if (dossier > 0) Theme.good else Theme.textMuted)).row()
        if (leader != null && leader.knownTraits.isNotEmpty()) {
            val traits = leader.knownTraits.sorted().joinToString(", ") { "${TRAITS[it] ?: it} ${Math.round(leader.trait(it) * 100)}" }
            box.add(ui.label("Caractère : $traits", "small", wrap = true)).growX().row()
        }
        into.add(box).growX().padBottom(4f).row()
        session.state.intel.operations.filter { it.target == c }.forEach { o ->
            into.add(ui.label("En cours : ${intel.op(o.op)?.label} — résultat dans ${session.context.now.daysUntil(o.endAt).coerceAtLeast(0.0).toInt()} j", "small", Theme.accent, wrap = true)).growX().row()
        }
        session.context.db.intel?.operations?.forEach { def ->
            val blocker = intel.blocker(def, c)
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(def.label, "bold", wrap = true)).growX().minWidth(0f)
            head.add(ui.label("${Math.round(intel.chance(def, c) * 100)} %", "bold", Theme.accent)).right().padLeft(4f)
            card.add(head).growX().row()
            card.add(ui.label(def.description, "small", Theme.textMuted, wrap = true)).growX().row()
            card.add(ui.label("${Formatting.billions(def.costBillions)} · ${def.days} jours · risque d'être découvert en cas d'échec : ${Math.round(def.exposure * 100)} %", "small", wrap = true)).growX().row()
            card.add(ui.colorButton("Lancer", if (def.kind == "coup") Theme.bad else Theme.accentDark) { run(intel.start(def.id, c)) }.also { it.isDisabled = blocker != null }).left().padTop(2f).row()
            blocker?.let { card.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
            into.add(card).growX().padBottom(4f).row()
        }
    }

    // ---- Groupes armés ----

    private fun groups(into: Table) {
        into.add(ui.label("Plus un groupe est fort et hostile, plus le risque d'attentat, d'enlèvement ou d'émeute monte. Infiltrer un groupe permet de déjouer ses projets.", "muted", wrap = true)).growX().padBottom(4f).row()
        intel.groups().forEach { r ->
            val open = r.def.id in expanded
            val color = when { r.threat >= 0.4 -> Theme.bad; r.threat >= 0.2 -> Theme.warning; else -> Theme.good }
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(r.def.label, "bold", wrap = true)).growX().minWidth(0f)
            head.add(ui.label("menace ${Math.round(r.threat * 100)}", "bold", color)).right().padLeft(4f)
            head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
            head.onClick { if (!expanded.remove(r.def.id)) expanded += r.def.id; nav.refresh() }
            card.add(head).growX().row()
            val gauge = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
            gauge.add(Table().apply { setBackground(ui.skin.fill(color)) }).width(Value.percentWidth(r.strength.toFloat().coerceIn(0.02f, 1f), gauge)).height(BAR).left().expandX()
            card.add(gauge).growX().height(BAR).padTop(2f).row()
            card.add(ui.label("Force ${Math.round(r.strength * 100)} · hostilité ${Math.round(r.hostility * 100)}" + (if (r.intel > 0) " · infiltré ${Math.round(r.intel * 100)} %" else "") + " · ${r.def.where}", "small", Theme.textMuted, wrap = true)).growX().row()
            if (open) {
                card.add(ui.label(r.def.description, "small", wrap = true)).growX().padTop(2f).row()
                r.def.sponsor?.let { card.add(ui.label("Soutenu par : ${name(it)}", "small", Theme.warning)).row() }
                r.def.actions.forEach { a ->
                    val blocker = intel.groupBlocker(r.def.id, a)
                    val chance = intel.groupChance(r.def.id, a)
                    val label = (ACTIONS[a] ?: a) + if (a in setOf("neutralize", "infiltrate", "dissolve")) " (${Math.round(chance * 100)} %)" else ""
                    card.add(ui.button(label + (blocker?.let { " — $it" } ?: ""), "flat") { run(intel.actOnGroup(r.def.id, a)) }.also { it.isDisabled = blocker != null }.wide()).growX().row()
                }
            }
            into.add(card).growX().padBottom(4f).row()
        }
    }

    // ---- DGSI ----

    private fun home(into: Table) {
        val s = intel.state
        into.add(ui.label("Les pays hostiles espionnent nos ministères, nos laboratoires et nos armées. Un réseau trop actif finit par provoquer des fuites.", "muted", wrap = true)).growX().padBottom(4f).row()
        val spies = intel.spies()
        if (spies.isEmpty()) into.add(ui.label("Aucun réseau étranger significatif repéré.", "small", Theme.good)).left().row()
        spies.forEach { r ->
            val color = if (r.level > 0.5) Theme.bad else if (r.level > 0.25) Theme.warning else Theme.textMuted
            into.add(ui.button("Démanteler le réseau ${fr.president.engine.data.CountryNames(session.context.db.country(r.country).definition).of} (activité ${Math.round(r.level * 100)} %)" + (r.blocker?.let { " — $it" } ?: ""), "flat") { run(intel.counterEspionage(r.country)) }
                .also { it.isDisabled = r.blocker != null; it.label.color = color }.wide()).growX().row()
        }
        into.add(ui.label("Moyens et posture", "bold")).padTop(GAP).row()
        into.add(ui.button(if (s.surveillance) "Surveillance renforcée : en vigueur (toucher pour revenir à la normale)" else "Renforcer la surveillance des milieux radicaux (moins d'attentats, libertés en recul)", "toggle") {
            message = intel.setSurveillance(!s.surveillance); nav.refresh()
        }.also { it.isChecked = s.surveillance }.wide()).growX().padTop(3f).row()
        val recruit = intel.recruitBlocker()
        into.add(ui.colorButton("Recruter 1 000 agents (0,4 Md€)", Theme.accentDark) { run(intel.recruit()) }.also { it.isDisabled = recruit != null }).left().padTop(3f).row()
        recruit?.let { into.add(ui.label("↻ $it", "small", Theme.textMuted)).left().row() }
    }

    private companion object {
        const val BAR = 5f
        const val MAX_HISTORY = 6
        val ACTIONS = mapOf("neutralize" to "Neutraliser un chef (DGSE)", "strike" to "Frapper ses positions", "infiltrate" to "Infiltrer le groupe (DGSI)",
            "negotiate" to "Ouvrir des négociations discrètes", "dissolve" to "Dissoudre en Conseil des ministres")
        val TRAITS = mapOf("aggressiveness" to "agressivité", "pragmatism" to "pragmatisme", "nationalism" to "nationalisme", "openness" to "ouverture",
            "ego" to "ego", "caution" to "prudence", "integrity" to "intégrité", "charisma" to "charisme", "toughness" to "fermeté", "militarism" to "militarisme")
    }
}
