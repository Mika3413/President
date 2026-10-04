package fr.president.engine.session

import fr.president.engine.effects.EffectSpec
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/** Durée d'agenda d'une activité, et si elle se déroule à l'étranger. */
@Serializable
data class AgendaCost(val days: Double, val abroad: Boolean = false, val place: String = "")

/**
 * Règles de l'agenda : jours de déplacement disponibles par semaine glissante et temps pris par
 * chaque activité (décisions nationales, entretiens, déplacements proposés dans les courriers,
 * sommets internationaux auxquels le président se rend).
 */
@Serializable
data class AgendaRules(
    val weeklyDays: Double = 5.0,
    val actions: Map<String, AgendaCost> = emptyMap(),
    val eventOptions: Map<String, AgendaCost> = emptyMap(),
    val summits: Map<String, AgendaCost> = emptyMap(),
    val foreignTalkDays: Double = 0.25,
    val localTalkDays: Double = 0.25,
    val localVisitDays: Double = 1.0,
)

@Serializable
data class AgendaEntry(val label: String, val start: WorldTime, val end: WorldTime, val days: Double, val abroad: Boolean, val place: String = "")

@Serializable
class AgendaState(val entries: MutableList<AgendaEntry> = mutableListOf())

/**
 * Le temps du président est compté, comme dans Geopolitical Simulator : un voyage à Pékin, un
 * sommet ou un tour de France occupent des jours. Pendant un déplacement à l'étranger, pas de
 * visite en France ; une crise grave qui éclate en son absence lui est reprochée.
 */
class AgendaService(private val ctx: SimulationContext) {
    private val rules get() = ctx.playerData.agenda ?: AgendaRules()
    private val state get() = ctx.state.agenda

    data class Summary(val usedDays: Double, val capacity: Double, val current: AgendaEntry?, val upcoming: List<AgendaEntry>, val recent: List<AgendaEntry>)

    fun costOfAction(id: String): AgendaCost? = rules.actions[id]
    fun costOfEventOption(id: String): AgendaCost? = rules.eventOptions[id]

    fun costOfTalk(foreign: Boolean, visit: Boolean) = AgendaCost(
        when { foreign -> rules.foreignTalkDays; visit -> rules.localVisitDays; else -> rules.localTalkDays },
    )

    /** Déplacement en cours à l'étranger, s'il y en a un. */
    fun abroad(): AgendaEntry? = state.entries.firstOrNull { it.abroad && it.start <= ctx.now && ctx.now < it.end }

    /** Jours d'agenda engagés sur les sept derniers jours. */
    fun usedDays(): Double = state.entries.filter { it.start.daysUntil(ctx.now) < WEEK && it.start <= ctx.now }.sumOf { it.days }

    /** Raison pour laquelle l'activité ne tient pas dans l'agenda, ou null. */
    fun blocker(cost: AgendaCost?): String? {
        if (cost == null || cost.days <= 0) return null
        // Un appel se passe de l'étranger ; une visite ou un déplacement, non.
        if (cost.days >= HALF_DAY) abroad()?.let { trip ->
            val left = kotlin.math.ceil(ctx.now.daysUntil(trip.end)).toInt().coerceAtLeast(1)
            return "Vous êtes en déplacement (${trip.label}) : de retour dans $left j."
        }
        val used = usedDays()
        if (used + cost.days > rules.weeklyDays + EPSILON) {
            // Le créneau se libère quand assez d'activités sortent de la semaine glissante.
            var free = rules.weeklyDays - used
            val leaving = state.entries.filter { it.start.daysUntil(ctx.now) < WEEK }.sortedBy { it.start }
            for (e in leaving) {
                free += e.days
                if (free + EPSILON >= cost.days) {
                    val wait = kotlin.math.ceil(WEEK - e.start.daysUntil(ctx.now)).toInt().coerceAtLeast(1)
                    return "Agenda complet cette semaine (${fmt(used)} j sur ${fmt(rules.weeklyDays)}) : créneau libre dans $wait j."
                }
            }
            return "Agenda complet cette semaine."
        }
        return null
    }

    /** Inscrit l'activité à l'agenda à partir de maintenant. */
    fun book(label: String, cost: AgendaCost) {
        if (cost.days <= 0) return
        state.entries += AgendaEntry(label, ctx.now, ctx.now.plusHours((cost.days * HOURS).toLong()), cost.days, cost.abroad, cost.place)
        state.entries.removeAll { it.end.daysUntil(ctx.now) > KEEP_DAYS }
    }

    /** Sommet international : le président s'y rend, que l'agenda soit plein ou non. */
    fun attendSummit(eventId: String, label: String) {
        rules.summits[eventId]?.let { book(label, it) }
    }

    /** Une crise grave éclate pendant un voyage à l'étranger : on reproche son absence au président. */
    fun crisisWhileAway(headline: String) {
        val trip = abroad() ?: return
        ctx.effects.trigger(EffectSpec("opinion.national", -ABSENCE_COST), null, emptyMap(), "agenda")
        ctx.notifications.news(fr.president.engine.notifications.NotificationCategory.POLITICS,
            "Le président en déplacement (${trip.label}) pendant : ${headline.lowercase()}", null)
    }

    fun summary(): Summary {
        val now = ctx.now
        return Summary(
            usedDays(), rules.weeklyDays,
            state.entries.firstOrNull { it.start <= now && now < it.end },
            state.entries.filter { it.start > now }.sortedBy { it.start },
            state.entries.filter { it.end <= now && it.end.daysUntil(now) < WEEK }.sortedByDescending { it.start },
        )
    }

    /** « 3 j d'agenda, à l'étranger » : à afficher sur une décision. */
    fun describe(cost: AgendaCost?): String? = cost?.takeIf { it.days > 0 }?.let {
        "◷ " + (if (it.days < 1) "une demi-journée" else "${fmt(it.days)} j") + " d'agenda" + if (it.abroad) " · à l'étranger" else ""
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.FRENCH, "%.1f", v)

    private companion object {
        const val WEEK = 7.0
        const val HOURS = 24.0
        const val KEEP_DAYS = 30.0
        const val EPSILON = 1e-6
        const val ABSENCE_COST = 0.004
        const val HALF_DAY = 0.5
    }
}
