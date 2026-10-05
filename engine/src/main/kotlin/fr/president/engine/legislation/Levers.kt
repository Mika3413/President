package fr.president.engine.legislation

import fr.president.engine.data.TaxPayer
import fr.president.engine.economy.BudgetCalculator
import fr.president.engine.economy.FiscalService
import fr.president.engine.economy.GrowthImpulse
import fr.president.engine.effects.EffectSpec
import fr.president.engine.government.LawService
import fr.president.engine.government.PolicyService
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting
import kotlin.math.abs

/**
 * Un levier : une chose que le président peut changer, avec une seule valeur en vigueur, rangée à
 * un seul endroit (taux d'impôt dans le budget, option d'une loi, réglage chiffré, mesure sur
 * mesure...). Tous les écrans lisent et modifient cette même valeur.
 */
data class Lever(
    val id: String,
    val source: LeverSource,
    val channel: Channel,
    /** Domaine (rubrique de l'écran) et sous-groupe. */
    val domain: String,
    val group: String = "",
    val label: String,
    val description: String = "",
    val numeric: Boolean = true,
    val min: Double = 0.0,
    val max: Double = 1.0,
    val step: Double = 1.0,
    val unit: String = "",
    val decimals: Int = 0,
    /** Libellés des options d'un levier à choix (valeur = indice). */
    val options: List<String> = emptyList(),
    val reference: Double = 0.0,
    val constitutional: Boolean = false,
    /** Ne se défait pas (une réforme engagée, un SMIC revalorisé). */
    val irreversible: Boolean = false,
    val format: String = "number",
    val zeroLabel: String? = null,
    val measure: MeasureConfig? = null,
)

/** Ce qu'un changement produirait : argent, gagnants et perdants, services, risques. */
class Preview {
    var revenue = 0.0
    var spending = 0.0
    val groups = mutableMapOf<String, Double>()
    val quality = mutableMapOf<String, Double>()
    val sectors = mutableMapOf<String, Double>()
    val lines = mutableListOf<String>()
    var censure = 0.0
    var censureReason = ""
    var difficulty = 0.0
    var appeal = 0.0
    var liberty = 0.0
    val events = mutableMapOf<String, Double>()
    val balance: Double get() = revenue - spending

    fun add(other: Preview) {
        revenue += other.revenue; spending += other.spending
        other.groups.forEach { (k, v) -> groups[k] = (groups[k] ?: 0.0) + v }
        other.quality.forEach { (k, v) -> quality[k] = (quality[k] ?: 0.0) + v }
        other.sectors.forEach { (k, v) -> sectors[k] = (sectors[k] ?: 0.0) + v }
        other.events.forEach { (k, v) -> events[k] = maxOf(events[k] ?: 0.0, v) }
        liberty += other.liberty
        appeal += other.appeal
    }
}

class LeverService(private val ctx: SimulationContext) {
    private val file get() = ctx.playerData.legislation
    private val laws get() = LawService(ctx)
    private val fiscal get() = FiscalService(ctx)
    private val builder get() = BuilderModel(ctx)
    private val economy get() = ctx.state.playerCountry.economy

    /** Réformes du catalogue remplacées par un levier chiffré (âge de la retraite, SMIC, ISF...). */
    val linkedReforms: Set<String> get() = buildSet {
        file?.parameters?.forEach { p -> p.links.forEach { add(it.reform) } }
        fiscal.taxes.forEach { t -> t.reform?.let { add(it.reform) } }
        laws.laws.forEach { l -> l.options.forEach { o -> o.reform?.let { add(it) } } }
    }

    /** Levier qui remplace une réforme du catalogue, et la valeur qui correspond à son adoption. */
    fun leverForReform(reform: String): Pair<String, Double>? {
        file?.parameters?.forEach { p ->
            p.links.firstOrNull { it.reform == reform }?.let { l -> return "param:${p.id}" to when {
                l.above != null -> if (p.id == "smic_boost") 5.0 else l.above + 1
                l.below != null -> if (p.id == "pension_age") 62.0 else l.below - 1
                else -> p.reference
            } }
        }
        fiscal.taxes.firstOrNull { it.reform?.reform == reform }?.let { t -> return "fiscal:${t.id}" to ((t.reform!!.above ?: t.reference) * 1.5).coerceAtMost(t.max).let { if (t.id == "wealth") 1.0 else it } }
        laws.laws.forEach { l -> l.options.indexOfFirst { it.reform == reform }.takeIf { it >= 0 }?.let { i -> return "law:${l.id}" to (l.numeric?.values?.get(i) ?: i.toDouble()) } }
        return null
    }

