package fr.president.engine.politics

import fr.president.engine.effects.EffectSpec
import fr.president.engine.economy.SectorSystem
import fr.president.engine.government.PolicyKind
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
import fr.president.engine.world.GameOver
import kotlinx.serialization.Serializable

@Serializable
data class Concession(val label: String, val effects: List<EffectSpec> = emptyList())

@Serializable
data class UnrestCause(
    val id: String,
    val label: String,
    val slogan: String = "",
    val reforms: List<String> = emptyList(),
    val laws: List<String> = emptyList(),
    val events: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val crowdThousands: Double,
    val concession: Concession,
    val blockades: Map<String, Double> = emptyMap(),
)

@Serializable
data class UnrestFile(val causes: List<UnrestCause>, val armyLoyalty: Double = 0.8)

@Serializable
enum class MovementPhase(val label: String, val icon: String) {
    MARCHES("Manifestations", "⚑"),
    BLOCKADES("Grèves et blocages", "⊘"),
    RIOTS("Émeutes", "⚠"),
    INSURRECTION("Insurrection", "⚔"),
}

@Serializable
class Movement(
    val id: String,
    val cause: String,
    val startedAt: WorldTime,
    /** Manifestants (milliers). */
    var crowd: Double,
    var peak: Double = 0.0,
    /** 0 : défilés pacifiques ; 1 : violence organisée. */
    var radicalization: Double = 0.1,
    /** Élan du mouvement (s'use avec le temps, relancé par la répression ou les événements). */
    var momentum: Double = 1.0,
    /** Colère attisée par la répression (retombe peu à peu). */
    var heat: Double = 0.0,
    var phase: MovementPhase = MovementPhase.MARCHES,
    var conceded: Boolean = false,
    var insurrectionDays: Int = 0,
    val history: MutableList<Double> = mutableListOf(),
    val lastAction: MutableMap<String, WorldTime> = mutableMapOf(),
)

@Serializable
class UnrestState(
    val movements: MutableList<Movement> = mutableListOf(),
    val past: MutableList<String> = mutableListOf(),
    val lastSpawn: MutableMap<String, WorldTime> = mutableMapOf(),
    /** Loyauté de l'armée envers le président (0..1). */
    var armyLoyalty: Double = -1.0,
    /** Avancement d'un complot militaire (1 : passage à l'acte). */
    var conspiracy: Double = 0.0,
    var plotKnown: Boolean = false,
    var lastMonth: Int = -1,
    val lastArmyAction: MutableMap<String, WorldTime> = mutableMapOf(),
    var coups: Int = 0,
)

/**
 * La rue et les casernes. Chaque jour, chaque mouvement grossit ou s'essouffle selon la colère des
 * groupes qu'il mobilise et son élan, se radicalise sous la répression, change de phase ; les
 * blocages et les émeutes coûtent à l'économie et à la popularité ; une insurrection qui dure peut
 * emporter le président. Chaque mois, la loyauté de l'armée évolue ; trop basse, un complot naît.
 */
