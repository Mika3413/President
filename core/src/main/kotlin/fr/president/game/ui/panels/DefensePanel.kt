package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Value
import com.badlogic.gdx.utils.Align
import fr.president.engine.military.EquipmentDef
import fr.president.engine.military.NuclearPosture
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/**
 * Défense : capacités des armées et catalogue d'armement à commander, ventes d'armes à
 * l'étranger, bases militaires hors de France, dissuasion nucléaire.
 */
class DefensePanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "✪ Défense"
    private val session get() = nav.session
    private val defense get() = session.defense
    private var tab = Tab.ARMAMENT
    private var category: String? = null
    private var message: String? = null

    private enum class Tab(val label: String) { ARMAMENT("Armement"), SALES("Ventes d'armes"), BASES("Bases à l'étranger"), NUCLEAR("Dissuasion") }

    override fun build(into: Table) {
        val tabs = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t -> tabs.addActor(ui.button(t.label, "toggle") { tab = t; message = null; nav.refresh() }.also { it.isChecked = t == tab }) }
        into.add(tabs).growX().left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        if (!defense.available) { into.add(ui.label("Données indisponibles.", "muted")).row(); return }
        when (tab) {
            Tab.ARMAMENT -> armament(into)
            Tab.SALES -> sales(into)
            Tab.BASES -> bases(into)
            Tab.NUCLEAR -> nuclear(into)
        }
    }

    override fun applyArgument(argument: String) {
        if (argument == "nuclear") tab = Tab.NUCLEAR
    }

    private fun run(r: Result<String>) { message = r.fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }

    private fun TextButton.wide() = apply { label.setWrap(true); label.setAlignment(Align.left) }

    // ---- Armement ----

    private fun armament(into: Table) {
        val file = session.context.db.defense ?: return
        into.add(ui.label("Capacités des armées", "bold")).left().row()
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        file.capabilityLabels.forEach { (id, label) ->
            val v = defense.capability(id)
            val color = when { v >= 0.6 -> Theme.good; v >= 0.35 -> Theme.warning; else -> Theme.bad }
            val row = Table()
            row.add(ui.label(label, "small")).width(Value.percentWidth(0.45f, box)).left()
            val gauge = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
            gauge.add(Table().apply { setBackground(ui.skin.fill(color)) }).width(Value.percentWidth(v.toFloat().coerceIn(0.02f, 1f), gauge)).height(BAR).left().expandX()
            row.add(gauge).growX().height(BAR).padLeft(4f)
            row.add(ui.label("${Math.round(v * 100)}", "small", color)).width(28f).right().padLeft(4f)
            box.add(row).growX().row()
        }
        box.add(ui.label("Défense aérienne : moins de dégâts des frappes ennemies. Frappe, drones, espace : frappes plus efficaces. Cyber : moins d'attaques hybrides. Munitions : les stocks se remplissent seuls.", "small", Theme.textMuted, wrap = true)).growX().padTop(3f).row()
        into.add(box).growX().padBottom(GAP).row()

        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        chips.addActor(ui.button("Tout", "toggle") { category = null; nav.refresh() }.also { it.isChecked = category == null })
        file.categories.forEach { c -> chips.addActor(ui.button("${c.icon} ${c.label}", "toggle") { category = c.id; nav.refresh() }.also { it.isChecked = category == c.id }) }
        into.add(chips).growX().padBottom(GAP).row()
        file.equipment.filter { category == null || it.category == category }.forEach { into.add(equipment(it)).growX().padBottom(4f).row() }
    }

    private fun equipment(def: EquipmentDef): Table {
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(def.label, "bold", wrap = true)).growX().minWidth(0f)
        head.add(ui.label(Formatting.billions(def.costBillions), "bold")).right().padLeft(4f)
        card.add(head).growX().row()
        card.add(ui.label(def.description, "small", Theme.textMuted, wrap = true)).growX().row()
        val gains = def.capabilities.entries.joinToString(", ") { (k, v) -> "${session.context.db.defense?.capabilityLabels?.get(k) ?: k} +${Math.round(v * 100)}" }
        val unit = def.unitType?.let { session.context.db.unitTypes[it]?.label }
        val line = listOfNotNull(unit?.let { "Livre : $it" }, gains.takeIf { it.isNotEmpty() }, "délai ${def.days / 30} mois",
            def.supplier?.let { "fournisseur : ${session.context.db.countries[it]?.definition?.name ?: it}" } ?: "industrie française").joinToString(" · ")
        card.add(ui.label(line, "small", wrap = true)).growX().row()
        val pending = defense.pending(def.id)
        val blocker = defense.orderBlocker(def)
        card.add(ui.colorButton("Commander" + (if (pending > 0) " ($pending en cours)" else ""), Theme.accentDark) { run(defense.order(def.id)) }
            .also { it.isDisabled = blocker != null }).left().padTop(3f).row()
        blocker?.let { card.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
        return card
    }

    // ---- Ventes d'armes ----

    private fun sales(into: Table) {
        into.add(ui.label("Chaque vente doit être autorisée : pas d'armes pour un pays qui mène une guerre d'agression. Une base française dans la région aide à convaincre. Vendre à un régime contesté fâche une partie de l'opinion ; armer un pays en guerre fâche son ennemi.", "muted", wrap = true)).growX().padBottom(GAP).row()
        val trade = session.trade
        session.state.trade.bids.filter { trade.product(it.product)?.arms == true }.forEach { b ->
            val p = trade.product(b.product)!!
            val days = session.context.now.daysUntil(b.decideAt).coerceAtLeast(0.0)
            into.add(ui.label("En cours : ${p.label} — ${country(b.client)}, décision dans ${days.toInt()} j", "small", Theme.accent, wrap = true)).growX().row()
        }
        session.context.db.trade?.products?.filter { it.arms }?.forEach { p ->
            val open = p.id in expanded
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(p.label, "bold", wrap = true)).growX().minWidth(0f)
            head.add(ui.label(Formatting.billions(p.valueBillions), "bold")).right()
            head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
            head.onClick { if (!expanded.remove(p.id)) expanded += p.id; nav.refresh() }
            card.add(head).growX().row()
            if (open) trade.bids(p).forEach { o ->
                val text = "${o.clientName} — ${Math.round(o.chance * 100)} %" + (if (o.client in p.sensitive) " · controversé" else "") + (o.blocker?.let { " — $it" } ?: "")
                card.add(ui.button(text, "flat") { run(trade.bid(p.id, o.client, false)) }.also { it.isDisabled = o.blocker != null }.wide()).growX().row()
            }
            into.add(card).growX().padBottom(4f).row()
        }
        session.state.trade.results.filter { trade.product(it.product)?.arms == true }.takeLast(MAX_RESULTS).reversed().forEach { r ->
            into.add(ui.label(if (r.won) "✔ ${trade.product(r.product)?.label} — ${country(r.client)}" else "✖ ${trade.product(r.product)?.label} — ${country(r.client)} a choisi ${country(r.rival)}",
                "small", if (r.won) Theme.good else Theme.bad, wrap = true)).growX().row()
        }
    }

    private fun country(id: String) = session.context.db.countries[id]?.definition?.name ?: id

    // ---- Bases ----

    private fun bases(into: Table) {
        val rows = defense.bases()
        val cost = rows.filter { it.open }.sumOf { it.def.annualCostBillions }
        into.add(ui.label("${rows.count { it.open }} bases ouvertes · ${Formatting.billions(cost)} par an. Une base donne de l'influence dans sa région (et aide à vendre des armes), mais peut inquiéter des voisins ; en Afrique, l'opinion locale peut exiger le départ des soldats.", "muted", wrap = true)).growX().padBottom(GAP).row()
        rows.sortedByDescending { it.open }.forEach { r ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(r.def.label, "bold", wrap = true)).growX().minWidth(0f)
            head.add(ui.label(if (r.open) "ouverte" else "fermée", "small", if (r.open) Theme.good else Theme.textMuted)).right().padLeft(4f)
            card.add(head).growX().row()
            val influence = r.def.influence.joinToString(", ") { country(it) }
            card.add(ui.label("${r.def.hostName} · ${Formatting.billions(r.def.annualCostBillions)} par an" + (if (influence.isNotEmpty()) " · influence : $influence" else "") +
                (if (r.def.worries.isNotEmpty()) " · inquiète : ${r.def.worries.joinToString(", ") { country(it) }}" else ""), "small", wrap = true)).growX().row()
            if (r.def.evictionRisk >= 0.004) card.add(ui.label("Sentiment antifrançais : risque de demande de départ.", "small", Theme.warning, wrap = true)).growX().row()
            if (r.open) card.add(ui.button("Fermer la base", "flat") { run(defense.closeBase(r.def.id)) }).left().row()
            else {
                card.add(ui.colorButton("Négocier l'ouverture (${Formatting.billions(r.def.openCostBillions)})", Theme.accentDark) { run(defense.openBase(r.def.id)) }
                    .also { it.isDisabled = r.blocker != null }).left().padTop(3f).row()
                r.blocker?.let { card.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
            }
            into.add(card).growX().padBottom(4f).row()
        }
    }

    // ---- Dissuasion ----

    private var confirming: String? = null

    /** Échelle d'escalade, décision après une frappe ennemie, ultime avertissement. */
    private fun escalation(into: Table) {
        val nuclear = session.nuclear
        if (nuclear.pendingDecision()) {
            val box = Table().apply { setBackground(ui.skin.fill(Theme.bad)); pad(8f, 10f, 8f, 10f); defaults().left() }
            box.add(ui.label("☢ DÉCISION NUCLÉAIRE", "title", com.badlogic.gdx.graphics.Color.WHITE)).row()
            box.add(ui.label("Une arme nucléaire a frappé nos forces. Vous seul décidez de la réponse.", "small", com.badlogic.gdx.graphics.Color.WHITE, wrap = true)).growX().row()
            into.add(box).growX().padBottom(4f).row()
            fr.president.engine.military.NuclearService.Response.entries.forEach { r ->
                into.add(ui.label(r.text, "small", wrap = true)).growX().padTop(4f).row()
                if (confirming == r.name) {
                    val row = Table().apply { defaults().padRight(4f) }
                    row.add(ui.colorButton("✔ Je confirme : ${r.label.lowercase()}", Theme.bad) { confirming = null; run(nuclear.respond(r)) })
                    row.add(ui.button("Annuler") { confirming = null; nav.refresh() })
                    into.add(row).left().row()
                } else into.add(ui.colorButton(r.label, if (r == fr.president.engine.military.NuclearService.Response.RESTRAINT) Theme.accentDark else Theme.bad) { confirming = r.name; nav.refresh() }).left().row()
            }
            into.add().height(GAP).row()
        }
        val ladder = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        ladder.add(ui.label("Échelle d'escalade", "bold")).row()
        val current = nuclear.rung()
        fr.president.engine.military.NuclearService.Rung.entries.forEach { r ->
            val on = r.ordinal <= current.ordinal
            val color = when { !on -> Theme.textMuted; r.ordinal >= 3 -> Theme.bad; r.ordinal == 2 -> Theme.warning; else -> Theme.accent }
            ladder.add(ui.label((if (r == current) "▶ " else "   ") + "${r.ordinal}. ${r.label}", if (r == current) "bold" else "small", color)).row()
            if (r == current) ladder.add(ui.label(r.text, "muted", wrap = true)).growX().row()
        }
        into.add(ladder).growX().padBottom(GAP).row()
    }

    private fun nuclear(into: Table) {
        escalation(into)
        val d = defense.state
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        fun line(label: String, value: String) { val r = Table(); r.add(ui.label(label, "small")).left().expandX(); r.add(ui.label(value, "bold")).right(); box.add(r).growX().row() }
        line("Têtes nucléaires", "${d.warheads}")
        line("Crédibilité de la dissuasion", "${Math.round(d.credibility * 100)} %")
        line("Posture", d.posture.label)
        line("Dimension européenne", if (d.europeanUmbrella) "oui" else "non")
        box.add(ui.label("La dissuasion protège nos intérêts vitaux : aucun pays ne nous attaque directement. Sa crédibilité s'use sans investissement ; elle compte quand il faut avertir un agresseur.", "small", Theme.textMuted, wrap = true)).growX().padTop(3f).row()
        into.add(box).growX().padBottom(GAP).row()

        fun action(text: String, blocker: String?, color: com.badlogic.gdx.graphics.Color? = null, act: () -> Result<String>) {
            val b = if (color != null) ui.colorButton(text, color) { run(act()) } else ui.button(text, "flat") { run(act()) }
            into.add(b.also { it.isDisabled = blocker != null }.wide()).growX().padTop(3f).row()
            blocker?.let { into.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
        }
        action("Moderniser : missiles M51, ASN4G, sous-marins de 3e génération (3 Md€)", defense.modernizeBlocker(), Theme.accentDark) { defense.modernize() }
        action(if (d.europeanUmbrella) "Revenir à une dissuasion strictement nationale" else "Proposer une dimension européenne de la dissuasion", null) { defense.setUmbrella(!d.europeanUmbrella) }
        NuclearPosture.entries.forEach { p ->
            val r = Table()
            r.add(ui.button(p.label, "toggle") { run(defense.setPosture(p)) }.also { it.isChecked = d.posture == p }).width(POSTURE_WIDTH).left()
            r.add(ui.label(p.description, "small", wrap = true)).growX().padLeft(6f)
            into.add(r).growX().padTop(3f).row()
        }
        action("Réduire l'arsenal de 30 têtes (geste de désarmement)", defense.reductionBlocker()) { defense.reduceArsenal() }
        action("Procéder à un essai nucléaire dans le Pacifique", defense.testBlocker(), Theme.bad) { defense.nuclearTest() }
        into.add(ui.label("Cas extrême", "bold")).padTop(GAP).row()
        action("Avertissement solennel à l'agresseur", defense.warningBlocker(), Theme.bad) { defense.solemnWarning() }
        // L'ultime avertissement : une frappe nucléaire unique sur un objectif militaire. Double confirmation.
        val blocker = session.nuclear.strikeBlocker()
        into.add(ui.label("Frappe unique sur la plus forte concentration militaire de l'agresseur. Si l'ennemi est une puissance nucléaire, il peut riposter. Le monde entier vous jugera.", "small", Theme.textMuted, wrap = true)).growX().padTop(4f).row()
        if (blocker == null && confirming == "strike") {
            val row = Table().apply { defaults().padRight(4f) }
            row.add(ui.colorButton("☢ J'ordonne l'ultime avertissement", Theme.bad) { confirming = null; run(session.nuclear.warningStrike()) })
            row.add(ui.button("Annuler") { confirming = null; nav.refresh() })
            into.add(row).left().padTop(3f).row()
        } else action("☢ Ultime avertissement nucléaire", blocker, Theme.bad) { confirming = "strike"; Result.success("Confirmez l'ordre : il n'y aura pas de retour en arrière.") }
    }

    private companion object {
        const val BAR = 6f
        const val MAX_RESULTS = 6
        const val POSTURE_WIDTH = 150f
    }
}
