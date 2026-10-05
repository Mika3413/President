package fr.president.engine.legislation

import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Conséquences chiffrées d'une décision, sur un an. Sert à la fois à l'aperçu (avant de déposer)
 * et à l'application (différence entre la nouvelle valeur et l'ancienne).
 */
class Impact {
    /** Recettes de l'État (Md€ par an ; négatif : recettes perdues). */
    var revenue = 0.0
    /** Dépenses de l'État (Md€ par an ; négatif : économies). */
    var spending = 0.0
    /** Effets sur l'opinion des groupes (points), sur les acteurs, sur les services (à terme), sur les secteurs. */
    val groups = mutableMapOf<String, Double>()
    val actors = mutableMapOf<String, Double>()
    val quality = mutableMapOf<String, Double>()
    val sectors = mutableMapOf<String, Double>()
    /** Autres effets immédiats (economy.inflation, economy.businessConfidence...). */
    val economy = mutableMapOf<String, Double>()
    /** Événements rendus plus probables (probabilité d'enchaînement). */
    val events = mutableMapOf<String, Double>()
    var liberty = 0.0
    /** Probabilité de censure par le Conseil constitutionnel (ou d'annulation par le juge). */
    var censure = 0.0
    var censureReason = ""
    /** Personnes (ou entreprises) concernées. */
    var people = 0.0
    val lines = mutableListOf<String>()

    /** Solde pour les finances publiques (+ : le déficit baisse). */
    val balance: Double get() = revenue - spending

    fun add(map: MutableMap<String, Double>, key: String, v: Double) { if (v != 0.0) map[key] = (map[key] ?: 0.0) + v }

    /** Ce qui change entre [old] et [this]. */
    operator fun minus(old: Impact): Impact {
        val d = Impact()
        d.revenue = revenue - old.revenue
        d.spending = spending - old.spending
        fun diff(a: Map<String, Double>, b: Map<String, Double>, out: MutableMap<String, Double>) =
            (a.keys + b.keys).forEach { k -> val v = (a[k] ?: 0.0) - (b[k] ?: 0.0); if (abs(v) > 1e-12) out[k] = v }
        diff(groups, old.groups, d.groups)
        diff(actors, old.actors, d.actors)
        diff(quality, old.quality, d.quality)
        diff(sectors, old.sectors, d.sectors)
        diff(economy, old.economy, d.economy)
        events.forEach { (k, v) -> val dv = v - (old.events[k] ?: 0.0); if (dv > 0) d.events[k] = dv }
        d.liberty = liberty - old.liberty
        d.censure = censure
        d.censureReason = censureReason
        d.people = people
        d.lines += lines
        return d
    }

    fun scale(k: Double): Impact {
        val d = Impact()
        d.revenue = revenue * k; d.spending = spending * k
        groups.forEach { (g, v) -> d.groups[g] = v * k }
        actors.forEach { (g, v) -> d.actors[g] = v * k }
        quality.forEach { (g, v) -> d.quality[g] = v * k }
        sectors.forEach { (g, v) -> d.sectors[g] = v * k }
        economy.forEach { (g, v) -> d.economy[g] = v * k }
        d.events += events
        d.liberty = liberty * k; d.censure = censure; d.censureReason = censureReason; d.people = people; d.lines += lines
        return d
    }
}

/** Les formules du constructeur : chaque mesure produit un [Impact] réaliste selon sa valeur. */
class BuilderModel(private val ctx: SimulationContext) {
    private val file get() = ctx.playerData.legislation?.builder

    fun target(id: String) = file?.targets?.firstOrNull { it.id == id }
    fun action(id: String) = file?.actions?.firstOrNull { it.id == id }

    /** Cibles possibles pour une action. */
    fun targets(action: BuilderAction): List<BuilderTarget> = file?.targets.orEmpty().filter { t ->
        t.category in action.categories && when (action.requires) {
            "public" -> t.public
            "ban" -> t.ban != null
            "obligation" -> t.obligation != null
            "normalIncrease" -> t.normalIncrease > 0
            else -> true
        } && (action.model != MeasureModel.SUBSIDY || t.category != "profession" || t.sector != null)
    }

