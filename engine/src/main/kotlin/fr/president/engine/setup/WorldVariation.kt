package fr.president.engine.setup

import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalService

/**
 * Un monde de départ qui change d'une partie à l'autre : des relations un peu différentes avec
 * chaque pays et une situation de départ tirée au sort (récession qui menace, énergie chère,
 * tensions entre deux voisins, croissance vigoureuse, pays méfiant, état de grâce).
 */
class WorldVariation(private val ctx: SimulationContext) {
    private class Situation(val id: String, val label: String, val text: String, val apply: (SimulationContext) -> Unit)

    fun apply() {
        val s = ctx.state
        val player = s.player.countryId
        // Des relations qui ne sont jamais tout à fait les mêmes.
        s.countries.keys.filter { it != player }.forEach { c ->
            val weight = (ctx.rng.nextDouble() * 2 - 1) * RELATION_NOISE
            s.diplomacy.relation(c, player).memories += DiplomaticMemory("HISTORY", weight, ctx.now, if (weight >= 0) "souvenirs de coopération" else "vieux contentieux")
        }
        val situation = SITUATIONS[ctx.rng.nextInt(SITUATIONS.size)]
        situation.apply(ctx)
        s.player.startSituation = situation.id
        s.player.startSituationText = "${situation.label} : ${situation.text}"
        JournalService(ctx).add("Partie", "Situation de départ : ${situation.label}")
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.INFO, "Situation de départ : ${situation.label}", situation.text, null, journal = false)
    }

    private companion object {
        const val RELATION_NOISE = 0.06

        fun fx(ctx: SimulationContext, target: String, amount: Double, days: Double = 0.0) =
            ctx.effects.trigger(EffectSpec(target, amount, days = days), null, emptyMap(), "start")

        val SITUATIONS = listOf(
            Situation("recession", "Une récession menace", "Les économistes annoncent un ralentissement mondial : les carnets de commandes se vident.") { ctx ->
                ctx.state.countries.values.forEach { it.economy.pendingOutputShock -= 0.006 }
                fx(ctx, "economy.businessConfidence", -0.03)
            },
            Situation("energy", "L'énergie flambe", "Les prix du gaz et du pétrole s'envolent : factures et inflation vont peser.") { ctx ->
                fx(ctx, "economy.inflation", 0.004, days = 120.0)
                fx(ctx, "economy.consumerConfidence", -0.02)
            },
            Situation("tensions", "Tensions entre voisins", "Deux pays s'invectivent et massent des troupes : la diplomatie française est attendue.") { ctx ->
                val player = ctx.state.player.countryId
                val geo = fr.president.engine.military.Geopolitics(ctx)
                val candidates = ctx.state.countries.keys.filter { it != player }.flatMap { a -> geo.landNeighbors(a).filter { b -> b != player && b in ctx.state.countries && a < b }.map { a to it } }
                if (candidates.isNotEmpty()) {
                    val (a, b) = candidates[ctx.rng.nextInt(candidates.size)]
                    ctx.state.diplomacy.relation(a, b).memories += DiplomaticMemory("BORDER", -0.35, ctx.now, "incidents à la frontière")
                    ctx.state.diplomacy.relation(b, a).memories += DiplomaticMemory("BORDER", -0.35, ctx.now, "incidents à la frontière")
                }
            },
            Situation("boom", "Une croissance vigoureuse", "L'économie mondiale accélère : une fenêtre pour réformer sans trop de douleur.") { ctx ->
                ctx.state.countries.values.forEach { it.economy.pendingOutputShock += 0.004 }
                fx(ctx, "economy.consumerConfidence", 0.02)
            },
            Situation("distrust", "Un pays méfiant", "Élu de justesse, vous héritez d'un pays divisé et d'une majorité fragile.") { ctx ->
                fx(ctx, "opinion.national", -0.025)
                fx(ctx, "government.parliamentSupport", -0.03)
            },
            Situation("grace", "L'état de grâce", "Votre victoire a suscité un vrai enthousiasme : profitez-en, il ne durera pas.") { ctx ->
                fx(ctx, "opinion.national", 0.025)
            },
        )
    }
}
