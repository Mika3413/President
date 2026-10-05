package fr.president.engine.military

import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.effects.EffectSpec
import fr.president.engine.economy.SectorSystem
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.CharacterSpec
import fr.president.engine.politics.Traits
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting
import kotlinx.serialization.Serializable

@Serializable
data class CovertOpDef(
    val id: String,
    val kind: String,
    val label: String,
    val description: String = "",
    val costBillions: Double,
    val days: Int,
    val success: Double,
    val exposure: Double,
    val hostileOnly: Boolean = false,
)

@Serializable
data class ArmedGroupDef(
    val id: String,
    val label: String,
    val kind: String,
    val description: String = "",
    val where: String = "",
    val countries: List<String> = emptyList(),
    val strength: Double,
    val hostility: Double,
    val sponsor: String? = null,
    /** Événements dont le groupe fait monter le risque (poids). */
    val events: Map<String, Double> = emptyMap(),
    val actions: List<String> = emptyList(),
    val domestic: Boolean = false,
)

@Serializable
data class IntelFile(val capacity: Double = 0.55, val operations: List<CovertOpDef> = emptyList(), val groups: List<ArmedGroupDef> = emptyList())

@Serializable
class CovertOp(val op: String, val target: String, val endAt: WorldTime, val chance: Double, val exposure: Double)

@Serializable
data class CovertResult(val label: String, val target: String, val time: WorldTime, val success: Boolean, val exposed: Boolean, val text: String)

@Serializable
class GroupState(var strength: Double, var hostility: Double, var intel: Double = 0.0)

@Serializable
class IntelState(
    var capacity: Double = -1.0,
    val operations: MutableList<CovertOp> = mutableListOf(),
    val history: MutableList<CovertResult> = mutableListOf(),
    /** Ce que nous savons d'un pays (0..1), qui s'use avec le temps. */
    val dossiers: MutableMap<String, Double> = mutableMapOf(),
    val groups: MutableMap<String, GroupState> = mutableMapOf(),
    /** Menace de chaque groupe au début : seuls les écarts changent le risque d'événement. */
    val baseline: MutableMap<String, Double> = mutableMapOf(),
    /** Intensité de l'espionnage étranger en France (0..1). */
    val foreignSpying: MutableMap<String, Double> = mutableMapOf(),
    var surveillance: Boolean = false,
    var lastRecruit: WorldTime? = null,
    val cooldowns: MutableMap<String, WorldTime> = mutableMapOf(),
    var lastMonth: Int = -1,
)

/**
 * Services secrets : les opérations en cours aboutissent (ou échouent, et parfois éclatent au grand
 * jour) ; chaque mois, les groupes armés se renforcent ou s'affaiblissent, la DGSI déjoue des
 * attentats quand elle a infiltré un groupe, et les pays hostiles espionnent la France.
 */
