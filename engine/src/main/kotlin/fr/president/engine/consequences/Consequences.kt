package fr.president.engine.consequences

import fr.president.engine.effects.EffectSpec
import fr.president.engine.events.VariableResolver
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Effet mensuel d'une conséquence, multiplié par sa gravité. [cap] : total cumulé maximal (par point de gravité), qui se résorbe ensuite. */
@Serializable
data class ConsequenceEffect(val target: String, val amount: Double, val cap: Double? = null)

/** Effet sur chaque département, pondéré : all, urban, rural, poor (revenus faibles), unemployed (chômage local), crime. */
@Serializable
data class DepartmentEffect(val field: String, val amount: Double, val weight: String = "all")

/**
 * Une règle de conséquence : quand une variable passe un seuil, le pays réagit chaque mois, d'autant
 * plus fort que l'on s'en éloigne (gravité 0..[maxSeverity]). Tout est dans les données.
 */
@Serializable
data class ConsequenceRule(
    val id: String,
    val label: String,
    val icon: String = "⚠",
    val category: String,
    val variable: String,
    val above: Double? = null,
    val below: Double? = null,
    /** Écart au seuil qui vaut une gravité de 1. */
    val scale: Double,
    val maxSeverity: Double = 3.0,
    /** Pourquoi cela arrive (le mécanisme, en clair). */
    val why: String,
    /** Message quand la conséquence apparaît. */
    val onset: String,
    /** Titre quand elle devient grave (gravité ≥ [severeAt]). */
    val severe: String = "",
    val severeAt: Double = 2.0,
    /** Ce qu'il faut faire pour en sortir. */
    val fix: String = "",
    val monthly: List<ConsequenceEffect> = emptyList(),
    val departments: List<DepartmentEffect> = emptyList(),
    /** Événements rendus plus fréquents : multiplicateur supplémentaire par point de gravité. */
    val events: Map<String, Double> = emptyMap(),
    /** Mouvements de contestation attisés : probabilité mensuelle par point de gravité. */
    val unrest: Map<String, Double> = emptyMap(),
)

@Serializable
data class ConsequenceFile(val rules: List<ConsequenceRule>)

@Serializable
class ActiveConsequence(
    val id: String,
    val since: WorldTime,
    var severity: Double,
    var peak: Double = 0.0,
    var severeNotified: Boolean = false,
    /** Faux : la cause a disparu, les effets cumulés se résorbent. */
    var active: Boolean = true,
    /** Effets cumulés (cibles plafonnées) encore en vigueur. */
    val applied: MutableMap<String, Double> = mutableMapOf(),
)

@Serializable
class ConsequenceState(
    val current: MutableMap<String, ActiveConsequence> = mutableMapOf(),
    /** Nombre de fois que chaque conséquence est apparue pendant le mandat. */
    val occurrences: MutableMap<String, Int> = mutableMapOf(),
)

/**
 * Les conséquences en chaîne : chaque mois, chaque règle compare une variable de la simulation
 * (qualité d'un service, financement d'un poste, RSA rapporté au SMIC, déficit, libertés...) à son
 * seuil. Au-delà, le pays réagit : opinion, économie, départements, acteurs, événements plus
 * fréquents, contestation. Quand la cause disparaît, les dégâts cumulés se résorbent lentement.
 */
class ConsequenceSystem : SimulationSystem {
    override val name = "consequences"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        if (ctx.state.player.gameOver != null) return
        ConsequenceService(ctx).monthly()
    }
}

