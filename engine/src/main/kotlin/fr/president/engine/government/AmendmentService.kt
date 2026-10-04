package fr.president.engine.government

import fr.president.engine.effects.EffectSpec
import fr.president.engine.simulation.SimulationContext
import kotlin.math.exp

/**
 * Négociation d'un texte avant son vote : chaque amendement ou tractation achète des voix
 * contre une contrepartie (texte affaibli, argent, mécontentement). Le joueur voit à tout
 * moment ses chances d'adoption.
 */
class AmendmentService(private val ctx: SimulationContext) {

    data class Amendment(
        val id: String,
        val label: String,
        val description: String,
        val supportGain: Double,
        /** Multiplie la portée du texte (1 = inchangé). */
        val scale: Double = 1.0,
        val costBillions: Double = 0.0,
        val sideEffects: List<EffectSpec> = emptyList(),
        val costText: String,
    )

    data class View(val amendment: Amendment, val available: Boolean)

    val catalog = listOf(
        Amendment("water_down", "Atténuer le texte", "Le texte est adouci : il rallie des hésitants mais produit moins d'effet.",
            supportGain = 0.04, scale = WATER_DOWN, costText = "Effets réduits de 30 %"),
        Amendment("local_funds", "Crédits pour les circonscriptions", "Des subventions locales pour les députés qui votent le texte.",
            supportGain = 0.03, costBillions = LOCAL_FUNDS, costText = "0,8 Md€"),
        Amendment("opposition", "Reprendre un amendement de l'opposition", "Un geste d'ouverture : quelques voix de plus, mais votre camp grince.",
            supportGain = 0.05, scale = OPPOSITION_SCALE,
            sideEffects = listOf(EffectSpec("opinion.national", -0.002)), costText = "Effets −15 %, popularité −0,2 pt"),
        Amendment("whip", "Discipline de vote", "Le Premier ministre menace les frondeurs : efficace, mais la majorité s'use.",
            supportGain = 0.02, sideEffects = listOf(EffectSpec("government.parliamentSupport", -0.01)), costText = "Soutien futur −1 pt"),
        Amendment("campaign", "Campagne d'explication", "Ministres sur les plateaux et réunions publiques pour défendre le texte.",
            supportGain = 0.02, costBillions = CAMPAIGN, sideEffects = listOf(EffectSpec("opinion.national", 0.001)), costText = "0,05 Md€"),
    )

    fun options(proposalId: String): List<View> {
        val p = proposal(proposalId) ?: return emptyList()
        val open = p.status == PolicyStatus.PENDING_VOTE
        return catalog.map { View(it, open && it.id !in p.amendments) }
    }

    fun amend(proposalId: String, amendmentId: String): Result<String> = runCatching {
        val p = proposal(proposalId) ?: error("Texte introuvable.")
        if (p.status != PolicyStatus.PENDING_VOTE) error("Le vote a déjà eu lieu.")
        val a = catalog.firstOrNull { it.id == amendmentId } ?: error("Amendement inconnu.")
        if (a.id in p.amendments) error("Déjà négocié.")
        p.amendments += a.id
        p.supportBonus += a.supportGain
        if (a.scale != 1.0) {
            p.effectScale *= a.scale
            // Pour un impôt ou un budget, adoucir rapproche la valeur visée de la valeur actuelle.
            if (p.kind != PolicyKind.REFORM) p.newValue = p.oldValue + (p.newValue - p.oldValue) * a.scale
        }
        if (a.costBillions > 0) ctx.effects.trigger(EffectSpec("budget.oneOff", a.costBillions, days = 1.0), null, emptyMap(), "amend:${a.id}")
        a.sideEffects.forEach { ctx.effects.trigger(it, null, emptyMap(), "amend:${a.id}") }
        "${a.label} : chances d'adoption ${Math.round(chance(proposalId) * PERCENT)} %."
    }

    /** Probabilité d'adoption au vote (soutien actuel, bonus, difficulté, aléa du vote). */
    fun chance(proposalId: String): Double {
        val p = proposal(proposalId) ?: return 0.0
        val params = ctx.playerData.government!!.parliament
        val difficulty = if (p.kind == PolicyKind.REFORM) ctx.playerData.reforms?.reforms?.firstOrNull { it.id == p.itemId }?.difficulty ?: 0.0 else 0.0
        val margin = ctx.state.government.parliamentSupport + p.supportBonus - params.passThreshold - difficulty
        return normalCdf(margin / params.voteNoise.coerceAtLeast(MIN_NOISE))
    }

    private fun proposal(id: String) = ctx.state.policy.proposals.firstOrNull { it.id == id }

    /** Approximation de la loi normale cumulée (erreur < 1e-3, suffisante pour l'affichage). */
    private fun normalCdf(z: Double): Double = 1.0 / (1.0 + exp(-LOGISTIC * z * (1 + CUBIC * z * z)))

    private companion object {
        const val WATER_DOWN = 0.7
        const val OPPOSITION_SCALE = 0.85
        const val LOCAL_FUNDS = 0.8
        const val CAMPAIGN = 0.05
        const val PERCENT = 100
        const val MIN_NOISE = 1e-3
        const val LOGISTIC = 1.5976
        const val CUBIC = 0.044715
    }
}
