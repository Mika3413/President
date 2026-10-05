package fr.president.engine.territory

import fr.president.engine.legislation.LeverService
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach
import kotlinx.serialization.Serializable

/**
 * La vie quotidienne des Français, au-delà des grands agrégats : loyers et prix des logements,
 * natalité, espérance de vie, épargne, travail au noir. Chaque chiffre a ses causes (décisions,
 * économie, services) et ses effets (opinion, croissance, recettes, démographie).
 */
@Serializable
class SocietyState(
    /** Indice des loyers (1 = départ). */
    var rentIndex: Double = 1.0,
    /** Indice des prix des logements (1 = départ). */
    var housePriceIndex: Double = 1.0,
    /** Hausse annuelle des loyers (dernier mois, en rythme annuel). */
    var rentGrowth: Double = BASE_RENT_GROWTH,
    /** Indicateur conjoncturel de fécondité (enfants par femme). */
    var fertility: Double = FERTILITY_START,
    /** Espérance de vie à la naissance (années). */
    var lifeExpectancy: Double = LIFE_START,
    /** Taux d'épargne des ménages (part du revenu disponible). */
    var savingsRate: Double = SAVINGS_START,
    /** Économie souterraine (part du PIB). */
    var informal: Double = INFORMAL_START,
    /** Recettes perdues à cause du travail au noir supplémentaire (Md€ par an). */
    var fraudLossBillions: Double = 0.0,
    /** Taux d'emprunt au départ, référence de l'immobilier. */
    var baseRate: Double = -1.0,
) {
    companion object {
        const val BASE_RENT_GROWTH = 0.02
        const val FERTILITY_START = 1.62
        const val LIFE_START = 83.0
        const val SAVINGS_START = 0.175
        const val INFORMAL_START = 0.11
    }
}

class SocietySystem : SimulationSystem {
    override val name = "society"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val s = ctx.state.society
        val e = ctx.state.playerCountry.economy
        val levers = LeverService(ctx)
        fun param(id: String, default: Double) = levers.lever("param:$id")?.let { levers.current(it.id) } ?: default
        fun quality(id: String) = ctx.state.playerCountry.services[id] ?: NEUTRAL
        if (s.baseRate < 0) s.baseRate = e.marketRate
        val rateGap = e.marketRate - s.baseRate
        val construction = ctx.state.market.sectors["construction"]?.activity ?: 1.0
        val immigration = ctx.state.demography.immigrationFactor

        // Loyers : les aides au logement sont en partie captées par les propriétaires, l'immigration
        // et les taux bas poussent la demande, la construction pousse l'offre. Un plafond légal freine.
        val housingAid = param("housing_aid", 100.0) / 100 - 1
        var rentGrowth = SocietyState.BASE_RENT_GROWTH + e.inflation * 0.5 + housingAid * AID_CAPTURE + (immigration - 1) * MIGRATION_RENT -
            (construction - 1) * SUPPLY_RENT - rateGap * RATE_RENT + (e.realGrowth - 0.01) * 0.3
        rentCap(ctx)?.let { cap -> rentGrowth = minOf(rentGrowth, cap / 100) }
        s.rentGrowth = rentGrowth
        s.rentIndex *= 1 + rentGrowth / MONTHS
        // Prix des logements : taux d'intérêt, confiance, revenus.
        val priceGrowth = 0.01 - rateGap * RATE_PRICE + (e.consumerConfidence - 0.45) * 0.1 + e.realGrowth * 0.5 + (immigration - 1) * MIGRATION_RENT
        s.housePriceIndex = (s.housePriceIndex * (1 + priceGrowth / MONTHS)).coerceAtLeast(0.3)

        // Natalité : allocations familiales, coût du logement, confiance dans l'avenir, chômage.
        val family = param("family_allowance", 100.0) / 100 - 1
        val fertilityTarget = SocietyState.FERTILITY_START + family * FAMILY_FERTILITY - (s.rentIndex / priceLevel(ctx) - 1) * RENT_FERTILITY +
            (e.consumerConfidence - 0.45) * 0.4 - (e.unemployment - e.reference.unemployment) * 2.0 + (immigration - 1) * 0.05
        s.fertility = approach(s.fertility, fertilityTarget.coerceIn(1.0, 2.6), FERTILITY_SPEED)