class UnrestSystem : SimulationSystem {
    override val name = "unrest"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val file = ctx.db.unrest ?: return
        if (ctx.state.player.gameOver != null) return
        val service = UnrestService(ctx)
        val s = service.state
        for (m in s.movements.toList()) {
            val cause = file.causes.firstOrNull { it.id == m.cause } ?: continue
            daily(ctx, service, m, cause)
        }
        val month = ctx.now.monthIndex
        if (s.lastMonth != month) {
            s.lastMonth = month
            service.monthlyAnger(file)
            army(ctx, service)
        }
    }

    private fun daily(ctx: SimulationContext, service: UnrestService, m: Movement, cause: UnrestCause) {
        val s = service.state
        val anger = service.anger(cause)
        val active = ctx.state.measures.active.map { it.id }.toSet()
        val restriction = (if ("gathering_ban" in active) BAN_FACTOR else 1.0) * (if ("curfew" in active || "lockdown" in active) CURFEW_FACTOR else 1.0)
        val target = cause.crowdThousands * m.momentum * (CROWD_BASE + CROWD_ANGER * anger) * restriction
        m.crowd = (m.crowd + (target - m.crowd) * CROWD_ADJUST).coerceAtLeast(0.0)
        m.peak = maxOf(m.peak, m.crowd)
        m.momentum *= if (m.conceded) CONCEDED_DECAY else MOMENTUM_DECAY
        m.momentum += m.heat * HEAT_MOMENTUM
        val banned = if (restriction < 1.0) BAN_RADICALIZATION else 0.0
        m.radicalization = (m.radicalization + RADICAL_ANGER * anger + RADICAL_HEAT * m.heat + banned - RADICAL_COOLING).coerceIn(0.0, 1.0)
        m.heat *= HEAT_DECAY
        m.history += m.crowd
        if (m.history.size > HISTORY) m.history.removeAt(0)
        val phase = when {
            m.crowd >= INSURRECTION_CROWD && m.radicalization >= INSURRECTION_RADICAL -> MovementPhase.INSURRECTION
            m.crowd >= RIOT_CROWD && m.radicalization >= RIOT_RADICAL -> MovementPhase.RIOTS
            m.crowd >= BLOCKADE_CROWD -> MovementPhase.BLOCKADES
            else -> MovementPhase.MARCHES
        }
        if (phase != m.phase) {
            val worse = phase.ordinal > m.phase.ordinal
            m.phase = phase
            ctx.notifications.post(NotificationCategory.POLITICS, if (phase >= MovementPhase.RIOTS && worse) Urgency.URGENT else Urgency.IMPORTANT,
                "${cause.label} : ${phase.label.lowercase()}", phaseText(phase, m), null)
        }
        effects(ctx, m, cause)
        if (m.phase == MovementPhase.INSURRECTION) {
            m.insurrectionDays++
            val popularity = ctx.state.opinion.nationalApproval
            if (m.insurrectionDays >= REVOLUTION_DAYS && (popularity < REVOLUTION_POPULARITY || s.armyLoyalty < REVOLUTION_LOYALTY) && ctx.rng.chance(REVOLUTION_CHANCE)) {
                service.overthrow("La rue a eu raison de votre présidence : après ${m.insurrectionDays} jours d'insurrection, vous démissionnez.")
                return
            }
        } else m.insurrectionDays = 0
        val age = m.startedAt.daysUntil(ctx.now)
        if (age > MIN_DAYS && m.crowd < END_CROWD) {
            s.movements.remove(m)
            s.past += "${cause.label} : jusqu'à ${service.people(m.peak)} manifestants, ${age.toInt()} jours" + if (m.conceded) " (concessions)" else ""
            if (s.past.size > MAX_PAST) s.past.removeAt(0)
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.INFO, "Le mouvement s'essouffle", "${cause.label} : les cortèges se clairsèment, le mouvement prend fin.", null)
        }
    }

    private fun phaseText(phase: MovementPhase, m: Movement): String = when (phase) {
        MovementPhase.MARCHES -> "Le mouvement redevient pacifique : défilés et pétitions."
        MovementPhase.BLOCKADES -> "Raffineries, ports et dépôts bloqués, grèves reconductibles : l'économie tourne au ralenti."
        MovementPhase.RIOTS -> "Vitrines brisées, voitures incendiées, affrontements avec les forces de l'ordre."
        MovementPhase.INSURRECTION -> "Des barricades dans les grandes villes, des bâtiments publics occupés : l'autorité de l'État vacille. Si cela dure, votre présidence est en jeu."
    }

    private fun effects(ctx: SimulationContext, m: Movement, cause: UnrestCause) {
        val scale = m.crowd / REFERENCE_CROWD
        ctx.effects.apply("president.popularity", -POPULARITY_DRAIN * scale * (1 + m.phase.ordinal))
        when (m.phase) {
            MovementPhase.MARCHES -> Unit
            MovementPhase.BLOCKADES -> {
                cause.blockades.forEach { (sector, w) -> SectorSystem.shock(ctx, sector, w / MONTH * scale) }
                ctx.state.playerCountry.economy.pendingOutputShock -= BLOCKADE_OUTPUT * scale
            }
            MovementPhase.RIOTS -> {
                SectorSystem.shock(ctx, "retail", -RIOT_SHOCK * scale); SectorSystem.shock(ctx, "tourism", -RIOT_SHOCK * scale)
                ctx.state.playerCountry.economy.pendingOneOffBillions += RIOT_COST * scale
                ctx.effects.apply("quality.security", -RIOT_SECURITY * scale)
            }
            MovementPhase.INSURRECTION -> {
                ctx.state.playerCountry.economy.pendingOutputShock -= INSURRECTION_OUTPUT * scale
                ctx.state.playerCountry.economy.pendingOneOffBillions += RIOT_COST * 2 * scale
                ctx.effects.apply("quality.security", -RIOT_SECURITY * 2 * scale)
            }
        }
    }

    /** Armée : loyauté mensuelle, complot, tentative de coup d'État. */
    private fun army(ctx: SimulationContext, service: UnrestService) {
        val s = service.state
        val e = ctx.state.playerCountry.economy
        val geo = fr.president.engine.military.Geopolitics(ctx)
        val player = ctx.state.player.countryId
        val defense = e.budget?.spending?.get("defense")?.policyFactor ?: 1.0
        val invaded = ctx.state.military.occupied.any { (zone, _) -> geo.ownerOf(zone) == player }
        val insurrection = s.movements.any { it.phase == MovementPhase.INSURRECTION }
        val popularity = ctx.state.opinion.nationalApproval
        val target = (service.baseLoyalty + DEFENSE_WEIGHT * (defense - 1) - (if (invaded) INVADED else 0.0) - (if (insurrection) INSURRECTION_STRAIN else 0.0) -
            (if (popularity < LOW_POPULARITY) UNPOPULAR else 0.0)).coerceIn(0.05, 1.0)
        s.armyLoyalty += (target - s.armyLoyalty) * LOYALTY_ADJUST
        if (s.armyLoyalty < PLOT_LOYALTY) s.conspiracy += (PLOT_LOYALTY - s.armyLoyalty) * PLOT_SPEED
        else s.conspiracy = (s.conspiracy - PLOT_DECAY).coerceAtLeast(0.0)
        if (s.conspiracy <= 0.0) s.plotKnown = false
        val capacity = ctx.state.intel.capacity.takeIf { it >= 0 } ?: 0.55
        if (!s.plotKnown && s.conspiracy > DETECTION_LEVEL && ctx.rng.chance(capacity)) {
            s.plotKnown = true
            ctx.notifications.post(NotificationCategory.SECURITY, Urgency.URGENT, "La DGSI signale un complot dans l'armée",
                "Des officiers supérieurs se réunissent en secret et parlent de « sauver le pays ». Agissez vite (écran « La rue et l'armée »).", null)
        }
        if (s.conspiracy >= 1.0) service.coupAttempt()
    }

    companion object {
        private const val BAN_FACTOR = 0.6
        private const val CURFEW_FACTOR = 0.8
        private const val CROWD_BASE = 0.4
        private const val CROWD_ANGER = 1.6
        private const val CROWD_ADJUST = 0.15
        private const val MOMENTUM_DECAY = 0.985
        private const val CONCEDED_DECAY = 0.94
        private const val HEAT_MOMENTUM = 0.01
        private const val BAN_RADICALIZATION = 0.004
        private const val RADICAL_ANGER = 0.004
        private const val RADICAL_HEAT = 0.03
        private const val RADICAL_COOLING = 0.004
        private const val HEAT_DECAY = 0.93
        private const val HISTORY = 120
        const val INSURRECTION_CROWD = 800.0
        const val INSURRECTION_RADICAL = 0.75
        const val RIOT_CROWD = 150.0
        const val RIOT_RADICAL = 0.5
        const val BLOCKADE_CROWD = 400.0
        private const val REVOLUTION_DAYS = 14
        private const val REVOLUTION_POPULARITY = 0.3
        private const val REVOLUTION_LOYALTY = 0.5
        private const val REVOLUTION_CHANCE = 0.08
        private const val MIN_DAYS = 7.0
        private const val END_CROWD = 15.0
        private const val MAX_PAST = 12
        private const val REFERENCE_CROWD = 500.0
        private const val POPULARITY_DRAIN = 0.0004
        private const val MONTH = 30.0
        private const val BLOCKADE_OUTPUT = 0.0002
        private const val RIOT_SHOCK = 0.002
        private const val RIOT_COST = 0.02
        private const val RIOT_SECURITY = 0.001
        private const val INSURRECTION_OUTPUT = 0.0006
        private const val DEFENSE_WEIGHT = 0.3
        private const val INVADED = 0.2
        private const val INSURRECTION_STRAIN = 0.15
        private const val LOW_POPULARITY = 0.25
        private const val UNPOPULAR = 0.15
        private const val LOYALTY_ADJUST = 0.2
        private const val PLOT_LOYALTY = 0.5
        private const val PLOT_SPEED = 0.6
        private const val PLOT_DECAY = 0.1
        private const val DETECTION_LEVEL = 0.3

        /** Un événement lié à une cause relance son mouvement, ou en fait naître un. */
        fun onEvent(ctx: SimulationContext, eventId: String, factor: Double) {
            val file = ctx.db.unrest ?: return
            file.causes.filter { eventId in it.events }.forEach { UnrestService(ctx).spark(it, EVENT_SPARK * factor) }
        }

        private const val EVENT_SPARK = 0.6
    }
}