    fun domains(): List<DomainDef> = file?.domains.orEmpty()

    // ---- Registre ---------------------------------------------------------------------------

    fun all(): List<Lever> = taxes() + spending() + fiscalLevers() + lawLevers() + reformLevers() + params() + measures()

    fun lever(id: String): Lever? = when {
        id.startsWith("tax:") -> taxes().firstOrNull { it.id == id }
        id.startsWith("spend:") -> spending().firstOrNull { it.id == id }
        id.startsWith("fiscal:") -> fiscalLevers().firstOrNull { it.id == id }
        id.startsWith("law:") -> lawLevers().firstOrNull { it.id == id }
        id.startsWith("reform:") -> reformLevers(includeLinked = true).firstOrNull { it.id == id }
        id.startsWith("param:") -> params().firstOrNull { it.id == id }
        id.startsWith("m:") -> measureLever(id)
        else -> null
    }

    private fun taxes(): List<Lever> {
        val defs = ctx.playerData.economy.budget?.revenues ?: return emptyList()
        return defs.filter { it.adjustable }.map { d ->
            Lever("tax:${d.id}", LeverSource.TAX, Channel.BUDGET, "budget_tax", "", d.label, d.description, min = d.minRate, max = d.maxRate,
                step = d.step, unit = d.rateLabel, decimals = if (d.step < 1) (if (d.step < 0.5) 1 else 1) else 0, reference = d.rate)
        }
    }

    private fun spending(): List<Lever> {
        val defs = ctx.playerData.economy.budget?.spending ?: return emptyList()
        return defs.filter { it.adjustable }.map { d ->
            Lever("spend:${d.id}", LeverSource.SPENDING, Channel.BUDGET, "budget_spending", "", d.label, d.description,
                min = MIN_SPENDING, max = MAX_SPENDING, step = SPENDING_STEP, unit = "", reference = 1.0, format = "factor")
        }
    }

    private fun fiscalLevers(): List<Lever> = fiscal.taxes.map { t ->
        Lever("fiscal:${t.id}", LeverSource.FISCAL, Channel.BUDGET, "fiscal", fiscal.categories.firstOrNull { it.id == t.category }?.label ?: "",
            t.label, t.description, min = t.min, max = t.max, step = t.step, unit = t.unit, decimals = t.decimals, reference = t.reference)
    }

    private fun lawLevers(): List<Lever> = laws.laws.map { l ->
        val n = l.numeric
        if (n != null) Lever("law:${l.id}", LeverSource.LAW, Channel.LAW, l.category, "", l.title, l.description, min = n.min, max = n.max, step = n.step,
            unit = n.unit, decimals = n.decimals, reference = n.values[0], constitutional = l.constitutional, zeroLabel = n.zeroLabel,
            options = l.options.map { it.label })
        else Lever("law:${l.id}", LeverSource.LAW, Channel.LAW, l.category, "", l.title, l.description, numeric = false,
            options = l.options.map { it.label }, reference = 0.0, max = (l.options.size - 1).toDouble(), constitutional = l.constitutional)
    }

    private fun reformLevers(includeLinked: Boolean = false): List<Lever> {
        val hidden = if (includeLinked) emptySet() else linkedReforms
        return PolicyService(ctx).reforms().filter { it.id !in hidden }.map { r ->
            Lever("reform:${r.id}", LeverSource.REFORM, Channel.LAW, "reformes", r.category, r.title, r.description, numeric = false,
                options = listOf("Pas de réforme", r.title), max = 1.0, irreversible = true)
        }
    }

    private fun params(): List<Lever> = file?.parameters.orEmpty().map { p ->
        val min = if (p.noDecrease) maxOf(p.min, paramValue(p)) else p.min
        Lever("param:${p.id}", LeverSource.PARAM, p.channel, p.domain, "", p.label, p.description, min = min, max = p.max, step = p.step,
            unit = p.unit, decimals = p.decimals, reference = p.reference, irreversible = p.noDecrease, format = p.format)
    }

    private fun measures(): List<Lever> = ctx.state.legislation.measures.keys.mapNotNull { measureLever(it) }