    /** Bornes de la valeur (une obligation a sa propre durée maximale). */
    fun range(action: BuilderAction, target: BuilderTarget): Triple<Double, Double, Double> = when (action.model) {
        MeasureModel.OBLIGATION -> Triple(1.0, target.obligation?.max ?: action.max, target.obligation?.step ?: action.step)
        else -> Triple(action.min, action.max, action.step)
    }

    fun unit(action: BuilderAction, target: BuilderTarget) = if (action.model == MeasureModel.OBLIGATION) target.obligation?.unit ?: action.unit else action.unit

    fun formatValue(action: BuilderAction, target: BuilderTarget, v: Double): String = when (action.model) {
        MeasureModel.BAN -> if (v >= 0.5) "interdit" else "autorisé"
        MeasureModel.RECRUIT -> "${Formatting.integer(v.toLong())} ${action.unit}"
        MeasureModel.SUBSIDY -> Formatting.billions(v) + " par an"
        MeasureModel.BONUS -> "${Formatting.integer(v.toLong())} € par mois"
        else -> "${Formatting.amount(v)} ${unit(action, target)}".trim()
    }

    /** La mesure en une phrase : « Taxer les médecins libéraux : surtaxe de 5 % sur les revenus, au-dessus de 120 000 €... ». */
    fun sentence(cfg: MeasureConfig, v: Double): String {
        val a = action(cfg.action) ?: return cfg.key
        val t = target(cfg.target) ?: return cfg.key
        val core = when (a.model) {
            MeasureModel.BAN -> "${a.verb} ${t.label}"
            MeasureModel.OBLIGATION -> "${a.verb} ${t.label} à ${t.obligation?.label ?: ""} pendant ${Formatting.amount(v)} ${unit(a, t)}"
            MeasureModel.RECRUIT -> "${a.verb} ${Formatting.integer(v.toLong())} ${t.short.lowercase()}"
            MeasureModel.PRICE_CAP -> "${a.verb} ${t.label} à ${Formatting.amount(v)} % par an"
            MeasureModel.SUBSIDY -> "${a.verb} ${t.label} (${Formatting.billions(v)} par an)"
            MeasureModel.BONUS -> "${a.verb} ${t.label} : ${Formatting.integer(v.toLong())} € par mois"
            MeasureModel.TURNOVER_TAX -> "${a.verb} ${t.label} : ${Formatting.amount(v)} % de leurs ${t.baseLabel}"
            MeasureModel.SURTAX -> "${a.verb} ${t.label} : surtaxe de ${Formatting.amount(v)} % de leurs revenus"
            MeasureModel.TAX_CUT -> "${a.verb} ${Formatting.amount(v)} % pour ${t.label}"
            MeasureModel.PAY_RAISE -> "${a.verb} ${t.label} de ${Formatting.amount(v)} %"
        }
        return core + conditions(cfg, a)
    }

    fun conditions(cfg: MeasureConfig, a: BuilderAction? = action(cfg.action)): String {
        val parts = mutableListOf<String>()
        if (cfg.threshold > 0) parts += if (a?.model == MeasureModel.BONUS) "pour les revenus sous ${Formatting.integer(cfg.threshold.toLong())} €"
            else "au-dessus de ${Formatting.integer(cfg.threshold.toLong())} € de revenus"
        if (cfg.zone != "national") parts += file?.zones?.firstOrNull { it.id == cfg.zone }?.label?.lowercase() ?: cfg.zone
        if (cfg.exemptRural) parts += "sauf en zone rurale"
        if (cfg.durationYears > 0) parts += "pendant ${cfg.durationYears} an${if (cfg.durationYears > 1) "s" else ""}"
        if (cfg.phaseIn) parts += "progressivement sur trois ans"
        return if (parts.isEmpty()) "" else ", " + parts.joinToString(", ")
    }