        // Espérance de vie : hôpital, pauvreté, environnement — lentement.
        val lifeTarget = SocietyState.LIFE_START + (quality("health") - 0.52) * HEALTH_LIFE + (quality("social") - 0.6) * SOCIAL_LIFE +
            (quality("environment") - 0.5) * ENV_LIFE - (e.unemployment - e.reference.unemployment) * 10
        s.lifeExpectancy = approach(s.lifeExpectancy, lifeTarget.coerceIn(70.0, 90.0), LIFE_SPEED)

        // Épargne : l'inquiétude fait épargner, l'inflation et les taux bas font dépenser.
        val savingsTarget = SocietyState.SAVINGS_START + (0.45 - e.consumerConfidence) * 0.15 + (e.unemployment - e.reference.unemployment) * 0.3 +
            rateGap * 0.8 - (e.inflation - 0.02) * 0.3
        val before = s.savingsRate
        s.savingsRate = approach(s.savingsRate, savingsTarget.coerceIn(0.05, 0.35), SAVINGS_SPEED)
        // Ce qui est épargné en plus n'est pas consommé : l'activité en pâtit (et inversement).
        e.pendingOutputShock -= (s.savingsRate - before) * CONSUMPTION_SHARE

        // Travail au noir : charges et impôts lourds, SMIC élevé, RSA proche du SMIC, contrôles faibles.
        val social = e.budget?.revenues?.get("social_contributions")?.let { it.rate / it.referenceRate - 1 } ?: 0.0
        val burden = fr.president.engine.economy.BudgetCalculator.taxBurden(e, fr.president.engine.data.TaxPayer.HOUSEHOLDS) - e.reference.householdTaxBurden
        val rsa = fr.president.engine.events.VariableResolver(ctx).resolve("derived.rsaToSmic") ?: 0.45
        val controls = (quality("justice") - 0.42) + ((e.budget?.spending?.get("state_operations")?.policyFactor ?: 1.0) - 1)
        val informalTarget = SocietyState.INFORMAL_START + social * 0.08 + burden * 0.4 + param("smic_boost", 0.0) / 100 * 0.1 +
            (rsa - 0.45).coerceAtLeast(0.0) * 0.05 - controls * 0.05 + (e.unemployment - e.reference.unemployment) * 0.12
        s.informal = approach(s.informal, informalTarget.coerceIn(0.03, 0.4), INFORMAL_SPEED)
        val loss = (s.informal - SocietyState.INFORMAL_START) * e.gdpBillions * INFORMAL_TAX_SHARE
        e.pendingOneOffBillions += loss / MONTHS
        s.fraudLossBillions = loss
    }

    private fun priceLevel(ctx: SimulationContext) = ctx.state.playerCountry.economy.priceLevel

    /** Plafond légal de hausse des loyers (mesure « plafonner les loyers »), en % par an. */
    private fun rentCap(ctx: SimulationContext): Double? = ctx.state.legislation.measures.values
        .filter { it.config.action == "price_cap" && it.config.target == "rents" }.minOfOrNull { it.value }

    private companion object {
        const val MONTHS = 12.0
        const val NEUTRAL = 0.5
        const val AID_CAPTURE = 0.02
        const val MIGRATION_RENT = 0.03
        const val SUPPLY_RENT = 0.08
        const val RATE_RENT = 0.5
        const val RATE_PRICE = 1.5
        const val FAMILY_FERTILITY = 0.15
        const val RENT_FERTILITY = 0.5
        const val FERTILITY_SPEED = 0.03
        const val HEALTH_LIFE = 6.0
        const val SOCIAL_LIFE = 3.0
        const val ENV_LIFE = 2.0
        const val LIFE_SPEED = 0.01
        const val SAVINGS_SPEED = 0.1
        const val CONSUMPTION_SHARE = 0.2
        const val INFORMAL_SPEED = 0.04
        const val INFORMAL_TAX_SHARE = 0.3
    }
}
