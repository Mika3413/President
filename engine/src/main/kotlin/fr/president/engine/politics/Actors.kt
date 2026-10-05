package fr.president.engine.politics

import fr.president.engine.data.TaxPayer
import fr.president.engine.economy.BudgetCalculator
import fr.president.engine.effects.EffectSpec
import fr.president.engine.government.LawService
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.territory.ActionCategory
import fr.president.engine.time.WorldTime
import fr.president.engine.util.clamp01
import kotlinx.serialization.Serializable

@Serializable
data class ActorDemand(
    val id: String,
    val label: String,
    val costBillions: Double = 0.0,
    val effects: List<EffectSpec> = emptyList(),
    /** Acteurs que cette concession mécontente. */
    val opposed: List<String> = emptyList(),
)

@Serializable
data class ActorDef(
    val id: String,
    val label: String,
    val kind: String,
    val icon: String = "",
    val influence: Double,
    val satisfaction: Double,
    /** Groupes sociaux que l'acteur entraîne avec lui. */
    val groups: List<String> = emptyList(),
    val description: String = "",
    /** Préférences : loi -> (option -> poids). */
    val laws: Map<String, Map<String, Double>> = emptyMap(),
    /** Réformes adoptées -> poids. */
    val reforms: Map<String, Double> = emptyMap(),
    /** Situation du pays -> poids (sur l'écart au début de la partie). */
    val conditions: Map<String, Double> = emptyMap(),
    val demands: List<ActorDemand> = emptyList(),
    /** Événements que l'acteur déclenche quand il est en colère (grèves, blocages...). */
    val mobilize: List<String> = emptyList(),
)

@Serializable
data class ActorsFile(val kinds: List<ActionCategory> = emptyList(), val actors: List<ActorDef>)

@Serializable
class ActorState(
    var satisfaction: Double = 0.5,
    /** Bonus des concessions et rencontres, qui s'estompe avec le temps. */
    var goodwill: Double = 0.0,
    var lastMeeting: WorldTime? = null,
    val granted: MutableSet<String> = mutableSetOf(),
)

@Serializable
class ActorsState(
    val actors: MutableMap<String, ActorState> = mutableMapOf(),
    /** Valeurs des conditions au début de la partie. */
    val baseline: MutableMap<String, Double> = mutableMapOf(),
)

/**
 * Syndicats, patronat, cultes, lobbies, associations : chaque mois, leur satisfaction suit vos
 * lois, vos réformes et l'état du pays ; elle entraîne les groupes sociaux qui les écoutent, et
 * un acteur influent en colère mobilise (grèves, blocages, manifestations).
 */
class ActorSystem : SimulationSystem {
    override val name = "actors"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val service = ActorService(ctx)
        val file = ctx.playerData.actors ?: return
        val state = ctx.state.actors
        if (state.baseline.isEmpty()) state.baseline.putAll(service.conditions())
        for (def in file.actors) {
            val a = state.actors.getOrPut(def.id) { ActorState(def.satisfaction) }
            a.goodwill *= GOODWILL_DECAY
            a.satisfaction += (service.target(def) - a.satisfaction) * ADJUST
            a.satisfaction = a.satisfaction.clamp01()
            // L'acteur entraîne les groupes qui l'écoutent.
            val pull = (a.satisfaction - NEUTRAL) * def.influence * GROUP_PULL
            def.groups.forEach { g -> ctx.effects.trigger(EffectSpec("opinion.group.$g", pull), null, emptyMap(), "actor:${def.id}") }
            if (a.satisfaction < ANGRY && def.mobilize.isNotEmpty() && ctx.rng.chance((ANGRY - a.satisfaction) * def.influence * MOBILIZE)) {
                val event = ctx.rng.pick(def.mobilize)
                ctx.effects.trigger(EffectSpec("chain.$event", 1.0, delayDays = MOBILIZE_DELAY), null, emptyMap(), "actor:${def.id}")
                ctx.notifications.news(NotificationCategory.POLITICS, "${def.label} appelle à la mobilisation", null)
            }
        }
    }

    private companion object {
        const val ADJUST = 0.35
        const val GOODWILL_DECAY = 0.85
        const val NEUTRAL = 0.5
        const val GROUP_PULL = 0.006
        const val ANGRY = 0.3
        const val MOBILIZE = 1.5
        const val MOBILIZE_DELAY = 5.0
    }
}

class ActorService(private val ctx: SimulationContext) {
    private val file get() = ctx.playerData.actors
    private val state get() = ctx.state.actors

    data class Row(val def: ActorDef, val satisfaction: Double, val reasons: List<Pair<String, Double>>, val meetBlocker: String?)

    val kinds: List<ActionCategory> get() = file?.kinds.orEmpty()

    fun rows(kind: String? = null): List<Row> = file?.actors.orEmpty().filter { kind == null || it.kind == kind }.map { def ->
        val s = state.actors[def.id]?.satisfaction ?: def.satisfaction
        Row(def, s, reasons(def), meetBlocker(def.id))
    }