    /** Conséquences annuelles de la mesure à la valeur [v] (0 : pas de mesure). */
    fun evaluate(cfg: MeasureConfig, v: Double): Impact {
        val out = Impact()
        val a = action(cfg.action) ?: return out
        val t = target(cfg.target) ?: return out
        if (v == 0.0 && a.model != MeasureModel.PRICE_CAP) return out
        if (a.model == MeasureModel.PRICE_CAP && v >= t.normalIncrease) return out
        val zone = zoneShare(t, cfg.zone) * if (cfg.exemptRural) 1 - t.rural else 1.0
        val temporary = cfg.durationYears > 0
        when (a.model) {
            MeasureModel.SURTAX -> surtax(out, t, cfg, v, zone, temporary)
            MeasureModel.TAX_CUT -> taxCut(out, t, cfg, v, zone)
            MeasureModel.BONUS -> bonus(out, t, cfg, v, zone)
            MeasureModel.TURNOVER_TAX -> turnover(out, t, v, zone, temporary)
            MeasureModel.SUBSIDY -> subsidy(out, t, v)
            MeasureModel.PRICE_CAP -> priceCap(out, t, v, zone)
            MeasureModel.BAN -> ban(out, t)
            MeasureModel.OBLIGATION -> obligation(out, t, cfg, v)
            MeasureModel.RECRUIT -> recruit(out, t, v, zone)
            MeasureModel.PAY_RAISE -> payRaise(out, t, v)
        }
        if (temporary) out.lines += "Mesure temporaire : elle s'arrêtera d'elle-même au bout de ${cfg.durationYears} an${if (cfg.durationYears > 1) "s" else ""}."
        if (cfg.phaseIn) out.lines += "Montée en charge sur trois ans : effets plus lents, réactions plus modérées."
        return out
    }

    // ---- Modèles ----------------------------------------------------------------------------

