package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.economy.CommodityDef
import fr.president.engine.economy.ExportProductDef
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.widgets.LineChart

/**
 * Commerce extérieur : cours des matières premières et ce que la France paie vraiment (contrats,
 * stocks, production nationale), offres d'exportation face à la concurrence, et institutions
 * économiques internationales (FMI, OMC, Banque mondiale).
 */
class TradePanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "⛁ Commerce et matières premières"
    private val session get() = nav.session
    private val trade get() = session.trade
    private var tab = Tab.COMMODITIES
    private var message: String? = null
    private var guaranteed = false

    private enum class Tab(val label: String) { COMMODITIES("Matières premières"), EXPORTS("Exportations"), INSTITUTIONS("FMI, OMC, Banque mondiale") }

    override fun build(into: Table) {
        val tabs = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t -> tabs.addActor(ui.button(t.label, "toggle") { tab = t; message = null; nav.refresh() }.also { it.isChecked = t == tab }) }
        into.add(tabs).growX().left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        if (!trade.available) { into.add(ui.label("Données indisponibles.", "muted")).row(); return }
        when (tab) {
            Tab.COMMODITIES -> commodities(into)
            Tab.EXPORTS -> exports(into)
            Tab.INSTITUTIONS -> institutions(into)
        }
    }

    /** Bouton dont le texte passe à la ligne au lieu d'élargir le panneau. */
    private fun com.badlogic.gdx.scenes.scene2d.ui.TextButton.wide() = apply { label.setWrap(true); label.setAlignment(com.badlogic.gdx.utils.Align.left) }

    private fun run(r: Result<String>) { message = r.fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }

    // ---- Matières premières ----

    private fun commodities(into: Table) {
        val factor = session.state.trade.energyFactor
        into.add(ui.label("Les cours mondiaux font le prix de l'énergie, l'inflation et l'activité des secteurs. Une guerre ou des sanctions chez un grand producteur les font flamber. Contrats à long terme, stocks et production nationale amortissent le choc.", "muted", wrap = true)).growX().padBottom(4f).row()
        into.add(ui.label("Effet des matières premières sur le prix de l'énergie : ${Formatting.signedPercent(factor - 1)}", "bold",
            if (factor > 1.1) Theme.bad else if (factor < 0.95) Theme.good else Theme.text, wrap = true)).growX().padBottom(GAP).row()
        session.context.db.trade?.commodities?.forEach { into.add(commodity(it)).growX().padBottom(4f).row() }
        into.add(ui.label("Ressources nationales", "bold")).padTop(GAP).row()
        val mine = trade.mineBlocker()
        into.add(ui.button("Autoriser la mine de lithium de l'Allier (1 Md€)" + (mine?.let { " — $it" } ?: ""), "flat") { run(trade.authorizeMine()) }.also { it.isDisabled = mine != null }.wide()).growX().row()
        val shale = trade.shaleBlocker()
        into.add(ui.button("Autoriser le gaz de schiste" + (shale?.let { " — $it" } ?: ""), "flat") { run(trade.authorizeShale()) }.also { it.isDisabled = shale != null }.wide()).growX().row()
        if (shale != null && !session.state.trade.shaleGas) into.add(ui.label("Il faut d'abord changer la loi (Lois et Constitution, onglet Société, « Gaz de schiste »).", "small", Theme.textMuted, wrap = true)).growX().row()
    }

    private fun commodity(c: CommodityDef): Table {
        val s = trade.state(c.id)
        val open = c.id in expanded
        val change = (s?.price ?: c.basePrice) / c.basePrice - 1
        val color = when { change > 0.15 -> Theme.bad; change < -0.1 -> Theme.good; else -> Theme.text }
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(c.label, "bold", wrap = true)).growX().minWidth(0f)
        head.add(ui.label("${price(s?.price ?: c.basePrice)} ${c.unit}", "bold", color)).right()
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
        head.onClick { if (!expanded.remove(c.id)) expanded += c.id; nav.refresh() }
        card.add(head).growX().row()
        val cover = trade.cover(c.id)
        card.add(ui.label("${Formatting.signedPercent(change)} par rapport au cours de référence" + (if (cover > 0) " · ${Math.round(cover * 100)} % de nos besoins couverts" else "") +
            (if (c.reserveDays > 0) " · stocks : ${s?.reserveDays?.toInt() ?: 0} jours" else ""), "small", Theme.textMuted, wrap = true)).growX().row()
        if (!open) return card
        s?.history?.takeIf { it.size > 2 }?.let { card.add(LineChart(ui.skin.white, it, false)).growX().height(CHART).padTop(3f).row() }
        val producers = c.producers.entries.sortedByDescending { it.value }.joinToString(", ") { (id, share) ->
            "${session.context.db.countries[id]?.definition?.name ?: id} ${Math.round(share * 100)} %"
        }
        card.add(ui.label("Grands producteurs : $producers", "small", wrap = true)).growX().padTop(2f).row()
        val hurt = c.sectors.filter { it.value < 0 }.keys.mapNotNull { id -> session.context.playerData.sectors?.sectors?.firstOrNull { it.id == id }?.label }
        if (hurt.isNotEmpty()) card.add(ui.label("Une hausse pénalise : ${hurt.joinToString(", ").lowercase()}", "small", Theme.warning, wrap = true)).growX().row()
        if (c.reserveDays > 0) {
            val row = Table().apply { defaults().padRight(4f).padTop(3f) }
            val release = trade.releaseBlocker(c)
            row.add(ui.colorButton("Débloquer un mois de stocks", Theme.accentDark) { run(trade.release(c.id)) }.also { it.isDisabled = release != null })
            val refill = trade.refillBlocker(c)
            row.add(ui.button("Racheter (${Formatting.billions(trade.refillCost(c))})", "flat") { run(trade.refill(c.id)) }.also { it.isDisabled = refill != null })
            card.add(row).left().row()
            (release ?: refill)?.let { card.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
        }
        val offers = trade.contracts().filter { it.def.commodity == c.id }
        if (offers.isNotEmpty()) card.add(ui.label("Contrats à long terme (${Math.round(fr.president.engine.economy.TradeService.CONTRACT_COVER * 100)} % des besoins à prix bloqué)", "bold")).padTop(4f).row()
        offers.forEach { o ->
            val label = "${o.name} : ${o.def.years} ans, rabais ${Math.round(o.def.discount * 100)} %" + when { o.active -> " — en cours"; o.blocker != null -> " — ${o.blocker}"; else -> "" }
            card.add(ui.button(label, "flat") { run(trade.signContract(o.def.country, c.id)) }.also { it.isDisabled = o.blocker != null }.wide()).growX().row()
        }
        return card
    }

    private fun price(v: Double) = if (v >= 1000) Formatting.integer(Math.round(v)) else String.format(java.util.Locale.FRENCH, "%.1f", v)

    // ---- Exportations ----

    private fun exports(into: Table) {
        into.add(ui.label("Le président porte les grands contrats : chaque offre vous coûte deux jours d'agenda. Les chances dépendent de la relation avec le client, du cours de l'euro et des appels d'offres en cours.", "muted", wrap = true)).growX().padBottom(4f).row()
        into.add(ui.button("Garantie de l'État (Bpifrance) : ${if (guaranteed) "oui, +15 points, coûte 3 % du contrat" else "non"}", "toggle") { guaranteed = !guaranteed; nav.refresh() }
            .also { it.isChecked = guaranteed }.wide()).growX().padBottom(GAP).row()
        val t = session.state.trade
        if (t.bids.isNotEmpty()) {
            into.add(ui.label("Offres en cours", "bold")).row()
            t.bids.forEach { b ->
                val p = trade.product(b.product) ?: return@forEach
                val days = session.context.now.daysUntil(b.decideAt).coerceAtLeast(0.0)
                into.add(ui.label("${p.label} — ${countryName(b.client)} : décision dans ${days.toInt()} j (chances ${Math.round(b.chance * 100)} %)", "small", wrap = true)).growX().row()
            }
        }
        session.context.db.trade?.products?.forEach { into.add(product(it)).growX().padTop(4f).row() }
        if (t.results.isNotEmpty()) {
            into.add(ui.label("Derniers résultats", "bold")).padTop(GAP).row()
            t.results.takeLast(MAX_RESULTS).reversed().forEach { r ->
                val p = trade.product(r.product)?.label ?: r.product
                into.add(ui.label(if (r.won) "✔ $p — ${countryName(r.client)} (${Formatting.billions(r.valueBillions)})" else "✖ $p — ${countryName(r.client)} a choisi ${countryName(r.rival)}",
                    "small", if (r.won) Theme.good else Theme.bad, wrap = true)).growX().row()
            }
        }
    }

    private fun product(p: ExportProductDef): Table {
        val open = p.id in expanded
        val options = trade.bids(p)
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(p.label, "bold", wrap = true)).growX().minWidth(0f)
        head.add(ui.label(Formatting.billions(p.valueBillions), "bold")).right()
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
        head.onClick { if (!expanded.remove(p.id)) expanded += p.id; nav.refresh() }
        card.add(head).growX().row()
        val tenders = options.filter { it.tender }
        card.add(ui.label(if (tenders.isNotEmpty()) "Appel d'offres : ${tenders.joinToString(", ") { it.clientName }}" else "${options.size} clients possibles · décision en ${p.delayDays.toInt()} jours",
            "small", if (tenders.isNotEmpty()) Theme.accent else Theme.textMuted, wrap = true)).growX().row()
        if (!open) return card
        options.forEach { o ->
            val chance = trade.chance(p, o.client, guaranteed)
            val text = "${if (o.tender) "★ " else ""}${o.clientName} — ${Math.round(chance * 100)} % de chances" + (o.blocker?.let { " — $it" } ?: "")
            card.add(ui.button(text, "flat") { run(trade.bid(p.id, o.client, guaranteed)) }.also { it.isDisabled = o.blocker != null }.wide()).growX().row()
        }
        return card
    }

    private fun countryName(id: String) = session.context.db.countries[id]?.definition?.name ?: id

    // ---- Institutions ----

    private fun institutions(into: Table) {
        val t = session.state.trade
        val e = session.state.playerCountry.economy
        fun section(title: String, text: String) {
            into.add(ui.label(title, "bold")).padTop(GAP).row()
            into.add(ui.label(text, "muted", wrap = true)).growX().row()
        }
        section("Fonds monétaire international", "Le FMI publie chaque année un rapport sur la France. En cas de crise de la dette, il peut prêter à taux réduit, contre une cure d'austérité.")
        if (trade.imfActive) into.add(ui.label("Programme en cours jusqu'au ${t.imfUntil?.let { Formatting.date(it) }} : taux allégés de ${Formatting.percent(e.imfRelief)}.", "small", Theme.warning, wrap = true)).growX().row()
        val imf = trade.imfBlocker()
        into.add(ui.colorButton("Appeler le FMI à l'aide", Theme.bad) { run(trade.requestImf()) }.also { it.isDisabled = imf != null }.wide()).growX().padTop(3f).row()
        imf?.let { into.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
        val contribution = trade.imfContributionBlocker()
        into.add(ui.button("Prêter 2 Md€ au fonds du FMI pour les pays pauvres" + (contribution?.let { " — $it" } ?: ""), "flat") { run(trade.contributeImf()) }
            .also { it.isDisabled = contribution != null }.wide()).growX().row()

        section("Organisation mondiale du commerce", "Contester des sanctions ou des pratiques déloyales devant l'organe de règlement des différends. Décision en six mois ; la cible le prend mal.")
        t.wto.forEach { c -> into.add(ui.label("En cours : ${countryName(c.target)} (${c.about})", "small", Theme.accent, wrap = true)).growX().row() }
        trade.wtoTargets().forEach { w ->
            into.add(ui.button("Porter plainte contre ${w.name} : ${w.about}" + (w.blocker?.let { " — $it" } ?: ""), "flat") { run(trade.fileWto(w.country)) }
                .also { it.isDisabled = w.blocker != null }.wide()).growX().row()
        }
        t.wtoHistory.takeLast(MAX_RESULTS).reversed().forEach { into.add(ui.label(it, "small", Theme.textMuted, wrap = true)).growX().row() }

        section("Banque mondiale", "Financer le développement : routes, écoles, santé. Les pays du Sud s'en souviennent.")
        val wb = trade.worldBankBlocker()
        into.add(ui.button("Verser 1,5 Md€ à l'Association internationale de développement" + (wb?.let { " — $it" } ?: ""), "flat") { run(trade.contributeWorldBank()) }
            .also { it.isDisabled = wb != null }.wide()).growX().row()
        if (t.worldBankBillions > 0) into.add(ui.label("Déjà versé : ${Formatting.billions(t.worldBankBillions)}", "small", Theme.textMuted)).left().row()
    }

    private companion object {
        const val CHART = 60f
        const val MAX_RESULTS = 6
    }
}
