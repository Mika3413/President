package fr.president.engine.economy

import fr.president.engine.data.CountryNames
import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.diplomacy.SanctionsService
import fr.president.engine.effects.EffectSpec
import fr.president.engine.military.Geopolitics
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.session.AgendaCost
import fr.president.engine.session.AgendaService
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln

@Serializable
data class CommodityDef(
    val id: String,
    val label: String,
    val unit: String,
    val basePrice: Double,
    /** Volatilité mensuelle du cours. */
    val volatility: Double,
    /** Poids dans le prix de l'énergie en France. */
    val energyWeight: Double = 0.0,
    /** Effet direct d'une hausse sur l'inflation (alimentation, biens importés). */
    val inflationWeight: Double = 0.0,
    /** Secteurs français touchés par une hausse (négatif : elle leur nuit). */
    val sectors: Map<String, Double> = emptyMap(),
    /** Part de la production mondiale des grands producteurs. */
    val producers: Map<String, Double> = emptyMap(),
    /** Stock stratégique de départ, en jours de consommation (0 : pas de stock). */
    val reserveDays: Double = 0.0,
    /** Coût d'un mois de stock au cours de référence (Md€). */
    val refillCostBillions: Double = 0.0,
)

@Serializable
data class SupplyContractDef(val country: String, val commodity: String, val discount: Double, val years: Int, val minRelation: Double)

@Serializable
data class ExportProductDef(
    val id: String,
    val label: String,
    val sector: String,
    val valueBillions: Double,
    val delayDays: Double,
    val clients: List<String>,
)

@Serializable
data class TradeFile(
    val commodities: List<CommodityDef>,
    val eventShocks: Map<String, Map<String, Double>> = emptyMap(),
    val contracts: List<SupplyContractDef> = emptyList(),
    val products: List<ExportProductDef> = emptyList(),
)

@Serializable
class CommodityState(
    var price: Double,
    /** Prix effectivement payé par la France le mois dernier (contrats et stocks compris). */
    var effective: Double,
    var reserveDays: Double = 0.0,
    var releaseUntil: WorldTime? = null,
    val history: MutableList<Double> = mutableListOf(),
)

@Serializable
class ActiveContract(val country: String, val commodity: String, val lockedPrice: Double, val until: WorldTime)

@Serializable
class ExportBid(val product: String, val client: String, val decideAt: WorldTime, val chance: Double, val guaranteed: Boolean)

@Serializable
class Tender(val product: String, val client: String, val until: WorldTime)

@Serializable
class WtoCase(val target: String, val decideAt: WorldTime, val about: String)

@Serializable
data class ExportResult(val product: String, val client: String, val time: WorldTime, val won: Boolean, val valueBillions: Double, val rival: String = "")

@Serializable
class TradeState(
    val commodities: MutableMap<String, CommodityState> = mutableMapOf(),
    /** Tensions sur l'offre au début de la partie : seuls les changements font bouger les cours. */
    val baseline: MutableMap<String, Double> = mutableMapOf(),
    val contracts: MutableList<ActiveContract> = mutableListOf(),
    val bids: MutableList<ExportBid> = mutableListOf(),
    val tenders: MutableList<Tender> = mutableListOf(),
    val results: MutableList<ExportResult> = mutableListOf(),
    /** Dernière offre par produit et pays. */
    val lastBid: MutableMap<String, WorldTime> = mutableMapOf(),
    val wto: MutableList<WtoCase> = mutableListOf(),
    val wtoHistory: MutableList<String> = mutableListOf(),
    var imfUntil: WorldTime? = null,
    var lastImfReport: WorldTime? = null,
    var lastImfContribution: WorldTime? = null,
    var lastWorldBank: WorldTime? = null,
    var worldBankBillions: Double = 0.0,
    var lithiumMine: Boolean = false,
    var shaleGas: Boolean = false,
    /** Effet des matières premières sur le prix de l'énergie (1 = cours de référence). */
    var energyFactor: Double = 1.0,
)

/**
 * Matières premières et commerce extérieur. Chaque mois, les cours mondiaux reviennent vers leur
 * niveau d'équilibre avec du bruit ; une guerre chez un grand producteur ou des sanctions contre lui
 * les font flamber. La France paie un prix effectif adouci par ses contrats à long terme et ses
 * stocks stratégiques : il entre dans le prix de l'énergie, l'inflation et l'activité des secteurs.
 * Les offres d'exportation (avions, centrales, TGV...) se jouent contre la concurrence étrangère ;
 * les plaintes à l'OMC sont tranchées ; le FMI rend son rapport annuel.
 */
