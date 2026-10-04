package fr.president.engine.economy

import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln

@Serializable
data class SectorDef(
    val id: String,
    val label: String,
    val icon: String = "",
    val gdpShare: Double,
    val jobShare: Double,
    /** Sensibilité de l'activité à chaque moteur de l'économie (écart à la situation de départ). */
    val drivers: Map<String, Double> = emptyMap(),
)

@Serializable
data class CompanyDef(val id: String, val name: String, val sector: String, val capBillions: Double, val employees: Int, val beta: Double = 1.0)

@Serializable
data class SectorsFile(
    val indexName: String = "Indice",
    val indexBase: Double = 1000.0,
    val sectors: List<SectorDef>,
    val companies: List<CompanyDef> = emptyList(),
    val eventShocks: Map<String, Map<String, Double>> = emptyMap(),
    val restrictions: Map<String, Double> = emptyMap(),
)

@Serializable
class SectorState(
    /** Activité du secteur (1 = niveau de départ). */
    var activity: Double = 1.0,
    /** Choc en cours (événements, décisions), qui se résorbe peu à peu. */
    var shock: Double = 0.0,
)

@Serializable
class CompanyState(var price: Double = 1.0, var employees: Int = 0, var lastSupport: WorldTime? = null)

@Serializable
class MarketState(
    val sectors: MutableMap<String, SectorState> = mutableMapOf(),
    val companies: MutableMap<String, CompanyState> = mutableMapOf(),
    /** Valeur des moteurs au début de la partie : l'activité suit leur écart à ce point de départ. */
    val baseline: MutableMap<String, Double> = mutableMapOf(),
    var index: Double = 0.0,
    val indexHistory: MutableList<Double> = mutableListOf(),
    var lastCrash: WorldTime? = null,
    /** Dernière convocation du PDG de chaque entreprise. */
    val summons: MutableMap<String, WorldTime> = mutableMapOf(),
)

/**
 * L'économie par secteurs, les grandes entreprises et la Bourse. Chaque jour, l'activité de
 * chaque secteur suit les moteurs de l'économie (moral, énergie, taux, croissance mondiale,
 * sécurité, mesures de crise) et les chocs d'événements ; les cours suivent leur secteur avec
 * leur propre volatilité. Un krach pèse sur le moral ; un choc sectoriel pèse sur la croissance.
 */
