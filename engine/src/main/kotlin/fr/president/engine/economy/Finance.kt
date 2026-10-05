package fr.president.engine.economy

import fr.president.engine.effects.EffectSpec
import fr.president.engine.government.PolicyKind
import fr.president.engine.government.PolicyProposal
import fr.president.engine.government.PolicyService
import fr.president.engine.government.PolicyStatus
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.territory.ActionCategory
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

// --- Fiscalité détaillée -----------------------------------------------------------------------------

/**
 * Dispositif fiscal détaillé : un curseur chiffré (taux, montant, prix). La courbe [revenue] donne
 * la recette supplémentaire par an (Md€) par rapport à la valeur de départ ; elle s'infléchit quand
 * la taxe devient trop lourde (effet Laffer). Les effets sont donnés pour une hausse de [per] unités.
 */
@Serializable
data class FiscalDef(
    val id: String,
    val category: String,
    val label: String,
    val description: String,
    val unit: String = "",
    val decimals: Int = 1,
    val reference: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 1.0,
    val step: Double = 1.0,
    val revenue: List<List<Double>> = emptyList(),
    val per: Double = 1.0,
    val effects: List<EffectSpec> = emptyList(),
    /** Valeur correspondant à chaque option des anciennes versions (reprise des sauvegardes). */
    val legacy: List<Double> = emptyList(),
    val reform: fr.president.engine.legislation.ReformLink? = null,
)

@Serializable
data class FiscalFile(val categories: List<ActionCategory> = emptyList(), val taxes: List<FiscalDef>)

@Serializable
class FiscalState(
    /** Anciennes sauvegardes : option choisie (remplacé par [amounts]). */
    val values: MutableMap<String, Int> = mutableMapOf(),
    val amounts: MutableMap<String, Double> = mutableMapOf(),
)

/** Les dispositifs fiscaux détaillés : votés dans une loi de finances, ils ajoutent ou retirent des recettes. */
class FiscalService(private val ctx: SimulationContext) {
    private val file get() = ctx.playerData.fiscal
    val categories: List<ActionCategory> get() = file?.categories.orEmpty()
    val taxes: List<FiscalDef> get() = file?.taxes.orEmpty()

    fun def(id: String) = taxes.firstOrNull { it.id == id }

    /** Valeur en vigueur (reprend l'option d'une ancienne sauvegarde si besoin). */
    fun value(id: String): Double {
        val d = def(id) ?: return 0.0
        ctx.state.fiscal.amounts[id]?.let { return it }
        ctx.state.fiscal.values[id]?.let { o -> d.legacy.getOrNull(o)?.let { return it } }
        return d.reference
    }

    /** Recette annuelle (Md€) par rapport à la valeur de départ, pour une valeur donnée. */
    fun revenueAt(d: FiscalDef, v: Double): Double {
        val c = d.revenue
        if (c.isEmpty()) return 0.0
        if (v <= c.first()[0]) return c.first()[1]
        if (v >= c.last()[0]) return c.last()[1]
        val i = c.indexOfFirst { it[0] >= v }
        val (x0, y0) = c[i - 1][0] to c[i - 1][1]
        val (x1, y1) = c[i][0] to c[i][1]
        return y0 + (y1 - y0) * (v - x0) / (x1 - x0)
    }

    /** Recette supplémentaire par an (Md€) si l'on passait à [to]. */
    fun delta(id: String, to: Double): Double {
        val d = def(id) ?: return 0.0
        return revenueAt(d, to) - revenueAt(d, value(id))
    }

    /** Dépôt d'un nouveau réglage : un budget rectificatif avec ce seul changement. */
    fun propose(id: String, to: Double): Result<PolicyProposal> =
        fr.president.engine.legislation.LegislationService(ctx).singleBudgetBill("fiscal:$id", to)

    /** Entrée en vigueur (appelée par la loi de finances). */
    fun enact(id: String, to: Double, scale: Double = 1.0) {
        val d = def(id) ?: return
        val from = value(id)
        val target = to.coerceIn(d.min, d.max)
        ctx.state.fiscal.amounts[id] = target
        ctx.state.fiscal.values.remove(id)
        val delta = revenueAt(d, target) - revenueAt(d, from)
        val e = ctx.state.playerCountry.economy
        e.fiscalAdjustmentBillions += delta
        BudgetCalculator.recompute(e)
        // Impulsion budgétaire : prélever plus freine la demande, moins la soutient.
        e.impulses += GrowthImpulse(-ctx.db.economyParameters.fiscalMultiplier * delta / e.gdpBillions, ctx.db.economyParameters.fiscalImpulseMonths, "fiscal:$id")
        val k = (target - from) / d.per * scale
        d.effects.forEach { fx -> if (!fx.target.startsWith("chain.") || k > 0) ctx.effects.trigger(fx.copy(amount = fx.amount * if (fx.target.startsWith("chain.")) kotlin.math.abs(k).coerceAtMost(1.0) else k), null, emptyMap(), "fiscal:$id") }
        fr.president.engine.legislation.LeverService(ctx).syncReforms()
    }
}