class IntelSystem : SimulationSystem {
    override val name = "intel"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val file = ctx.db.intel ?: return
        val service = SecretService(ctx)
        val s = service.state
        val due = s.operations.filter { it.endAt.daysUntil(ctx.now) >= 0 }
        s.operations.removeAll(due)
        due.forEach { service.resolve(it) }
        val month = ctx.now.monthIndex
        if (s.lastMonth == month) return
        s.lastMonth = month
        monthly(ctx, file, s)
    }

    private fun monthly(ctx: SimulationContext, file: IntelFile, s: IntelState) {
        val player = ctx.state.player.countryId
        val relations = RelationCalculator(ctx)
        for (g in file.groups) {
            val st = s.groups[g.id] ?: continue
            val sponsor = g.sponsor?.takeIf { it in ctx.state.countries }?.let { if (relations.score(it, player) < HOSTILE) SPONSOR_PUSH else 0.0 } ?: 0.0
            val pressure = if (s.surveillance && g.domestic) SURVEILLANCE_PRESSURE else 0.0
            st.strength = (st.strength + REVERSION * (g.strength - st.strength) + sponsor - pressure + ctx.rng.nextGaussian() * NOISE).coerceIn(MIN_STRENGTH, 1.0)
            st.hostility += REVERSION * (g.hostility - st.hostility)
            st.intel = (st.intel - INTEL_DECAY).coerceAtLeast(0.0)
            // Un groupe infiltré voit ses projets d'attentat déjoués.
            if (g.events.containsKey("terror_attack") && st.intel > FOIL_MIN && ctx.rng.chance(st.intel * FOIL_CHANCE * st.strength * 2)) {
                st.strength = (st.strength - FOIL_DAMAGE).coerceAtLeast(MIN_STRENGTH)
                st.intel -= FOIL_INTEL_COST
                ctx.effects.trigger(EffectSpec("president.popularity", FOIL_POPULARITY), null, emptyMap(), "intel")
                ctx.notifications.post(NotificationCategory.SECURITY, Urgency.IMPORTANT, "Attentat déjoué",
                    "La DGSI a interpellé une cellule liée à ${g.label} qui préparait une attaque. Les enquêteurs saluent le travail d'infiltration.", null)
            }
        }
        s.dossiers.replaceAll { _, v -> (v - DOSSIER_DECAY).coerceAtLeast(0.0) }
        s.dossiers.values.removeAll { it <= 0.0 }
        // Espionnage étranger : les pays hostiles s'intéressent à nos secrets.
        val geo = Geopolitics(ctx)
        for (c in ctx.state.countries.keys.filter { it != player }) {
            val relation = relations.score(c, player)
            val level = s.foreignSpying[c] ?: 0.0
            val grow = when {
                geo.allied(player, c) -> -SPY_GROWTH
                relation < HOSTILE -> SPY_GROWTH * 2
                relation < NEUTRAL -> SPY_GROWTH
                else -> -SPY_GROWTH
            }
            val next = (level + grow).coerceIn(0.0, 1.0)
            if (next <= 0.0) s.foreignSpying.remove(c) else s.foreignSpying[c] = next
            if (next > LEAK_LEVEL && ctx.rng.chance(LEAK_CHANCE * next * (1.5 - s.capacity))) {
                s.foreignSpying[c] = next - LEAK_RELIEF
                val event = if (ctx.rng.chance(0.5)) "cyber_espionage" else "defense_leak"
                if (ctx.db.events.any { it.id == event }) ctx.scheduler.schedule(ScheduledAction.EventLaunch(ctx.now.plusHours(2), event, c))
            }
        }
        if (s.surveillance) ctx.state.playerCountry.economy.pendingOneOffBillions += SURVEILLANCE_COST
    }

    companion object {
        const val HOSTILE = 0.3
        const val NEUTRAL = 0.45
        private const val SPONSOR_PUSH = 0.015
        private const val SURVEILLANCE_PRESSURE = 0.01
        private const val REVERSION = 0.06
        private const val NOISE = 0.025
        const val MIN_STRENGTH = 0.05
        private const val INTEL_DECAY = 0.04
        private const val FOIL_MIN = 0.3
        private const val FOIL_CHANCE = 0.25
        private const val FOIL_DAMAGE = 0.05
        private const val FOIL_INTEL_COST = 0.15
        private const val FOIL_POPULARITY = 0.006
        private const val DOSSIER_DECAY = 0.05
        private const val SPY_GROWTH = 0.03
        private const val LEAK_LEVEL = 0.5
        private const val LEAK_CHANCE = 0.08
        private const val LEAK_RELIEF = 0.2
        private const val SURVEILLANCE_COST = 0.02
    }
}

