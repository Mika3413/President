package fr.president.engine.session

import fr.president.engine.effects.EffectSpec
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.LocalActionDef
import fr.president.engine.util.Formatting
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Mise en forme commune des actions du président (locales et nationales) : coût, durée,
 * délai avant de pouvoir recommencer, et effets résumés en clair (« Santé +15 », « Retraités −2 pts »).
 */
class ActionPresenter(private val ctx: SimulationContext) {

    /** Effet résumé pour l'interface : bon (vert), mauvais (rouge) ou neutre (gris). */
    data class EffectLine(val text: String, val good: Boolean, val neutral: Boolean = false)

    data class ActionView(
        val def: LocalActionDef,
        /** Raison pour laquelle l'action est indisponible, ou null. */
        val blocker: String?,
        val effects: List<EffectLine>,
        val costText: String,
        val durationText: String,
    )

    fun view(def: LocalActionDef, blocker: String?) = ActionView(def, blocker, summarize(def), costText(def), durationText(def))

    /** Effets groupés par libellé : plusieurs souvenirs diplomatiques d'un même pays font une seule ligne. */
    fun summarize(def: LocalActionDef): List<EffectLine> =
        (def.immediate + def.onCompletion).mapNotNull { spec -> meta(spec.target)?.let { it to spec } }
            .groupBy { it.first.label }
            .map { (_, list) ->
                val m = list.first().first
                val amount = list.sumOf { (_, spec) -> spec.amount * spec.factor }
                val shown = amount * m.scale
                val text = "${m.label} ${if (shown >= 0) "+" else "−"}${short(abs(shown))}${m.unit}"
                EffectLine(text, (amount > 0) == m.higherIsBetter, m.neutral)
            }

    fun costText(def: LocalActionDef) = if (def.costBillions <= 0) "Gratuit" else Formatting.billions(def.costBillions)

    fun durationText(def: LocalActionDef): String = if (def.durationDays <= 0) "immédiat" else days(def.durationDays)

    /** Délai restant avant de pouvoir recommencer, ou null si c'est possible maintenant. */
    fun wait(key: String, cooldown: Int): String? {
        if (cooldown <= 0) return null
        val last = ctx.state.localActions[key] ?: return null
        val left = cooldown - last.daysUntil(ctx.now)
        return if (left > 0) days(ceil(left).toInt()) else null
    }

    fun days(n: Int): String = when {
        n >= DAYS_PER_YEAR && n % DAYS_PER_YEAR == 0 -> "${n / DAYS_PER_YEAR} an${if (n >= 2 * DAYS_PER_YEAR) "s" else ""}"
        n >= DAYS_PER_MONTH * 2 -> "${Math.round(n / DAYS_PER_MONTH.toDouble())} mois"
        else -> "$n jour${if (n > 1) "s" else ""}"
    }

    /** Une décimale au plus : « 4 », « 1,5 », « 0,2 ». */
    private fun short(v: Double): String {
        val r = Math.round(v * TENTHS) / TENTHS
        return if (r == Math.floor(r)) r.toLong().toString() else String.format(java.util.Locale.FRENCH, "%.1f", r)
    }

    private data class EffectMeta(val label: String, val scale: Double, val unit: String, val higherIsBetter: Boolean, val neutral: Boolean = false)

    private fun meta(target: String): EffectMeta? {
        FIXED[target]?.let { return it }
        val parts = target.split('.')
        return when (parts[0]) {
            "opinion" -> if (parts.getOrNull(1) == "group") {
                val label = ctx.playerData.socialGroups?.groups?.firstOrNull { it.id == parts.getOrNull(2) }?.label ?: return null
                EffectMeta(label, PERCENT, " pts", true)
            } else null
            "quality" -> EffectMeta("Service : " + ctx.db.readouts.domainLabel(parts.getOrNull(1) ?: return null).lowercase(), PERCENT, "", true)
            "spending" -> {
                val label = ctx.playerData.economy.budget?.spending?.firstOrNull { it.id == parts.getOrNull(1) }?.label ?: return null
                EffectMeta("Budget $label".let { if (it.length > MAX_LABEL) it.take(MAX_LABEL - 1) + "…" else it }, PERCENT, " %", true, neutral = true)
            }
            "memory" -> {
                val name = ctx.db.countries[parts.getOrNull(1)]?.definition?.name ?: return null
                EffectMeta("Relations $name", PERCENT, "", true)
            }
            "alliance" -> {
                val name = ctx.db.alliances.firstOrNull { it.id == parts.getOrNull(1) }?.name ?: return null
                EffectMeta("Relations $name", PERCENT, "", true)
            }
            "energy" -> EffectMeta("Production électrique", 1.0 / MW_PER_GW, " GW", true)
            "local" -> LOCAL[target]
            else -> null
        }
    }

    private companion object {
        const val TENTHS = 10.0
        const val PERCENT = 100.0
        const val MW_PER_GW = 1000.0
        const val MAX_LABEL = 28
        const val DAYS_PER_YEAR = 365
        const val DAYS_PER_MONTH = 30
        val LOCAL = mapOf(
            "local.approval" to EffectMeta("Popularité locale", PERCENT, " pts", true),
            "local.healthAccess" to EffectMeta("Santé", PERCENT, "", true),
            "local.crime" to EffectMeta("Délinquance", PERCENT, "", false),
            "local.unemployment" to EffectMeta("Chômage local", PERCENT, " pt", false),
            "local.pollution" to EffectMeta("Pollution", PERCENT, "", false),
            "local.industry" to EffectMeta("Emplois industriels", PERCENT, " %", true),
        )
        val FIXED = mapOf(
            "opinion.national" to EffectMeta("Popularité nationale", PERCENT, " pt", true),
            "economy.consumerConfidence" to EffectMeta("Confiance des ménages", PERCENT, "", true),
            "economy.businessConfidence" to EffectMeta("Confiance des entreprises", PERCENT, "", true),
            "economy.output" to EffectMeta("Activité", PERCENT, " % du PIB", true),
            "economy.potentialGrowth" to EffectMeta("Croissance potentielle", PERCENT, " pt", true),
            "economy.unemployment" to EffectMeta("Chômage", PERCENT, " pt", false),
            "economy.naturalUnemployment" to EffectMeta("Chômage structurel", PERCENT, " pt", false),
            "economy.inflation" to EffectMeta("Inflation", PERCENT, " pt", false),
            "budget.oneOff" to EffectMeta("Dépense", 1.0, " Md€", false),
            "government.parliamentSupport" to EffectMeta("Soutien à l'Assemblée", PERCENT, " pts", true),
            "president.popularity" to EffectMeta("Votre image", PERCENT, "", true),
            "military.readiness" to EffectMeta("Préparation des armées", PERCENT, "", true),
            "military.ammoStock" to EffectMeta("Stocks de munitions", PERCENT, "", true),
            "military.fuelStock" to EffectMeta("Stocks de carburant", PERCENT, "", true),
            "demography.immigration" to EffectMeta("Immigration", PERCENT, " %", true, neutral = true),
        )
    }
}