/** Les réponses du président à la rue, et ses relations avec l'armée. */
class UnrestService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.unrest
    private val agenda = AgendaService(ctx)

    val available: Boolean get() = file != null

    val state: UnrestState get() = ctx.state.unrest.also { s -> if (s.armyLoyalty < 0) s.armyLoyalty = file?.armyLoyalty ?: 0.8 }

    val baseLoyalty: Double get() = file?.armyLoyalty ?: 0.8

    fun cause(id: String) = file?.causes?.firstOrNull { it.id == id }

    /** Colère des groupes que la cause mobilise (0 : satisfaits, 1 : furieux). */
    fun anger(cause: UnrestCause): Double {
        val groups = cause.groups.mapNotNull { ctx.state.opinion.groups[it]?.effective }
        val approval = if (groups.isEmpty()) ctx.state.opinion.nationalApproval else groups.average()
        return (1 - approval / NEUTRAL_APPROVAL).coerceIn(0.0, 1.0)
    }

    fun people(thousands: Double): String = if (thousands >= 1000) String.format(java.util.Locale.FRENCH, "%.1f million", thousands / 1000) else "${(Math.round(thousands / 10) * 10_000).let { fr.president.engine.util.Formatting.integer(it) }}"

    /** Faire naître un mouvement (ou relancer celui qui existe). */
    fun spark(cause: UnrestCause, strength: Double) {
        val s = state
        s.movements.firstOrNull { it.cause == cause.id }?.let { it.momentum += strength * RELAUNCH; return }
        if (s.movements.size >= MAX_MOVEMENTS) return
        s.lastSpawn[cause.id]?.let { if (it.daysUntil(ctx.now) < SPAWN_COOLDOWN) return }
        s.lastSpawn[cause.id] = ctx.now
        val m = Movement(ctx.state.newId("mvt"), cause.id, ctx.now, cause.crowdThousands * START_SHARE * strength, momentum = strength.coerceIn(MIN_START, MAX_START))
        s.movements += m
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Mobilisation : ${cause.label.lowercase()}",
            "${cause.slogan} Les premiers cortèges se forment. Vous pouvez parler aux Français, recevoir les organisateurs, céder ou tenir bon (écran « La rue et l'armée »).", null)
        JournalService(ctx).add("Société", "Début du mouvement : ${cause.label.lowercase()}", Tone.WARNING)
    }

    /** Une réforme ou une loi qui fâche les groupes d'une cause fait descendre dans la rue. */
    fun onPolicy(kind: PolicyKind, itemId: String, option: Int) {
        val f = file ?: return
        for (c in f.causes) {
            val hit = when (kind) {
                PolicyKind.REFORM -> itemId in c.reforms
                PolicyKind.LAW -> itemId in c.laws && fr.president.engine.government.LawService(ctx).law(itemId)?.options?.getOrNull(option)
                    ?.effects.orEmpty().any { e -> e.amount < 0 && c.groups.any { g -> e.target == "opinion.group.$g" } }
                else -> false
            }
            if (hit) spark(c, POLICY_SPARK)
        }
    }

    /** Chaque mois : la colère diffuse peut faire naître un mouvement. */
    fun monthlyAnger(f: UnrestFile) {
        val e = ctx.state.playerCountry.economy
        val popularity = ctx.state.opinion.nationalApproval
        fun maybe(id: String, chance: Double) { cause(id)?.let { if (ctx.rng.chance(chance)) spark(it, ANGER_SPARK) } }
        if (popularity < ANGRY_POPULARITY) maybe("anger", (ANGRY_POPULARITY - popularity) * 2)
        if (e.inflation > HIGH_INFLATION) maybe("cost_of_living", (e.inflation - HIGH_INFLATION) * 10)
        if (fr.president.engine.military.Geopolitics(ctx).isAtWar(ctx.state.player.countryId) && ctx.state.military.warWeariness > WEARY) maybe("war", ctx.state.military.warWeariness - WEARY)
        f.causes.filter { it.id !in setOf("anger", "cost_of_living", "war") }.forEach { c -> if (anger(c) > FURIOUS) maybe(c.id, FURIOUS_CHANCE) }
    }

    // ---- Réponses à un mouvement ----

    data class Response(val id: String, val label: String, val description: String, val blocker: String?)

    fun responses(m: Movement): List<Response> {
        val cause = cause(m.cause) ?: return emptyList()
        fun wait(id: String, days: Double): String? = m.lastAction[id]?.let { val left = days - it.daysUntil(ctx.now); if (left > 0) "Possible dans ${kotlin.math.ceil(left).toInt()} j." else null }
        return listOfNotNull(
            Response("address", "S'adresser aux Français à la télévision", "Expliquer, apaiser. Si vous êtes impopulaire, cela peut attiser la colère.", wait("address", 14.0) ?: agenda.blocker(SPEECH)),
            Response("dialogue", "Recevoir les organisateurs à l'Élysée", "Le mouvement se calme un peu, sa frange dure s'isole.", wait("dialogue", 7.0) ?: agenda.blocker(MEETING)),
            Response("concede", "Céder : ${cause.concession.label.replaceFirstChar { it.lowercase() }}", "Le mouvement retombe vite. Coût budgétaire, et une image de faiblesse auprès d'une partie de l'opinion.", if (m.conceded) "Déjà fait." else null),
            Response("policing", "Maintien de l'ordre proportionné", "Encadrer les cortèges, isoler les casseurs : moins de violences.", wait("policing", 7.0)),
            Response("crackdown", "Fermeté : interpellations massives, nasses, LBD", "La foule recule, mais la colère et la radicalité montent. Risque de bavure.", wait("crackdown", 7.0)),
            if (m.phase == MovementPhase.INSURRECTION) Response("army", "Faire appel à l'armée", "Peut briser l'insurrection… si l'armée obéit.", wait("army", 15.0)) else null,
        )
    }

    fun respond(movementId: String, action: String): Result<String> = runCatching {
        val m = state.movements.first { it.id == movementId }
        val cause = cause(m.cause)!!
        responses(m).firstOrNull { it.id == action }?.blocker?.let { error(it) }
        m.lastAction[action] = ctx.now
        val popularity = ctx.state.opinion.nationalApproval
        val text = when (action) {
            "address" -> {
                agenda.book("Allocution télévisée", SPEECH)
                if (popularity >= ADDRESS_THRESHOLD) { m.momentum *= ADDRESS_CALM; "Votre allocution porte : une partie des manifestants rentre chez elle." }
                else { m.momentum *= ADDRESS_BACKFIRE; m.heat += 0.1; "Votre allocution passe mal : « Il ne nous écoute pas ! » Les cortèges grossissent." }
            }
            "dialogue" -> {
                agenda.book("Réunion avec les organisateurs du mouvement", MEETING)
                m.momentum *= DIALOGUE_CALM
                m.radicalization = (m.radicalization - DIALOGUE_RADICAL).coerceAtLeast(0.0)
                cause.actors.forEach { ctx.state.actors.actors[it]?.let { a -> a.goodwill += DIALOGUE_GOODWILL } }
                "Les organisateurs saluent un « premier pas ». La frange radicale dénonce une trahison."
            }
            "concede" -> {
                m.conceded = true
                m.momentum *= CONCEDE_CALM
                m.radicalization = (m.radicalization - CONCEDE_RADICAL).coerceAtLeast(0.0)
                cause.concession.effects.forEach { ctx.effects.trigger(it, null, emptyMap(), "unrest:${cause.id}") }
                ctx.effects.trigger(EffectSpec("president.popularity", -CONCEDE_WEAKNESS), null, emptyMap(), "unrest")
                JournalService(ctx).add("Société", "Concession au mouvement : ${cause.concession.label.lowercase()}", Tone.NEUTRAL)
                "Concession annoncée : ${cause.concession.label.lowercase()}. Les syndicats crient victoire."
            }
            "policing" -> {
                m.radicalization = (m.radicalization - POLICING_RADICAL).coerceAtLeast(0.0)
                ctx.effects.trigger(EffectSpec("budget.oneOff", POLICING_COST), null, emptyMap(), "unrest")
                "Préfets et forces mobiles encadrent les cortèges : les débordements reculent."
            }
            "crackdown" -> {
                m.crowd *= CRACKDOWN_CROWD
                m.heat += CRACKDOWN_HEAT
                m.radicalization = (m.radicalization + CRACKDOWN_RADICAL).coerceAtMost(1.0)
                ctx.effects.trigger(EffectSpec("opinion.group.young", -0.01), null, emptyMap(), "unrest")
                ctx.effects.trigger(EffectSpec("opinion.group.seniors", 0.005), null, emptyMap(), "unrest")
                if (ctx.rng.chance(BLUNDER)) {
                    m.momentum += BLUNDER_MOMENTUM
                    ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "Bavure policière",
                        "Un manifestant grièvement blessé : la vidéo fait le tour des réseaux. La colère redouble.", null)
                    "Bavure : un manifestant grièvement blessé. Le mouvement repart de plus belle."
                } else "Des centaines d'interpellations : les rues se vident, pour l'instant."
            }
            "army" -> {
                val s = state
                if (ctx.rng.chance(s.armyLoyalty * ARMY_OBEY)) {
                    m.crowd *= ARMY_CROWD
                    m.radicalization = (m.radicalization - ARMY_RADICAL).coerceAtLeast(0.0)
                    ctx.effects.trigger(EffectSpec("president.popularity", -0.05), null, emptyMap(), "unrest")
                    ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", -0.05), null, emptyMap(), "unrest")
                    JournalService(ctx).add("Société", "L'armée déployée face à l'insurrection", Tone.BAD)
                    "Les blindés dans les rues : l'insurrection recule. Le pays est sous le choc ; l'Europe s'inquiète."
                } else {
                    s.armyLoyalty = (s.armyLoyalty - ARMY_REFUSAL).coerceAtLeast(0.0)
                    s.conspiracy += ARMY_REFUSAL_PLOT
                    m.momentum *= 1.3
                    JournalService(ctx).add("Société", "L'état-major refuse de tirer sur la foule", Tone.BAD)
                    "L'état-major refuse : « L'armée ne tirera pas sur les Français. » Votre autorité s'effondre."
                }
            }
            else -> error("Action inconnue.")
        }
        text
    }

    // ---- Armée ----

    fun armyBlocker(action: String): String? {
        val s = state
        s.lastArmyAction[action]?.let { if (it.daysUntil(ctx.now) < ARMY_COOLDOWN) return "Déjà fait récemment." }
        return when (action) {
            "purge" -> if (!s.plotKnown) "Aucun complot identifié." else null
            "visit" -> agenda.blocker(VISIT)
            else -> null
        }
    }

    fun armyAction(action: String): Result<String> = runCatching {
        armyBlocker(action)?.let { error(it) }
        val s = state
        s.lastArmyAction[action] = ctx.now
        when (action) {
            "pay" -> {
                s.armyLoyalty = (s.armyLoyalty + PAY_LOYALTY).coerceAtMost(1.0)
                ctx.effects.trigger(EffectSpec("budget.oneOff", PAY_COST, days = 365.0), null, emptyMap(), "army")
                "Solde et primes revalorisées : les militaires apprécient."
            }
            "visit" -> {
                agenda.book("Visite aux troupes", VISIT)
                s.armyLoyalty = (s.armyLoyalty + VISIT_LOYALTY).coerceAtMost(1.0)
                "Vous partagez la soupe avec un régiment : le chef des armées est là."
            }
            "purge" -> {
                s.conspiracy = 0.0
                s.plotKnown = false
                s.armyLoyalty = (s.armyLoyalty - PURGE_COST).coerceAtLeast(0.0)
                JournalService(ctx).add("Défense", "Officiers comploteurs limogés", Tone.NEUTRAL)
                ctx.notifications.news(NotificationCategory.SECURITY, "Le chef d'état-major et plusieurs généraux limogés")
                "Les comploteurs sont limogés. L'armée grince, mais le danger est écarté."
            }
            else -> error("Action inconnue.")
        }
    }

    /** Tentative de coup d'État militaire contre le président. */
    fun coupAttempt() {
        val s = state
        s.coups++
        val popularity = ctx.state.opinion.nationalApproval
        val success = (1 - s.armyLoyalty) * COUP_LOYALTY + (COUP_POPULARITY - popularity).coerceAtLeast(0.0)
        s.conspiracy = 0.0
        s.plotKnown = false
        if (ctx.rng.chance(success)) {
            overthrow("Un coup d'État militaire vous a renversé : un « Comité de salut national » prend le pouvoir.")
            return
        }
        s.armyLoyalty = (s.armyLoyalty + FAILED_COUP_LOYALTY).coerceAtMost(1.0)
        ctx.effects.trigger(EffectSpec("president.popularity", FAILED_COUP_RALLY), null, emptyMap(), "coup")
        ctx.notifications.post(NotificationCategory.SECURITY, Urgency.URGENT, "Tentative de coup d'État déjouée",
            "Des blindés ont pris position autour de l'Élysée avant de se rendre. Les Français se rassemblent derrière les institutions.", null)
        JournalService(ctx).add("Défense", "Tentative de coup d'État déjouée", Tone.WARNING)
    }

    fun overthrow(reason: String) {
        ctx.state.player.gameOver = GameOver(ctx.now, reason)
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "Fin de votre présidence", reason, null)
        JournalService(ctx).add("Société", reason, Tone.BAD)
    }

    companion object {
        private const val NEUTRAL_APPROVAL = 0.5
        private const val RELAUNCH = 0.5
        const val MAX_MOVEMENTS = 2
        private const val SPAWN_COOLDOWN = 90.0
        private const val START_SHARE = 0.3
        private const val MIN_START = 0.5
        private const val MAX_START = 1.5
        private const val POLICY_SPARK = 1.2
        private const val ANGER_SPARK = 0.9
        private const val ANGRY_POPULARITY = 0.3
        private const val HIGH_INFLATION = 0.045
        private const val WEARY = 0.4
        private const val FURIOUS = 0.6
        private const val FURIOUS_CHANCE = 0.1
        private const val ADDRESS_THRESHOLD = 0.35
        private const val ADDRESS_CALM = 0.85
        private const val ADDRESS_BACKFIRE = 1.15
        private const val DIALOGUE_CALM = 0.85
        private const val DIALOGUE_RADICAL = 0.1
        private const val DIALOGUE_GOODWILL = 0.03
        private const val CONCEDE_CALM = 0.4
        private const val CONCEDE_RADICAL = 0.2
        private const val CONCEDE_WEAKNESS = 0.01
        private const val POLICING_RADICAL = 0.08
        private const val POLICING_COST = 0.05
        private const val CRACKDOWN_CROWD = 0.75
        private const val CRACKDOWN_HEAT = 0.35
        private const val CRACKDOWN_RADICAL = 0.1
        private const val BLUNDER = 0.2
        private const val BLUNDER_MOMENTUM = 0.6
        private const val ARMY_OBEY = 0.9
        private const val ARMY_CROWD = 0.3
        private const val ARMY_RADICAL = 0.4
        private const val ARMY_REFUSAL = 0.2
        private const val ARMY_REFUSAL_PLOT = 0.4
        private const val ARMY_COOLDOWN = 90.0
        private const val PAY_LOYALTY = 0.1
        private const val PAY_COST = 1.5
        private const val VISIT_LOYALTY = 0.03
        private const val PURGE_COST = 0.05
        private const val COUP_LOYALTY = 0.9
        private const val COUP_POPULARITY = 0.3
        private const val FAILED_COUP_LOYALTY = 0.15
        private const val FAILED_COUP_RALLY = 0.06
        val SPEECH = AgendaCost(0.5)
        val MEETING = AgendaCost(0.5)
        val VISIT = AgendaCost(1.0)
    }
}
