package fr.president.engine.session

import fr.president.engine.economy.CompanyDef
import fr.president.engine.economy.SectorDef
import fr.president.engine.economy.SectorSystem
import fr.president.engine.effects.EffectSpec
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime

/** Secteurs, entreprises et Bourse : lecture pour l'interface et leviers du président. */
class MarketCommands(private val ctx: SimulationContext) {
    private val file get() = ctx.playerData.sectors
    private val market get() = ctx.state.market
    private val agenda = AgendaService(ctx)

    data class SectorRow(val def: SectorDef, val activity: Double, val shock: Double, val jobs: Long)
    data class CompanyRow(
        val def: CompanyDef,
        val sector: String,
        /** Variation du cours depuis le début du mandat. */
        val change: Double,
        val capBillions: Double,
        val employees: Int,
        val supportBlocker: String?,
        val summonBlocker: String?,
        val supportCost: Double,
        /** Part du capital détenue par l'État. */
        val stake: Double,
    )

    val available: Boolean get() = file != null
    val indexName: String get() = file?.indexName ?: "Bourse"
    val index: Double get() = market.index.takeIf { it > 0 } ?: file?.indexBase ?: 0.0
    val history: List<Double> get() = market.indexHistory

    /** Variation de l'indice sur trente jours. */
    val monthChange: Double? get() = history.takeIf { it.size > MONTH }?.let { it.last() / it[it.size - 1 - MONTH] - 1 }

    fun sectors(): List<SectorRow> {
        val f = file ?: return emptyList()
        val workforce = ctx.state.playerCountry.economy.let { POPULATION_ACTIVE * (1 - it.unemployment) }
        return f.sectors.map { d ->
            val s = market.sectors[d.id]
            SectorRow(d, s?.activity ?: 1.0, s?.shock ?: 0.0, (workforce * d.jobShare).toLong())
        }.sortedByDescending { it.def.gdpShare }
    }

    fun companies(): List<CompanyRow> {
        val f = file ?: return emptyList()
        return f.companies.map { c ->
            val st = market.companies[c.id]
            val price = st?.price ?: 1.0
            CompanyRow(c, f.sectors.firstOrNull { it.id == c.sector }?.label ?: c.sector, price - 1, c.capBillions * price,
                st?.employees ?: c.employees, wait(st?.lastSupport), wait(lastSummon[c.id]) ?: agenda.blocker(SUMMON_AGENDA), supportCost(c), stake(c))
        }.sortedByDescending { it.capBillions }
    }

    /** Commandes publiques et prêt de Bpifrance : l'entreprise et son secteur respirent. */
    fun support(companyId: String): Result<String> = runCatching {
        val c = file!!.companies.first { it.id == companyId }
        val st = market.companies.getOrPut(c.id) { fr.president.engine.economy.CompanyState(employees = c.employees) }
        wait(st.lastSupport)?.let { error(it) }
        val cost = supportCost(c)
        st.lastSupport = ctx.now
        st.price *= SUPPORT_PRICE
        ctx.effects.trigger(EffectSpec("budget.oneOff", cost, days = SUPPORT_DAYS), null, emptyMap(), "market:${c.id}")
        SectorSystem.shock(ctx, c.sector, SUPPORT_SHOCK)
        ctx.effects.trigger(EffectSpec("opinion.group.private_employees", SUPPORT_OPINION), null, emptyMap(), "market:${c.id}")
        JournalService(ctx).add("Économie", "Soutien de l'État à ${c.name}", Tone.NEUTRAL)
        "${c.name} : commandes publiques et prêt accordés (${fr.president.engine.util.Formatting.billions(cost)})."
    }

    /** Convoquer le PDG à l'Élysée : pression sur l'emploi et les salaires, les marchés grincent. */
    fun summon(companyId: String): Result<String> = runCatching {
        val c = file!!.companies.first { it.id == companyId }
        (wait(lastSummon[c.id]) ?: agenda.blocker(SUMMON_AGENDA))?.let { error(it) }
        val st = market.companies.getOrPut(c.id) { fr.president.engine.economy.CompanyState(employees = c.employees) }
        lastSummon[c.id] = ctx.now
        agenda.book("Entretien avec le PDG de ${c.name}", SUMMON_AGENDA)
        st.price *= SUMMON_PRICE
        ctx.effects.trigger(EffectSpec("economy.businessConfidence", -SUMMON_CONFIDENCE), null, emptyMap(), "market:${c.id}")
        ctx.effects.trigger(EffectSpec("opinion.national", SUMMON_OPINION), null, emptyMap(), "market:${c.id}")
        ctx.effects.trigger(EffectSpec("opinion.group.low_income", SUMMON_OPINION), null, emptyMap(), "market:${c.id}")
        JournalService(ctx).add("Économie", "PDG de ${c.name} convoqué à l'Élysée", Tone.NEUTRAL)
        "${c.name} : le PDG s'engage sur l'emploi en France. Les investisseurs n'apprécient guère."
    }