    private fun measureLever(id: String): Lever? {
        val cfg = ctx.state.legislation.measures[id]?.config ?: ctx.state.legislation.budgetMeasures[id]
            ?: ctx.state.legislation.lawDraft.changes.firstOrNull { it.lever == id }?.measure
            ?: ctx.state.policy.proposals.flatMap { it.changes }.firstOrNull { it.lever == id }?.measure ?: return null
        return measureLever(cfg)
    }

    fun measureLever(cfg: MeasureConfig): Lever? {
        val a = builder.action(cfg.action) ?: return null
        val t = builder.target(cfg.target) ?: return null
        val (min, max, step) = builder.range(a, t)
        val ban = a.model == MeasureModel.BAN
        return Lever(cfg.key, LeverSource.MEASURE, a.channel, "measures", t.short, builder.sentence(cfg, ctx.state.legislation.measures[cfg.key]?.value ?: a.default),
            builder.conditions(cfg, a).removePrefix(", ").replaceFirstChar { it.uppercase() },
            numeric = !ban, min = if (a.model == MeasureModel.PRICE_CAP) min else 0.0, max = max, step = step,
            unit = builder.unit(a, t), decimals = if (step < 1) 1 else 0, options = if (ban) listOf("Autorisé", "Interdit") else emptyList(),
            reference = if (a.model == MeasureModel.PRICE_CAP) t.normalIncrease else 0.0, measure = cfg)
    }

    // ---- Valeurs ----------------------------------------------------------------------------

    fun current(id: String): Double {
        val key = id.substringAfter(':')
        val budget = economy.budget
        return when {
            id.startsWith("tax:") -> budget?.revenues?.get(key)?.rate ?: 0.0
            id.startsWith("spend:") -> budget?.spending?.get(key)?.policyFactor ?: 1.0
            id.startsWith("fiscal:") -> fiscal.value(key)
            id.startsWith("law:") -> laws.law(key)?.let { laws.value(it) } ?: 0.0
            id.startsWith("reform:") -> if (key in ctx.state.policy.adoptedReforms) 1.0 else 0.0
            id.startsWith("param:") -> file?.parameters?.firstOrNull { it.id == key }?.let { paramValue(it) } ?: 0.0
            id.startsWith("m:") -> ctx.state.legislation.measures[id]?.value
                ?: measureLever(id)?.takeIf { it.measure?.let { m -> builder.action(m.action)?.model == MeasureModel.PRICE_CAP } == true }?.reference ?: 0.0
            else -> 0.0
        }
    }

    fun paramValue(p: ParameterDef): Double = ctx.state.legislation.params[p.id] ?: p.reference

    fun format(l: Lever, v: Double): String {
        if (!l.numeric) return l.options.getOrNull(Math.round(v).toInt()) ?: ""
        if (v == 0.0 && l.zeroLabel != null) return l.zeroLabel
        if (l.format == "factor") return "budget ${Formatting.signedPercent(v - 1.0)}"
        if (l.format == "years") {
            val years = v.toInt()
            val months = Math.round((v - years) * 12).toInt()
            return if (months == 0) "$years ans" else "$years ans $months mois"
        }
        l.measure?.let { cfg -> builder.action(cfg.action)?.let { a -> builder.target(cfg.target)?.let { t -> return builder.formatValue(a, t, v) } } }
        val text = if (l.decimals == 0) Formatting.integer(Math.round(v)) else String.format(java.util.Locale.FRENCH, "%.${l.decimals}f", v)
        return "$text ${l.unit}".trim()
    }

    // ---- Aperçu ----------------------------------------------------------------------------

