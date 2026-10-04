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
    data class EffectLine(val text: String, val good: Boolean, val neutral: Boolean = false, val hint: String = "")

    data class ActionView(
        val def: LocalActionDef,
        /** Raison pour laquelle l'action est indisponible, ou null. */
        val blocker: String?,
        val effects: List<EffectLine>,
        val costText: String,
        val durationText: String,
        /** Prévision chiffrée sur vos indicateurs (« Popularité 53,0 % → 53,4 % »). */
        val forecast: List<EffectLine> = emptyList(),
        /** Décision verrouillée tant que la situation ne s'y prête pas. */
        val locked: Boolean = false,
        /** Temps pris dans l'agenda présidentiel (« ◷ 3 j d'agenda · à l'étranger »). */
        val agenda: String? = null,
    ) {
        /** Faut-il confirmer avant d'agir ? */
        val needsConfirmation: Boolean get() = def.confirm || def.costBillions >= HEAVY_COST
    }

    fun view(def: LocalActionDef, blocker: String?, locked: Boolean = false) =
        ActionView(def, blocker, summarize(def), costText(def), durationText(def), forecast(def), locked)

    /** Vrai si toutes les conditions de la décision sont remplies. */
    fun unlocked(def: LocalActionDef): Boolean = def.requires.all { c ->
        val v = ctx.variables.resolve(c.variable) ?: return@all false
        (c.min == null || v >= c.min) && (c.max == null || v <= c.max) && (c.oneOf.isEmpty() || v in c.oneOf)
    }

    /**
     * Avant / après : l'effet direct attendu sur la popularité nationale et sur le déficit.
     * C'est une estimation (l'opinion évolue ensuite avec le reste de la simulation).
     */
    fun forecast(def: LocalActionDef): List<EffectLine> {
        val effects = def.immediate + def.onCompletion
        val lines = mutableListOf<EffectLine>()
        val groups = ctx.playerData.socialGroups
        var popularity = effects.filter { it.target == "opinion.national" }.sumOf { it.amount }
        if (groups != null) {
            val partitions = groups.partitions.size.coerceAtLeast(1)
            popularity += effects.filter { it.target.startsWith(GROUP) }.sumOf { spec ->
                val share = groups.groups.firstOrNull { it.id == spec.target.removePrefix(GROUP) }?.populationShare ?: 0.0
                spec.amount * share / partitions
            }
        }
        val now = ctx.state.opinion.nationalApproval
        if (abs(popularity) >= MIN_POPULARITY) {
            lines += EffectLine("Popularité ${pct(now)} → ${pct((now + popularity).coerceIn(0.0, 1.0))}", popularity > 0)
        }
        val e = ctx.state.playerCountry.economy
        val gdp = e.gdpBillions.coerceAtLeast(1.0)
        val oneOff = def.costBillions + effects.filter { it.target == "budget.oneOff" }.sumOf { it.amount }
        if (abs(oneOff) / gdp >= MIN_DEFICIT) {
            lines += EffectLine("Déficit ${signedPts(oneOff / gdp)} de PIB (une fois)", oneOff < 0)
        }
        val yearly = effects.filter { it.target.startsWith(SPENDING) }.sumOf { spec ->
            (e.budget?.spending?.get(spec.target.removePrefix(SPENDING))?.amount ?: 0.0) * spec.amount
        }
        if (abs(yearly) / gdp >= MIN_DEFICIT) {
            lines += EffectLine("Dépenses ${if (yearly >= 0) "+" else "−"}${Formatting.billions(abs(yearly))} par an", yearly < 0)
        }
        return lines
    }

    private fun pct(v: Double) = String.format(java.util.Locale.FRENCH, "%.1f %%", v * PERCENT)
    private fun signedPts(v: Double) = (if (v >= 0) "+" else "−") + String.format(java.util.Locale.FRENCH, "%.2f pt", abs(v) * PERCENT)

    /** Effets groupés par libellé : plusieurs souvenirs diplomatiques d'un même pays font une seule ligne. */
    fun summarize(def: LocalActionDef): List<EffectLine> =
        (def.immediate + def.onCompletion).mapNotNull { spec -> meta(spec.target)?.let { it to spec } }
            .groupBy { it.first.label }
            .map { (_, list) ->
                val m = list.first().first
                val amount = list.sumOf { (_, spec) -> spec.amount * spec.factor }
                val shown = amount * m.scale
                val text = "${m.label} ${if (shown >= 0) "+" else "−"}${short(abs(shown))}${m.unit}"
                EffectLine(text, (amount > 0) == m.higherIsBetter, m.neutral, m.hint)
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

    private data class EffectMeta(
        val label: String,
        val scale: Double,
        val unit: String,
        val higherIsBetter: Boolean,
        val neutral: Boolean = false,
        /** Explication affichée en info-bulle. */
        val hint: String = "",
    )

    private fun meta(target: String): EffectMeta? {
        FIXED[target]?.let { return it }
        val parts = target.split('.')
        return when (parts[0]) {
            "opinion" -> if (parts.getOrNull(1) == "group") {
                val label = ctx.playerData.socialGroups?.groups?.firstOrNull { it.id == parts.getOrNull(2) }?.label ?: return null
                EffectMeta(label, PERCENT, " pts", true, hint = "Opinion de ce groupe à votre égard, en points. L'effet d'une décision s'estompe en quelques mois si les faits ne suivent pas.")
            } else null
            "quality" -> EffectMeta("Service : " + ctx.db.readouts.domainLabel(parts.getOrNull(1) ?: return null).lowercase(), PERCENT, "", true,
                hint = "Qualité du service public (sur 100). Les Français la jugent et votent en conséquence.")
            "spending" -> {
                val label = ctx.playerData.economy.budget?.spending?.firstOrNull { it.id == parts.getOrNull(1) }?.label ?: return null
                EffectMeta("Budget $label".let { if (it.length > MAX_LABEL) it.take(MAX_LABEL - 1) + "…" else it }, PERCENT, " %", true, neutral = true,
                    hint = "Variation durable des crédits de « $label » : plus de moyens pour le service, mais une dépense de plus chaque année.")
            }
            "memory" -> {
                val name = ctx.db.countries[parts.getOrNull(1)]?.definition?.name ?: return null
                EffectMeta("Relations $name", PERCENT, "", true, hint = "Souvenir laissé à ce pays : il pèse sur ses réponses à vos propositions, puis s'efface avec le temps.")
            }
            "alliance" -> {
                val name = ctx.db.alliances.firstOrNull { it.id == parts.getOrNull(1) }?.name ?: return null
                EffectMeta("Relations $name", PERCENT, "", true, hint = "Souvenir laissé à chaque membre de l'alliance : il pèse sur leurs réponses à vos propositions.")
            }
            "energy" -> EffectMeta("Production électrique", 1.0 / MW_PER_GW, " GW", true)
            "local" -> LOCAL[target]
            else -> null
        }
    }

    private companion object {
        const val HEAVY_COST = 5.0
        const val GROUP = "opinion.group."
        const val SPENDING = "spending."
        const val MIN_POPULARITY = 0.0005
        const val MIN_DEFICIT = 0.00005
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
            "opinion.national" to EffectMeta("Popularité nationale", PERCENT, " pt", true, hint = "Effet direct sur toute la population, en points de popularité."),
            "economy.consumerConfidence" to EffectMeta("Confiance des ménages", PERCENT, "", true, hint = "Moral des ménages : quand il monte, ils consomment plus et la croissance suit."),
            "economy.businessConfidence" to EffectMeta("Confiance des entreprises", PERCENT, "", true, hint = "Moral des entreprises : il pousse l'investissement, l'embauche et baisse les taux d'intérêt."),
            "economy.output" to EffectMeta("Activité", PERCENT, " % du PIB", true, hint = "Production supplémentaire de l'économie, étalée dans le temps."),
            "economy.potentialGrowth" to EffectMeta("Croissance potentielle", PERCENT, " pt", true, hint = "Croissance que l'économie peut tenir durablement : un effet lent mais permanent."),
            "economy.unemployment" to EffectMeta("Chômage", PERCENT, " pt", false, hint = "Variation du taux de chômage national, en points."),
            "economy.naturalUnemployment" to EffectMeta("Chômage structurel", PERCENT, " pt", false),
            "economy.inflation" to EffectMeta("Inflation", PERCENT, " pt", false, hint = "Variation de la hausse des prix sur un an, en points."),
            "budget.oneOff" to EffectMeta("Dépense", 1.0, " Md€", false, hint = "Dépense ou recette exceptionnelle, en milliards d'euros. Négative = de l'argent qui rentre."),
            "government.parliamentSupport" to EffectMeta("Soutien à l'Assemblée", PERCENT, " pts", true, hint = "Chances de faire voter vos textes. Sous 50 %, vos lois risquent d'être rejetées."),
            "president.popularity" to EffectMeta("Votre image", PERCENT, "", true),
            "military.readiness" to EffectMeta("Préparation des armées", PERCENT, "", true, hint = "Disponibilité des unités : entraînement, matériel, moral."),
            "military.ammoStock" to EffectMeta("Stocks de munitions", PERCENT, "", true),
            "military.fuelStock" to EffectMeta("Stocks de carburant", PERCENT, "", true),
            "demography.immigration" to EffectMeta("Immigration", PERCENT, " %", true, neutral = true),
        )
    }
}