    /** Ce qui, dans votre politique, plaît ou déplaît à l'acteur (pour l'afficher). */
    fun reasons(def: ActorDef): List<Pair<String, Double>> {
        val laws = LawService(ctx)
        val out = mutableListOf<Pair<String, Double>>()
        def.laws.forEach { (lawId, prefs) ->
            val law = laws.law(lawId) ?: return@forEach
            val o = laws.currentOption(law)
            prefs[o.id]?.let { out += "${law.title} : ${o.label}" to it }
        }
        def.reforms.forEach { (id, w) ->
            if (id in ctx.state.policy.adoptedReforms) out += (ctx.playerData.reforms?.reforms?.firstOrNull { it.id == id }?.title ?: id) to w
        }
        state.actors[def.id]?.goodwill?.takeIf { kotlin.math.abs(it) > 0.01 }?.let { out += "Vos gestes récents" to it }
        return out.sortedByDescending { kotlin.math.abs(it.second) }
    }

    fun target(def: ActorDef): Double {
        val laws = LawService(ctx)
        var t = def.satisfaction
        def.laws.forEach { (lawId, prefs) -> laws.law(lawId)?.let { t += prefs[laws.currentOption(it).id] ?: 0.0 } }
        def.reforms.forEach { (id, w) -> if (id in ctx.state.policy.adoptedReforms) t += w }
        val now = conditions()
        def.conditions.forEach { (k, w) -> t += w * ((now[k] ?: 0.0) - (state.baseline[k] ?: now[k] ?: 0.0)) }
        t += state.actors[def.id]?.goodwill ?: 0.0
        return t.coerceIn(0.0, 1.0)
    }

    /** Situation du pays telle que les acteurs la jugent. */
    fun conditions(): Map<String, Double> {
        val s = ctx.state
        val e = s.playerCountry.economy
        return mapOf(
            "unemployment" to e.unemployment,
            "purchasingPower" to e.wageIndex / e.priceLevel.coerceAtLeast(0.1),
            "spending" to e.spendingBillions / e.gdpBillions.coerceAtLeast(1.0),
            "agriculture" to (s.playerCountry.services["agriculture"] ?: 0.5),
            "businessConfidence" to e.businessConfidence,
            "consumerConfidence" to e.consumerConfidence,
            "businessTaxes" to BudgetCalculator.taxBurden(e, TaxPayer.BUSINESSES),
            "security" to (s.playerCountry.services["security"] ?: 0.5),
            "energy" to e.energyPriceIndex,
            "environment" to (s.playerCountry.services["environment"] ?: 0.5),
        )
    }

    fun meetBlocker(actorId: String): String? {
        val last = state.actors[actorId]?.lastMeeting
        if (last != null && last.daysUntil(ctx.now) < MEET_COOLDOWN) return "Rencontré récemment."
        return fr.president.engine.session.AgendaService(ctx).blocker(MEET_AGENDA)
    }

    /** Recevoir les dirigeants à l'Élysée : un peu de considération, une demi-journée d'agenda. */
    fun meet(actorId: String): Result<String> = runCatching {
        val def = file!!.actors.first { it.id == actorId }
        meetBlocker(actorId)?.let { error(it) }
        val a = state.actors.getOrPut(actorId) { ActorState(def.satisfaction) }
        a.lastMeeting = ctx.now
        a.goodwill += MEET_GOODWILL
        a.satisfaction = (a.satisfaction + MEET_GOODWILL).clamp01()
        fr.president.engine.session.AgendaService(ctx).book("Rencontre : ${def.label}", MEET_AGENDA)
        JournalService(ctx).add("Société", "Rencontre avec ${def.label}", Tone.NEUTRAL)
        "${def.label} : rencontre cordiale, les tensions s'apaisent un peu."
    }

    fun grantBlocker(actorId: String, demandId: String): String? =
        if (demandId in (state.actors[actorId]?.granted ?: emptySet<String>())) "Déjà accordé." else null

    /** Céder à une revendication : l'acteur est satisfait, ceux qui s'y opposent beaucoup moins. */
    fun grant(actorId: String, demandId: String): Result<String> = runCatching {
        val def = file!!.actors.first { it.id == actorId }
        val d = def.demands.first { it.id == demandId }
        grantBlocker(actorId, demandId)?.let { error(it) }
        val a = state.actors.getOrPut(actorId) { ActorState(def.satisfaction) }
        a.granted += demandId
        a.goodwill += GRANT_GOODWILL
        a.satisfaction = (a.satisfaction + GRANT_GOODWILL).clamp01()
        if (d.costBillions > 0) ctx.effects.trigger(EffectSpec("budget.oneOff", d.costBillions, days = GRANT_DAYS), null, emptyMap(), "actor:$actorId")
        d.effects.forEach { ctx.effects.trigger(it, null, emptyMap(), "actor:$actorId") }
        d.opposed.forEach { o ->
            val other = file!!.actors.firstOrNull { it.id == o } ?: return@forEach
            val s = state.actors.getOrPut(o) { ActorState(other.satisfaction) }
            s.goodwill -= OPPOSED_LOSS
            s.satisfaction = (s.satisfaction - OPPOSED_LOSS).clamp01()
        }
        JournalService(ctx).add("Société", "Concession à ${def.label} : ${d.label}", Tone.NEUTRAL)
        ctx.notifications.news(NotificationCategory.POLITICS, "${def.label} obtient gain de cause : ${d.label.lowercase()}", null)
        "${def.label} : revendication accordée." + if (d.opposed.isNotEmpty()) " Certains le prennent mal." else ""
    }

    private companion object {
        const val MEET_COOLDOWN = 60.0
        const val MEET_GOODWILL = 0.05
        const val GRANT_GOODWILL = 0.2
        const val OPPOSED_LOSS = 0.1
        const val GRANT_DAYS = 365.0
        val MEET_AGENDA = fr.president.engine.session.AgendaCost(0.5)
    }
}