    private val lastSummon get() = ctx.state.market.summons

    fun stake(c: CompanyDef): Double = market.stakes[c.id] ?: c.stateStake

    private fun value(c: CompanyDef) = c.capBillions * (market.companies[c.id]?.price ?: 1.0)

    /** Nationaliser : l'État rachète le capital qu'il ne détient pas, avec une prime. */
    fun nationalize(companyId: String): Result<String> = runCatching {
        val c = file!!.companies.first { it.id == companyId }
        val stake = stake(c)
        require(stake < 1.0) { "Déjà entièrement publique." }
        val cost = value(c) * (1 - stake) * NATIONALIZATION_PREMIUM
        market.stakes[c.id] = 1.0
        ctx.effects.trigger(EffectSpec("budget.oneOff", cost), null, emptyMap(), "market:${c.id}")
        ctx.effects.trigger(EffectSpec("economy.businessConfidence", -NATIONALIZATION_CONFIDENCE), null, emptyMap(), "market:${c.id}")
        ctx.effects.trigger(EffectSpec("opinion.group.low_income", NATIONALIZATION_OPINION), null, emptyMap(), "market:${c.id}")
        ctx.effects.trigger(EffectSpec("opinion.group.high_income", -NATIONALIZATION_OPINION), null, emptyMap(), "market:${c.id}")
        JournalService(ctx).add("Économie", "Nationalisation de ${c.name}", Tone.NEUTRAL)
        ctx.notifications.news(fr.president.engine.notifications.NotificationCategory.ECONOMY, "L'État nationalise ${c.name}", null)
        "${c.name} nationalisée pour ${fr.president.engine.util.Formatting.billions(cost)}."
    }

    /** Céder une partie (ou la totalité) de la participation de l'État. */
    fun privatize(companyId: String, share: Double): Result<String> = runCatching {
        val c = file!!.companies.first { it.id == companyId }
        val stake = stake(c)
        require(stake > 0.0) { "L'État ne détient rien." }
        val sold = minOf(share, stake)
        val revenue = value(c) * sold * PRIVATIZATION_DISCOUNT
        market.stakes[c.id] = stake - sold
        ctx.effects.trigger(EffectSpec("budget.oneOff", -revenue), null, emptyMap(), "market:${c.id}")
        ctx.effects.trigger(EffectSpec("economy.businessConfidence", NATIONALIZATION_CONFIDENCE / 2), null, emptyMap(), "market:${c.id}")
        ctx.effects.trigger(EffectSpec("opinion.group.civil_servants", -NATIONALIZATION_OPINION), null, emptyMap(), "market:${c.id}")
        JournalService(ctx).add("Économie", "Privatisation : ${Math.round(sold * 100)} % de ${c.name}", Tone.NEUTRAL)
        ctx.notifications.news(fr.president.engine.notifications.NotificationCategory.ECONOMY, "L'État cède ${Math.round(sold * 100)} % de ${c.name}", null)
        "${Math.round(sold * 100)} % de ${c.name} cédés pour ${fr.president.engine.util.Formatting.billions(revenue)}."
    }

    private fun supportCost(c: CompanyDef) = (c.capBillions * SUPPORT_SHARE).coerceIn(MIN_SUPPORT, MAX_SUPPORT)

    private fun wait(last: WorldTime?): String? {
        val left = last?.let { COOLDOWN - it.daysUntil(ctx.now) } ?: return null
        return if (left > 0) "Possible à nouveau dans ${kotlin.math.ceil(left).toInt()} j." else null
    }

    private companion object {
        const val MONTH = 30
        const val NATIONALIZATION_PREMIUM = 1.25
        const val PRIVATIZATION_DISCOUNT = 0.95
        const val NATIONALIZATION_CONFIDENCE = 0.02
        const val NATIONALIZATION_OPINION = 0.008
        const val POPULATION_ACTIVE = 31_000_000.0
        const val COOLDOWN = 120.0
        const val SUPPORT_SHARE = 0.005
        const val MIN_SUPPORT = 0.1
        const val MAX_SUPPORT = 1.5
        const val SUPPORT_DAYS = 180.0
        const val SUPPORT_PRICE = 1.04
        const val SUPPORT_SHOCK = 0.01
        const val SUPPORT_OPINION = 0.003
        const val SUMMON_PRICE = 0.98
        const val SUMMON_CONFIDENCE = 0.002
        const val SUMMON_OPINION = 0.002
        val SUMMON_AGENDA = AgendaCost(0.5)
    }
}
