package fr.president.engine.elections

import fr.president.engine.government.PromiseDef
import fr.president.engine.government.PromiseKind
import fr.president.engine.simulation.SimulationContext

enum class PromiseStatus(val label: String) { KEPT("Tenue"), ON_TRACK("En bonne voie"), AT_RISK("Compromise"), BROKEN("Rompue") }

/** État des promesses de campagne : les électeurs jugent le président sur sa parole. */
class PromiseEvaluator(private val ctx: SimulationContext) {
    private val file get() = ctx.playerData.promises

    fun all(): List<PromiseDef> = file?.promises.orEmpty()
    fun chosen(): List<PromiseDef> = ctx.state.player.promises.mapNotNull { id -> all().firstOrNull { it.id == id } }

    /** Mémorise les valeurs de départ (début de mandat) des promesses relatives. */
    fun setBaselines() {
        val player = ctx.state.player
        player.promiseBaselines.clear()
        chosen().forEach { p -> p.variable?.let { v -> ctx.variables.resolve(v)?.let { player.promiseBaselines[p.id] = it } } }
        player.warsThisTerm = 0
    }

    fun status(p: PromiseDef, final: Boolean = false): PromiseStatus {
        val value = p.variable?.let { ctx.variables.resolve(it) }
        val start = ctx.state.player.promiseBaselines[p.id]
        val adopted = p.reform?.let { it in ctx.state.policy.adoptedReforms } ?: false
        val ok = when (p.kind) {
            PromiseKind.BELOW -> value != null && value < p.threshold
            PromiseKind.ABOVE -> value != null && value > p.threshold
            PromiseKind.NOT_ABOVE_START -> value != null && start != null && value <= start + p.threshold
            PromiseKind.ABOVE_START -> value != null && start != null && value >= start + p.threshold
            PromiseKind.BELOW_START -> value != null && start != null && value < start - p.threshold
            PromiseKind.REFORM_ADOPTED -> adopted
            PromiseKind.REFORM_NOT_ADOPTED -> !adopted
            PromiseKind.NO_WAR -> ctx.state.player.warsThisTerm == 0
        }
        val definitive = p.kind == PromiseKind.NOT_ABOVE_START || p.kind == PromiseKind.REFORM_NOT_ADOPTED || p.kind == PromiseKind.NO_WAR
        return when {
            ok && (final || definitive) -> if (final) PromiseStatus.KEPT else PromiseStatus.ON_TRACK
            ok -> PromiseStatus.ON_TRACK
            final || (definitive && p.kind != PromiseKind.NOT_ABOVE_START) -> PromiseStatus.BROKEN
            else -> PromiseStatus.AT_RISK
        }
    }

    /** Effet des promesses sur l'attrait du sortant pour un groupe social. */
    fun electoralEffect(groupId: String): Double {
        val f = file ?: return 0.0
        return chosen().sumOf { p ->
            val weight = if (groupId in p.groups) 1.0 else GENERAL_WEIGHT
            when (status(p, final = true)) {
                PromiseStatus.KEPT -> f.keptBonus * weight
                PromiseStatus.BROKEN -> -f.brokenPenalty * weight
                else -> 0.0
            }
        }
    }

    private companion object {
        const val GENERAL_WEIGHT = 0.4
    }
}