    fun preview(id: String, from: Double, to: Double): Preview {
        val p = Preview()
        if (abs(to - from) < 1e-9) return p
        val key = id.substringAfter(':')
        when {
            id.startsWith("tax:") -> previewTax(p, key, from, to)
            id.startsWith("spend:") -> previewSpending(p, key, from, to)
            id.startsWith("fiscal:") -> fiscal.def(key)?.let { d ->
                p.revenue = fiscal.revenueAt(d, to) - fiscal.revenueAt(d, from)
                parseEffects(p, d.effects, (to - from) / d.per)
                p.difficulty = DIFF_PER_BILLION * abs(p.revenue) + if (p.revenue > 0) DIFF_PER_BILLION * p.revenue else 0.0
                p.appeal = groupAppeal(p)
            }
            id.startsWith("law:") -> laws.law(key)?.let { l -> previewLaw(p, l, from, to) }
            id.startsWith("reform:") -> PolicyService(ctx).reforms().firstOrNull { it.id == key }?.let { r ->
                if (to >= 0.5 && from < 0.5) {
                    parseEffects(p, r.immediateEffects + r.longTermEffects, 1.0)
                    p.difficulty = r.difficulty
                    p.appeal = groupAppeal(p)
                }
            }
            id.startsWith("param:") -> file?.parameters?.firstOrNull { it.id == key }?.let { d -> previewParam(p, d, from, to) }
            id.startsWith("m:") -> measureLever(id)?.measure?.let { cfg -> previewMeasure(p, cfg, from, to) }
        }
        return p
    }

    fun previewMeasure(p: Preview, cfg: MeasureConfig, from: Double, to: Double) {
        val d = builder.evaluate(cfg, to) - builder.evaluate(cfg, from)
        p.revenue = d.revenue; p.spending = d.spending
        p.groups += d.groups; p.quality += d.quality; p.sectors += d.sectors; p.events += d.events
        p.liberty = d.liberty
        p.censure = d.censure; p.censureReason = d.censureReason
        p.lines += d.lines
        d.economy.forEach { (k, v) -> economyLine(k, v)?.let { p.lines += it } }
        if (cfg.phaseIn) p.groups.replaceAll { _, v -> v * PHASE_IN_OPINION }
        p.difficulty = (DIFF_PER_GROUP_POINT * p.groups.values.filter { it < 0 }.sumOf { -it } + DIFF_PER_BILLION * maxOf(0.0, p.balance)).coerceAtMost(0.15)
        p.appeal = groupAppeal(p)
    }

    private fun previewTax(p: Preview, key: String, from: Double, to: Double) {
        val item = economy.budget?.revenues?.get(key) ?: return
        p.revenue = BudgetCalculator.taxAmount(item, to, economy) - BudgetCalculator.taxAmount(item, from, economy)
        file?.incidence?.get("tax:$key")?.forEach { (g, w) -> p.groups[g] = -p.revenue * OPINION_PER_BILLION * w }
        val payer = when (item.payer) { TaxPayer.HOUSEHOLDS -> "les ménages"; TaxPayer.BUSINESSES -> "les entreprises"; else -> "les ménages et les entreprises" }
        p.lines += (if (p.revenue >= 0) "Prélèvement supplémentaire sur $payer : " else "Allègement pour $payer : ") + Formatting.billions(abs(p.revenue)) + " par an."
        p.lines += "Une partie de la hausse se perd en comportements (fraude, moindre activité) : ${Math.round(item.behaviouralLoss * 100)} %."
        p.difficulty = DIFF_PER_BILLION * maxOf(0.0, p.revenue)
        p.appeal = groupAppeal(p)
    }

    private fun previewSpending(p: Preview, key: String, from: Double, to: Double) {
        val item = economy.budget?.spending?.get(key) ?: return
        p.spending = BudgetCalculator.spendingAmount(item, to, economy) - BudgetCalculator.spendingAmount(item, from, economy)
        file?.incidence?.get("spend:$key")?.forEach { (g, w) -> p.groups[g] = p.spending * OPINION_PER_BILLION * w }
        item.domain?.let { d -> p.quality[d] = (to - from) * SPENDING_QUALITY }
        p.lines += (if (p.spending >= 0) "Dépense supplémentaire : " else "Économie : ") + Formatting.billions(abs(p.spending)) + " par an."
        item.domain?.let { p.lines += "La qualité du service suit les moyens, en quelques mois." }
        p.difficulty = DIFF_PER_BILLION * maxOf(0.0, -p.spending)
        p.appeal = groupAppeal(p)
    }

