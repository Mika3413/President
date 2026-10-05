package fr.president.engine.government

import fr.president.engine.effects.EffectSpec
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

@Serializable
data class LawOption(
    val id: String,
    val label: String,
    val description: String,
    /** Exigence parlementaire supplémentaire pour adopter cette option. */
    val difficulty: Double = 0.0,
    val effects: List<EffectSpec> = emptyList(),
    val longTerm: List<EffectSpec> = emptyList(),
    /** Multiplicateurs de fréquence d'événements tant que l'option est en vigueur. */
    val events: Map<String, Double> = emptyMap(),
    /** Effet sur les indices (points sur 100) : libertés publiques, presse, État de droit. */
    val liberty: Double = 0.0,
    val press: Double = 0.0,
    val rule: Double = 0.0,
    /** Réglages lus par la simulation (durée du mandat, limite, 49.3, censure). */
    val flags: Map<String, Double> = emptyMap(),
    /** Attrait de l'option auprès des électeurs en cas de référendum (-1..1). */
    val appeal: Double = 0.0,
    /** Réforme du catalogue considérée comme adoptée quand cette option est en vigueur. */
    val reform: String? = null,
)

/** Loi réglée par un nombre (heures, âge...) : [values] donne la valeur de chaque option. */
@Serializable
data class LawNumeric(
    val unit: String = "",
    val values: List<Double>,
    val min: Double,
    val max: Double,
    val step: Double = 1.0,
    val decimals: Int = 0,
    val zeroLabel: String? = null,
)

@Serializable
data class LawDef(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val constitutional: Boolean = false,
    /** La première option est la situation au début de la partie. */
    val options: List<LawOption>,
    val numeric: LawNumeric? = null,
)

@Serializable
data class LawIndices(val liberty: Double = 75.0, val press: Double = 70.0, val rule: Double = 80.0)

@Serializable
data class LawsFile(
    val baseline: LawIndices = LawIndices(),
    val referendumDays: Int = 30,
    val categories: List<ActionCategory> = emptyList(),
    val laws: List<LawDef>,
)

@Serializable
data class LawReferendum(val lawId: String, val option: Int, val at: WorldTime)

@Serializable
class LawState(
    /** Option en vigueur pour chaque loi modifiée (index ; absente = situation de départ). */
    val values: MutableMap<String, Int> = mutableMapOf(),
    val referendums: MutableList<LawReferendum> = mutableListOf(),
    /** Date du dernier changement de chaque loi (on ne revient pas dessus aussitôt). */
    val changedAt: MutableMap<String, WorldTime> = mutableMapOf(),
    /** Valeur des lois réglées par un nombre. */
    val numbers: MutableMap<String, Double> = mutableMapOf(),
)

/**
 * Le catalogue des lois : société, justice, travail, libertés, Constitution. Une loi change par
 * un vote du Parlement (difficulté propre à chaque option), ou par référendum si elle touche à la
 * Constitution. Les options en vigueur modifient la fréquence de certains événements, trois
 * indices (libertés, presse, État de droit) et certaines règles du jeu (mandat, 49.3, censure).
 */
class LawService(private val ctx: SimulationContext) {
    private val file get() = ctx.playerData.laws
    private val state get() = ctx.state.laws

    val categories: List<ActionCategory> get() = file?.categories.orEmpty()
    val laws: List<LawDef> get() = file?.laws.orEmpty()

    fun law(id: String) = laws.firstOrNull { it.id == id }
    fun current(law: LawDef): Int = law.numeric?.let { n -> weights(law, value(law)).maxByOrNull { it.value }?.takeIf { it.value >= 0.5 }?.key ?: 0 }
        ?: state.values[law.id] ?: 0
    fun currentOption(law: LawDef): LawOption = law.options[current(law).coerceIn(law.options.indices)]

    /** Valeur d'une loi réglée par un nombre. */
    fun value(law: LawDef): Double {
        val n = law.numeric ?: return (state.values[law.id] ?: 0).toDouble()
        return state.numbers[law.id] ?: n.values.getOrNull(state.values[law.id] ?: 0) ?: n.values[0]
    }

