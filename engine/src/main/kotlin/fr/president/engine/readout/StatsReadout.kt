package fr.president.engine.readout

import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.military.Geopolitics
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalEntry
import fr.president.engine.util.Formatting
import kotlin.math.abs

/**
 * Lecture des statistiques du mandat pour l'interface : courbes, explications « Pourquoi ? »,
 * tableau des groupes sociaux, classement des pays et journal.
 */
class StatsReadout(private val ctx: SimulationContext) {

    /** Courbe prête à tracer : valeurs hebdomadaires et mise en forme de l'axe. */
    data class Series(
        val key: String,
        val label: String,
        val values: List<Double>,
        val higherIsBetter: Boolean,
        val format: (Double) -> String,
    ) {
        val current: Double? get() = values.lastOrNull()
        /** Évolution sur environ un mois (4 relevés). */
        val monthChange: Double? get() = if (values.size >= 2) values.last() - values[maxOf(0, values.size - 1 - MONTH)] else null
    }

    /** Une cause et son poids, en points (positif = favorable). */
    data class Cause(val label: String, val points: Double)

    data class Why(val title: String, val summary: String, val causes: List<Cause>)

    data class GroupRow(
        val id: String,
        val label: String,
        val approval: Double,
        val change: Double?,
        val populationShare: Double,
        val worry: String?,
        val support: String?,
        val partition: String,
    )

    data class CountryRow(
        val id: String,
        val name: String,
        val gdpBillions: Double,
        val growth: Double,
        val relation: Double,
        val units: Int,
        val militaryBudgetBillions: Double,
        val alliances: List<String>,
        val atWar: Boolean,
        val leader: String,
    )

    /** Courbes principales du tableau de bord, dans l'ordre d'affichage. */
    val mainKeys = listOf("approval", "unemployment", "growth", "inflation", "deficit", "debt", "parliament", "readiness")

    fun series(key: String): Series? {
        val values = ctx.state.stats.series[key]?.all() ?: return null
        val (label, better, format) = describe(key) ?: return null
        return Series(key, label, values.toList(), better, format)
    }

    private fun describe(key: String): Triple<String, Boolean, (Double) -> String>? {
        val pct: (Double) -> String = { Formatting.percent(it) }
        val whole: (Double) -> String = { Formatting.wholePercent(it) }
        return when {
            key == "approval" -> Triple("Popularité", true, whole)
            key == "unemployment" -> Triple("Chômage", false, pct)
            key == "growth" -> Triple("Croissance", true) { v: Double -> Formatting.signedPercent(v) }
            key == "inflation" -> Triple("Inflation", false, pct)
            key == "deficit" -> Triple("Déficit public (% du PIB)", true) { v: Double -> Formatting.signedPercent(-v) }
            key == "debt" -> Triple("Dette publique (% du PIB)", false, whole)
            key == "parliament" -> Triple("Soutien à l'Assemblée", true, whole)
            key == "readiness" -> Triple("Préparation des armées", true, whole)
            key.startsWith("group.") -> {
                val label = ctx.playerData.socialGroups?.groups?.firstOrNull { it.id == key.removePrefix("group.") }?.label ?: return null
                Triple(label, true, whole)
            }
            key.startsWith("relation.") -> {
                val name = ctx.db.countries[key.removePrefix("relation.")]?.definition?.name ?: return null
                Triple("Relation avec $name", true) { v: Double -> "${Math.round(v * PERCENT)} / 100" }
            }
            else -> null
        }
    }

    /** « Pourquoi ? » : les causes d'un chiffre, de la plus lourde à la plus légère. */
    fun why(key: String): Why? = when (key) {
        "approval" -> approvalWhy()
        "unemployment" -> unemploymentWhy()
        "growth" -> growthWhy()
        "deficit" -> deficitWhy()
        else -> null
    }

    private fun approvalWhy(): Why? {
        val def = ctx.playerData.socialGroups ?: return null
        val o = ctx.state.opinion
        val partitions = def.partitions.size.coerceAtLeast(1)
        val causes = def.factors.map { f ->
            val score = o.factorScores[f.id] ?: 0.0
            val weight = def.groups.sumOf { g -> g.populationShare * (g.sensitivities[f.id] ?: 0.0) } / partitions
            Cause(f.label, weight * score * PERCENT)
        }.toMutableList()
        val shock = def.groups.sumOf { g -> g.populationShare * (o.groups[g.id]?.shock ?: 0.0) } / partitions
        causes += Cause("Décisions et événements récents", shock * PERCENT)
        if (o.honeymoon > EPSILON) causes += Cause("État de grâce", o.honeymoon * PERCENT)
        return Why("Ce qui fait votre popularité",
            "Chaque ligne pousse votre popularité vers le haut (vert) ou vers le bas (rouge). L'opinion suit ces forces avec un peu de retard.",
            causes.filter { abs(it.points) >= MIN_POINTS }.sortedByDescending { abs(it.points) })
    }

