package fr.president.engine.military

import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.effects.EffectSpec
import fr.president.engine.economy.SectorSystem
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.session.AgendaCost
import fr.president.engine.session.AgendaService
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting
import kotlinx.serialization.Serializable

@Serializable
data class DefenseCategory(val id: String, val label: String, val icon: String = "")

@Serializable
data class EquipmentDef(
    val id: String,
    val category: String,
    val label: String,
    val description: String = "",
    val costBillions: Double,
    val days: Int,
    /** Unité livrée à la fin de la commande (sinon : seulement des capacités). */
    val unitType: String? = null,
    val capabilities: Map<String, Double> = emptyMap(),
    /** Fournisseur étranger (sinon : industrie française). */
    val supplier: String? = null,
)

@Serializable
data class BaseDef(
    val id: String,
    val label: String,
    /** Pays hôte s'il est simulé. */
    val host: String? = null,
    val hostName: String,
    val annualCostBillions: Double,
    val open: Boolean,
    /** Chance mensuelle que l'hôte demande le départ des troupes françaises. */
    val evictionRisk: Double = 0.0,
    val influence: List<String> = emptyList(),
    val worries: List<String> = emptyList(),
    val openCostBillions: Double = 0.3,
)

@Serializable
data class DefenseFile(
    val categories: List<DefenseCategory> = emptyList(),
    val equipment: List<EquipmentDef> = emptyList(),
    val capabilities: Map<String, Double> = emptyMap(),
    val capabilityLabels: Map<String, String> = emptyMap(),
    val bases: List<BaseDef> = emptyList(),
    val warheads: Int = 290,
    val credibility: Double = 0.7,
)

@Serializable
class EquipmentOrder(val equipment: String, val readyAt: WorldTime)

@Serializable
enum class NuclearPosture(val label: String, val description: String) {
    NORMAL("Posture permanente", "Un sous-marin lanceur d'engins toujours en mer, les Forces aériennes stratégiques prêtes."),
    REINFORCED("Posture renforcée", "Deux ou trois sous-marins en mer, avions en alerte : un signal clair, coûteux et anxiogène."),
}

@Serializable
class DefenseState(
    val capabilities: MutableMap<String, Double> = mutableMapOf(),
    val orders: MutableList<EquipmentOrder> = mutableListOf(),
    /** Bases ouvertes (identifiants). */
    val bases: MutableSet<String> = mutableSetOf(),
    var basesInitialized: Boolean = false,
    val lastBaseTalks: MutableMap<String, WorldTime> = mutableMapOf(),
    var warheads: Int = 290,
    var credibility: Double = 0.7,
    var posture: NuclearPosture = NuclearPosture.NORMAL,
    var europeanUmbrella: Boolean = false,
    var modernizationUntil: WorldTime? = null,
    var lastTest: WorldTime? = null,
    var lastWarning: WorldTime? = null,
    var lastReduction: WorldTime? = null,
    /** Échelle d'escalade nucléaire (0 : calme … 4 : échange). */
    var escalation: Int = 0,
    /** Puissance qui a employé l'arme nucléaire contre nous (décision en attente). */
    var nuclearAttackBy: String? = null,
    var nuclearAttackAt: WorldTime? = null,
    var decisionTaken: Boolean? = null,
    var lastNuclearStrike: WorldTime? = null,
)

/**
 * Défense : livraisons des commandes d'armement, coût et influence des bases à l'étranger (et
 * demandes de départ des pays hôtes), usure de la crédibilité de la dissuasion, coût de la posture
 * nucléaire renforcée, production des usines de munitions.
 */