    fun format(law: LawDef, v: Double = value(law)): String {
        val n = law.numeric ?: return law.options.getOrNull(v.toInt())?.label ?: ""
        if (v == 0.0 && n.zeroLabel != null) return n.zeroLabel
        val text = if (n.decimals == 0) Math.round(v).toString() else String.format(java.util.Locale.FRENCH, "%.${n.decimals}f", v)
        return "$text ${n.unit}".trim()
    }

    /**
     * Poids de chaque option pour une valeur donnée : 37 h = moitié du passage aux 39 h. Au-delà de
     * la dernière option, les effets se prolongent (42 h = 7/4 du passage aux 39 h).
     */
    fun weights(law: LawDef, v: Double): Map<Int, Double> {
        val n = law.numeric ?: return mapOf(v.toInt() to 1.0)
        val ref = n.values[0]
        if (kotlin.math.abs(v - ref) < 1e-9) return emptyMap()
        val side = n.values.withIndex().filter { (i, x) -> i > 0 && (x - ref) * (v - ref) > 0 }.sortedBy { kotlin.math.abs(it.value - ref) }
        if (side.isEmpty()) return emptyMap()
        var prev = ref
        var prevIndex = -1
        for ((i, x) in side) {
            if (kotlin.math.abs(v - ref) <= kotlin.math.abs(x - ref)) {
                val f = (v - prev) / (x - prev)
                return if (prevIndex < 0) mapOf(i to f) else mapOf(prevIndex to 1 - f, i to f)
            }
            prev = x; prevIndex = i
        }
        val last = side.last()
        return mapOf(last.index to (v - ref) / (last.value - ref))
    }

    /** Poids des options aujourd'hui (une loi à choix : l'option en vigueur). */
    fun currentWeights(law: LawDef): Map<Int, Double> = if (law.numeric != null) weights(law, value(law)) else mapOf(current(law) to 1.0)

    /** Raison pour laquelle on ne peut pas changer cette loi maintenant, ou null. */
    fun blocker(lawId: String, option: Int): String? {
        val law = law(lawId) ?: return "Loi inconnue"
        if (ctx.state.player.gameOver != null) return "La partie est terminée."
        if (law.numeric == null && option == current(law)) return "Déjà en vigueur."
        if (fr.president.engine.legislation.LegislationService(ctx).pendingFor("law:$lawId") != null)
            return "Un texte est déjà en discussion au Parlement."
        if (state.referendums.any { it.lawId == lawId }) return "Un référendum est déjà convoqué."
        state.changedAt[lawId]?.let { last ->
            val left = COOLDOWN_DAYS - last.daysUntil(ctx.now)
            if (left > 0) return "Loi modifiée récemment : possible à nouveau dans ${kotlin.math.ceil(left).toInt()} jours."
        }
        return null
    }

    /** Dépôt au Parlement d'un projet de loi ne contenant que ce changement. */
    fun propose(lawId: String, option: Int): Result<PolicyProposal> = runCatching {
        blocker(lawId, option)?.let { error(it) }
        fr.president.engine.legislation.LegislationService(ctx).singleLawBill("law:$lawId", option.toDouble()).getOrThrow()
    }

    /** Chance d'adoption au Parlement, d'après le soutien actuel (courbe normale du bruit du vote). */
    fun passChance(lawId: String, option: Int): Double {
        val law = law(lawId) ?: return 0.0
        val p = ctx.playerData.government?.parliament ?: return 0.0
        val need = p.passThreshold + (law.options.getOrNull(option)?.difficulty ?: 0.0) + if (law.constitutional) PolicyService.CONSTITUTIONAL_EXTRA else 0.0
        val z = (ctx.state.government.parliamentSupport - need) / p.voteNoise.coerceAtLeast(1e-3)
        return (1 / (1 + kotlin.math.exp(-1.7 * z))).coerceIn(0.01, 0.99)
    }