class ConsequenceService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.consequences
    private val state get() = ctx.state.consequences
    private val resolver by lazy { VariableResolver(ctx) }

    fun rule(id: String) = file?.rules?.firstOrNull { it.id == id }

    /** Gravité actuelle d'une règle (0 : pas déclenchée). */
    fun severity(r: ConsequenceRule): Double {
        val v = resolver.resolve(r.variable) ?: return 0.0
        val excess = when {
            r.above != null && v > r.above -> v - r.above
            r.below != null && v < r.below -> r.below - v
            else -> return 0.0
        }
        return (excess / r.scale.coerceAtLeast(1e-9)).coerceIn(0.0, r.maxSeverity)
    }

    fun value(r: ConsequenceRule): Double? = resolver.resolve(r.variable)

    /** Conséquences en cours, de la plus grave à la plus légère. */
    fun active(): List<Pair<ConsequenceRule, ActiveConsequence>> = state.current.values.filter { it.active }
        .mapNotNull { a -> rule(a.id)?.let { it to a } }.sortedByDescending { it.second.severity }

    /** Conséquences proches du seuil (alerte avant qu'elles ne frappent). */
    fun nearThreshold(margin: Double = 0.5): List<Pair<ConsequenceRule, Double>> = file?.rules.orEmpty()
        // Les règles tout-ou-rien (article 16) n'ont pas d'« approche » du seuil.
        .filter { state.current[it.id]?.active != true && it.maxSeverity > 1.0 }
        .mapNotNull { r ->
            val v = resolver.resolve(r.variable) ?: return@mapNotNull null
            val gap = when {
                r.above != null -> (r.above - v) / r.scale
                r.below != null -> (v - r.below) / r.scale
                else -> return@mapNotNull null
            }
            if (gap in 0.0..margin) r to gap else null
        }.sortedBy { it.second }

    /** Seuil qu'un changement de levier ferait franchir : la règle, sa gravité prévue, et si l'effet ne vient qu'à terme. */
    data class Crossing(val rule: ConsequenceRule, val severity: Double, val later: Boolean)

    /**
     * Ce qu'un réglage déclencherait : pour chaque règle dont la variable dépend directement du levier
     * (crédits d'un poste, réglage chiffré, RSA/SMIC, qualité du service financé à terme...).
     */
    fun crossings(leverId: String, to: Double): List<Crossing> {
        val rules = file?.rules ?: return emptyList()
        val economy = ctx.state.playerCountry.economy
        val spendKey = leverId.removePrefix("spend:").takeIf { leverId.startsWith("spend:") }
        val item = spendKey?.let { economy.budget?.spending?.get(it) }
        val out = mutableListOf<Crossing>()
        for (r in rules) {
            var later = false
            val projected: Double = when {
                r.variable == "spending.$spendKey" -> to
                r.variable == "lever.$leverId" -> to
                r.variable == "derived.rsaToSmic" && (leverId == "param:rsa_amount" || leverId == "param:smic_boost") -> {
                    val l = fr.president.engine.legislation.LeverService(ctx)
                    val rsa = if (leverId == "param:rsa_amount") to else l.current("param:rsa_amount")
                    val boost = if (leverId == "param:smic_boost") to else l.current("param:smic_boost")
                    rsa / (SMIC_NET * (1 + boost / 100))
                }
                r.variable == "derived.servicesFunding" && item?.domain != null && item.domain != "pensions" -> {
                    val items = economy.budget?.spending?.values?.filter { it.domain != null && it.domain != "pensions" }.orEmpty()
                    if (items.isEmpty()) continue
                    (items.sumOf { it.policyFactor } - item.policyFactor + to) / items.size
                }
                item?.domain != null && r.variable == "quality.${item.domain}" -> {
                    later = true
                    val now = ctx.state.playerCountry.services[item.domain] ?: continue
                    now + ctx.db.economyParameters.serviceQualityFundingSensitivity * (to - item.policyFactor)
                }
                else -> continue
            }
            val sev = severityAt(r, projected)
            if (sev > MIN_SEVERITY && sev > (state.current[r.id]?.takeIf { it.active }?.severity ?: 0.0) + MIN_SEVERITY) out += Crossing(r, sev, later)
        }
        return out.sortedByDescending { it.severity }
    }

    private fun severityAt(r: ConsequenceRule, v: Double): Double {
        val excess = when {
            r.above != null && v > r.above -> v - r.above
            r.below != null && v < r.below -> r.below - v
            else -> return 0.0
        }
        return (excess / r.scale.coerceAtLeast(1e-9)).coerceIn(0.0, r.maxSeverity)
    }

    fun monthly() {
        val rules = file?.rules ?: return
        for (r in rules) {
            val sev = severity(r)
            val a = state.current[r.id]
            if (sev > MIN_SEVERITY) {
                val cur = if (a == null || !a.active) start(r, sev, a) else a
                cur.severity = sev
                cur.peak = maxOf(cur.peak, sev)
                hit(r, cur, sev)
                if (!cur.severeNotified && r.severe.isNotEmpty() && sev >= r.severeAt) {
                    cur.severeNotified = true
                    ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "${r.icon} ${r.severe}", r.why + if (r.fix.isNotEmpty()) "\nPour en sortir : ${r.fix}" else "", r.id)
                }
            } else if (a != null) {
                if (a.active) {
                    a.active = false
                    a.severity = 0.0
                    ctx.notifications.news(NotificationCategory.POLITICS, "${r.icon} Fin : ${r.label.replaceFirstChar { it.lowercase() }}")
                }
                unwind(a)
                if (a.applied.values.all { abs(it) < 1e-6 }) state.current.remove(r.id)
            }
        }
    }

    private fun start(r: ConsequenceRule, sev: Double, previous: ActiveConsequence?): ActiveConsequence {
        val a = ActiveConsequence(r.id, ctx.now, sev)
        previous?.applied?.let { a.applied += it }
        state.current[r.id] = a
        state.occurrences.merge(r.id, 1, Int::plus)
        ctx.notifications.post(NotificationCategory.POLITICS, if (sev >= r.severeAt) Urgency.URGENT else Urgency.IMPORTANT,
            "${r.icon} ${r.label}", r.onset + "\nPourquoi : " + r.why + if (r.fix.isNotEmpty()) "\nPour en sortir : ${r.fix}" else "", r.id)
        if (sev >= r.severeAt) a.severeNotified = true
        return a
    }

    private fun hit(r: ConsequenceRule, a: ActiveConsequence, sev: Double) {
        for (fx in r.monthly) {
            var delta = fx.amount * sev
            fx.cap?.let { cap ->
                val limit = abs(cap) * sev
                val done = a.applied[fx.target] ?: 0.0
                val room = limit - abs(done)
                if (room <= 0) return@let run { delta = 0.0 }
                if (abs(delta) > room) delta = room * kotlin.math.sign(delta)
                a.applied[fx.target] = done + delta
            }
            if (delta != 0.0) ctx.effects.trigger(EffectSpec(fx.target, delta), null, emptyMap(), "consequence:${r.id}")
        }
        if (r.departments.isNotEmpty()) departments(r, sev)
        if (r.unrest.isNotEmpty()) {
            val service = fr.president.engine.politics.UnrestService(ctx)
            r.unrest.forEach { (cause, chance) ->
                service.cause(cause)?.let { c -> if (ctx.rng.chance((chance * sev).coerceAtMost(MAX_CHANCE))) service.spark(c, UNREST_SPARK * sev) }
            }
        }
    }

    private fun departments(r: ConsequenceRule, sev: Double) {
        val depts = ctx.state.territory.departments.values
        if (depts.isEmpty()) return
        val national = ctx.state.playerCountry.economy.unemployment
        val avgCrime = depts.sumOf { it.crime } / depts.size
        for (d in depts) for (fx in r.departments) {
            val w = when (fx.weight) {
                "urban" -> d.urbanShare * 1.5
                "rural" -> (1 - d.urbanShare) * 1.5
                "poor" -> (2 - d.incomeIndex).coerceIn(0.3, 2.0)
                "unemployed" -> (d.unemployment / national.coerceAtLeast(0.01)).coerceIn(0.3, 2.5)
                "crime" -> (d.crime / avgCrime.coerceAtLeast(0.01)).coerceIn(0.3, 2.5)
                else -> 1.0
            }
            ctx.effects.apply("dept.${d.code}.${fx.field}", fx.amount * sev * w)
        }
    }

    /** Les dégâts cumulés se réparent lentement une fois la cause disparue. */
    private fun unwind(a: ActiveConsequence) {
        a.applied.keys.toList().forEach { t ->
            val v = a.applied[t] ?: 0.0
            val back = if (abs(v) < UNWIND_FLOOR) v else v * UNWIND_RATE
            ctx.effects.trigger(EffectSpec(t, -back), null, emptyMap(), "consequence:${a.id}")
            a.applied[t] = v - back
        }
    }

    companion object {
        private const val MIN_SEVERITY = 0.05
        private const val MAX_CHANCE = 0.6
        private const val UNREST_SPARK = 0.5
        private const val UNWIND_RATE = 0.15
        private const val UNWIND_FLOOR = 1e-5
        const val SMIC_NET = 1430.0

        fun severityLabel(sev: Double) = when { sev >= 2 -> "grave"; sev >= 1 -> "forte"; else -> "modérée" }

        /** Multiplicateur de probabilité d'un événement selon les conséquences en cours. */
        fun eventFactor(ctx: SimulationContext, eventId: String): Double {
            val file = ctx.db.consequences ?: return 1.0
            var f = 1.0
            for (a in ctx.state.consequences.current.values) {
                if (!a.active) continue
                val boost = file.rules.firstOrNull { it.id == a.id }?.events?.get(eventId) ?: continue
                f *= 1 + boost * a.severity
            }
            return f.coerceAtMost(MAX_EVENT_FACTOR)
        }

        private const val MAX_EVENT_FACTOR = 6.0
    }
}