class TradeSystem : SimulationSystem {
    override val name = "trade"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val file = ctx.db.trade ?: return
        val t = ctx.state.trade
        if (t.commodities.isEmpty()) init(ctx, file)
        val e = ctx.state.playerCountry.economy
        var energy = 0.0
        for (c in file.commodities) {
            val s = t.commodities.getOrPut(c.id) { CommodityState(c.basePrice, c.basePrice, c.reserveDays) }
            val target = c.basePrice * (1 + SUPPLY_WEIGHT * (disruption(ctx, c) - (t.baseline[c.id] ?: 0.0))).coerceAtLeast(MIN_TARGET)
            s.price = (s.price * exp(REVERSION * ln(target / s.price) + c.volatility * ctx.rng.nextGaussian()))
                .coerceIn(c.basePrice * MIN_PRICE, c.basePrice * MAX_PRICE)
            val before = s.effective
            s.effective = TradeService(ctx).effectivePrice(c)
            val change = s.effective / before - 1
            c.sectors.forEach { (sector, w) -> SectorSystem.shock(ctx, sector, w * change) }
            e.inflation += c.inflationWeight * change
            energy += c.energyWeight * (s.effective / c.basePrice - 1)
            s.history += s.price
            if (s.history.size > HISTORY) s.history.removeAt(0)
            if (change > ALERT) ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.IMPORTANT, "Flambée du cours : ${c.label.lowercase()}",
                "Le prix payé par la France bondit de ${Math.round(change * 100)} % ce mois-ci. Contrats à long terme et stocks stratégiques peuvent amortir le choc (panneau « Commerce »).", null)
        }
        t.energyFactor = (1 + energy).coerceIn(MIN_ENERGY_FACTOR, MAX_ENERGY_FACTOR)
        t.contracts.removeAll { it.until.daysUntil(ctx.now) >= 0 }
        t.tenders.removeAll { it.until.daysUntil(ctx.now) >= 0 }
        resolveBids(ctx, file)
        resolveWto(ctx)
        tender(ctx, file)
        imf(ctx)
    }

    private fun resolveBids(ctx: SimulationContext, file: TradeFile) {
        val t = ctx.state.trade
        val due = t.bids.filter { it.decideAt.daysUntil(ctx.now) >= 0 }
        t.bids.removeAll(due)
        for (b in due) {
            val p = file.products.firstOrNull { it.id == b.product } ?: continue
            val client = CountryNames(ctx.db.country(b.client).definition)
            val won = ctx.rng.nextDouble() < b.chance
            if (won) {
                SectorSystem.shock(ctx, p.sector, WIN_SHOCK * p.valueBillions / BIG_CONTRACT)
                val e = ctx.state.playerCountry.economy
                e.pendingOutputShock += p.valueBillions / e.gdpBillions * OUTPUT_SHARE
                e.tradeBalanceBillions += p.valueBillions / YEARS_OF_DELIVERY
                ctx.state.diplomacy.relation(b.client, ctx.state.player.countryId).memories += DiplomaticMemory("TRADE_PARTNER", WIN_GOODWILL, ctx.now, p.label.lowercase())
                ctx.effects.trigger(EffectSpec("president.popularity", WIN_POPULARITY * p.valueBillions / BIG_CONTRACT), null, emptyMap(), "trade")
                t.results += ExportResult(p.id, b.client, ctx.now, true, p.valueBillions)
                ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.IMPORTANT, "Contrat signé : ${p.label.lowercase()} pour ${client.the}",
                    "${Formatting.billions(p.valueBillions)} de commandes pour l'industrie française, des milliers d'emplois préservés.", b.client)
            } else {
                val rival = ctx.rng.pick(RIVALS.filter { it != b.client && it in ctx.state.countries })
                t.results += ExportResult(p.id, b.client, ctx.now, false, p.valueBillions, rival)
                ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.INFO, "Contrat perdu : ${p.label.lowercase()}",
                    "${client.the.replaceFirstChar { it.uppercase() }} a préféré l'offre ${CountryNames(ctx.db.country(rival).definition).of}.", b.client)
            }
            if (t.results.size > MAX_RESULTS) t.results.removeAt(0)
        }
    }

    private fun resolveWto(ctx: SimulationContext) {
        val t = ctx.state.trade
        val due = t.wto.filter { it.decideAt.daysUntil(ctx.now) >= 0 }
        t.wto.removeAll(due)
        val player = ctx.state.player.countryId
        for (case in due) {
            val name = CountryNames(ctx.db.country(case.target).definition)
            val sanctions = SanctionsService(ctx)
            val won = ctx.rng.nextDouble() < WTO_WIN
            if (won) {
                if (sanctions.isSanctioning(case.target, player)) sanctions.lift(case.target, player)
                else SectorSystem.shock(ctx, "industry", WTO_SHOCK)
                ctx.effects.trigger(EffectSpec("economy.businessConfidence", WTO_CONFIDENCE), null, emptyMap(), "wto")
            }
            val line = "${if (won) "Gagnée" else "Perdue"} contre ${name.the} (${case.about})"
            t.wtoHistory += line
            if (t.wtoHistory.size > MAX_RESULTS) t.wtoHistory.removeAt(0)
            ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.IMPORTANT,
                if (won) "L'OMC donne raison à la France" else "L'OMC déboute la France",
                if (won) "L'organe de règlement des différends condamne ${name.the} : ${case.about}. Les mesures contestées doivent être levées."
                else "L'organe de règlement des différends estime les mesures ${name.of} conformes aux règles du commerce.", case.target)
        }
    }

    /** Un appel d'offres s'ouvre de temps en temps : l'offre française a plus de chances pendant deux mois. */
    private fun tender(ctx: SimulationContext, file: TradeFile) {
        if (file.products.isEmpty() || ctx.rng.nextDouble() > TENDER_CHANCE) return
        val p = ctx.rng.pick(file.products)
        val clients = p.clients.filter { it in ctx.state.countries && TradeService(ctx).bidBlocker(p, it) == null }
        if (clients.isEmpty()) return
        val client = ctx.rng.pick(clients)
        ctx.state.trade.tenders += Tender(p.id, client, ctx.now.plusDays(TENDER_DAYS))
        val name = CountryNames(ctx.db.country(client).definition)
        ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.INFO, "Appel d'offres : ${p.label.lowercase()}",
            "${name.the.replaceFirstChar { it.uppercase() }} lance un appel d'offres (${Formatting.billions(p.valueBillions)}). Une offre française déposée dans les deux mois a plus de chances.", client, journal = false)
    }

    /** Rapport annuel du FMI (article IV) sur l'économie française. */
    private fun imf(ctx: SimulationContext) {
        val t = ctx.state.trade
        val e = ctx.state.playerCountry.economy
        t.imfUntil?.let { if (it.daysUntil(ctx.now) >= 0) { t.imfUntil = null; e.imfRelief = 0.0
            ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.IMPORTANT, "Fin du programme du FMI", "La France se finance de nouveau seule sur les marchés.", null) } }
        if (t.lastImfReport?.let { it.daysUntil(ctx.now) < YEAR } == true) return
        if (ctx.state.trade.lastImfReport == null) { t.lastImfReport = ctx.now; return }
        t.lastImfReport = ctx.now
        val advice = when {
            e.debtRatio > 1.3 || e.marketRate > 0.055 -> "La dette devient difficile à soutenir : le Fonds appelle à un ajustement rapide et crédible."
            e.deficitRatio > 0.05 -> "Le déficit reste trop élevé : le Fonds recommande de le réduire d'un demi-point par an."
            e.realGrowth < 0.005 -> "La croissance est faible : le Fonds conseille de protéger l'investissement public."
            else -> "Le Fonds salue une trajectoire maîtrisée et encourage les réformes de productivité."
        }
        ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.INFO, "Rapport annuel du FMI sur la France",
            "Dette : ${Formatting.percent(e.debtRatio)} du PIB, déficit : ${Formatting.percent(e.deficitRatio)}, croissance : ${Formatting.percent(e.realGrowth)}.\n\n$advice", null)
    }

    companion object {
        private const val SUPPLY_WEIGHT = 0.8
        private const val MIN_TARGET = 0.5
        private const val REVERSION = 0.15
        private const val MIN_PRICE = 0.4
        private const val MAX_PRICE = 3.5
        private const val HISTORY = 120
        private const val ALERT = 0.15
        private const val MIN_ENERGY_FACTOR = 0.7
        private const val MAX_ENERGY_FACTOR = 2.0
        private const val WIN_SHOCK = 0.04
        private const val BIG_CONTRACT = 10.0
        private const val OUTPUT_SHARE = 0.3
        private const val YEARS_OF_DELIVERY = 5.0
        private const val WIN_GOODWILL = 0.06
        private const val WIN_POPULARITY = 0.01
        private const val MAX_RESULTS = 40
        private const val WTO_WIN = 0.6
        private const val WTO_SHOCK = 0.02
        private const val WTO_CONFIDENCE = 0.01
        private const val TENDER_CHANCE = 0.3
        private const val TENDER_DAYS = 60.0
        private const val YEAR = 360.0
        val RIVALS = listOf("USA", "CHN", "DEU", "KOR", "JPN", "RUS", "GBR", "ITA", "ESP", "TUR")

        /** Tension sur l'offre : part de la production chez des producteurs en guerre ou sous sanctions. */
        fun disruption(ctx: SimulationContext, c: CommodityDef): Double {
            val geo = Geopolitics(ctx)
            val player = ctx.state.player.countryId
            val sanctions = SanctionsService(ctx)
            return c.producers.entries.sumOf { (country, share) ->
                when {
                    country !in ctx.state.countries -> 0.0
                    geo.enemiesOf(country).isNotEmpty() -> share * WAR_LOSS
                    sanctions.isSanctioning(player, country) -> share * SANCTION_LOSS
                    else -> 0.0
                }
            }
        }

        fun init(ctx: SimulationContext, file: TradeFile) {
            val t = ctx.state.trade
            file.commodities.forEach { c ->
                t.commodities[c.id] = CommodityState(c.basePrice, c.basePrice, c.reserveDays)
                t.baseline[c.id] = disruption(ctx, c)
            }
        }

        private const val WAR_LOSS = 0.6
        private const val SANCTION_LOSS = 0.4

        /** Les matières premières touchées par un événement (vague de froid : gaz ; sécheresse : blé...). */
        fun onEvent(ctx: SimulationContext, eventId: String, factor: Double) {
            val shocks = ctx.db.trade?.eventShocks?.get(eventId) ?: return
            shocks.forEach { (id, delta) -> ctx.state.trade.commodities[id]?.let { it.price *= 1 + delta * factor } }
        }
    }
}