class SectorSystem : SimulationSystem {
    override val name = "sectors"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val file = ctx.playerData.sectors ?: return
        val market = ctx.state.market
        if (market.baseline.isEmpty()) init(ctx, file)
        val drivers = drivers(ctx, file)
        val rng = ctx.rng
        for (def in file.sectors) {
            val s = market.sectors.getOrPut(def.id) { SectorState() }
            val target = 1 + def.drivers.entries.sumOf { (k, w) -> w * ((drivers[k] ?: 0.0) - (market.baseline[k] ?: 0.0)) }
            val before = s.activity
            s.activity = (s.activity + (target.coerceIn(MIN_ACTIVITY, MAX_ACTIVITY) + s.shock - s.activity) * ADJUST).coerceAtLeast(MIN_ACTIVITY)
            s.shock *= SHOCK_DECAY
            val sectorMove = ln(s.activity / before)
            file.companies.filter { it.sector == def.id }.forEach { c ->
                val st = market.companies.getOrPut(c.id) { CompanyState(employees = c.employees) }
                val noise = rng.nextGaussian() * VOLATILITY
                st.price *= exp(c.beta * sectorMove * LEVERAGE + noise + DRIFT)
                st.employees = (c.employees * (JOBS_FLOOR + (1 - JOBS_FLOOR) * s.activity)).toInt()
            }
        }
        val index = index(file, market)
        market.index = index
        market.indexHistory += index
        if (market.indexHistory.size > HISTORY_DAYS) market.indexHistory.removeAt(0)
        crash(ctx, file, market)
    }

    private fun init(ctx: SimulationContext, file: SectorsFile) {
        val market = ctx.state.market
        market.baseline.putAll(drivers(ctx, file))
        file.sectors.forEach { market.sectors[it.id] = SectorState() }
        file.companies.forEach { market.companies[it.id] = CompanyState(employees = it.employees) }
        market.index = file.indexBase
    }

    /** Krach : plus de 12 % de baisse en vingt jours ; le moral s'effondre. */
    private fun crash(ctx: SimulationContext, file: SectorsFile, market: MarketState) {
        val h = market.indexHistory
        if (h.size < CRASH_WINDOW) return
        val change = h.last() / h[h.size - CRASH_WINDOW] - 1
        if (change > -CRASH_DROP) return
        if (market.lastCrash?.let { it.daysUntil(ctx.now) < CRASH_COOLDOWN } == true) return
        market.lastCrash = ctx.now
        ctx.effects.trigger(EffectSpec("economy.businessConfidence", -CRASH_CONFIDENCE), null, emptyMap(), "market")
        ctx.effects.trigger(EffectSpec("economy.consumerConfidence", -CRASH_CONFIDENCE / 2), null, emptyMap(), "market")
        ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.IMPORTANT, "Krach à la Bourse de Paris",
            "${file.indexName} : ${Math.round(change * 100)} % en quelques semaines. Les épargnants et les entreprises s'inquiètent.", null)
        fr.president.engine.stats.JournalService(ctx).add("Économie", "Krach boursier (${Math.round(change * 100)} %)", fr.president.engine.readout.Tone.BAD)
    }

    companion object {
        private const val ADJUST = 0.04
        private const val SHOCK_DECAY = 0.985
        private const val MIN_ACTIVITY = 0.4
        private const val MAX_ACTIVITY = 1.6
        private const val VOLATILITY = 0.009
        private const val DRIFT = 0.0002
        private const val LEVERAGE = 2.5
        private const val JOBS_FLOOR = 0.6
        private const val HISTORY_DAYS = 400
        private const val CRASH_WINDOW = 20
        private const val CRASH_DROP = 0.12
        private const val CRASH_COOLDOWN = 120.0
        private const val CRASH_CONFIDENCE = 0.03
        /** Part d'un choc sectoriel qui se transmet à la croissance du pays. */
        private const val OUTPUT_PASS_THROUGH = 0.05

        /** Valeur des moteurs de l'économie aujourd'hui. */
        fun drivers(ctx: SimulationContext, file: SectorsFile): Map<String, Double> {
            val s = ctx.state
            val e = s.playerCountry.economy
            val player = s.player.countryId
            val others = s.countries.values.filter { it.id != player }
            val gdp = others.sumOf { it.economy.gdpBillions }.coerceAtLeast(1.0)
            val active = s.measures.active.map { it.id }
            return mapOf(
                "consumer" to e.consumerConfidence,
                "business" to e.businessConfidence,
                "energy" to e.energyPriceIndex,
                "rates" to e.marketRate,
                "world" to others.sumOf { it.economy.realGrowth * it.economy.gdpBillions } / gdp,
                "security" to (s.playerCountry.services["security"] ?: 0.5),
                "defense" to (e.budget?.spending?.get("defense")?.policyFactor ?: 1.0),
                "agriculture" to (s.playerCountry.services["agriculture"] ?: 0.5),
                "restrictions" to active.sumOf { file.restrictions[it] ?: 0.0 },
                "growth" to e.realGrowth,
            )
        }

        fun index(file: SectorsFile, market: MarketState): Double {
            val total = file.companies.sumOf { it.capBillions }.coerceAtLeast(1.0)
            return file.indexBase * file.companies.sumOf { c -> c.capBillions * (market.companies[c.id]?.price ?: 1.0) } / total
        }

        /** Choc sur un secteur (effet « sector.<id> »), transmis en partie à la croissance. */
        fun shock(ctx: SimulationContext, sector: String, delta: Double) {
            val def = ctx.playerData.sectors?.sectors?.firstOrNull { it.id == sector } ?: return
            ctx.state.market.sectors.getOrPut(sector) { SectorState() }.shock += delta
            ctx.state.playerCountry.economy.pendingOutputShock += delta * def.gdpShare * OUTPUT_PASS_THROUGH
        }

        /** Les secteurs touchés par un événement (attentat : tourisme ; droits de douane : luxe...). */
        fun onEvent(ctx: SimulationContext, eventId: String, factor: Double) {
            ctx.playerData.sectors?.eventShocks?.get(eventId)?.forEach { (sector, delta) -> shock(ctx, sector, delta * factor) }
        }
    }
}