    private fun previewLaw(p: Preview, l: fr.president.engine.government.LawDef, from: Double, to: Double) {
        val before = if (l.numeric != null) laws.weights(l, from) else mapOf(from.toInt() to 1.0)
        val after = if (l.numeric != null) laws.weights(l, to) else mapOf(to.toInt() to 1.0)
        var libertyDrop = 0.0
        for (i in before.keys + after.keys) {
            val k = (after[i] ?: 0.0) - (before[i] ?: 0.0)
            val o = l.options.getOrNull(i) ?: continue
            parseEffects(p, o.effects + o.longTerm, k)
            o.events.forEach { (e, f) -> if (f > 1 && k > 0) p.events[e] = maxOf(p.events[e] ?: 0.0, (f - 1) * k) }
            libertyDrop += k * ((o.liberty - l.options[0].liberty) + (o.rule - l.options[0].rule) + (o.press - l.options[0].press))
        }
        p.liberty = libertyDrop
        p.difficulty = if (l.numeric != null) laws.difficulty(l, from, to) else l.options.getOrNull(to.toInt())?.difficulty ?: 0.0
        p.appeal = laws.appeal(l, from, to)
        if (libertyDrop < -LIBERTY_ALERT && !l.constitutional) {
            p.censure = (-libertyDrop / LIBERTY_CENSURE).coerceAtMost(0.6)
            p.censureReason = "atteinte aux libertés fondamentales"
        }
        if (libertyDrop != 0.0) p.lines += "Libertés publiques, presse et État de droit : ${if (libertyDrop > 0) "+" else ""}${Math.round(libertyDrop)} points."
    }

    private fun previewParam(p: Preview, d: ParameterDef, from: Double, to: Double) {
        val k = (to - from) / d.per
        parseEffects(p, if (k > 0) d.up else d.down, abs(k))
        val cost = (to - from) * d.costPerUnit
        if (cost != 0.0) p.spending += cost
        p.difficulty = abs(k) * if (k > 0) d.difficultyUp else d.difficultyDown
        p.appeal = abs(k) * if (k > 0) d.appealUp else d.appealDown
        if (p.appeal == 0.0) p.appeal = groupAppeal(p)
        if (d.channel == Channel.DECREE && d.decreeLimit > 0 && abs(to - from) > d.decreeLimit) {
            p.censure = DECREE_RISK
            p.censureReason = "un changement aussi brutal par simple décret peut être annulé par le Conseil d'État"
        }
    }

    /** Lecture des effets d'une liste (pour l'aperçu). */
    private fun parseEffects(p: Preview, effects: List<EffectSpec>, k: Double) {
        for (fx in effects) {
            val v = fx.amount * k
            val parts = fx.target.split('.')
            when (parts[0]) {
                "opinion" -> if (parts.getOrNull(1) == "group") p.groups[parts[2]] = (p.groups[parts[2]] ?: 0.0) + v
                    else if (parts.getOrNull(1) == "national") p.groups["national"] = (p.groups["national"] ?: 0.0) + v
                "quality" -> p.quality[parts[1]] = (p.quality[parts[1]] ?: 0.0) + v
                "sector" -> p.sectors[parts[1]] = (p.sectors[parts[1]] ?: 0.0) + v
                "budget" -> if (fx.days > 0) p.spending += v * 365.0 / fx.days.coerceAtLeast(365.0) else p.lines += "Coût unique : ${Formatting.billions(v)}."
                "spending" -> economy.budget?.spending?.get(parts[1])?.let { p.spending += it.referenceAmount * v }
                "revenue" -> economy.budget?.revenues?.get(parts[1])?.let { item ->
                    p.revenue += BudgetCalculator.taxAmount(item, item.rate + v, economy) - BudgetCalculator.taxAmount(item, item.rate, economy)
                }
                "chain" -> if (v > 0) p.events[parts[1]] = maxOf(p.events[parts[1]] ?: 0.0, v)
                else -> economyLine(fx.target, v)?.let { p.lines += it }
            }
        }
    }

    private fun economyLine(target: String, v: Double): String? = if (target.startsWith("economy.") && target != "economy.businessConfidence" && target != "economy.consumerConfidence" && abs(v) < 0.00005) null else when (target) {
        "economy.potentialGrowth" -> "Croissance à long terme : ${sign(v * 100)} point."
        "economy.naturalUnemployment" -> "Chômage structurel : ${sign(v * 100)} point."
        "economy.inflation" -> "Inflation : ${sign(v * 100)} point."
        "economy.unemployment" -> "Chômage : ${sign(v * 100)} point."
        "economy.businessConfidence" -> if (v > 0) "Les entreprises apprécient." else "Les entreprises s'inquiètent."
        "economy.consumerConfidence" -> if (v > 0) "Le moral des ménages monte." else "Le moral des ménages baisse."
        else -> if (target.startsWith("memory.")) "${ctx.db.countries[target.split('.')[1]]?.definition?.name ?: ""} ${if (v < 0) "le prend mal" else "apprécie"}." else null
    }