/** Les leviers du président sur le commerce, les matières premières et les institutions. */
class TradeService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.trade
    private val t: TradeState get() = ctx.state.trade.also { if (it.commodities.isEmpty()) file?.let { f -> TradeSystem.init(ctx, f) } }
    private val player get() = ctx.state.player.countryId
    private val agenda = AgendaService(ctx)

    val available: Boolean get() = file != null

    fun commodity(id: String) = file?.commodities?.firstOrNull { it.id == id }
    fun product(id: String) = file?.products?.firstOrNull { it.id == id }
    fun state(id: String) = t.commodities[id]

    /** Part de la consommation couverte par un contrat à long terme. */
    fun cover(id: String): Double = (t.contracts.count { it.commodity == id } * CONTRACT_COVER).coerceAtMost(MAX_COVER) +
        (if (id == "lithium" && t.lithiumMine) MINE_COVER else 0.0) + (if (id == "gas" && t.shaleGas) SHALE_COVER else 0.0)

    /** Prix réellement payé : contrats au prix bloqué, production nationale au coût, stocks débloqués. */
    fun effectivePrice(c: CommodityDef): Double {
        val s = t.commodities[c.id] ?: return c.basePrice
        val contracts = t.contracts.filter { it.commodity == c.id }
        val locked = contracts.map { it.lockedPrice }.average().takeIf { contracts.isNotEmpty() } ?: s.price
        val contractShare = (contracts.size * CONTRACT_COVER).coerceAtMost(MAX_COVER)
        val home = cover(c.id) - contractShare
        var price = contractShare * locked + home * c.basePrice * HOME_COST + (1 - contractShare - home) * s.price
        if (s.releaseUntil?.let { ctx.now.daysUntil(it) > 0 } == true) price *= RELEASE_DISCOUNT
        return price
    }

    // ---- Stocks stratégiques ----

    fun releaseBlocker(c: CommodityDef): String? {
        val s = t.commodities[c.id] ?: return "Pas de stock."
        return when {
            c.reserveDays <= 0 -> "Pas de stock stratégique pour cette matière."
            s.releaseUntil?.let { ctx.now.daysUntil(it) > 0 } == true -> "Des stocks sont déjà en cours de déblocage."
            s.reserveDays < RELEASE_DAYS -> "Stocks trop bas (${s.reserveDays.toInt()} jours)."
            else -> null
        }
    }

    /** Débloquer un mois de stocks : le prix payé baisse pendant deux mois. */
    fun release(id: String): Result<String> = runCatching {
        val c = commodity(id)!!
        releaseBlocker(c)?.let { error(it) }
        val s = t.commodities.getValue(id)
        s.reserveDays -= RELEASE_DAYS
        s.releaseUntil = ctx.now.plusDays(RELEASE_EFFECT_DAYS)
        s.effective = effectivePrice(c)
        JournalService(ctx).add("Économie", "Déblocage des stocks stratégiques : ${c.label.lowercase()}", Tone.NEUTRAL)
        "Stocks débloqués : ${c.label.lowercase()} moins cher pendant deux mois. Il reste ${s.reserveDays.toInt()} jours de réserve."
    }

    fun refillCost(c: CommodityDef): Double = c.refillCostBillions * ((t.commodities[c.id]?.price ?: c.basePrice) / c.basePrice)

    fun refillBlocker(c: CommodityDef): String? {
        val s = t.commodities[c.id] ?: return "Pas de stock."
        return when {
            c.reserveDays <= 0 -> "Pas de stock stratégique pour cette matière."
            s.reserveDays + RELEASE_DAYS > c.reserveDays * MAX_RESERVE -> "Les capacités de stockage sont pleines."
            else -> null
        }
    }

    /** Racheter un mois de stocks au cours du jour (mieux vaut le faire quand il est bas). */
    fun refill(id: String): Result<String> = runCatching {
        val c = commodity(id)!!
        refillBlocker(c)?.let { error(it) }
        val cost = refillCost(c)
        t.commodities.getValue(id).reserveDays += RELEASE_DAYS
        ctx.effects.trigger(EffectSpec("budget.oneOff", cost), null, emptyMap(), "trade")
        "Stocks reconstitués (${Formatting.billions(cost)})."
    }

    // ---- Contrats d'approvisionnement ----

    data class ContractOffer(val def: SupplyContractDef, val name: String, val blocker: String?, val active: Boolean)

    fun contracts(): List<ContractOffer> = file?.contracts.orEmpty().filter { it.country in ctx.state.countries }.map { d ->
        ContractOffer(d, ctx.db.country(d.country).definition.name, contractBlocker(d), t.contracts.any { it.country == d.country && it.commodity == d.commodity })
    }

    fun contractBlocker(d: SupplyContractDef): String? {
        val relation = RelationCalculator(ctx).score(d.country, player)
        return when {
            t.contracts.any { it.country == d.country && it.commodity == d.commodity } -> "Contrat en cours."
            SanctionsService(ctx).isSanctioning(player, d.country) || SanctionsService(ctx).isSanctioning(d.country, player) -> "Sanctions en vigueur."
            Geopolitics(ctx).atWar(player, d.country) -> "Nous sommes en guerre."
            relation < d.minRelation -> "Relation insuffisante avec ce pays."
            else -> agenda.blocker(CONTRACT_AGENDA)
        }
    }

    /** Signer un contrat à long terme : une part de la consommation à prix bloqué (cours du jour moins le rabais). */
    fun signContract(country: String, commodity: String): Result<String> = runCatching {
        val d = file!!.contracts.first { it.country == country && it.commodity == commodity }
        contractBlocker(d)?.let { error(it) }
        val c = commodity(commodity)!!
        val s = t.commodities.getValue(commodity)
        val locked = minOf(s.price, c.basePrice * LOCK_CEILING) * (1 - d.discount)
        t.contracts += ActiveContract(country, commodity, locked, ctx.now.plusDays(d.years * YEAR))
        agenda.book("Signature d'un contrat d'approvisionnement", CONTRACT_AGENDA)
        ctx.state.diplomacy.relation(country, player).memories += DiplomaticMemory("TRADE_PARTNER", CONTRACT_GOODWILL, ctx.now, c.label.lowercase())
        s.effective = effectivePrice(c)
        val name = CountryNames(ctx.db.country(country).definition)
        JournalService(ctx).add("Économie", "Contrat d'approvisionnement avec ${name.the} : ${c.label.lowercase()}", Tone.GOOD)
        "Contrat signé pour ${d.years} ans : ${Math.round(CONTRACT_COVER * 100)} % de nos besoins à ${locked.toInt()} ${c.unit}."
    }

    // ---- Ressources nationales ----

    fun mineBlocker(): String? = if (t.lithiumMine) "Mine déjà autorisée." else null
    fun shaleBlocker(): String? = when {
        t.shaleGas -> "Exploitation déjà autorisée."
        fr.president.engine.government.LawService(ctx).flag("shaleBan") == 0.0 -> "Une loi interdit la fracturation hydraulique."
        else -> null
    }

    /** Autoriser la mine de lithium de l'Allier : souveraineté pour les batteries, colère écologiste. */
    fun authorizeMine(): Result<String> = runCatching {
        mineBlocker()?.let { error(it) }
        t.lithiumMine = true
        ctx.effects.trigger(EffectSpec("budget.oneOff", MINE_COST), null, emptyMap(), "trade")
        ctx.effects.trigger(EffectSpec("sector.industry", 0.02, delayDays = 365.0), null, emptyMap(), "trade")
        ctx.effects.trigger(EffectSpec("opinion.group.young", -0.02), null, emptyMap(), "trade")
        ctx.effects.trigger(EffectSpec("economy.businessConfidence", 0.005), null, emptyMap(), "trade")
        JournalService(ctx).add("Économie", "Mine de lithium autorisée dans l'Allier", Tone.NEUTRAL)
        "Mine autorisée : ${Math.round(MINE_COVER * 100)} % de nos besoins en lithium produits en France."
    }

    /** Autoriser le gaz de schiste : moins de dépendance, levée de boucliers. */
    fun authorizeShale(): Result<String> = runCatching {
        shaleBlocker()?.let { error(it) }
        t.shaleGas = true
        ctx.effects.trigger(EffectSpec("opinion.group.young", -0.04), null, emptyMap(), "trade")
        ctx.effects.trigger(EffectSpec("opinion.national", -0.02), null, emptyMap(), "trade")
        ctx.effects.trigger(EffectSpec("quality.environment", -0.03), null, emptyMap(), "trade")
        ctx.effects.trigger(EffectSpec("chain.protest", 0.3), null, emptyMap(), "trade")
        JournalService(ctx).add("Économie", "Exploitation du gaz de schiste autorisée", Tone.WARNING)
        "Gaz de schiste autorisé : ${Math.round(SHALE_COVER * 100)} % de notre gaz produit en France, mais l'opinion gronde."
    }

    // ---- Exportations ----

    data class BidOption(val product: ExportProductDef, val client: String, val clientName: String, val chance: Double, val blocker: String?, val tender: Boolean)

    fun bidBlocker(p: ExportProductDef, client: String): String? {
        val sanctions = SanctionsService(ctx)
        return when {
            t.bids.any { it.product == p.id && it.client == client } -> "Offre en cours d'examen."
            sanctions.isSanctioning(player, client) || sanctions.isSanctioning(client, player) -> "Sanctions en vigueur."
            Geopolitics(ctx).atWar(player, client) -> "Nous sommes en guerre."
            t.lastBid["${p.id}:$client"]?.let { it.daysUntil(ctx.now) < BID_COOLDOWN } == true -> "Ce client a déjà reçu une offre récemment."
            else -> null
        }
    }

    /** Chance de l'emporter : relation, change de l'euro, appel d'offres en cours, garantie de l'État. */
    fun chance(p: ExportProductDef, client: String, guaranteed: Boolean = false): Double {
        val relation = RelationCalculator(ctx).score(client, player)
        val fx = MonetarySystemFx.REFERENCE / ctx.state.monetary.eurUsd - 1
        val tender = if (t.tenders.any { it.product == p.id && it.client == client }) TENDER_BONUS else 0.0
        val guarantee = if (guaranteed) GUARANTEE_BONUS else 0.0
        return (BASE_CHANCE + RELATION_WEIGHT * (relation - 0.5) + FX_WEIGHT * fx + tender + guarantee).coerceIn(MIN_CHANCE, MAX_CHANCE)
    }

    fun bids(product: ExportProductDef): List<BidOption> = product.clients.filter { it in ctx.state.countries }.map { c ->
        BidOption(product, c, ctx.db.country(c).definition.name, chance(product, c), bidBlocker(product, c) ?: agenda.blocker(BID_AGENDA),
            t.tenders.any { it.product == product.id && it.client == c })
    }.sortedByDescending { it.chance }

    fun guaranteeCost(p: ExportProductDef) = p.valueBillions * GUARANTEE_COST

    /** Déposer une offre (le président se déplace) ; la garantie de l'État la rend plus compétitive. */
    fun bid(productId: String, client: String, guaranteed: Boolean): Result<String> = runCatching {
        val p = product(productId)!!
        (bidBlocker(p, client) ?: agenda.blocker(BID_AGENDA))?.let { error(it) }
        val chance = chance(p, client, guaranteed)
        t.bids += ExportBid(p.id, client, ctx.now.plusDays(p.delayDays), chance, guaranteed)
        t.lastBid["${p.id}:$client"] = ctx.now
        t.tenders.removeAll { it.product == p.id && it.client == client }
        val name = CountryNames(ctx.db.country(client).definition)
        agenda.book("Déplacement commercial ${name.inside}", BID_AGENDA.copy(abroad = true, place = client))
        if (guaranteed) ctx.effects.trigger(EffectSpec("budget.oneOff", guaranteeCost(p)), null, emptyMap(), "trade")
        JournalService(ctx).add("Économie", "Offre française ${name.inside} : ${p.label.lowercase()}", Tone.NEUTRAL)
        "Offre déposée : décision ${name.of} dans ${p.delayDays.toInt()} jours (chances : ${Math.round(chance * 100)} %)."
    }

    // ---- Institutions ----

    data class WtoTarget(val country: String, val name: String, val about: String, val blocker: String?)

    fun wtoTargets(): List<WtoTarget> {
        val sanctioning = ctx.state.diplomacy.sanctions.filter { it.target == player }.map { it.by to "sanctions commerciales contre la France" }
        val subsidies = listOf("CHN" to "acier et véhicules électriques vendus à perte", "USA" to "subventions massives à l'industrie verte")
        return (sanctioning + subsidies).distinctBy { it.first }.filter { it.first in ctx.state.countries }.map { (c, about) ->
            WtoTarget(c, ctx.db.country(c).definition.name, about, if (t.wto.any { it.target == c }) "Plainte en cours." else null)
        }
    }

    /** Porter plainte à l'OMC : décision dans six mois, la cible le prend mal. */
    fun fileWto(country: String): Result<String> = runCatching {
        val target = wtoTargets().first { it.country == country }
        target.blocker?.let { error(it) }
        t.wto += WtoCase(country, ctx.now.plusDays(WTO_DAYS), target.about)
        ctx.state.diplomacy.relation(country, player).memories += DiplomaticMemory("DISAGREEMENT", -WTO_ANGER, ctx.now, "plainte à l'OMC")
        JournalService(ctx).add("Diplomatie", "Plainte à l'OMC contre ${CountryNames(ctx.db.country(country).definition).the}", Tone.NEUTRAL)
        "Plainte déposée à Genève : décision dans six mois environ."
    }

    val imfActive: Boolean get() = t.imfUntil?.let { ctx.now.daysUntil(it) > 0 } == true

    fun imfBlocker(): String? {
        val e = ctx.state.playerCountry.economy
        return when {
            imfActive -> "Un programme est déjà en cours."
            e.marketRate < IMF_RATE && e.debtRatio < IMF_DEBT -> "La France se finance encore sur les marchés : le FMI n'intervient qu'en cas de crise de la dette (taux > ${Formatting.percent(IMF_RATE)})."
            else -> null
        }
    }

    /** Appeler le FMI : taux allégés pendant trois ans, en échange d'une cure d'austérité impopulaire. */
    fun requestImf(): Result<String> = runCatching {
        imfBlocker()?.let { error(it) }
        val e = ctx.state.playerCountry.economy
        t.imfUntil = ctx.now.plusDays(IMF_YEARS * YEAR)
        e.imfRelief = IMF_RELIEF
        e.fiscalAdjustmentBillions += IMF_ADJUSTMENT * e.reference.gdpBillions
        ctx.effects.trigger(EffectSpec("president.popularity", -0.06), null, emptyMap(), "imf")
        ctx.effects.trigger(EffectSpec("opinion.national", -0.05), null, emptyMap(), "imf")
        ctx.effects.trigger(EffectSpec("opinion.group.civil_servants", -0.06), null, emptyMap(), "imf")
        ctx.effects.trigger(EffectSpec("economy.consumerConfidence", -0.03), null, emptyMap(), "imf")
        ctx.effects.trigger(EffectSpec("chain.national_strike", 0.4), null, emptyMap(), "imf")
        ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.URGENT, "La France fait appel au FMI",
            "Un programme de trois ans allège le coût de la dette ; en contrepartie, ${Formatting.percent(IMF_ADJUSTMENT)} du PIB d'économies et de hausses d'impôts.", null)
        "Programme du FMI signé : taux allégés de ${Formatting.percent(IMF_RELIEF)} pendant trois ans."
    }

    fun imfContributionBlocker(): String? = if (t.lastImfContribution?.let { it.daysUntil(ctx.now) < YEAR } == true) "Déjà fait cette année." else null

    /** Prêter des droits de tirage spéciaux au FMI pour les pays pauvres : gratitude du Sud. */
    fun contributeImf(): Result<String> = runCatching {
        imfContributionBlocker()?.let { error(it) }
        t.lastImfContribution = ctx.now
        ctx.effects.trigger(EffectSpec("budget.oneOff", IMF_CONTRIBUTION), null, emptyMap(), "imf")
        south().forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("AID", SOUTH_GOODWILL, ctx.now, "soutien via le FMI") }
        "${Formatting.billions(IMF_CONTRIBUTION)} prêtés au fonds du FMI pour les pays pauvres."
    }

    fun worldBankBlocker(): String? = if (t.lastWorldBank?.let { it.daysUntil(ctx.now) < YEAR } == true) "Déjà fait cette année." else null

    /** Abonder l'Association internationale de développement de la Banque mondiale. */
    fun contributeWorldBank(): Result<String> = runCatching {
        worldBankBlocker()?.let { error(it) }
        t.lastWorldBank = ctx.now
        t.worldBankBillions += WORLD_BANK
        ctx.effects.trigger(EffectSpec("budget.oneOff", WORLD_BANK), null, emptyMap(), "worldbank")
        south().forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("AID", SOUTH_GOODWILL * 1.5, ctx.now, "aide au développement") }
        ctx.effects.trigger(EffectSpec("opinion.group.young", 0.005), null, emptyMap(), "worldbank")
        "${Formatting.billions(WORLD_BANK)} versés à la Banque mondiale : les pays du Sud apprécient."
    }

    /** Pays en développement simulés : hors UE, OTAN et G7, PIB par habitant modeste. */
    fun south(): List<String> {
        val rich = ctx.db.alliances.filter { it.id == "EU" || it.id == "NATO" }.flatMap { it.members }.toSet() + setOf("JPN", "KOR", "AUS", "CHN", "RUS", "SAU", "ARE", "ISR", "CHE")
        return ctx.state.countries.keys.filter { it !in rich && it != player }
    }

    private object MonetarySystemFx { const val REFERENCE = 1.16 }

    companion object {
        const val CONTRACT_COVER = 0.3
        private const val MAX_COVER = 0.6
        private const val MINE_COVER = 0.4
        private const val SHALE_COVER = 0.25
        private const val HOME_COST = 0.9
        private const val RELEASE_DAYS = 30.0
        private const val RELEASE_EFFECT_DAYS = 60.0
        private const val RELEASE_DISCOUNT = 0.8
        private const val MAX_RESERVE = 1.5
        private const val LOCK_CEILING = 1.3
        private const val YEAR = 365.0
        private const val CONTRACT_GOODWILL = 0.05
        private const val MINE_COST = 1.0
        private const val BID_COOLDOWN = 180.0
        private const val BASE_CHANCE = 0.4
        private const val RELATION_WEIGHT = 0.8
        private const val FX_WEIGHT = 1.0
        private const val TENDER_BONUS = 0.15
        private const val GUARANTEE_BONUS = 0.15
        private const val GUARANTEE_COST = 0.03
        private const val MIN_CHANCE = 0.05
        private const val MAX_CHANCE = 0.9
        private const val WTO_DAYS = 180.0
        private const val WTO_ANGER = 0.08
        private const val IMF_RATE = 0.06
        private const val IMF_DEBT = 1.6
        private const val IMF_RELIEF = 0.02
        private const val IMF_YEARS = 3
        private const val IMF_ADJUSTMENT = 0.015
        private const val IMF_CONTRIBUTION = 2.0
        private const val WORLD_BANK = 1.5
        private const val SOUTH_GOODWILL = 0.03
        val CONTRACT_AGENDA = AgendaCost(1.0)
        val BID_AGENDA = AgendaCost(2.0)
    }
}
