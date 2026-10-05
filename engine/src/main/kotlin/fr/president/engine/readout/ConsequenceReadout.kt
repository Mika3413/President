package fr.president.engine.readout

import fr.president.engine.consequences.ActiveConsequence
import fr.president.engine.consequences.ConsequenceRule
import fr.president.engine.consequences.ConsequenceService
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting
import kotlin.math.abs

/**
 * Les conséquences rendues lisibles : valeur actuelle et seuil en clair, risques accrus,
 * dégâts en voie de réparation. Sert au Bilan et au Bureau du président.
 */
class ConsequenceReadout(private val ctx: SimulationContext) {
    private val service = ConsequenceService(ctx)

    data class Row(val rule: ConsequenceRule, val value: String, val threshold: String, val severity: Double, val since: fr.president.engine.time.WorldTime?)
    data class Risk(val label: String, val factor: Double, val causes: List<String>)

    fun active(): List<Row> = service.active().map { (r, a) -> row(r, a) }

    /** Proches du seuil : la règle, la valeur et le seuil en clair, la distance (0 = au seuil). */
    fun near(): List<Pair<Row, Double>> = service.nearThreshold().map { (r, gap) -> row(r, null) to gap }

    fun row(r: ConsequenceRule, a: ActiveConsequence?): Row {
        val v = service.value(r)
        val limit = r.above ?: r.below ?: 0.0
        val dir = if (r.above != null) "au-delà de" else "en dessous de"
        return Row(r, v?.let { format(r.variable, it) } ?: "inconnu", "alerte $dir ${format(r.variable, limit)}", a?.severity ?: 0.0, a?.since)
    }

    /** Événements devenus plus probables à cause des conséquences en cours. */
    fun risks(): List<Risk> {
        val file = ctx.db.consequences ?: return emptyList()
        val causes = mutableMapOf<String, MutableList<String>>()
        ctx.state.consequences.current.values.filter { it.active }.forEach { a ->
            file.rules.firstOrNull { it.id == a.id }?.events?.keys?.forEach { causes.getOrPut(it) { mutableListOf() } += file.rules.first { r -> r.id == a.id }.label }
        }
        return causes.mapNotNull { (id, why) ->
            val f = ConsequenceService.eventFactor(ctx, id)
            if (f < MIN_FACTOR) return@mapNotNull null
            val label = ctx.db.events.firstOrNull { it.id == id }?.let { cleanHeadline(it.headline) } ?: id
            Risk(label, f, why)
        }.sortedByDescending { it.factor }
    }

    /** Dégâts encore présents alors que la cause a disparu. */
    fun healing(): List<Pair<ConsequenceRule, Int>> = ctx.state.consequences.current.values.filter { !it.active }.mapNotNull { a ->
        val r = service.rule(a.id) ?: return@mapNotNull null
        // Mois restants avant réparation presque complète (15 % par mois).
        val biggest = a.applied.values.maxOfOrNull { abs(it) } ?: 0.0
        if (biggest < 1e-6) null else r to (Math.log(1e-5 / biggest) / Math.log(0.85)).toInt().coerceIn(1, 60)
    }

    private fun cleanHeadline(h: String) = h.replace(Regex("\\{[^}]*\\}"), "…").trim()

    companion object {
        private const val MIN_FACTOR = 1.1

        /** Une valeur de variable en clair, avec son unité. */
        fun format(variable: String, v: Double): String = when {
            variable.startsWith("spending.") || variable == "derived.servicesFunding" -> "${Math.round(v * 100)} % des crédits de départ"
            variable.startsWith("quality.") -> "${Math.round(v * 100)} / 100"
            variable == "derived.rsaToSmic" -> "RSA à ${Math.round(v * 100)} % du SMIC"
            variable == "economy.growth" -> "croissance ${signed(v)}"
            variable in setOf("economy.deficitRatio", "economy.debtRatio") -> "${Formatting.percent(v)} du PIB"
            variable in setOf("economy.inflation", "economy.unemployment") -> Formatting.percent(v)
            variable.startsWith("tax.") -> "${Formatting.percent(v)} de prélèvements"
            variable == "society.realRent" -> "loyers ${signed(v - 1)} (hors inflation)"
            variable == "society.housePrices" -> "prix des logements ${signed(v - 1)} (hors inflation)"
            variable == "society.fertility" -> String.format(java.util.Locale.FRENCH, "%.2f enfant par femme", v)
            variable == "society.lifeExpectancy" -> String.format(java.util.Locale.FRENCH, "%.1f ans", v)
            variable == "society.savingsRate" -> "épargne ${Formatting.percent(v)} du revenu"
            variable == "society.informal" -> "${Formatting.percent(v)} du PIB au noir"
            variable == "energy.priceIndex" -> "énergie ${signed(v - 1)}"
            variable.startsWith("laws.") -> "${Math.round(v)} / 100"
            variable == "demography.immigration" -> "immigration ${signed(v - 1)}"
            variable == "unrest.phase" -> when { v >= 4 -> "insurrection"; v >= 3 -> "émeutes"; v >= 2 -> "blocages"; v >= 1 -> "manifestations"; else -> "calme" }
            variable == "derived.article16" -> if (v >= 0.5) "en vigueur" else "non"
            variable == "lever.param:pension_age" -> "${Formatting.amount(v)} ans"
            variable.startsWith("lever.param:") -> "${Formatting.amount(v)} %"
            else -> Formatting.percent(v)
        }

        private fun signed(v: Double) = (if (v >= 0) "+" else "−") + Formatting.percent(abs(v))
    }
}