class DefenseSystem : SimulationSystem {
    override val name = "defense"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val file = ctx.db.defense ?: return
        val service = DefenseService(ctx)
        val d = service.state
        val e = ctx.state.playerCountry.economy
        // Livraisons des équipements qui renforcent une capacité.
        val due = d.orders.filter { it.readyAt.daysUntil(ctx.now) >= 0 }
        d.orders.removeAll(due)
        due.forEach { o ->
            val def = file.equipment.firstOrNull { it.id == o.equipment } ?: return@forEach
            def.capabilities.forEach { (k, v) -> d.capabilities[k] = ((d.capabilities[k] ?: 0.0) + v).coerceAtMost(1.0) }
            ctx.notifications.post(NotificationCategory.MILITARY, Urgency.INFO, "Livraison : ${def.label}", "Les armées réceptionnent la commande.", null, journal = false)
        }
        // Les usines de munitions remplissent les stocks.
        val ammo = d.capabilities["ammo"] ?: 0.0
        ctx.state.military.stocks.ammunition = (ctx.state.military.stocks.ammunition + ammo * AMMO_PER_MONTH).coerceAtMost(1.0)
        // Bases : coût, influence chaque année, éviction possible.
        for (b in file.bases.filter { it.id in d.bases }) {
            e.pendingOneOffBillions += b.annualCostBillions / MONTHS
            if (ctx.state.time.month == 1) b.influence.filter { it in ctx.state.countries }.forEach {
                ctx.state.diplomacy.relation(it, ctx.state.player.countryId).memories += DiplomaticMemory("MILITARY_SUPPORT", BASE_INFLUENCE, ctx.now, "présence militaire française")
            }
            val hostRelation = b.host?.takeIf { it in ctx.state.countries }?.let { RelationCalculator(ctx).score(it, ctx.state.player.countryId) }
            val risk = b.evictionRisk + if (hostRelation != null && hostRelation < HOST_MIN) EVICTION_HOSTILE else 0.0
            if (risk > 0 && ctx.rng.chance(risk)) {
                d.bases -= b.id
                ctx.effects.trigger(EffectSpec("opinion.national", -0.005), null, emptyMap(), "base:${b.id}")
                ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, "${b.hostName} demande le départ des troupes françaises",
                    "Sous la pression de la rue et de rivaux étrangers, les autorités exigent la fermeture : ${b.label.lowercase()}. Nos militaires rentrent.", null)
            }
        }
        // Dissuasion : la crédibilité s'use sans investissement ; la posture renforcée coûte.
        if (d.modernizationUntil?.let { ctx.now.daysUntil(it) > 0 } != true) d.credibility = (d.credibility - CREDIBILITY_DECAY).coerceAtLeast(MIN_CREDIBILITY)
        if (d.posture == NuclearPosture.REINFORCED) e.pendingOneOffBillions += REINFORCED_COST
    }

    companion object {
        private const val MONTHS = 12.0
        private const val AMMO_PER_MONTH = 0.05
        private const val BASE_INFLUENCE = 0.03
        private const val HOST_MIN = 0.3
        private const val EVICTION_HOSTILE = 0.02
        private const val CREDIBILITY_DECAY = 0.002
        private const val MIN_CREDIBILITY = 0.3
        private const val REINFORCED_COST = 0.05
    }
}