    /** Référendum (lois constitutionnelles) : le peuple tranche dans un mois. */
    fun referendum(lawId: String, option: Int): Result<String> = runCatching {
        val law = law(lawId) ?: error("Loi inconnue")
        require(law.constitutional) { "Seules les questions constitutionnelles se règlent par référendum." }
        blocker(lawId, option)?.let { error(it) }
        val at = ctx.now.plusDays((file?.referendumDays ?: DEFAULT_REFERENDUM_DAYS).toLong())
        state.referendums += LawReferendum(lawId, option, at)
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Référendum convoqué : ${law.title}",
            "Les Français voteront sur « ${law.options[option].label} » le ${fr.president.engine.util.Formatting.date(at)}.")
        "Référendum convoqué pour le ${fr.president.engine.util.Formatting.date(at)}."
    }

    /** Chance de victoire au référendum : popularité du président et attrait de la question. */
    fun referendumChance(lawId: String, option: Int): Double {
        val o = law(lawId)?.options?.getOrNull(option) ?: return 0.0
        return (REF_BASE + (ctx.state.opinion.nationalApproval - REF_NEUTRAL) * REF_APPROVAL + o.appeal).coerceIn(REF_MIN, REF_MAX)
    }

    /** Une loi est adoptée : effets, indices, journal. */
    fun enact(lawId: String, option: Int, scale: Double = 1.0) {
        val law = law(lawId) ?: return
        val o = law.options.getOrNull(option) ?: return
        state.values[lawId] = option
        state.changedAt[lawId] = ctx.now
        o.effects.forEach { ctx.effects.trigger(it.copy(amount = it.amount * scale), null, emptyMap(), "law:$lawId") }
        o.longTerm.forEach { ctx.effects.trigger(it.copy(amount = it.amount * scale), null, emptyMap(), "law:$lawId") }
        // Atteinte aux libertés : nos partenaires européens s'en émeuvent.
        val drop = o.liberty + o.press + o.rule - law.options[0].let { it.liberty + it.press + it.rule }
        if (drop <= -ILLIBERAL) ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", drop / ILLIBERAL * EU_REACTION), null, emptyMap(), "law:$lawId")
        fr.president.engine.legislation.LeverService(ctx).syncReforms()
        JournalService(ctx).add("Loi", "${law.title} : ${o.label}", Tone.GOOD)
        ctx.notifications.news(NotificationCategory.POLITICS, "${law.title} : ${o.label.lowercase()}")
        if (law.id in TERM_LAWS) ctx.notifications.post(NotificationCategory.POLITICS, Urgency.INFO, "Constitution modifiée",
            "La nouvelle règle s'appliquera à partir de la prochaine élection présidentielle.", journal = false)
    }

    /**
     * Une loi réglée par un nombre change de valeur : les effets sont la différence entre les effets
     * interpolés de la nouvelle valeur et ceux de l'ancienne.
     */
    fun enactNumeric(lawId: String, to: Double, scale: Double = 1.0) {
        val law = law(lawId) ?: return
        val n = law.numeric ?: return
        val from = value(law)
        val target = to.coerceIn(n.min, n.max)
        state.numbers[lawId] = target
        state.changedAt[lawId] = ctx.now
        val before = weights(law, from)
        val after = weights(law, target)
        for (i in (before.keys + after.keys)) {
            val k = ((after[i] ?: 0.0) - (before[i] ?: 0.0)) * scale
            if (kotlin.math.abs(k) < 1e-9) continue
            val o = law.options[i]
            (o.effects + o.longTerm).forEach { ctx.effects.trigger(it.copy(amount = it.amount * k), null, emptyMap(), "law:$lawId") }
        }
        fr.president.engine.legislation.LeverService(ctx).syncReforms()
        JournalService(ctx).add("Loi", "${law.title} : ${format(law, target)}", Tone.GOOD)
        ctx.notifications.news(NotificationCategory.POLITICS, "${law.title} : ${format(law, target)}")
    }

    /** Difficulté parlementaire d'un changement de valeur. */
    fun difficulty(law: LawDef, from: Double, to: Double): Double {
        if (law.numeric == null) return law.options.getOrNull(to.toInt())?.difficulty ?: 0.0
        val a = weights(law, from); val b = weights(law, to)
        return (a.keys + b.keys).sumOf { i -> kotlin.math.abs((b[i] ?: 0.0) - (a[i] ?: 0.0)) * (law.options[i].difficulty) }
    }

    /** Attrait d'un changement auprès des électeurs. */
    fun appeal(law: LawDef, from: Double, to: Double): Double {
        if (law.numeric == null) return law.options.getOrNull(to.toInt())?.appeal ?: 0.0
        val a = weights(law, from); val b = weights(law, to)
        return (a.keys + b.keys).sumOf { i -> ((b[i] ?: 0.0) - (a[i] ?: 0.0)) * law.options[i].appeal }
    }

    /** Valeur d'un réglage de la simulation (null : règle par défaut). */
    fun flag(name: String): Double? = laws.firstNotNullOfOrNull { l -> currentOption(l).flags[name] }

    fun eventFactor(eventId: String): Double = laws.fold(1.0) { acc, l ->
        acc * currentWeights(l).entries.fold(1.0) { f, (i, w) -> f * Math.pow(l.options.getOrNull(i)?.events?.get(eventId) ?: 1.0, w) }
    }

    /** Indices (sur 100) : libertés publiques, liberté de la presse, État de droit. */
    fun indices(): LawIndices {
        val base = file?.baseline ?: LawIndices()
        var liberty = base.liberty
        var press = base.press
        var rule = base.rule
        laws.forEach { l ->
            currentWeights(l).forEach { (i, w) ->
                val o = l.options.getOrNull(i) ?: return@forEach
                liberty += w * (o.liberty - l.options[0].liberty)
                press += w * (o.press - l.options[0].press)
                rule += w * (o.rule - l.options[0].rule)
            }
        }
        liberty += ctx.state.legislation.libertyOffset
        return LawIndices(liberty.coerceIn(0.0, MAX), press.coerceIn(0.0, MAX), rule.coerceIn(0.0, MAX))
    }

    companion object {
        const val COOLDOWN_DAYS = 180.0
        const val DEFAULT_REFERENDUM_DAYS = 30
        private const val REF_BASE = 0.5
        private const val REF_NEUTRAL = 0.45
        private const val REF_APPROVAL = 0.8
        private const val REF_MIN = 0.05
        private const val REF_MAX = 0.95
        private const val ILLIBERAL = 8.0
        private const val EU_REACTION = 0.01
        private const val MAX = 100.0
        private val TERM_LAWS = setOf("term_length", "term_limit")
    }
}