    private fun unemploymentWhy(): Why {
        val e = ctx.state.playerCountry.economy
        val cyclical = e.unemployment - e.naturalUnemployment
        return Why("Ce qui fait le chômage",
            "Le chômage structurel ne baisse qu'avec des réformes et de la formation ; la part conjoncturelle suit l'activité.",
            listOf(
                Cause("Chômage structurel (${Formatting.percent(e.naturalUnemployment)})", -e.naturalUnemployment * PERCENT),
                Cause(if (cyclical > 0) "Activité trop faible" else "Activité soutenue", -cyclical * PERCENT),
            ))
    }

    private fun growthWhy(): Why {
        val e = ctx.state.playerCountry.economy
        val p = ctx.db.economyParameters
        return Why("Ce qui fait la croissance",
            "La croissance dépend du potentiel de l'économie, du moral des ménages et des entreprises, et des plans de relance en cours.",
            listOf(
                Cause("Croissance potentielle", e.potentialGrowth * PERCENT),
                Cause("Moral des ménages", p.confidenceGrowthEffect * (e.consumerConfidence - NEUTRAL_CONFIDENCE) * PERCENT),
                Cause("Moral des entreprises", p.confidenceGrowthEffect * (e.businessConfidence - NEUTRAL_CONFIDENCE) * PERCENT),
                Cause("Plans de relance et chocs en cours", e.impulses.sumOf { it.monthlyAnnualizedGrowth } * PERCENT),
            ).filter { abs(it.points) >= MIN_POINTS / 10 })
    }

    private fun deficitWhy(): Why {
        val e = ctx.state.playerCountry.economy
        val gdp = e.gdpBillions.coerceAtLeast(1.0)
        return Why("Ce qui fait le déficit",
            "Le déficit, c'est ce que l'État dépense au-delà de ses recettes. Chaque ligne est en points de PIB.",
            listOf(
                Cause("Recettes (${Formatting.billions(e.revenueBillions)})", e.revenueBillions / gdp * PERCENT),
                Cause("Dépenses (${Formatting.billions(e.spendingBillions)})", -e.spendingBillions / gdp * PERCENT),
                Cause("Intérêts de la dette (${Formatting.billions(e.interestBillions)})", -e.interestBillions / gdp * PERCENT),
                Cause("Dépenses exceptionnelles de l'année (${Formatting.billions(e.oneOffThisYearBillions)})", -e.oneOffThisYearBillions / gdp * PERCENT),
            ))
    }

    /** Les groupes sociaux, du moins favorable au plus favorable. */
    fun groups(): List<GroupRow> {
        val def = ctx.playerData.socialGroups ?: return emptyList()
        val o = ctx.state.opinion
        return def.groups.map { g ->
            val contributions = g.sensitivities.map { (f, w) -> f to w * (o.factorScores[f] ?: 0.0) }
            val worst = contributions.filter { it.second < -SMALL }.minByOrNull { it.second }?.first
            val best = contributions.filter { it.second > SMALL }.maxByOrNull { it.second }?.first
            fun label(id: String?) = id?.let { fid -> def.factors.firstOrNull { it.id == fid }?.label }
            val partition = def.partitions.firstOrNull { it.id == g.partition }?.label ?: g.partition
            GroupRow(g.id, g.label, o.groups[g.id]?.effective ?: g.baseApproval, series("group.${g.id}")?.monthChange,
                g.populationShare, label(worst), label(best), partition)
        }.sortedBy { it.approval }
    }

    /** Les pays simulés, des plus grandes économies aux plus petites. */
    fun countries(): List<CountryRow> {
        val player = ctx.state.player.countryId
        val relations = RelationCalculator(ctx)
        val geo = Geopolitics(ctx)
        val units = ctx.state.military.units.values.groupingBy { it.countryId }.eachCount()
        return ctx.state.countries.values.filter { it.id != player }.map { c ->
            CountryRow(
                id = c.id,
                name = ctx.db.countries[c.id]?.definition?.name ?: c.id,
                gdpBillions = c.economy.gdpBillions,
                growth = c.economy.realGrowth,
                relation = relations.score(c.id, player),
                units = units[c.id] ?: 0,
                militaryBudgetBillions = c.militaryBudgetBillions,
                alliances = ctx.db.alliances.filter { c.id in it.members }.map { it.id },
                atWar = geo.atWar(player, c.id),
                leader = ctx.state.characters[c.leaderId]?.fullName ?: "",
            )
        }.sortedByDescending { it.gdpBillions }
    }

    /** Journal du mandat, le plus récent d'abord. */
    fun journal(kind: String? = null): List<JournalEntry> =
        ctx.state.stats.journal.filter { kind == null || it.kind == kind }.asReversed()

    fun journalKinds(): List<String> = ctx.state.stats.journal.map { it.kind }.distinct().sorted()

    private companion object {
        const val PERCENT = 100.0
        const val MONTH = 4
        const val EPSILON = 1e-4
        const val MIN_POINTS = 0.1
        const val SMALL = 0.005
        const val NEUTRAL_CONFIDENCE = 0.5
    }
}