    private fun surtax(out: Impact, t: BuilderTarget, cfg: MeasureConfig, rate: Double, zone: Double, temporary: Boolean) {
        val (countShare, incomeShare) = above(t, cfg.threshold)
        val people = t.count * zone * countShare
        val incomeBase = t.count * t.income * zone * incomeShare
        val avg = if (people > 0) incomeBase / people else t.income
        val gross = incomeBase * rate / 100 / 1e9
        // Fuite, optimisation, moindre activité : plus le taux est lourd, plus la base fond (effet Laffer).
        val loss = (t.mobility * (rate / 10).pow(1.3) * (if (temporary) 0.5 else 1.0)).coerceIn(0.0, 0.9)
        out.people = people
        out.revenue = gross * (1 - loss)
        val leavers = people * loss * LEAVING_SHARE
        t.quality?.let { q -> out.add(out.quality, q, -t.qualityWeight * leavers / t.count.coerceAtLeast(1.0) * QUALITY_LEAVERS) }
        if (cfg.exemptRural) t.quality?.let { q -> out.add(out.quality, q, t.qualityWeight * (rate / 5) * RURAL_PULL) }
        val concerned = sqrt((people / t.count.coerceAtLeast(1.0)).coerceIn(0.0, 1.0))
        t.groups.forEach { (g, w) -> out.add(out.groups, g, -GROUP_TAX * (rate / 5) * w * concerned) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, -ACTOR_TAX * (rate / 5) * w) }
        if (avg > RICH_INCOME || t.richTax) {
            // Faire payer les plus aisés plaît aux classes populaires, inquiète les investisseurs.
            out.add(out.groups, "low_income", POPULAR * (rate / 5) * concerned)
            out.add(out.groups, "middle_income", POPULAR / 2 * (rate / 5) * concerned)
            out.add(out.economy, "economy.businessConfidence", -CONFIDENCE * (rate / 5) * (0.5 + t.mobility))
        } else if (avg < MODEST_INCOME) {
            out.add(out.groups, "low_income", -POPULAR * (rate / 5))
        }
        t.event?.let { out.events[it] = (rate / 20).coerceAtMost(0.5) }
        val total = baseMarginal(avg) + rate
        if (total > CONFISCATORY) { out.censure += 0.3 + (total - CONFISCATORY) / 20; out.censureReason = "prélèvement confiscatoire (${Math.round(total)} % au total)" }
        if (t.category == "profession" && rate > 2) {
            out.censure += 0.1 + rate / 50
            if (out.censureReason.isEmpty()) out.censureReason = "rupture d'égalité devant l'impôt (une seule profession visée)"
        }
        out.censure = out.censure.coerceIn(0.0, 0.95)
        out.lines += "${people(people)} concernés, revenu moyen ${Formatting.integer(avg.toLong())} € par an."
        out.lines += "Recettes : ${Formatting.billions(out.revenue)} par an (${Math.round(loss * 100)} % perdus en départs, optimisation et activité réduite)."
        if (leavers >= 50) out.lines += "≈ ${Formatting.integer(leavers.toLong())} partiraient à l'étranger ou cesseraient leur activité."
        out.lines += "Taux marginal total pour eux : environ ${Math.round(total)} %."
    }

    private fun taxCut(out: Impact, t: BuilderTarget, cfg: MeasureConfig, rate: Double, zone: Double) {
        val (countShare, incomeShare) = above(t, cfg.threshold)
        val people = t.count * zone * countShare
        val incomeBase = t.count * t.income * zone * incomeShare
        val avg = if (people > 0) incomeBase / people else t.income
        // Impôt sur le revenu moyen payé : environ 3 à 15 % du revenu selon le niveau.
        val tax = incomeBase * avgTaxRate(avg) / 1e9
        val cost = tax * rate / 100 * (1 - (t.mobility * rate / 50).coerceAtMost(0.3))
        out.people = people
        out.revenue = -cost
        t.quality?.let { q -> out.add(out.quality, q, t.qualityWeight * t.mobility * rate / 100 * QUALITY_ATTRACT) }
        val concerned = sqrt((people / t.count.coerceAtLeast(1.0)).coerceIn(0.0, 1.0))
        t.groups.forEach { (g, w) -> out.add(out.groups, g, GROUP_GIFT * (rate / 10) * w * concerned) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, ACTOR_TAX * (rate / 10) * w) }
        if (avg > RICH_INCOME || t.richTax) {
            out.add(out.groups, "low_income", -POPULAR * (rate / 10))
            out.add(out.economy, "economy.businessConfidence", CONFIDENCE * (rate / 10))
        }
        if (t.category == "profession" && rate > 10) { out.censure = 0.1 + rate / 200; out.censureReason = "avantage fiscal réservé à une profession" }
        out.lines += "${people(people)} concernés. Coût : ${Formatting.billions(cost)} par an."
    }

    private fun bonus(out: Impact, t: BuilderTarget, cfg: MeasureConfig, amount: Double, zone: Double) {
        val share = if (cfg.threshold > 0) 1 - above(t, cfg.threshold).first else 1.0
        val people = t.count * zone * share
        val cost = people * amount * 12 / 1e9
        out.people = people
        out.spending = cost
        val relative = amount * 12 / t.income.coerceAtLeast(5000.0)
        t.groups.forEach { (g, w) -> out.add(out.groups, g, (GROUP_GIFT * relative * 10).coerceAtMost(0.05) * w) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, (ACTOR_TAX * relative * 10).coerceAtMost(0.3) * w) }
        t.quality?.let { q ->
            val pull = if (cfg.zone == "rural" || cfg.exemptRural) 2.0 else 1.0
            out.add(out.quality, q, t.qualityWeight * (relative * QUALITY_BONUS * pull).coerceAtMost(0.06))
        }
        out.add(out.economy, "economy.inflation", cost / GDP * 0.2)
        out.lines += "${people(people)} bénéficiaires. Coût : ${Formatting.billions(cost)} par an (${Math.round(relative * 100)} % de leur revenu moyen)."
    }

    private fun turnover(out: Impact, t: BuilderTarget, rate: Double, zone: Double, temporary: Boolean) {
        val gross = t.base * zone * rate / 100
        val loss = (t.mobility * rate / 5 * (if (temporary) 0.5 else 1.0)).coerceIn(0.0, 0.85)
        out.revenue = gross * (1 - loss)
        val passThrough = t.priceWeight * rate / 100 * PASS_THROUGH
        if (passThrough > 0) {
            out.add(out.economy, "economy.inflation", passThrough)
            out.add(out.groups, "low_income", -passThrough * 4)
        }
        t.sector?.let { out.add(out.sectors, it, -rate / 100 * SECTOR_HIT) }
        t.groups.forEach { (g, w) -> out.add(out.groups, g, -GROUP_TAX * (rate / 10) * w) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, -ACTOR_TAX * (rate / 5) * w) }
        if (t.health > 0) out.add(out.quality, "health", t.health * rate / 10)
        if (t.environment > 0) out.add(out.quality, "environment", t.environment * rate / 10)
        if (t.symbolic > 0) out.add(out.groups, "low_income", t.symbolic * (rate / 10).coerceAtMost(2.0))
        t.foreign?.let { out.add(out.economy, "memory.$it.DISAGREEMENT", -0.01 * rate / 5) }
        if (t.category == "company") out.add(out.economy, "economy.businessConfidence", -CONFIDENCE * rate / 10 * (1 + t.mobility))
        t.event?.let { out.events[it] = (rate / 25).coerceAtMost(0.5) }
        if (rate > 25) { out.censure = 0.25 + (rate - 25) / 50; out.censureReason = "taux confiscatoire" }
        out.lines += "Assiette : ${Formatting.billions(t.base * zone)} de ${t.baseLabel} par an."
        out.lines += "Recettes : ${Formatting.billions(out.revenue)} par an (${Math.round(loss * 100)} % perdus en délocalisation, fraude ou baisse des ventes)."
        if (passThrough > 0.0005) out.lines += "Une partie se retrouve dans les prix : inflation +${Formatting.amount(passThrough * 100)} point."
    }

    private fun subsidy(out: Impact, t: BuilderTarget, amount: Double) {
        out.spending = amount
        val base = t.base.takeIf { it > 0 } ?: (t.count * t.income / 1e9).coerceAtLeast(1.0)
        t.sector?.let { out.add(out.sectors, it, amount / base * SUBSIDY_SHOCK) }
        t.groups.forEach { (g, w) -> out.add(out.groups, g, (GROUP_GIFT * amount / base * 5).coerceAtMost(0.04) * w) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, (ACTOR_TAX * amount / base * 10).coerceAtMost(0.3) * w) }
        if (t.environment > 0) out.add(out.quality, "environment", -t.environment * amount / base)
        t.quality?.let { q -> out.add(out.quality, q, t.qualityWeight * amount / base * 0.2) }
        out.add(out.economy, "economy.businessConfidence", amount * 0.001)
        out.lines += "Aide de ${Formatting.billions(amount)} par an, soit ${Formatting.percent(amount / base)} de leurs ${t.baseLabel.ifEmpty { "revenus" }}."
    }

    private fun priceCap(out: Impact, t: BuilderTarget, cap: Double, zone: Double) {
        val strength = ((t.normalIncrease - cap) / t.normalIncrease).coerceIn(0.0, 2.0) * zone
        out.add(out.economy, "economy.inflation", -t.priceWeight * strength * t.normalIncrease / 100 * 0.5)
        t.capSector?.let { out.add(out.sectors, it, -CAP_SUPPLY * strength) }
        t.capWinners.forEach { (g, w) -> out.add(out.groups, g, CAP_OPINION * strength * w) }
        t.capLosers.forEach { (g, w) -> out.add(out.groups, g, -CAP_OPINION * strength * w) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, -0.1 * strength * w) }
        if (t.capCost > 0) out.spending = t.capCost * strength * t.normalIncrease
        if (cap < 0) { out.censure = 0.35; out.censureReason = "atteinte au droit de propriété (baisse imposée des prix)" }
        else if (strength > 0.8) { out.censure = 0.12; out.censureReason = "atteinte disproportionnée à la liberté d'entreprendre" }
        out.lines += "Hausse habituelle : ${Formatting.amount(t.normalIncrease)} % par an ; plafond : ${Formatting.amount(cap)} %."
        t.capSector?.let { out.lines += "Moins de rentabilité : l'offre se réduit (investissement, construction, production)." }
        if (out.spending > 0) out.lines += "L'État compense une partie de l'écart : ${Formatting.billions(out.spending)} par an."
    }

    private fun ban(out: Impact, t: BuilderTarget) {
        val b = t.ban ?: return
        out.revenue = -b.revenueLoss
        b.effects.forEach { fx ->
            when {
                fx.target.startsWith("opinion.group.") -> out.add(out.groups, fx.target.removePrefix("opinion.group."), fx.amount)
                fx.target.startsWith("quality.") -> out.add(out.quality, fx.target.removePrefix("quality."), fx.amount)
                fx.target.startsWith("sector.") -> out.add(out.sectors, fx.target.removePrefix("sector."), fx.amount)
                else -> out.add(out.economy, fx.target, fx.amount)
            }
        }
        out.liberty = b.liberty
        out.censure = b.risk
        if (b.risk > 0) out.censureReason = "atteinte à la liberté d'entreprendre ou au droit de propriété"
        t.event?.let { out.events[it] = 0.2 }
        if (b.revenueLoss > 0) out.lines += "Recettes perdues : ${Formatting.billions(b.revenueLoss)} par an."
        if (b.revenueLoss < 0) out.lines += "Moins de fraude : ${Formatting.billions(-b.revenueLoss)} de recettes en plus par an."
    }

    private fun obligation(out: Impact, t: BuilderTarget, cfg: MeasureConfig, years: Double) {
        val o = t.obligation ?: return
        t.quality?.let { q -> out.add(out.quality, q, t.qualityWeight * years * OBLIGATION_QUALITY) }
        t.groups.forEach { (g, w) -> out.add(out.groups, g, -GROUP_TAX * years / 2 * w) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, -0.08 * years * w) }
        out.add(out.groups, "rural", 0.006 * years)
        val leavers = t.count * t.mobility * years * 0.01
        t.quality?.let { q -> out.add(out.quality, q, -t.qualityWeight * leavers / t.count * QUALITY_LEAVERS) }
        out.liberty = -0.5 * years
        out.censure = (o.risk + 0.05 * years).coerceAtMost(0.8)
        out.censureReason = "atteinte à la liberté d'installation"
        t.event?.let { out.events[it] = 0.1 * years }
        out.lines += "${people(t.count)} concernés ; l'accès au service s'améliore dans les zones sous-dotées."
        if (leavers >= 50) out.lines += "≈ ${Formatting.integer(leavers.toLong())} pourraient changer de métier ou partir à l'étranger."
    }

    private fun recruit(out: Impact, t: BuilderTarget, posts: Double, zone: Double) {
        val cost = posts * t.salary / 1e9
        out.spending = cost
        t.quality?.let { q -> out.add(out.quality, q, t.qualityWeight * posts / t.count.coerceAtLeast(1.0) * RECRUIT_QUALITY * (if (zone < 1) 1.3 else 1.0)) }
        out.add(out.economy, "economy.unemployment", -posts / WORKFORCE)
        t.groups.forEach { (g, w) -> out.add(out.groups, g, 0.004 * w * (posts / t.count * 20).coerceAtMost(1.0)) }
        out.lines += "Coût : ${Formatting.billions(cost)} par an (${Formatting.integer(t.salary.toLong())} € par poste, charges comprises)."
        out.lines += "Effectifs : ${Formatting.integer(t.count.toLong())} aujourd'hui, +${Formatting.percent(posts / t.count)}."
        out.lines += "Il faut former les recrues : effets pleins en un à deux ans."
    }

    private fun payRaise(out: Impact, t: BuilderTarget, rate: Double) {
        val cost = t.count * t.salary * rate / 100 / 1e9
        out.spending = cost
        t.groups.forEach { (g, w) -> out.add(out.groups, g, GROUP_GIFT * (rate / 5) * w) }
        t.actors.forEach { (id, w) -> out.add(out.actors, id, 0.1 * (rate / 5) * w) }
        t.quality?.let { q -> out.add(out.quality, q, t.qualityWeight * rate / 100 * PAY_QUALITY) }
        out.add(out.economy, "economy.inflation", cost / GDP * 0.3)
        out.lines += "Coût : ${Formatting.billions(cost)} par an pour ${people(t.count)}."
        out.lines += "Des métiers plus attractifs : moins de postes vacants."
    }

    // ---- Outils -------------------------------------------------------------------------------

    private fun zoneShare(t: BuilderTarget, zone: String) = when (zone) {
        "idf" -> t.idf
        "province" -> 1 - t.idf
        "rural" -> t.rural
        "urban" -> ((1 - t.rural) * 0.6).coerceAtLeast(0.05)
        else -> 1.0
    }

    /** Part des personnes et part des revenus au-dessus d'un seuil (revenus log-normaux). */
    fun above(t: BuilderTarget, threshold: Double): Pair<Double, Double> {
        if (threshold <= 0 || t.income <= 0) return 1.0 to 1.0
        val s = t.sigma.coerceAtLeast(0.1)
        val mu = ln(t.income) - s * s / 2
        val z = (ln(threshold) - mu) / s
        return (1 - cdf(z)) to (1 - cdf(z - s))
    }

    private fun cdf(z: Double): Double = 1.0 / (1.0 + exp(-1.702 * z))

    /** Taux marginal déjà payé (impôt sur le revenu, prélèvements sociaux, contribution hauts revenus). */
    private fun baseMarginal(income: Double) = when {
        income > 250_000 -> 58.0
        income > 180_000 -> 55.0
        income > 80_000 -> 51.0
        income > 28_000 -> 40.0
        else -> 25.0
    }

    private fun avgTaxRate(income: Double) = when {
        income > 250_000 -> 0.3
        income > 100_000 -> 0.18
        income > 50_000 -> 0.1
        income > 25_000 -> 0.05
        else -> 0.01
    }

    private fun people(n: Double): String = when {
        n >= 1e6 -> String.format(java.util.Locale.FRENCH, "%.1f million%s de personnes", n / 1e6, if (n >= 2e6) "s" else "")
        else -> "${Formatting.integer((Math.round(n / 100) * 100))} personnes"
    }

    companion object {
        private const val LEAVING_SHARE = 0.4
        private const val QUALITY_LEAVERS = 1.0
        private const val RURAL_PULL = 0.004
        private const val GROUP_TAX = 0.012
        private const val GROUP_GIFT = 0.01
        private const val ACTOR_TAX = 0.08
        private const val POPULAR = 0.004
        private const val CONFIDENCE = 0.003
        private const val RICH_INCOME = 80_000.0
        private const val MODEST_INCOME = 25_000.0
        private const val CONFISCATORY = 66.0
        private const val QUALITY_ATTRACT = 0.3
        private const val QUALITY_BONUS = 0.08
        private const val GDP = 2900.0
        private const val PASS_THROUGH = 0.6
        private const val SECTOR_HIT = 0.6
        private const val SUBSIDY_SHOCK = 0.8
        private const val CAP_SUPPLY = 0.03
        private const val CAP_OPINION = 0.012
        private const val OBLIGATION_QUALITY = 0.012
        private const val RECRUIT_QUALITY = 1.5
        private const val PAY_QUALITY = 0.5
        private const val WORKFORCE = 31_000_000.0
    }
}