// --- Monnaie, BCE, change, dette ---------------------------------------------------------------------

@Serializable
enum class DebtStrategy(val label: String, val rollover: Double, val offset: Double, val description: String) {
    SHORT("Court terme", 2.0, -0.002, "Moins cher aujourd'hui ; très exposé à une hausse des taux."),
    BALANCED("Équilibrée", 1.0, 0.0, "La stratégie actuelle de l'Agence France Trésor."),
    LONG("Long terme", 0.5, 0.002, "Un peu plus cher ; la dette est protégée des hausses de taux."),
}

@Serializable
class MonetaryState(
    var ecbRate: Double = 0.02,
    var fedRate: Double = 0.0375,
    var eurUsd: Double = 1.16,
    var initialEcbRate: Double = 0.02,
    var initialRiskFree: Double = -1.0,
    var strategy: DebtStrategy = DebtStrategy.BALANCED,
    var greenBondsBillions: Double = 0.0,
    var lastPressure: WorldTime? = null,
    /** Pression française en attente sur la prochaine décision de la BCE (points de taux). */
    var pressure: Double = 0.0,
    val ecbHistory: MutableList<Double> = mutableListOf(),
    val fxHistory: MutableList<Double> = mutableListOf(),
)

/**
 * La France est dans l'euro : la BCE fixe ses taux d'après l'inflation et la croissance de la zone,
 * l'euro flotte face au dollar selon l'écart de taux et de croissance. Le taux sans risque français
 * suit la BCE ; un euro fort pèse sur les exportateurs et freine l'inflation. Chaque mois aussi,
 * l'État encaisse les dividendes de ses participations.
 */
class MonetarySystem : SimulationSystem {
    override val name = "monetary"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val m = ctx.state.monetary
        val e = ctx.state.playerCountry.economy
        if (m.initialRiskFree < 0) m.initialRiskFree = e.riskFreeRate
        val euro = euroArea(ctx)
        val us = ctx.state.countries["USA"]?.economy
        // Règle de Taylor de la BCE, lissée ; la pression française la fait à peine bouger.
        val target = NEUTRAL_RATE + TAYLOR_INFLATION * (euro.first - INFLATION_TARGET) + TAYLOR_GROWTH * (euro.second - POTENTIAL_GROWTH) + m.pressure
        val before = m.ecbRate
        m.ecbRate = (m.ecbRate + (target - m.ecbRate) * ECB_SMOOTHING).coerceIn(MIN_RATE, MAX_RATE)
        m.ecbRate = Math.round(m.ecbRate * QUARTER_POINTS) / QUARTER_POINTS
        m.pressure *= PRESSURE_DECAY
        m.fedRate = (m.fedRate + ((us?.inflation ?: INFLATION_TARGET) * 1.5 + 0.01 - m.fedRate) * FED_SMOOTHING).coerceIn(MIN_RATE, MAX_RATE)
        if (m.ecbRate != before) {
            ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.INFO, if (m.ecbRate > before) "La BCE relève ses taux" else "La BCE baisse ses taux",
                "Taux directeur : ${fr.president.engine.util.Formatting.percent(m.ecbRate)}. Le coût des emprunts de l'État et des ménages suit.", null)
        }
        e.riskFreeRate = m.initialRiskFree + (m.ecbRate - m.initialEcbRate) * PASS_THROUGH
        // Change euro / dollar : écart de taux et de croissance, plus un bruit de marché.
        val usGrowth = us?.realGrowth ?: POTENTIAL_GROWTH
        val fxTarget = FX_BASE * (1 + FX_RATE_WEIGHT * (m.ecbRate - m.fedRate) + FX_GROWTH_WEIGHT * (euro.second - usGrowth))
        val oldFx = m.eurUsd
        m.eurUsd = (m.eurUsd + (fxTarget - m.eurUsd) * FX_SMOOTHING + ctx.rng.nextGaussian() * FX_NOISE).coerceIn(0.8, 1.6)
        val fxChange = m.eurUsd / oldFx - 1
        // Un euro qui monte freine les exportateurs et l'inflation importée.
        EXPORTERS.forEach { (sector, w) -> SectorSystem.shock(ctx, sector, -fxChange * w) }
        e.inflation -= fxChange * FX_INFLATION
        // Dividendes des participations de l'État.
        val dividends = dividends(ctx)
        if (dividends > 0) e.pendingOneOffBillions -= dividends / MONTHS
        m.ecbHistory += m.ecbRate; if (m.ecbHistory.size > HISTORY) m.ecbHistory.removeAt(0)
        m.fxHistory += m.eurUsd; if (m.fxHistory.size > HISTORY) m.fxHistory.removeAt(0)
    }

    companion object {
        private const val NEUTRAL_RATE = 0.02
        private const val INFLATION_TARGET = 0.02
        private const val POTENTIAL_GROWTH = 0.012
        private const val TAYLOR_INFLATION = 1.5
        private const val TAYLOR_GROWTH = 0.5
        private const val ECB_SMOOTHING = 0.25
        private const val FED_SMOOTHING = 0.1
        private const val QUARTER_POINTS = 400.0
        private const val MIN_RATE = 0.0
        private const val MAX_RATE = 0.08
        private const val PRESSURE_DECAY = 0.5
        private const val PASS_THROUGH = 0.8
        private const val FX_BASE = 1.16
        private const val FX_RATE_WEIGHT = 4.0
        private const val FX_GROWTH_WEIGHT = 3.0
        private const val FX_SMOOTHING = 0.2
        private const val FX_NOISE = 0.008
        private const val FX_INFLATION = 0.05
        private const val MONTHS = 12.0
        private const val HISTORY = 120
        const val DIVIDEND_YIELD = 0.04
        private val EXPORTERS = mapOf("luxury" to 0.4, "aerospace" to 0.5, "industry" to 0.3, "agrifood" to 0.2, "tourism" to 0.2)

        /** Inflation et croissance de la zone euro (pays simulés de l'UE, pondérés par le PIB). */
        fun euroArea(ctx: SimulationContext): Pair<Double, Double> {
            val members = ctx.db.alliances.firstOrNull { it.id == "EU" }?.members.orEmpty().mapNotNull { ctx.state.countries[it]?.economy }
            val gdp = members.sumOf { it.gdpBillions }.coerceAtLeast(1.0)
            return members.sumOf { it.inflation * it.gdpBillions } / gdp to members.sumOf { it.realGrowth * it.gdpBillions } / gdp
        }

        /** Dividendes annuels (Md€) des participations de l'État. */
        fun dividends(ctx: SimulationContext): Double {
            val file = ctx.playerData.sectors ?: return 0.0
            return file.companies.sumOf { c ->
                val stake = ctx.state.market.stakes[c.id] ?: c.stateStake
                stake * c.capBillions * (ctx.state.market.companies[c.id]?.price ?: 1.0) * DIVIDEND_YIELD
            }
        }
    }
}