    private fun sign(v: Double) = (if (v >= 0) "+" else "−") + String.format(java.util.Locale.FRENCH, "%.2f", abs(v))

    /** Attrait auprès des électeurs, d'après les gagnants et les perdants pondérés par leur poids. */
    private fun groupAppeal(p: Preview): Double {
        val groups = ctx.playerData.socialGroups?.groups ?: return 0.0
        val partitions = ctx.playerData.socialGroups?.partitions?.size?.coerceAtLeast(1) ?: 1
        return (groups.sumOf { g -> (p.groups[g.id] ?: 0.0) * g.populationShare } / partitions * APPEAL_SCALE + (p.groups["national"] ?: 0.0) * APPEAL_SCALE).coerceIn(-0.5, 0.5)
    }

    // ---- Application ------------------------------------------------------------------------

    /** Un changement entre en vigueur ([scale] : portée après amendements). */
    fun apply(change: LeverChange, scale: Double = 1.0) {
        val id = change.lever
        val key = id.substringAfter(':')
        val from = current(id)
        val to = change.to
        when {
            id.startsWith("tax:") -> applyBudgetItem(id) { economy.budget!!.revenues.getValue(key).rate = to }
            id.startsWith("spend:") -> applyBudgetItem(id) { economy.budget!!.spending.getValue(key).policyFactor = to }
            id.startsWith("fiscal:") -> fiscal.enact(key, to, scale)
            id.startsWith("law:") -> laws.law(key)?.let { l -> if (l.numeric != null) laws.enactNumeric(key, to, scale) else laws.enact(key, to.toInt(), scale) }
            id.startsWith("reform:") -> if (to >= 0.5 && key !in ctx.state.policy.adoptedReforms) PolicyService(ctx).applyReform(key, scale = scale)
            id.startsWith("param:") -> file?.parameters?.firstOrNull { it.id == key }?.let { applyParam(it, from, to, scale) }
            id.startsWith("m:") -> (change.measure ?: measureLever(id)?.measure)?.let { applyMeasure(it, from, to, scale) }
        }
        syncReforms()
    }

    private fun applyBudgetItem(id: String, set: () -> Unit) {
        val e = economy
        val params = ctx.db.economyParameters
        val revenueBefore = e.revenueBillions
        val spendingBefore = e.spendingBillions
        val householdBefore = BudgetCalculator.taxBurden(e, TaxPayer.HOUSEHOLDS)
        val businessBefore = BudgetCalculator.taxBurden(e, TaxPayer.BUSINESSES)
        set()
        BudgetCalculator.recompute(e)
        // Impulsion budgétaire : moins de demande quand l'État prélève plus ou dépense moins.
        val impulse = ((e.spendingBillions - spendingBefore) - (e.revenueBillions - revenueBefore)) / e.gdpBillions
        e.impulses += GrowthImpulse(params.fiscalMultiplier * impulse, params.fiscalImpulseMonths, id)
        val householdDelta = BudgetCalculator.taxBurden(e, TaxPayer.HOUSEHOLDS) - householdBefore
        val businessDelta = BudgetCalculator.taxBurden(e, TaxPayer.BUSINESSES) - businessBefore
        e.consumerConfidence = (e.consumerConfidence - params.householdTaxConfidenceShock * householdDelta).coerceIn(0.0, 1.0)
        e.businessConfidence = (e.businessConfidence - params.businessTaxConfidenceShock * businessDelta).coerceIn(0.0, 1.0)
    }

    private fun applyParam(d: ParameterDef, from: Double, to: Double, scale: Double) {
        val target = to.coerceIn(d.min, d.max)
        ctx.state.legislation.params[d.id] = target
        val k = (target - from) / d.per
        (if (k > 0) d.up else d.down).forEach { fx -> ctx.effects.trigger(fx.copy(amount = fx.amount * abs(k) * scale), null, emptyMap(), "param:${d.id}") }
        val cost = (target - from) * d.costPerUnit
        if (cost != 0.0) {
            val item = d.spendingItem?.let { economy.budget?.spending?.get(it) }
            if (item != null) item.policyFactor = (item.policyFactor + cost / item.referenceAmount).coerceAtLeast(0.0)
            else economy.measureSpendingBillions += cost
            BudgetCalculator.recompute(economy)
            economy.impulses += GrowthImpulse(ctx.db.economyParameters.fiscalMultiplier * cost / economy.gdpBillions, ctx.db.economyParameters.fiscalImpulseMonths, "param:${d.id}")
        }
    }