/** Commandes d'armement, bases à l'étranger, dissuasion nucléaire. */
class DefenseService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.defense
    private val player get() = ctx.state.player.countryId
    private val agenda = AgendaService(ctx)

    val available: Boolean get() = file != null

    val state: DefenseState get() = ctx.state.defense.also { d ->
        val f = file ?: return@also
        if (d.capabilities.isEmpty()) d.capabilities.putAll(f.capabilities)
        if (!d.basesInitialized) {
            d.basesInitialized = true
            d.bases += f.bases.filter { it.open }.map { it.id }
            d.warheads = f.warheads
            d.credibility = f.credibility
        }
    }

    fun capability(id: String): Double = state.capabilities[id] ?: 0.0

    // ---- Armement ----

    fun equipment(id: String) = file?.equipment?.firstOrNull { it.id == id }

    fun pending(id: String): Int = state.orders.count { it.equipment == id } +
        ctx.state.military.production.count { it.id.startsWith("$ORDER_PREFIX$id|") }

    fun orderBlocker(def: EquipmentDef): String? {
        if (pending(def.id) >= MAX_PENDING) return "Commande déjà en cours."
        def.supplier?.let { s ->
            if (fr.president.engine.diplomacy.SanctionsService(ctx).isSanctioning(s, player)) return "Le fournisseur nous sanctionne."
            if (RelationCalculator(ctx).score(s, player) < SUPPLIER_MIN) return "Le fournisseur refuse de nous vendre."
        }
        return null
    }

    /** Commander : paiement étalé sur la durée de fabrication, commandes pour l'industrie française. */
    fun order(id: String): Result<String> = runCatching {
        val def = equipment(id)!!
        orderBlocker(def)?.let { error(it) }
        val readyAt = ctx.now.plusDays(def.days.toLong())
        ctx.effects.trigger(EffectSpec("budget.oneOff", def.costBillions, days = def.days.toDouble()), null, emptyMap(), "defense:$id")
        if (def.unitType != null) {
            val order = ProductionOrder("$ORDER_PREFIX$id|${ctx.state.newId("prod")}", def.unitType, readyAt, def.costBillions)
            ctx.state.military.production += order
            ctx.scheduler.schedule(ScheduledAction.UnitDelivery(readyAt, order.id))
            if (def.capabilities.isNotEmpty()) state.orders += EquipmentOrder(id, readyAt)
        } else state.orders += EquipmentOrder(id, readyAt)
        if (def.supplier == null) {
            SectorSystem.shock(ctx, "aerospace", def.costBillions * INDUSTRY_SHOCK)
            ctx.effects.trigger(EffectSpec("economy.output", def.costBillions / ctx.state.playerCountry.economy.gdpBillions * INDUSTRY_MULTIPLIER, days = def.days.toDouble()), null, emptyMap(), "defense:$id")
        } else {
            ctx.state.diplomacy.relation(def.supplier, player).memories += DiplomaticMemory("MILITARY_SUPPORT", SUPPLIER_GOODWILL, ctx.now, "achat d'armement")
            ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", -EU_ANNOYANCE), null, emptyMap(), "defense:$id")
        }
        JournalService(ctx).add("Défense", "Commande : ${def.label}", Tone.NEUTRAL)
        "Commande passée : ${def.label} (${Formatting.billions(def.costBillions)}), livraison dans ${def.days / DAYS_PER_MONTH} mois."
    }

    // ---- Bases ----

    data class BaseRow(val def: BaseDef, val open: Boolean, val blocker: String?)

    fun bases(): List<BaseRow> = file?.bases.orEmpty().map { b -> BaseRow(b, b.id in state.bases, if (b.id in state.bases) null else openBlocker(b)) }

    fun openBlocker(b: BaseDef): String? {
        if (b.id in state.bases) return "Base déjà ouverte."
        state.lastBaseTalks[b.id]?.let { if (it.daysUntil(ctx.now) < TALKS_COOLDOWN) return "Négociations rompues récemment." }
        b.host?.takeIf { it in ctx.state.countries }?.let { h ->
            if (RelationCalculator(ctx).score(h, player) < HOST_RELATION) return "Relation insuffisante avec ${b.hostName}."
        }
        return agenda.blocker(BASE_AGENDA)
    }

    /** Négocier l'ouverture : acceptée si l'hôte est simulé et ami ; sinon une chance d'aboutir. */
    fun openBase(id: String): Result<String> = runCatching {
        val b = file!!.bases.first { it.id == id }
        openBlocker(b)?.let { error(it) }
        state.lastBaseTalks[id] = ctx.now
        agenda.book("Négociations : ${b.label}", BASE_AGENDA)
        val accepted = b.host?.takeIf { it in ctx.state.countries } != null || ctx.rng.chance(OPEN_CHANCE - b.evictionRisk * EVICTION_WEIGHT)
        if (!accepted) return@runCatching "${b.hostName} refuse : l'opinion locale ne veut plus de soldats français. Nouvel essai possible dans un an."
        state.bases += id
        ctx.effects.trigger(EffectSpec("budget.oneOff", b.openCostBillions), null, emptyMap(), "base:$id")
        b.influence.filter { it in ctx.state.countries }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("MILITARY_SUPPORT", OPEN_INFLUENCE, ctx.now, "base française") }
        b.worries.filter { it in ctx.state.countries }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("THREAT", -OPEN_WORRY, ctx.now, "base française à nos portes") }
        JournalService(ctx).add("Défense", "Ouverture : ${b.label}", Tone.NEUTRAL)
        "Accord signé : ${b.label}. Coût : ${Formatting.billions(b.annualCostBillions)} par an."
    }

    /** Fermer une base : économies, perte d'influence dans la région. */
    fun closeBase(id: String): Result<String> = runCatching {
        val b = file!!.bases.first { it.id == id }
        require(id in state.bases) { "Base déjà fermée." }
        state.bases -= id
        b.influence.filter { it in ctx.state.countries }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("MILITARY_SUPPORT", -OPEN_INFLUENCE, ctx.now, "départ des troupes françaises") }
        JournalService(ctx).add("Défense", "Fermeture : ${b.label}", Tone.NEUTRAL)
        "Base fermée : ${Formatting.billions(b.annualCostBillions)} économisés chaque année."
    }

    /** Une base à proximité aide à vendre des armes et à peser sur un pays. */
    fun influenceBonus(country: String): Double =
        if (file?.bases.orEmpty().any { it.id in state.bases && country in it.influence }) BASE_EXPORT_BONUS else 0.0

    // ---- Dissuasion ----

    val modernizing: Boolean get() = state.modernizationUntil?.let { ctx.now.daysUntil(it) > 0 } == true

    fun modernizeBlocker(): String? = if (modernizing) "Programme de modernisation en cours." else null

    /** Moderniser la dissuasion (missiles M51, ASN4G, sous-marins de 3e génération). */
    fun modernize(): Result<String> = runCatching {
        modernizeBlocker()?.let { error(it) }
        val d = state
        d.modernizationUntil = ctx.now.plusDays(YEAR)
        d.credibility = (d.credibility + MODERNIZATION_GAIN).coerceAtMost(1.0)
        ctx.effects.trigger(EffectSpec("budget.oneOff", MODERNIZATION_COST, days = YEAR), null, emptyMap(), "nuclear")
        SectorSystem.shock(ctx, "aerospace", 0.01)
        JournalService(ctx).add("Défense", "Programme de modernisation de la dissuasion", Tone.NEUTRAL)
        "Modernisation lancée : ${Formatting.billions(MODERNIZATION_COST)} sur un an, crédibilité renforcée."
    }

    /** Proposer aux Européens une dimension européenne de la dissuasion française. */
    fun setUmbrella(on: Boolean): Result<String> = runCatching {
        require(state.europeanUmbrella != on) { "Déjà fait." }
        agenda.blocker(UMBRELLA_AGENDA)?.let { error(it) }
        state.europeanUmbrella = on
        agenda.book(if (on) "Discours sur la dissuasion européenne" else "Discours sur la souveraineté de la dissuasion", UMBRELLA_AGENDA)
        val sign = if (on) 1.0 else -1.0
        ctx.effects.trigger(EffectSpec("alliance.EU.EU_PARTNERSHIP", UMBRELLA_EU * sign), null, emptyMap(), "nuclear")
        listOf("DEU", "POL").filter { it in ctx.state.countries }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("MILITARY_SUPPORT", UMBRELLA_EU * sign, ctx.now, "dissuasion européenne") }
        if (on) listOf("RUS", "BLR").filter { it in ctx.state.countries }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("THREAT", -UMBRELLA_THREAT, ctx.now, "parapluie nucléaire français") }
        ctx.effects.trigger(EffectSpec("opinion.national", if (on) -0.008 else 0.004), null, emptyMap(), "nuclear")
        JournalService(ctx).add("Défense", if (on) "La dissuasion française s'étend à l'Europe" else "Fin de la dimension européenne de la dissuasion", Tone.NEUTRAL)
        if (on) "Les partenaires européens saluent un tournant historique ; Moscou dénonce une provocation." else "La dissuasion redevient strictement nationale."
    }

    fun setPosture(p: NuclearPosture): Result<String> = runCatching {
        require(state.posture != p) { "Déjà en vigueur." }
        state.posture = p
        if (p == NuclearPosture.REINFORCED) state.escalation = maxOf(state.escalation, 1)
        if (p == NuclearPosture.REINFORCED) {
            Geopolitics(ctx).enemiesOf(player).plus(listOf("RUS")).distinct().filter { it in ctx.state.countries }
                .forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("WARNING", -POSTURE_WARNING, ctx.now, "posture nucléaire renforcée") }
            ctx.effects.trigger(EffectSpec("opinion.national", -0.006), null, emptyMap(), "nuclear")
            ctx.notifications.news(NotificationCategory.MILITARY, "La France renforce sa posture de dissuasion")
        }
        JournalService(ctx).add("Défense", "Dissuasion : ${p.label.lowercase()}", Tone.NEUTRAL)
        "${p.label} : ${p.description}"
    }

    fun testBlocker(): String? = state.lastTest?.let { if (it.daysUntil(ctx.now) < TEST_COOLDOWN) "Un essai a déjà eu lieu récemment." else null }

    /** Essai nucléaire dans le Pacifique : la France viole le traité d'interdiction, tollé mondial. */
    fun nuclearTest(): Result<String> = runCatching {
        testBlocker()?.let { error(it) }
        state.lastTest = ctx.now
        state.escalation = maxOf(state.escalation, 1)
        state.credibility = (state.credibility + TEST_GAIN).coerceAtMost(1.0)
        ctx.state.countries.keys.filter { it != player }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("CONDEMNATION", -TEST_OUTRAGE, ctx.now, "essai nucléaire") }
        ctx.effects.trigger(EffectSpec("opinion.national", -0.03), null, emptyMap(), "nuclear")
        ctx.effects.trigger(EffectSpec("opinion.group.young", -0.03), null, emptyMap(), "nuclear")
        ctx.effects.trigger(EffectSpec("president.popularity", -0.03), null, emptyMap(), "nuclear")
        ctx.effects.trigger(EffectSpec("chain.protest", 0.5), null, emptyMap(), "nuclear")
        ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.URGENT, "Essai nucléaire français : tollé mondial",
            "La France rompt le moratoire de 1996. Condamnations dans le monde entier, manifestations à Papeete et à Paris.", null)
        "Essai réalisé. La dissuasion gagne en crédibilité ; la France perd des amis."
    }

    fun reductionBlocker(): String? = when {
        state.warheads - REDUCTION < MIN_WARHEADS -> "En dessous de $MIN_WARHEADS têtes, la dissuasion ne serait plus crédible."
        state.lastReduction?.let { it.daysUntil(ctx.now) < YEAR } == true -> "Déjà annoncé cette année."
        else -> null
    }

    /** Réduire l'arsenal : un geste de désarmement salué, quelques économies. */
    fun reduceArsenal(): Result<String> = runCatching {
        reductionBlocker()?.let { error(it) }
        state.lastReduction = ctx.now
        state.warheads -= REDUCTION
        state.credibility = (state.credibility - REDUCTION_COST).coerceAtLeast(0.0)
        ctx.effects.trigger(EffectSpec("budget.oneOff", -REDUCTION_SAVINGS), null, emptyMap(), "nuclear")
        ctx.state.countries.keys.filter { it != player }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("AID", DISARMAMENT_GOODWILL, ctx.now, "désarmement") }
        ctx.effects.trigger(EffectSpec("opinion.group.young", 0.008), null, emptyMap(), "nuclear")
        JournalService(ctx).add("Défense", "Arsenal réduit à ${state.warheads} têtes nucléaires", Tone.NEUTRAL)
        "Arsenal ramené à ${state.warheads} têtes : geste salué à l'ONU."
    }

    /** Ennemi qui occupe une partie du territoire national (seul cas où l'avertissement est concevable). */
    fun invader(): War? {
        val geo = Geopolitics(ctx)
        val occupiers = ctx.state.military.occupied.filter { (zone, occ) -> geo.ownerOf(zone) == player && geo.atWar(player, occ) }.values.toSet()
        return geo.activeWars().firstOrNull { w -> player in w.participants && occupiers.any { it in w.participants } }
    }

    fun warningBlocker(): String? = when {
        invader() == null -> "Seulement si un ennemi occupe une partie du territoire national."
        state.lastWarning?.let { it.daysUntil(ctx.now) < WARNING_COOLDOWN } == true -> "Un avertissement vient d'être adressé."
        else -> null
    }

    /** Avertissement solennel : l'agresseur sait que les intérêts vitaux de la France sont en jeu. */
    fun solemnWarning(): Result<String> = runCatching {
        warningBlocker()?.let { error(it) }
        val war = invader()!!
        state.lastWarning = ctx.now
        state.escalation = maxOf(state.escalation, 2)
        val enemies = war.participants.filter { !Geopolitics(ctx).allied(player, it) && it != player && Geopolitics(ctx).atWar(player, it) }
        val nuclearEnemy = enemies.any { Geopolitics(ctx).isNuclear(it) }
        val chance = if (nuclearEnemy) NUCLEAR_ENEMY_CHANCE else WARNING_BASE + WARNING_CREDIBILITY * state.credibility
        ctx.state.countries.keys.filter { it != player }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("WARNING", -WARNING_WORRY, ctx.now, "menace nucléaire") }
        JournalService(ctx).add("Défense", "Avertissement solennel aux agresseurs", Tone.WARNING)
        if (ctx.rng.chance(chance)) {
            WarService(ctx).ceasefire(war, CEASEFIRE_DAYS)
            ctx.effects.trigger(EffectSpec("president.popularity", 0.03), null, emptyMap(), "nuclear")
            "L'ennemi a compris : il accepte un cessez-le-feu de $CEASEFIRE_DAYS jours."
        } else {
            ctx.effects.trigger(EffectSpec("opinion.national", -0.01), null, emptyMap(), "nuclear")
            "L'ennemi ne cède pas. Le monde retient son souffle."
        }
    }

    companion object {
        const val ORDER_PREFIX = "def|"
        private const val MAX_PENDING = 2
        private const val SUPPLIER_MIN = 0.35
        private const val INDUSTRY_SHOCK = 0.004
        private const val INDUSTRY_MULTIPLIER = 0.5
        private const val SUPPLIER_GOODWILL = 0.04
        private const val EU_ANNOYANCE = 0.005
        private const val DAYS_PER_MONTH = 30
        private const val TALKS_COOLDOWN = 365.0
        private const val HOST_RELATION = 0.55
        private const val OPEN_CHANCE = 0.65
        private const val EVICTION_WEIGHT = 40.0
        private const val OPEN_INFLUENCE = 0.06
        private const val OPEN_WORRY = 0.05
        private const val BASE_EXPORT_BONUS = 0.08
        private const val YEAR = 365.0
        private const val MODERNIZATION_GAIN = 0.1
        private const val MODERNIZATION_COST = 3.0
        private const val UMBRELLA_EU = 0.05
        private const val UMBRELLA_THREAT = 0.08
        private const val POSTURE_WARNING = 0.05
        private const val TEST_COOLDOWN = 730.0
        private const val TEST_GAIN = 0.1
        private const val TEST_OUTRAGE = 0.12
        private const val REDUCTION = 30
        const val MIN_WARHEADS = 150
        private const val REDUCTION_COST = 0.03
        private const val REDUCTION_SAVINGS = 0.3
        private const val DISARMAMENT_GOODWILL = 0.03
        private const val WARNING_COOLDOWN = 60.0
        private const val NUCLEAR_ENEMY_CHANCE = 0.15
        private const val WARNING_BASE = 0.2
        private const val WARNING_CREDIBILITY = 0.7
        private const val WARNING_WORRY = 0.04
        private const val CEASEFIRE_DAYS = 60
        val BASE_AGENDA = AgendaCost(1.0)
        val UMBRELLA_AGENDA = AgendaCost(0.5)

        /** Facteur sur les attaques hybrides contre la France (cyberdéfense, posture, parapluie). */
        fun hybridFactor(ctx: SimulationContext): Double {
            val d = ctx.state.defense
            if (ctx.db.defense == null) return 1.0
            val cyber = d.capabilities["cyber"] ?: 0.35
            return (1 - CYBER_SHIELD * (cyber - 0.35)) * (if (d.posture == NuclearPosture.REINFORCED) 0.7 else 1.0) * (if (d.europeanUmbrella) 0.85 else 1.0)
        }

        /** Part des dégâts des frappes ennemies absorbée par la défense aérienne. */
        fun airShield(ctx: SimulationContext): Double = ((ctx.state.defense.capabilities["airDefense"] ?: 0.3) - 0.3).coerceIn(0.0, 0.7)

        /** Bonus de puissance des frappes françaises (missiles, drones, renseignement spatial). */
        fun strikeBonus(ctx: SimulationContext): Double {
            val c = ctx.state.defense.capabilities
            return ((c["strike"] ?: 0.35) - 0.35) + ((c["drones"] ?: 0.2) - 0.2) * 0.5 + ((c["space"] ?: 0.4) - 0.4) * 0.3
        }

        private const val CYBER_SHIELD = 1.2
    }
}
