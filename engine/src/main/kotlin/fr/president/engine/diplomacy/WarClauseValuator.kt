package fr.president.engine.diplomacy

import fr.president.engine.military.Geopolitics
import fr.president.engine.military.War
import fr.president.engine.politics.Character
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.SimulationContext

/** Valorisation par l'IA des clauses de guerre et de sécurité. */
class WarClauseValuator(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)

    fun value(evaluator: String, partner: String, c: Clause, gives: Boolean, w: Map<String, Double>, leader: Character): ClauseValuator.Value {
        val war = geo.warBetween(evaluator, partner)
        val weariness = war?.weariness?.get(evaluator) ?: 0.0
        val advantage = war?.let { advantage(it, evaluator, partner) } ?: 0.0
        val amount = c.params["amountBillions"] ?: 0.0
        val gdp = ctx.state.countries.getValue(evaluator).economy.gdpBillions
        return when (c.type) {
            "CEASEFIRE" -> if (war == null) ClauseValuator.Value(-0.1, "nous ne sommes pas en guerre")
            else v(w.g("wearinessWeight") * weariness - w.g("advantageWeight") * advantage, "le rapport de forces nous est favorable")
            "PEACE_TREATY" -> {
                if (war == null) return ClauseValuator.Value(-0.1, "nous ne sommes pas en guerre")
                val keep = (c.params["keepOccupied"] ?: 0.0) >= 1
                val held = held(evaluator, war) - held(partner, war)
                val zones = if (keep) held else -held
                v(w.g("base") + w.g("wearinessWeight") * weariness + w.g("zoneWeight") * zones, "les conditions de paix sont inacceptables pour nous")
            }
            "REPARATIONS", "MILITARY_AID" -> {
                val relative = amount / gdp * THOUSAND
                val multiplier = if (!gives && c.type == "MILITARY_AID" && geo.isAtWar(evaluator)) w.g("warMultiplier") else 1.0
                if (gives) v(-w.g("aidWeight") * relative * w.g("giverCost"), "ce montant pèserait trop lourd")
                else ClauseValuator.Value(w.g("aidWeight") * relative * multiplier, null)
            }
            "PRISONER_EXCHANGE" -> ClauseValuator.Value(w.g("goodwill"), null)
            "ARMS_SALE" -> if (gives) {
                v(w.g("sellerWeight") * amount - if (geo.isAtWar(partner) && !geo.allied(evaluator, partner)) RISKY_SALE else 0.0, "nous ne souhaitons pas armer un pays en guerre")
            } else {
                v(w.g("buyerWeight") * amount * (leader.trait(Traits.MILITARISM) + if (geo.isAtWar(evaluator)) 1.0 else 0.0) - amount / gdp * THOUSAND * PRICE_WEIGHT, "nos finances ne le permettent pas")
            }
            "PASSAGE_RIGHTS" -> if (gives) {
                val ally = partner in geo.coBelligerents(evaluator) || geo.allied(evaluator, partner)
                v(-w.g("grantCost") - w.g("sovereigntyWeight") * leader.trait(Traits.NATIONALISM) + if (ally) ALLY_BONUS else 0.0, "notre souveraineté territoriale n'est pas négociable")
            } else ClauseValuator.Value(w.g("receiverValue"), null)
            "DEFENSIVE_ALLIANCE" -> {
                val risk = if (geo.isAtWar(partner)) w.g("riskWeight") else 0.0
                v(w.g("militarismWeight") * leader.trait(Traits.MILITARISM) - w.g("sovereigntyWeight") * leader.trait(Traits.NATIONALISM) - risk,
                    if (risk > 0) "nous refusons d'être entraînés dans votre guerre" else "une alliance formelle ne nous semble pas nécessaire")
            }
            "SECURITY_GUARANTEE" -> if (gives) v(-w.g("giverCost") - w.g("giverCost") * leader.trait(Traits.CAUTION), "nous ne pouvons pas garantir votre sécurité")
            else ClauseValuator.Value(w.g("receiverValue"), null)
            else -> ClauseValuator.Value(0.0, null)
        }
    }

    private fun v(value: Double, reason: String) = ClauseValuator.Value(value, if (value < 0) reason else null)
    private fun Map<String, Double>.g(k: String) = this[k] ?: 0.0

    private fun held(country: String, war: War) = ctx.state.military.occupied.count { (z, occ) -> occ == country && geo.ownerOf(z) in war.participants }

    /** Avantage militaire (-1..1) : zones tenues et puissance terrestre. */
    private fun advantage(war: War, evaluator: String, partner: String): Double {
        val zones = (held(evaluator, war) - held(partner, war)) / ZONE_SCALE
        val a = geo.landPower(evaluator); val b = geo.landPower(partner)
        val power = if (a + b > 0) (a - b) / (a + b) else 0.0
        return ((zones + power) / 2).coerceIn(-1.0, 1.0)
    }

    private companion object {
        const val THOUSAND = 1000.0
        const val RISKY_SALE = 0.2
        const val PRICE_WEIGHT = 0.05
        const val ALLY_BONUS = 0.12
        const val ZONE_SCALE = 10.0
    }
}