    fun applyMeasure(cfg: MeasureConfig, from: Double, to: Double, scale: Double) {
        val state = ctx.state.legislation
        val d = builder.evaluate(cfg, to) - builder.evaluate(cfg, from)
        val e = economy
        e.measureRevenueBillions += d.revenue
        e.measureSpendingBillions += d.spending
        BudgetCalculator.recompute(e)
        e.impulses += GrowthImpulse(ctx.db.economyParameters.fiscalMultiplier * (d.spending - d.revenue) / e.gdpBillions, ctx.db.economyParameters.fiscalImpulseMonths, cfg.key)
        val opinion = scale * if (cfg.phaseIn) PHASE_IN_OPINION else 1.0
        val days = if (cfg.phaseIn) QUALITY_DAYS * 3 else QUALITY_DAYS
        d.groups.forEach { (g, v) -> ctx.effects.trigger(EffectSpec("opinion.group.$g", v * opinion), null, emptyMap(), cfg.key) }
        d.actors.forEach { (a, v) -> ctx.state.actors.actors[a]?.let { it.goodwill += v * scale } }
        d.quality.forEach { (q, v) -> ctx.effects.trigger(EffectSpec("quality.$q", v * scale, days = days), null, emptyMap(), cfg.key) }
        d.sectors.forEach { (s, v) -> ctx.effects.trigger(EffectSpec("sector.$s", v * scale), null, emptyMap(), cfg.key) }
        d.economy.forEach { (t, v) -> ctx.effects.trigger(EffectSpec(t, v * scale, days = if (t == "economy.inflation") HALF_YEAR else 0.0), null, emptyMap(), cfg.key) }
        d.events.forEach { (ev, p) -> ctx.effects.trigger(EffectSpec("chain.$ev", p), null, emptyMap(), cfg.key) }
        state.libertyOffset += d.liberty
        val stillActive = to != 0.0 && !(builder.action(cfg.action)?.model == MeasureModel.PRICE_CAP && to >= (builder.target(cfg.target)?.normalIncrease ?: 0.0))
        if (stillActive) {
            val m = state.measures.getOrPut(cfg.key) { MeasureState(cfg) }
            m.value = to
            if (m.since == null) m.since = ctx.now
            m.until = if (cfg.durationYears > 0) ctx.now.plusDays(cfg.durationYears * YEAR) else null
        } else state.measures.remove(cfg.key)
        state.budgetMeasures.remove(cfg.key)
    }

    /** Les réformes du catalogue liées à un levier suivent sa valeur (promesses, syndicats, rue). */
    fun syncReforms() {
        val adopted = ctx.state.policy.adoptedReforms
        fun set(reform: String, on: Boolean) {
            if (on) adopted.putIfAbsent(reform, ctx.now) else adopted.remove(reform)
        }
        file?.parameters?.forEach { p -> val v = paramValue(p); p.links.forEach { set(it.reform, it.holds(v)) } }
        fiscal.taxes.forEach { t -> t.reform?.let { set(it.reform, it.holds(fiscal.value(t.id))) } }
        laws.laws.forEach { l ->
            val w = laws.currentWeights(l)
            l.options.forEachIndexed { i, o -> o.reform?.let { set(it, (w[i] ?: 0.0) >= 0.5) } }
        }
    }

    companion object {
        const val MIN_SPENDING = 0.5
        const val MAX_SPENDING = 1.6
        const val SPENDING_STEP = 0.01
        private const val OPINION_PER_BILLION = 0.0015
        private const val SPENDING_QUALITY = 0.15
        private const val DIFF_PER_BILLION = 0.0015
        private const val DIFF_PER_GROUP_POINT = 0.3
        private const val LIBERTY_ALERT = 3.0
        private const val LIBERTY_CENSURE = 25.0
        private const val DECREE_RISK = 0.4
        private const val APPEAL_SCALE = 6.0
        private const val PHASE_IN_OPINION = 0.6
        private const val QUALITY_DAYS = 365.0
        private const val HALF_YEAR = 180.0
        private const val YEAR = 365.0
    }
}
