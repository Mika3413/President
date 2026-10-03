package fr.president.engine.diplomacy

import fr.president.engine.politics.Traits
import fr.president.engine.simulation.SimulationContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Valeur d'une clause pour un pays évaluateur, selon ses besoins, son économie,
 * le tempérament de son dirigeant et ce qu'il perçoit de son partenaire.
 */
class ClauseValuator(private val ctx: SimulationContext) {

    data class Value(val value: Double, val reason: String?)

    fun value(evaluator: String, partner: String, clause: Clause, durationYears: Int): Value {
        val def = ctx.db.diplomacy.clause(clause.type)
        val w = def.valuation
        val leader = ctx.state.characters.getValue(ctx.state.countries.getValue(evaluator).leaderId)
        val evaluatorGives = !def.mutual && clause.giver == evaluator
        val raw = when (clause.type) {
            ELECTRICITY -> electricity(evaluator, partner, clause, evaluatorGives, w)
            TARIFFS -> tariffs(evaluator, partner, clause, leader.trait(Traits.NATIONALISM), w)
            AID -> aid(evaluator, clause, evaluatorGives, w)
            DEFENSE -> defense(evaluator, partner, clause, leader.trait(Traits.MILITARISM), leader.trait(Traits.NATIONALISM), w)
            INVESTMENT -> investment(clause, evaluatorGives, w)
            else -> WarClauseValuator(ctx).value(evaluator, partner, clause, evaluatorGives, w, leader)
        }
        val recurring = (w["recurring"] ?: 0.0) > 0
        val durationFactor = if (recurring) sqrt(durationYears / REFERENCE_YEARS) else 1.0
        return raw.copy(value = raw.value * durationFactor)
    }

    private fun electricity(evaluator: String, partner: String, c: Clause, gives: Boolean, w: Map<String, Double>): Value {
        val volume = c.params["volumeTWh"] ?: 0.0
        val price = (c.params["pricePercent"] ?: PERCENT) / PERCENT
        if (gives) {
            val balance = ctx.state.countries.getValue(evaluator).electricityBalanceTWh
            val strain = max(0.0, volume - balance)
            val v = w.getValue("sellWeight") * volume * price - w.getValue("strainWeight") * strain
            return Value(v, if (strain > 0) "nous ne disposons pas de tels excédents" else null)
        }
        val need = max(0.0, -ctx.state.countries.getValue(evaluator).electricityBalanceTWh - receivedElectricity(evaluator))
        val covered = min(volume, need)
        var v = w.getValue("needWeight") * covered + w.getValue("surplusWeight") * (volume - covered) -
            w.getValue("priceWeight") * volume * (price - 1.0)
        var reason: String? = if (price > 1.0) "le prix demandé est jugé trop élevé" else null
        // L'IA vérifie si le fournisseur semble capable de livrer, d'après ses propres estimations.
        if (partner == ctx.state.player.countryId) {
            val perceived = Perception(ctx).playerExportCapacity(evaluator)
            val doubt = max(0.0, volume - perceived)
            if (doubt > 0) {
                v -= w.getValue("credibilityWeight") * doubt
                reason = "nous doutons de votre capacité à livrer de tels volumes"
            }
        }
        return Value(v, reason)
    }

    private fun tariffs(evaluator: String, partner: String, c: Clause, nationalism: Double, w: Map<String, Double>): Value {
        val percent = c.params["percent"] ?: 0.0
        val trade = ctx.db.country(evaluator).definition.strategic.tradeWithPartnersBillions[partner] ?: 0.0
        val v = w.getValue("tradeWeight") * percent * trade / PERCENT * (1.0 - nationalism) -
            w.getValue("protectionWeight") * percent * nationalism
        return Value(v, if (v < 0) "nos producteurs craignent la concurrence" else null)
    }

    private fun aid(evaluator: String, c: Clause, gives: Boolean, w: Map<String, Double>): Value {
        val amount = c.params["amountBillions"] ?: 0.0
        val gdp = ctx.state.countries.getValue(evaluator).economy.gdpBillions
        val relative = amount / gdp * THOUSAND
        return if (gives) {
            Value(-w.getValue("aidWeight") * relative * w.getValue("giverCost"), "ce montant pèserait trop sur nos finances")
        } else {
            Value(w.getValue("aidWeight") * relative, null)
        }
    }

    private fun defense(evaluator: String, partner: String, c: Clause, militarism: Double, nationalism: Double, w: Map<String, Double>): Value {
        val intensity = c.params["intensity"] ?: 1.0
        val shared = ctx.db.country(evaluator).definition.strategic.alliances
            .intersect(ctx.db.country(partner).definition.strategic.alliances.toSet()).size
        val v = intensity * (w.getValue("militarismWeight") * (militarism - NEUTRAL) +
            w.getValue("allianceWeight") * shared - w.getValue("sovereigntyWeight") * nationalism)
        return Value(v, if (v < 0) "notre opinion reste attachée à l'autonomie de notre défense" else null)
    }

    private fun investment(c: Clause, gives: Boolean, w: Map<String, Double>): Value {
        val amount = c.params["amountBillions"] ?: 0.0
        return if (gives) {
            Value(-w.getValue("investWeight") * amount * w.getValue("giverCost"), "nos entreprises ne sont pas prêtes à un tel engagement")
        } else {
            Value(w.getValue("investWeight") * amount, null)
        }
    }

    /** Électricité déjà reçue au titre d'accords en vigueur. */
    private fun receivedElectricity(country: String): Double =
        ctx.state.diplomacy.agreements.filter { it.active && country in it.parties }
            .flatMap { it.clauses }.filter { it.type == ELECTRICITY && it.giver != country }
            .sumOf { it.params["volumeTWh"] ?: 0.0 }

    companion object {
        const val ELECTRICITY = "ELECTRICITY_SUPPLY"
        const val TARIFFS = "TARIFF_REDUCTION"
        const val AID = "FINANCIAL_AID"
        const val DEFENSE = "DEFENSE_COOPERATION"
        const val INVESTMENT = "INDUSTRIAL_INVESTMENT"
        private const val PERCENT = 100.0
        private const val THOUSAND = 1000.0
        private const val NEUTRAL = 0.5
        private const val REFERENCE_YEARS = 5.0
    }
}