/** Les ordres du président aux services : DGSE à l'étranger, DGSI en France. */
class SecretService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.intel
    private val player get() = ctx.state.player.countryId
    private val relations = RelationCalculator(ctx)
    private val geo = Geopolitics(ctx)

    val available: Boolean get() = file != null

    val state: IntelState get() = ctx.state.intel.also { s ->
        val f = file ?: return@also
        if (s.capacity < 0) {
            s.capacity = f.capacity
            f.groups.forEach { g -> s.groups[g.id] = GroupState(g.strength, g.hostility); s.baseline[g.id] = g.strength * g.hostility }
        }
    }

    fun op(id: String) = file?.operations?.firstOrNull { it.id == id }
    fun group(id: String) = file?.groups?.firstOrNull { it.id == id }
    fun dossier(country: String): Double = state.dossiers[country] ?: 0.0

    // ---- Opérations à l'étranger ----

    fun chance(def: CovertOpDef, target: String): Double {
        val c = ctx.state.countries[target] ?: return 0.0
        val quality = ctx.db.country(target).definition.strategic.intelligenceQuality
        var p = def.success * (0.5 + state.capacity) * (1.3 - quality * 0.6)
        if (def.kind == "coup") p *= (1 - c.leaderApproval) * COUP_APPROVAL_WEIGHT
        return p.coerceIn(MIN_CHANCE, MAX_CHANCE)
    }

    fun blocker(def: CovertOpDef, target: String): String? {
        if (target == player || target !in ctx.state.countries) return "Cible impossible."
        if (state.operations.any { it.op == def.id && it.target == target }) return "Opération déjà en cours."
        if (state.operations.size >= MAX_OPERATIONS) return "Les services sont saturés : $MAX_OPERATIONS opérations à la fois au plus."
        if (def.hostileOnly && relations.score(target, player) >= HOSTILE_ONLY && !geo.atWar(player, target)) return "Réservé aux pays hostiles : le risque politique serait énorme."
        if (def.kind == "coup") {
            if (geo.isNuclear(target)) return "Impensable contre une puissance nucléaire."
            if (ctx.db.alliances.any { (it.id == "EU" || it.id == "NATO") && target in it.members }) return "Impensable contre un allié."
        }
        return null
    }

    fun start(opId: String, target: String): Result<String> = runCatching {
        val def = op(opId)!!
        blocker(def, target)?.let { error(it) }
        launch(def.id, target, def.days, chance(def, target), def.exposure, def.costBillions)
        "Opération lancée : ${def.label.lowercase()} (${name(target)}). Résultat dans ${def.days} jours environ."
    }

    private fun launch(op: String, target: String, days: Int, chance: Double, exposure: Double, cost: Double) {
        state.operations += CovertOp(op, target, ctx.now.plusDays(days.toLong()), chance, exposure)
        ctx.effects.trigger(EffectSpec("budget.oneOff", cost), null, emptyMap(), "intel")
    }

    fun resolve(o: CovertOp) {
        val success = ctx.rng.chance(o.chance)
        val exposed = !success && ctx.rng.chance(o.exposure)
        val groupId = o.target.removePrefix(GROUP_PREFIX).takeIf { o.target.startsWith(GROUP_PREFIX) }
        val (label, text) = if (groupId != null) groupOutcome(o, groupId, success, exposed) else countryOutcome(o, success, exposed)
        state.history += CovertResult(label, o.target, ctx.now, success, exposed, text)
        if (state.history.size > MAX_HISTORY) state.history.removeAt(0)
        ctx.notifications.post(if (exposed) NotificationCategory.POLITICS else NotificationCategory.SECURITY,
            if (exposed) Urgency.URGENT else Urgency.IMPORTANT,
            when { success -> "Services secrets : succès"; exposed -> "Scandale : une opération secrète éclate au grand jour"; else -> "Services secrets : échec" }, text, null, journal = success || exposed)
    }

    private fun countryOutcome(o: CovertOp, success: Boolean, exposed: Boolean): Pair<String, String> {
        val def = op(o.op) ?: return o.op to ""
        val country = ctx.state.countries.getValue(o.target)
        val n = name(o.target)
        if (!success) {
            if (exposed) {
                val weight = if (def.kind == "coup") COUP_EXPOSED else EXPOSED
                ctx.state.diplomacy.relation(o.target, player).memories += DiplomaticMemory("CONDEMNATION", -weight, ctx.now, "opération clandestine française")
                if (ctx.db.alliances.any { it.id == "EU" && o.target in it.members }) ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", -0.03), null, emptyMap(), "intel")
                ctx.effects.trigger(EffectSpec("president.popularity", -EXPOSED_POPULARITY), null, emptyMap(), "intel")
                ctx.effects.trigger(EffectSpec("opinion.national", -0.01), null, emptyMap(), "intel")
                if (def.kind == "coup") country.leaderApproval = (country.leaderApproval + RALLY).coerceAtMost(1.0)
                return def.label to "$n a démasqué nos agents (${def.label.lowercase()}). Expulsions de diplomates, presse déchaînée : l'affaire remonte jusqu'à l'Élysée."
            }
            return def.label to "${def.label} ($n) : l'opération n'a rien donné, sans laisser de traces."
        }
        val leader = ctx.state.characters[country.leaderId]
        val text = when (def.kind) {
            "political" -> {
                state.dossiers[o.target] = 1.0
                leader?.let { it.knownTraits += it.traits.keys }
                val score = relations.score(o.target, player)
                val aggressive = (leader?.trait(Traits.AGGRESSIVENESS) ?: 0.5) > 0.6
                "Dossier complet sur ${leader?.fullName ?: "le dirigeant"} : son caractère est désormais connu. Ce qu'il pense vraiment de la France : ${relations.label(score).lowercase()}." +
                    if (aggressive) " Attention : il envisage l'usage de la force contre ses voisins." else ""
            }
            "military" -> {
                state.dossiers[o.target] = 1.0
                val forces = ctx.state.military.units.values.count { it.countryId == o.target && !it.destroyed }
                "Forces de $n cartographiées : $forces unités localisées. Nos estimations de ses forces sont désormais fiables."
            }
            "economic" -> {
                SectorSystem.shock(ctx, "tech", ECONOMIC_GAIN); SectorSystem.shock(ctx, "aerospace", ECONOMIC_GAIN / 2)
                "Secrets industriels récupérés : nos entreprises gagnent des mois de recherche."
            }
            "influence" -> {
                country.leaderApproval = (country.leaderApproval - INFLUENCE_DROP).coerceAtLeast(0.05)
                "La campagne porte : la popularité du pouvoir en place recule (${Math.round(country.leaderApproval * 100)} %)."
            }
            "opposition" -> {
                country.leaderApproval = (country.leaderApproval - OPPOSITION_DROP).coerceAtLeast(0.05)
                val soon = ctx.now.plusDays(OPPOSITION_ELECTION_DAYS)
                if (country.nextLeadershipChange?.let { it > soon } != false) country.nextLeadershipChange = soon
                "L'opposition est en ordre de bataille : une alternance est possible d'ici quelques mois."
            }
            "sabotage" -> {
                country.economy.pendingOutputShock -= SABOTAGE_ECONOMY
                ctx.state.military.units.values.filter { it.countryId == o.target && !it.destroyed }.forEach { it.readiness = (it.readiness - SABOTAGE_READINESS).coerceAtLeast(0.0) }
                "Sabotages réussis : usines et dépôts touchés, ses forces perdent en disponibilité."
            }
            "coup" -> { coup(o.target); "Coup d'État réussi : un nouveau pouvoir, reconnaissant envers Paris, s'installe." }
            else -> ""
        }
        JournalService(ctx).add("Renseignement", "${def.label} : succès ($n)", Tone.GOOD)
        return def.label to text
    }

    private fun coup(target: String) {
        val country = ctx.state.countries.getValue(target)
        val def = ctx.db.country(target).definition
        ctx.state.characters[country.leaderId]?.let { it.role = CharacterRole.FORMER; it.active = false }
        val leader = ctx.characters.generate(ctx.state.newId("chr"),
            CharacterSpec(target, CharacterRole.FOREIGN_LEADER, target, ctx.now.toDateTime().year,
                ageRange = def.leader.ageRange[0]..def.leader.ageRange[1], economicLeaning = def.leader.economicLeaningRange.average(), traitRanges = def.leader.traitRanges),
            ctx.rng)
        leader.traits[Traits.AGGRESSIVENESS] = COUP_LEADER_AGGRESSIVENESS
        leader.traits[Traits.OPENNESS] = COUP_LEADER_OPENNESS
        ctx.state.characters[leader.id] = leader
        country.leaderId = leader.id
        country.leaderApproval = COUP_APPROVAL
        country.nextLeadershipChange = ctx.now.plusDays(COUP_TERM_DAYS)
        ctx.state.diplomacy.relations.filterKeys { it.startsWith("$target>") }.values.forEach { r -> r.memories.replaceAll { it.copy(weight = it.weight * COUP_RESET) } }
        ctx.state.diplomacy.relation(target, player).memories += DiplomaticMemory("MILITARY_SUPPORT", COUP_GRATITUDE, ctx.now, "soutien au nouveau pouvoir")
        ctx.notifications.news(NotificationCategory.DIPLOMACY, "Coup d'État ${fr.president.engine.data.CountryNames(def).inside} : ${leader.fullName} prend le pouvoir", target)
    }

    // ---- Groupes armés ----

    data class GroupRow(val def: ArmedGroupDef, val strength: Double, val hostility: Double, val intel: Double, val threat: Double)

    fun groups(): List<GroupRow> = file?.groups.orEmpty().map { g ->
        val st = state.groups[g.id] ?: GroupState(g.strength, g.hostility)
        GroupRow(g, st.strength, st.hostility, st.intel, st.strength * st.hostility)
    }.sortedByDescending { it.threat }

    fun groupBlocker(groupId: String, action: String): String? {
        val g = group(groupId) ?: return "Groupe inconnu."
        if (action !in g.actions) return "Action impossible contre ce groupe."
        if (state.operations.any { it.target == "$GROUP_PREFIX$groupId" && it.op == action }) return "Opération déjà en cours."
        state.cooldowns["$groupId|$action"]?.let { if (it.daysUntil(ctx.now) < GROUP_COOLDOWN) return "Possible à nouveau dans ${(GROUP_COOLDOWN - it.daysUntil(ctx.now)).toInt()} j." }
        if (action == "strike" && DefenseService(ctx).capability("strike") < STRIKE_MIN) return "Capacités de frappe insuffisantes (missiles, drones)."
        if (action in setOf("neutralize", "infiltrate") && state.operations.size >= MAX_OPERATIONS) return "Les services sont saturés."
        return null
    }

    fun groupChance(groupId: String, action: String): Double {
        val st = state.groups[groupId] ?: return 0.0
        return when (action) {
            "neutralize" -> NEUTRALIZE_BASE + NEUTRALIZE_INTEL * st.intel + (state.capacity - 0.5) * 0.4
            "infiltrate" -> INFILTRATE_BASE + state.capacity * 0.3
            "dissolve" -> DISSOLVE_CHANCE
            else -> 1.0
        }.coerceIn(MIN_CHANCE, MAX_CHANCE)
    }

    fun actOnGroup(groupId: String, action: String): Result<String> = runCatching {
        groupBlocker(groupId, action)?.let { error(it) }
        val g = group(groupId)!!
        val st = state.groups.getValue(groupId)
        state.cooldowns["$groupId|$action"] = ctx.now
        when (action) {
            "neutralize" -> { launch(action, "$GROUP_PREFIX$groupId", NEUTRALIZE_DAYS, groupChance(groupId, action), if (g.domestic) 0.0 else 0.3, NEUTRALIZE_COST)
                "Le service Action prépare l'opération contre un chef de ${g.label}. Résultat dans un mois environ." }
            "infiltrate" -> { launch(action, "$GROUP_PREFIX$groupId", INFILTRATE_DAYS, groupChance(groupId, action), 0.0, INFILTRATE_COST)
                "La DGSI tente d'infiltrer ${g.label}. Résultat dans deux mois environ." }
            "strike" -> {
                st.strength = (st.strength - STRIKE_DAMAGE).coerceAtLeast(IntelSystem.MIN_STRENGTH)
                st.hostility = (st.hostility + STRIKE_ANGER).coerceAtMost(1.0)
                ctx.effects.trigger(EffectSpec("budget.oneOff", STRIKE_COST), null, emptyMap(), "intel")
                ctx.effects.trigger(EffectSpec("chain.terror_attack", RETALIATION), null, emptyMap(), "intel")
                val collateral = ctx.rng.chance(COLLATERAL)
                if (collateral) {
                    ctx.effects.trigger(EffectSpec("opinion.national", -0.006), null, emptyMap(), "intel")
                    g.countries.filter { it in ctx.state.countries }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("CONDEMNATION", -0.05, ctx.now, "frappes françaises") }
                }
                JournalService(ctx).add("Opération", "Frappes contre ${g.label}", Tone.NEUTRAL)
                "Frappes sur les positions de ${g.label} : ses capacités reculent" + if (collateral) ", mais des civils ont été tués." else "."
            }
            "negotiate" -> {
                st.hostility = (st.hostility - NEGOTIATE_CALM).coerceAtLeast(0.0)
                st.strength = (st.strength - NEGOTIATE_STRENGTH).coerceAtLeast(IntelSystem.MIN_STRENGTH)
                ctx.effects.trigger(EffectSpec("opinion.group.seniors", -0.008), null, emptyMap(), "intel")
                JournalService(ctx).add("Sécurité", "Négociations discrètes avec ${g.label}", Tone.NEUTRAL)
                "Des émissaires ont noué le contact : ${g.label} baisse d'un ton. La droite dénonce une faiblesse."
            }
            "dissolve" -> {
                ctx.effects.trigger(EffectSpec("opinion.group.young", -0.004), null, emptyMap(), "intel")
                if (ctx.rng.chance(DISSOLVE_CHANCE)) {
                    st.strength = (st.strength - DISSOLVE_DAMAGE).coerceAtLeast(IntelSystem.MIN_STRENGTH)
                    JournalService(ctx).add("Sécurité", "Dissolution : ${g.label}", Tone.NEUTRAL)
                    "Dissolution prononcée en Conseil des ministres : ${g.label} perd ses structures."
                } else "Le Conseil d'État suspend la dissolution, faute de preuves suffisantes."
            }
            else -> error("Action inconnue.")
        }
    }

    private fun groupOutcome(o: CovertOp, groupId: String, success: Boolean, exposed: Boolean): Pair<String, String> {
        val g = group(groupId) ?: return o.op to ""
        val st = state.groups.getValue(groupId)
        return when (o.op) {
            "neutralize" -> "Neutralisation" to if (success) {
                st.strength = (st.strength - NEUTRALIZE_DAMAGE).coerceAtLeast(IntelSystem.MIN_STRENGTH)
                ctx.effects.trigger(EffectSpec("president.popularity", 0.01), null, emptyMap(), "intel")
                ctx.effects.trigger(EffectSpec("chain.terror_attack", RETALIATION), null, emptyMap(), "intel")
                JournalService(ctx).add("Renseignement", "Un chef de ${g.label} neutralisé", Tone.GOOD)
                "Un chef de ${g.label} a été neutralisé. Le groupe est désorganisé."
            } else {
                if (exposed) g.countries.filter { it in ctx.state.countries }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("CONDEMNATION", -EXPOSED, ctx.now, "opération sur son sol") }
                "La cible de ${g.label} a échappé à nos agents" + if (exposed) " et l'opération a été révélée par la presse locale." else "."
            }
            "infiltrate" -> "Infiltration" to if (success) {
                st.intel = 1.0
                "Un agent est entré dans ${g.label} : nous suivons désormais ses projets de l'intérieur."
            } else "La tentative d'infiltration de ${g.label} a échoué ; notre source a dû être exfiltrée."
            else -> o.op to ""
        }
    }

    // ---- DGSI : contre-espionnage, surveillance, moyens ----

    data class SpyRow(val country: String, val name: String, val level: Double, val blocker: String?)

    fun spies(): List<SpyRow> = state.foreignSpying.entries.sortedByDescending { it.value }.map { (c, v) ->
        SpyRow(c, name(c), v, state.cooldowns["counter|$c"]?.let { if (it.daysUntil(ctx.now) < COUNTER_COOLDOWN) "Réseau démantelé récemment." else null })
    }

    /** Démanteler un réseau d'espionnage : diplomates expulsés, le pays proteste. */
    fun counterEspionage(country: String): Result<String> = runCatching {
        val row = spies().firstOrNull { it.country == country } ?: error("Aucun réseau connu.")
        row.blocker?.let { error(it) }
        state.cooldowns["counter|$country"] = ctx.now
        if (!ctx.rng.chance(COUNTER_BASE + state.capacity * 0.4)) return@runCatching "Les agents de ${row.name} ont filé avant l'interpellation."
        state.foreignSpying.remove(country)
        ctx.state.diplomacy.relation(country, player).memories += DiplomaticMemory("CONDEMNATION", -COUNTER_ANGER, ctx.now, "diplomates expulsés")
        ctx.effects.trigger(EffectSpec("president.popularity", 0.004), null, emptyMap(), "intel")
        ctx.notifications.news(NotificationCategory.SECURITY, "La DGSI démantèle un réseau d'espionnage au service ${fr.president.engine.data.CountryNames(ctx.db.country(country).definition).of}")
        "Réseau démantelé : ${row.name} rappelle ses « diplomates » et proteste."
    }

    fun setSurveillance(on: Boolean): String {
        state.surveillance = on
        ctx.effects.trigger(EffectSpec("opinion.group.young", if (on) -0.006 else 0.003), null, emptyMap(), "intel")
        JournalService(ctx).add("Sécurité", if (on) "Surveillance renforcée des milieux radicaux" else "Fin de la surveillance renforcée", Tone.NEUTRAL)
        return if (on) "Surveillance renforcée : écoutes, filatures, fichés S suivis de près. Les défenseurs des libertés s'inquiètent." else "Retour à la surveillance ordinaire."
    }

    fun recruitBlocker(): String? = when {
        state.capacity >= MAX_CAPACITY -> "Les services sont à leur meilleur niveau."
        state.lastRecruit?.let { it.daysUntil(ctx.now) < RECRUIT_COOLDOWN } == true -> "Recrutement en cours."
        else -> null
    }

    fun recruit(): Result<String> = runCatching {
        recruitBlocker()?.let { error(it) }
        state.lastRecruit = ctx.now
        state.capacity = (state.capacity + RECRUIT_GAIN).coerceAtMost(MAX_CAPACITY)
        ctx.effects.trigger(EffectSpec("budget.oneOff", RECRUIT_COST), null, emptyMap(), "intel")
        "1 000 agents recrutés (${Formatting.billions(RECRUIT_COST)}) : capacité des services ${Math.round(state.capacity * 100)} %."
    }

    private fun name(c: String) = ctx.db.countries[c]?.definition?.name ?: c

    companion object {
        const val GROUP_PREFIX = "group:"
        const val MAX_OPERATIONS = 4
        private const val HOSTILE_ONLY = 0.4
        private const val MIN_CHANCE = 0.05
        private const val MAX_CHANCE = 0.95
        private const val COUP_APPROVAL_WEIGHT = 1.4
        private const val MAX_HISTORY = 30
        private const val EXPOSED = 0.15
        private const val COUP_EXPOSED = 0.4
        private const val EXPOSED_POPULARITY = 0.02
        private const val RALLY = 0.05
        private const val ECONOMIC_GAIN = 0.02
        private const val INFLUENCE_DROP = 0.08
        private const val OPPOSITION_DROP = 0.1
        private const val OPPOSITION_ELECTION_DAYS = 120.0
        private const val SABOTAGE_ECONOMY = 0.004
        private const val SABOTAGE_READINESS = 0.1
        private const val COUP_LEADER_AGGRESSIVENESS = 0.3
        private const val COUP_LEADER_OPENNESS = 0.7
        private const val COUP_APPROVAL = 0.4
        private const val COUP_TERM_DAYS = 900.0
        private const val COUP_RESET = 0.3
        private const val COUP_GRATITUDE = 0.25
        private const val GROUP_COOLDOWN = 120.0
        private const val STRIKE_MIN = 0.3
        private const val NEUTRALIZE_BASE = 0.35
        private const val NEUTRALIZE_INTEL = 0.4
        private const val NEUTRALIZE_DAYS = 30
        private const val NEUTRALIZE_COST = 0.03
        private const val NEUTRALIZE_DAMAGE = 0.2
        private const val INFILTRATE_BASE = 0.4
        private const val INFILTRATE_DAYS = 60
        private const val INFILTRATE_COST = 0.02
        private const val STRIKE_DAMAGE = 0.12
        private const val STRIKE_ANGER = 0.05
        private const val STRIKE_COST = 0.08
        private const val RETALIATION = 0.05
        private const val COLLATERAL = 0.2
        private const val NEGOTIATE_CALM = 0.15
        private const val NEGOTIATE_STRENGTH = 0.05
        private const val DISSOLVE_CHANCE = 0.7
        private const val DISSOLVE_DAMAGE = 0.12
        private const val COUNTER_COOLDOWN = 180.0
        private const val COUNTER_BASE = 0.4
        private const val COUNTER_ANGER = 0.06
        private const val MAX_CAPACITY = 0.9
        private const val RECRUIT_COOLDOWN = 180.0
        private const val RECRUIT_GAIN = 0.05
        private const val RECRUIT_COST = 0.4

        /** Effet des groupes armés et de la surveillance sur le risque d'un événement. */
        fun eventFactor(ctx: SimulationContext, eventId: String): Double {
            val file = ctx.db.intel ?: return 1.0
            val s = ctx.state.intel
            if (s.capacity < 0) return 1.0
            var f = 1.0
            for (g in file.groups) {
                val w = g.events[eventId] ?: continue
                val st = s.groups[g.id] ?: continue
                val threat = st.strength * st.hostility
                f *= (1 + w * THREAT_WEIGHT * (threat - (s.baseline[g.id] ?: threat))).coerceAtLeast(0.2) * (1 - INTEL_SHIELD * st.intel * w.coerceAtMost(1.0))
            }
            if (s.surveillance) f *= SURVEILLANCE_FACTOR[eventId] ?: 1.0
            return f.coerceIn(0.2, 3.0)
        }

        private const val THREAT_WEIGHT = 3.0
        private const val INTEL_SHIELD = 0.3
        private val SURVEILLANCE_FACTOR = mapOf("terror_attack" to 0.75, "urban_riots" to 0.9, "corsica_tensions" to 0.85)
    }
}
