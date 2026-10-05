package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import fr.president.engine.government.PolicyStatus
import fr.president.engine.legislation.BillStatus
import fr.president.engine.legislation.Channel
import fr.president.engine.legislation.LeverChange
import fr.president.engine.legislation.MeasureConfig
import fr.president.engine.legislation.MeasureModel
import fr.president.engine.util.Formatting
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.widgets.LeverCards
import fr.president.game.ui.widgets.LeverCards.wide
import kotlin.math.abs

/**
 * Lois et budget : toute la fabrique de la loi au même endroit.
 *  - Budget : vos réglages d'impôts et de dépenses forment le projet de budget, voté chaque année
 *    à l'automne (ou tout de suite en budget rectificatif). Pas de nom à donner.
 *  - Projet de loi : plusieurs changements réunis dans un texte, avec un nom proposé.
 *  - Créer une mesure : « Action + Cible + Valeur + Conditions », chiffrée en direct.
 *  - Décrets : petits réglages immédiats du gouvernement.
 *  - Au Parlement : textes en discussion, textes votés, abrogation.
 */
class LegislationPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "⚖ Lois et budget"
    private val session get() = nav.session
    private val leg get() = session.legislation
    private var tab = Tab.BUDGET
    private var budgetDomain = "budget_tax"
    private var lawDomain = "societe"
    private var message: String? = null
    private val decreeDrafts = mutableMapOf<String, Double>()
    private val nameField = TextField("", ui.s).apply {
        setTextFieldListener { field, _ -> leg.setLawName(field.text) }
        messageText = "Nom proposé"
    }
    // Constructeur de mesure.
    private var action: String? = null
    private var category: String? = null
    private var target: String? = null
    private var value: Double? = null
    private var threshold = 0.0
    private var zone = "national"
    private var exempt = false
    private var duration = 0
    private var phaseIn = false

    private enum class Tab(val label: String) { BUDGET("Budget"), LAW("Projet de loi"), BUILDER("Créer une mesure"), DECREE("Décrets"), PARLIAMENT("Au Parlement") }

    override fun applyArgument(argument: String) {
        // « budget », « budget:fiscal », « law », « law:travail », « builder », « decree », « parliament ».
        val (t, d) = argument.split(':').let { it[0] to it.getOrNull(1) }
        tab = when (t) { "law" -> Tab.LAW; "builder" -> Tab.BUILDER; "decree" -> Tab.DECREE; "parliament" -> Tab.PARLIAMENT; else -> Tab.BUDGET }
        d?.let { if (tab == Tab.BUDGET) budgetDomain = it else lawDomain = it }
    }

    private val ctx get() = LeverCards.Context(ui, session, expanded, decreeDrafts, { message = it }, { nav.refresh() })

    override fun build(into: Table) {
        val tabs = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t ->
            val badge = when (t) {
                Tab.BUDGET -> leg.state.budgetDraft.size + (leg.openPlf?.changes?.size ?: 0)
                Tab.LAW -> leg.lawChanges().size
                Tab.PARLIAMENT -> leg.pendingBills().size
                else -> 0
            }
            tabs.addActor(ui.button(t.label + if (badge > 0) " ($badge)" else "", "toggle") { tab = t; message = null; nav.refresh() }.also { it.isChecked = t == tab })
        }
        into.add(tabs).growX().left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        if (session.context.playerData.legislation == null) { into.add(ui.label("Données indisponibles.", "muted")).row(); return }
        when (tab) {
            Tab.BUDGET -> budget(into)
            Tab.LAW -> law(into)
            Tab.BUILDER -> builder(into)
            Tab.DECREE -> decrees(into)
            Tab.PARLIAMENT -> parliament(into)
        }
    }

    private fun run(r: Result<*>, ok: String? = null) { message = r.fold({ ok ?: (it as? String) ?: "C'est fait." }, { it.message ?: "Impossible." }); nav.refresh() }

    // ---- Budget ----------------------------------------------------------------------------

    private fun budget(into: Table) {
        val plf = leg.openPlf
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        if (plf != null) {
            box.add(ui.label("${plf.title} : en discussion", "value", Theme.highlight, wrap = true)).growX().row()
            box.add(ui.label("Vote le ${Formats.date(plf.voteAt)}. Jusqu'au ${Formats.date(plf.voteAt.plusDays(-fr.president.engine.legislation.LegislationService.AMEND_CLOSE_DAYS))}, chaque réglage que vous faites ici y est ajouté directement.", "small", wrap = true)).growX().row()
            val p = leg.preview(plf.changes)
            box.add(ui.label(if (plf.changes.isEmpty()) "Budget de reconduction : aucun changement." else "${plf.changes.size} changement(s) :", "bold")).padTop(3f).row()
            plf.changes.forEach { c -> changeLine(box, c) }
            if (plf.changes.isNotEmpty()) LeverCards.preview(ctx, box, p, false)
            val chance = session.amendments.chance(plf.id)
            box.add(ui.label("Chances d'adoption : ${Math.round(chance * 100)} %", "bold", LeverCards.chanceColor(chance))).padTop(2f).row()
        } else {
            val changes = leg.budgetChanges()
            box.add(ui.label("Projet de budget en préparation", "value", wrap = true)).growX().row()
            box.add(ui.label("Réglez les impôts et les dépenses ci-dessous : rien ne change avant le vote. La loi de finances de l'an prochain ${leg.nextPlfDeposit().let { if (it <= session.context.now) "sera déposée dans les prochains jours" else "sera déposée le ${Formats.date(it)}" }} et reprendra ces réglages ; vous pouvez aussi les faire voter tout de suite en budget rectificatif.", "small", wrap = true)).growX().row()
            if (changes.isEmpty()) box.add(ui.label("Aucun changement pour l'instant.", "small", Theme.textMuted)).row()
            else {
                box.add(ui.label("${changes.size} changement(s) :", "bold")).padTop(3f).row()
                changes.forEach { c -> changeLine(box, c) }
                val p = leg.preview(changes)
                LeverCards.preview(ctx, box, p, false)
                box.add(ui.label("Chances au Parlement : ${Math.round(leg.passChance(p.difficulty) * 100)} %", "small", LeverCards.chanceColor(leg.passChance(p.difficulty)))).left().row()
                val row = Table().apply { defaults().padRight(4f).padTop(3f) }
                val blocker = leg.correctiveBlocker()
                row.add(ui.colorButton("Voter un budget rectificatif", Theme.accentDark) { run(leg.depositCorrective(), "Budget rectificatif déposé : vote dans deux semaines.") }.also { it.isDisabled = blocker != null })
                row.add(ui.button("Tout annuler", "flat") { leg.state.budgetDraft.keys.toList().forEach { leg.clearBudget(it) }; nav.refresh() })
                box.add(row).left().row()
                blocker?.let { box.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().row() }
            }
        }
        into.add(box).growX().padBottom(GAP).row()
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        BUDGET_DOMAINS.forEach { (id, label) -> chips.addActor(ui.button(label, "toggle") { budgetDomain = id; nav.refresh() }.also { it.isChecked = id == budgetDomain }) }
        into.add(chips).growX().padBottom(GAP).row()
        val list = session.levers.all().filter { it.channel == Channel.BUDGET && (if (budgetDomain == "params") it.domain !in BUDGET_DOMAINS.keys else it.domain == budgetDomain) }
        if (budgetDomain == "measures") {
            into.add(ui.label("Les mesures que vous créez (onglet « Créer une mesure ») apparaissent ici une fois en vigueur ou dans le projet.", "muted", wrap = true)).growX().padBottom(4f).row()
            leg.state.budgetMeasures.values.filter { leg.state.budgetDraft.containsKey(it.key) && it.key !in leg.state.measures }.forEach { cfg ->
                session.levers.measureLever(cfg)?.let { into.add(LeverCards.build(ctx, it, LeverCards.Mode.BUDGET)).growX().padBottom(4f).row() }
            }
        }
        var group = ""
        list.forEach { l ->
            if (l.group.isNotEmpty() && l.group != group) { group = l.group; into.add(ui.label(group, "bold")).padTop(4f).row() }
            into.add(LeverCards.build(ctx, l, LeverCards.Mode.BUDGET)).growX().padBottom(4f).row()
        }
    }

    private fun changeLine(box: Table, c: LeverChange) {
        val l = session.levers.lever(c.lever) ?: c.measure?.let { session.levers.measureLever(it) } ?: return
        val row = Table()
        row.add(ui.label("• ${l.label} : ${session.levers.format(l, c.from)} → ${session.levers.format(l, c.to)}", "small", wrap = true)).growX().minWidth(0f)
        row.add(ui.button("✕", "flat") {
            if (l.channel == Channel.BUDGET) leg.clearBudget(c.lever) else leg.removeFromLaw(c.lever)
            nav.refresh()
        }).right()
        box.add(row).growX().row()
    }

    // ---- Projet de loi ----------------------------------------------------------------------

    private fun law(into: Table) {
        val changes = leg.lawChanges()
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        box.add(ui.label("Votre projet de loi", "value")).row()
        box.add(ui.label("Réunissez plusieurs changements dans un même texte (société, justice, travail, libertés, institutions, réformes, réglages). Le nom est proposé ; vous pouvez le changer.", "small", wrap = true)).growX().row()
        if (nameField.text != (leg.state.lawDraft.name ?: "")) nameField.text = leg.state.lawDraft.name ?: ""
        nameField.messageText = leg.autoName(changes)
        box.add(nameField).growX().padTop(4f).row()
        box.add(ui.label("Nom retenu : « ${leg.lawName()} »", "small", Theme.textMuted, wrap = true)).growX().row()
        if (changes.isEmpty()) box.add(ui.label("Le projet est vide : ajoutez des changements ci-dessous, ou créez une mesure sur mesure.", "small", Theme.textMuted, wrap = true)).growX().padTop(3f).row()
        else {
            box.add(ui.label("${changes.size} article(s) :", "bold")).padTop(3f).row()
            changes.forEach { changeLine(box, it) }
            val p = leg.preview(changes)
            LeverCards.preview(ctx, box, p, true)
            val chance = leg.passChance(p.difficulty)
            box.add(ui.label("Chances au Parlement : ${Math.round(chance * 100)} %" + if (changes.any { session.levers.lever(it.lever)?.constitutional == true }) " (majorité des trois cinquièmes : révision de la Constitution)" else "",
                "bold", LeverCards.chanceColor(chance), wrap = true)).growX().padTop(2f).row()
            val yes = leg.referendumEstimate(p)
            box.add(ui.label("Si vous le soumettez au peuple : environ ${Math.round(yes * 100)} % de oui (sans contrôle du Conseil constitutionnel, mais un « non » serait un désaveu).", "small",
                if (yes > 0.5) Theme.good else Theme.warning, wrap = true)).growX().row()
            val blocker = leg.lawDepositBlocker()
            val row = Table().apply { defaults().padRight(4f).padTop(3f) }
            row.add(ui.colorButton("⌂ Déposer au Parlement", Theme.accentDark) { run(leg.depositLaw(), "Projet déposé : vote dans un mois.") }.also { it.isDisabled = blocker != null })
            val ref = leg.referendumBlocker()
            row.add(ui.button("✔ Référendum", "flat") { run(leg.depositLaw(referendum = true), "Référendum convoqué.") }.also { it.isDisabled = ref != null })
            row.add(ui.button("Vider", "flat") { leg.state.lawDraft = fr.president.engine.legislation.LawDraft(); nav.refresh() })
            box.add(row).left().row()
            blocker?.let { box.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().row() }
        }
        into.add(box).growX().padBottom(GAP).row()

        into.add(ui.label("Ajouter au projet", "bold")).row()
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        LAW_DOMAINS.forEach { (id, label) -> chips.addActor(ui.button(label, "toggle") { lawDomain = id; nav.refresh() }.also { it.isChecked = id == lawDomain }) }
        into.add(chips).growX().padBottom(GAP).row()
        val list = session.levers.all().filter { it.channel != Channel.BUDGET && when (lawDomain) {
            "params" -> it.source == fr.president.engine.legislation.LeverSource.PARAM
            "measures" -> it.source == fr.president.engine.legislation.LeverSource.MEASURE
            else -> it.domain == lawDomain && it.source != fr.president.engine.legislation.LeverSource.PARAM
        } }
        if (list.isEmpty()) into.add(ui.label("Rien dans cette rubrique.", "muted")).left().row()
        var group = ""
        list.forEach { l ->
            if (l.group.isNotEmpty() && l.group != group) { group = l.group; into.add(ui.label(group, "bold")).padTop(4f).row() }
            into.add(LeverCards.build(ctx, l, LeverCards.Mode.LAW)).growX().padBottom(4f).row()
        }
    }

    // ---- Constructeur ----------------------------------------------------------------------

    private fun builder(into: Table) {
        val b = fr.president.engine.legislation.BuilderModel(session.context)
        val file = session.context.playerData.legislation?.builder ?: return
        into.add(ui.label("Composez votre mesure comme une phrase : une action, une cible, une valeur, des conditions. Tout est chiffré en direct.", "muted", wrap = true)).growX().padBottom(GAP).row()

        step(into, "1. Que voulez-vous faire ?")
        val actions = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        file.actions.forEach { a -> actions.addActor(ui.button(a.button.ifEmpty { "${a.verb}…" }, "toggle") { if (action != a.id) { action = a.id; target = null; value = null; category = null }; nav.refresh() }.also { it.isChecked = a.id == action }) }
        into.add(actions).growX().padBottom(GAP).row()
        val a = action?.let { b.action(it) } ?: return

        step(into, "2. Qui ou quoi ?")
        val targets = b.targets(a)
        val cats = file.categories.filter { c -> targets.any { it.category == c.id } }
        if (cats.size > 1) {
            val catChips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
            cats.forEach { c -> catChips.addActor(ui.button(c.label, "toggle") { category = c.id; nav.refresh() }.also { it.isChecked = c.id == (category ?: cats.first().id) }) }
            into.add(catChips).growX().padBottom(4f).row()
        }
        val cat = category ?: cats.firstOrNull()?.id
        val tChips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(3f); wrapSpace(3f) }
        targets.filter { it.category == cat }.forEach { t -> tChips.addActor(ui.button(t.short, "toggle") { target = t.id; value = null; nav.refresh() }.also { it.isChecked = t.id == target }) }
        into.add(tChips).growX().padBottom(GAP).row()
        val t = target?.let { b.target(it) } ?: return

        val (min, max, stepSize) = b.range(a, t)
        val v = (value ?: if (a.model == MeasureModel.BAN) 1.0 else a.default.coerceIn(min, max))
        if (a.model != MeasureModel.BAN) {
            step(into, "3. Combien ?")
            val row = Table().apply { defaults().padRight(3f) }
            row.add(ui.button("−−", "flat") { value = (v - stepSize * 5).coerceAtLeast(min); nav.refresh() })
            row.add(ui.button("−") { value = (v - stepSize).coerceAtLeast(min); nav.refresh() })
            row.add(ui.label(b.formatValue(a, t, v), "value", Theme.highlight)).minWidth(110f).center()
            row.add(ui.button("+") { value = (v + stepSize).coerceAtMost(max); nav.refresh() })
            row.add(ui.button("++", "flat") { value = (v + stepSize * 5).coerceAtMost(max); nav.refresh() })
            into.add(row).left().row()
            into.add(ui.label("De ${b.formatValue(a, t, min)} à ${b.formatValue(a, t, max)}", "small", Theme.textMuted)).left().padBottom(GAP).row()
        }

        step(into, if (a.model == MeasureModel.BAN) "3. Conditions" else "4. Conditions (facultatives)")
        fun chipRow(label: String, items: List<Pair<String, () -> Unit>>, selected: Int) {
            into.add(ui.label(label, "small")).left().row()
            val g = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(3f); wrapSpace(3f) }
            items.forEachIndexed { i, (text, act) -> g.addActor(ui.button(text, "toggle") { act(); nav.refresh() }.also { it.isChecked = i == selected }) }
            into.add(g).growX().padBottom(4f).row()
        }
        if ("threshold" in a.conditions && t.income > 0) {
            val list = file.thresholds
            chipRow("Revenus concernés", list.map { th -> (if (th == 0.0) "Tous" else "Au-dessus de ${Formatting.integer(th.toLong())} €") to { threshold = th } }, list.indexOf(threshold).coerceAtLeast(0))
        }
        if ("below" in a.conditions && t.income > 0) {
            val list = file.below
            chipRow("Bénéficiaires", list.map { th -> (if (th == 0.0) "Tous" else "Revenus sous ${Formatting.integer(th.toLong())} €") to { threshold = th } }, list.indexOf(threshold).coerceAtLeast(0))
        }
        if ("zone" in a.conditions) chipRow("Où ?", file.zones.map { z -> z.label to { zone = z.id } }, file.zones.indexOfFirst { it.id == zone }.coerceAtLeast(0))
        if ("exempt" in a.conditions && t.quality != null) chipRow("Exception", listOf("Aucune" to { exempt = false }, "Sauf en zone rurale (déserts)" to { exempt = true }), if (exempt) 1 else 0)
        if ("duration" in a.conditions) chipRow("Durée", file.durations.map { d -> (if (d == 0) "Permanente" else "$d an${if (d > 1) "s" else ""}") to { duration = d } }, file.durations.indexOf(duration).coerceAtLeast(0))
        if ("phaseIn" in a.conditions) chipRow("Mise en œuvre", listOf("Immédiate" to { phaseIn = false }, "Progressive sur trois ans" to { phaseIn = true }), if (phaseIn) 1 else 0)

        val cfg = MeasureConfig(a.id, t.id, threshold = if ("threshold" in a.conditions || "below" in a.conditions) threshold else 0.0,
            zone = if ("zone" in a.conditions) zone else "national", exemptRural = exempt && "exempt" in a.conditions && t.quality != null,
            durationYears = if ("duration" in a.conditions) duration else 0, phaseIn = phaseIn && "phaseIn" in a.conditions)
        val existing = leg.state.measures[cfg.key]
        val from = existing?.value ?: if (a.model == MeasureModel.PRICE_CAP) t.normalIncrease else 0.0
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        box.add(ui.label("« ${b.sentence(cfg, v)} »", "bold", Theme.highlight, wrap = true)).growX().row()
        box.add(ui.label("Voie : ${a.channel.label}" + if (a.channel == Channel.BUDGET) " — elle rejoindra le projet de budget" else " — elle rejoindra votre projet de loi", "small", Theme.textMuted, wrap = true)).growX().row()
        existing?.let { box.add(ui.label("Une règle identique est déjà en vigueur (${b.formatValue(a, t, it.value)}) : la nouvelle valeur la remplacera.", "small", Theme.warning, wrap = true)).growX().row() }
        val p = leg.previewOf(LeverChange(cfg.key, from, v, measure = cfg))
        LeverCards.preview(ctx, box, p, true, if (a.channel == Channel.BUDGET) null else LeverCards.Mode.LAW)
        val add = ui.colorButton(if (a.channel == Channel.BUDGET) "Ajouter au projet de budget" else "Ajouter au projet de loi", Theme.accentDark) {
            message = (if (a.channel == Channel.BUDGET) leg.setBudget(cfg.key, v, cfg) else leg.addToLaw(cfg.key, v, cfg))
                ?: if (a.channel == Channel.BUDGET) "Mesure ajoutée au projet de budget (onglet Budget, rubrique « Mesures sur mesure »)." else "Mesure ajoutée au projet de loi."
            nav.refresh()
        }.wide()
        box.add(add).growX().padTop(4f).row()
        if (existing != null) box.add(ui.button("Supprimer la règle en vigueur", "flat") {
            message = (if (a.channel == Channel.BUDGET) leg.setBudget(cfg.key, if (a.model == MeasureModel.PRICE_CAP) t.normalIncrease else 0.0, cfg) else leg.addToLaw(cfg.key, 0.0, cfg)) ?: "Suppression ajoutée au projet."
            nav.refresh()
        }).left().row()
        into.add(box).growX().padTop(GAP).row()
    }

    private fun step(into: Table, text: String) { into.add(ui.label(text, "bold", Theme.accent)).left().padTop(2f).row() }

    // ---- Décrets ----------------------------------------------------------------------------

    private fun decrees(into: Table) {
        into.add(ui.label("Le gouvernement règle seul certaines choses par décret : c'est immédiat, sans vote. Mais un changement trop brutal peut être annulé par le Conseil d'État, et il faut attendre quatre mois entre deux décrets sur le même sujet.", "muted", wrap = true)).growX().padBottom(GAP).row()
        session.levers.all().filter { it.channel == Channel.DECREE }.forEach { into.add(LeverCards.build(ctx, it, LeverCards.Mode.DECREE)).growX().padBottom(4f).row() }
        leg.state.contests.forEach { c ->
            into.add(ui.label("Recours devant le Conseil d'État : ${session.levers.lever(c.lever)?.label} — décision le ${Formats.date(c.at)}", "small", Theme.warning, wrap = true)).growX().row()
        }
    }

    // ---- Au Parlement ----------------------------------------------------------------------

    private fun parliament(into: Table) {
        val pending = leg.pendingBills()
        into.add(ui.label("Textes en discussion", "bold")).row()
        if (pending.isEmpty()) into.add(ui.label("Aucun texte au Parlement.", "muted")).left().row()
        pending.forEach { p ->
            into.add(fr.president.game.ui.widgets.ProposalCard.build(ui, session, p) { message = it; nav.refresh() }).growX().padBottom(4f).row()
        }
        session.state.policy.proposals.filter { it.status == PolicyStatus.REJECTED && it.kind in fr.president.engine.legislation.LegislationService.BILLS && it.voteAt.daysUntil(session.context.now) < 60 }.forEach { p ->
            val block = session.policy.forceBlocker(p)
            val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            row.add(ui.label("Rejeté : ${p.title}", "small", Theme.bad, wrap = true)).growX().row()
            row.add(ui.button("Passer en force (49.3 : coût politique, risque de censure)", "flat") { session.policy.forcePass(p.id); nav.refresh() }.also { it.isDisabled = block != null }.wide()).growX().row()
            block?.let { row.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
            into.add(row).growX().padBottom(4f).row()
        }

        into.add(ui.label("Mesures sur mesure en vigueur", "bold")).padTop(GAP).row()
        if (leg.state.measures.isEmpty()) into.add(ui.label("Aucune.", "muted")).left().row()
        leg.state.measures.values.forEach { m ->
            val l = session.levers.measureLever(m.config) ?: return@forEach
            into.add(LeverCards.build(ctx, l, if (l.channel == Channel.BUDGET) LeverCards.Mode.BUDGET else LeverCards.Mode.LAW)).growX().padBottom(4f).row()
            m.until?.let { into.add(ui.label("Prend fin le ${Formats.date(it)}", "small", Theme.textMuted)).left().row() }
        }

        into.add(ui.label("Textes votés", "bold")).padTop(GAP).row()
        val records = leg.state.bills.takeLast(MAX_RECORDS).reversed()
        if (records.isEmpty()) into.add(ui.label("Aucun pour l'instant.", "muted")).left().row()
        records.forEach { r ->
            val open = r.id in expanded
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            val color = when (r.status) { BillStatus.REJECTED -> Theme.bad; BillStatus.ABROGATED -> Theme.textMuted; else -> Theme.good }
            val head = Table()
            head.add(ui.label(r.title, "bold", wrap = true)).growX().minWidth(0f)
            head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right()
            head.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) { if (!expanded.remove(r.id)) expanded += r.id; nav.refresh() }
            })
            card.add(head).growX().row()
            card.add(ui.label("${Formats.date(r.time)} · ${r.status.label} · ${r.channel.label}" + if (r.censured.isNotEmpty()) " · ${r.censured.size} article(s) censuré(s)" else "", "small", color, wrap = true)).growX().row()
            if (open) {
                r.changes.forEach { c ->
                    val l = session.levers.lever(c.lever) ?: c.measure?.let { session.levers.measureLever(it) } ?: return@forEach
                    card.add(ui.label((if (c.censured) "✖ " else "• ") + "${l.label} : ${session.levers.format(l, c.from)} → ${session.levers.format(l, c.to)}", "small",
                        if (c.censured) Theme.bad else Theme.text, wrap = true)).growX().row()
                }
                r.censured.forEach { card.add(ui.label("Censuré : $it", "small", Theme.bad, wrap = true)).growX().row() }
                val block = leg.abrogateBlocker(r)
                if (r.status != BillStatus.REJECTED) {
                    card.add(ui.button("Abroger (préparer le texte inverse)", "flat") { run(leg.abrogate(r.id)) }.also { it.isDisabled = block != null }).left().padTop(2f).row()
                    block?.let { card.add(ui.label("↻ $it", "small", Theme.textMuted, wrap = true)).growX().row() }
                }
            }
            into.add(card).growX().padBottom(3f).row()
        }
    }

    private companion object {
        const val MAX_RECORDS = 25
        val BUDGET_DOMAINS = linkedMapOf("budget_tax" to "Impôts", "budget_spending" to "Dépenses", "fiscal" to "Fiscalité fine", "params" to "Allocations", "measures" to "Mesures sur mesure")
        val LAW_DOMAINS = linkedMapOf("societe" to "♥ Société", "justice" to "⚖ Justice", "travail" to "⚒ Travail", "libertes" to "▤ Libertés",
            "institutions" to "⌂ Constitution", "reformes" to "★ Réformes", "params" to "# Réglages chiffrés", "measures" to "✎ Mesures sur mesure")
    }
}