/** Référendums sur la Constitution : dépouillement le jour prévu. */
class LawSystem : SimulationSystem {
    override val name = "laws"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val state = ctx.state.laws
        val due = state.referendums.filter { ctx.now >= it.at }
        if (due.isEmpty()) return
        val service = LawService(ctx)
        due.forEach { r ->
            state.referendums.remove(r)
            val law = service.law(r.lawId) ?: return@forEach
            val share = (service.referendumChance(r.lawId, r.option) + ctx.rng.nextGaussian() * NOISE).coerceIn(0.2, 0.8)
            val label = law.options[r.option].label
            if (share >= 0.5) {
                service.enact(r.lawId, r.option)
                ctx.effects.trigger(EffectSpec("president.popularity", WIN), null, emptyMap(), "referendum")
                ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "Référendum : le oui l'emporte",
                    "${law.title} — « $label » adopté avec ${Math.round(share * 100)} % des voix.")
            } else {
                state.changedAt[r.lawId] = ctx.now
                ctx.effects.trigger(EffectSpec("opinion.national", -LOSS), null, emptyMap(), "referendum")
                ctx.effects.trigger(EffectSpec("president.popularity", -LOSS * 2), null, emptyMap(), "referendum")
                JournalService(ctx).add("Loi", "Référendum perdu : ${law.title}", Tone.BAD)
                ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "Référendum : le non l'emporte",
                    "${law.title} — « $label » rejeté avec ${Math.round((1 - share) * 100)} % de non. Un désaveu personnel.")
            }
        }
    }

    private companion object {
        const val NOISE = 0.05
        const val WIN = 0.01
        const val LOSS = 0.01
    }
}