class MonetaryService(private val ctx: SimulationContext) {
    private val m get() = ctx.state.monetary

    fun pressureBlocker(): String? {
        val last = m.lastPressure
        if (last != null && last.daysUntil(ctx.now) < PRESSURE_COOLDOWN) return "Vous vous êtes déjà exprimé récemment."
        return fr.president.engine.session.AgendaService(ctx).blocker(AGENDA)
    }

    /** Appeler publiquement la BCE à baisser (ou relever) ses taux : effet faible, coût politique en Europe. */
    fun pressureEcb(lower: Boolean): Result<String> = runCatching {
        pressureBlocker()?.let { error(it) }
        m.lastPressure = ctx.now
        m.pressure += if (lower) -PRESSURE else PRESSURE
        fr.president.engine.session.AgendaService(ctx).book("Déclaration sur la politique monétaire", AGENDA)
        ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", -EU_COST), null, emptyMap(), "ecb")
        ctx.effects.trigger(EffectSpec("economy.businessConfidence", -CONFIDENCE_COST), null, emptyMap(), "ecb")
        "La BCE est indépendante : votre appel pèsera peu, et Berlin le prend mal."
    }

    fun setStrategy(s: DebtStrategy) {
        m.strategy = s
        val e = ctx.state.playerCountry.economy
        e.debtRolloverFactor = s.rollover
        e.debtRateOffset = s.offset
        JournalService(ctx).add("Économie", "Stratégie de dette : ${s.label.lowercase()}", Tone.NEUTRAL)
    }

    /** Émettre 10 Md€ d'obligations vertes, fléchées vers la transition (taux un peu plus bas, effets écologiques). */
    fun issueGreenBonds(): Result<String> = runCatching {
        require(m.greenBondsBillions < MAX_GREEN) { "Le marché des obligations vertes est saturé pour l'instant." }
        m.greenBondsBillions += GREEN_TRANCHE
        val e = ctx.state.playerCountry.economy
        e.debtRateOffset -= GREEN_DISCOUNT
        ctx.effects.trigger(EffectSpec("quality.environment", GREEN_ENV), null, emptyMap(), "green_bonds")
        ctx.effects.trigger(EffectSpec("budget.oneOff", GREEN_TRANCHE * GREEN_SPENT, days = 365.0), null, emptyMap(), "green_bonds")
        JournalService(ctx).add("Économie", "Émission de 10 Md€ d'obligations vertes", Tone.GOOD)
        "10 Md€ levés pour la transition écologique."
    }

    private companion object {
        const val PRESSURE_COOLDOWN = 90.0
        const val PRESSURE = 0.0025
        const val EU_COST = 0.01
        const val CONFIDENCE_COST = 0.003
        const val MAX_GREEN = 60.0
        const val GREEN_TRANCHE = 10.0
        const val GREEN_DISCOUNT = 0.0002
        const val GREEN_ENV = 0.004
        const val GREEN_SPENT = 0.2
        val AGENDA = fr.president.engine.session.AgendaCost(0.25)
    }
}
