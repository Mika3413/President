package fr.president.engine.effects

import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.events.EventScope
import fr.president.engine.events.ScopeRef
import fr.president.engine.government.GovernmentChanges
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.clamp01

/**
 * Résout et applique les effets décrits dans les données.
 * Les cibles génériques ("scope.approval", "sender.relation") sont converties en cibles
 * concrètes ("dept.34.approval", "character.c-12.relation") au moment du déclenchement.
 */
class EffectApplier(private val ctx: SimulationContext) {

    /** Déclenche un effet : immédiat si sans durée ni délai, progressif sinon. */
    fun trigger(spec: EffectSpec, scope: ScopeRef?, params: Map<String, Double>, source: String) {
        val target = resolveTarget(spec.target, scope) ?: run {
            ctx.log("effects", "Cible non résolue ${spec.target} ($source)")
            return
        }
        val value = (spec.param?.let { params[it] } ?: spec.amount) * spec.factor
        if (spec.days <= 0.0 && spec.delayDays <= 0.0) {
            apply(target, value)
        } else {
            ctx.state.effects.add(
                ActiveEffect(target, value, ctx.now.plusDays(spec.delayDays), ctx.now.plusDays(spec.delayDays + spec.days), source = source)
            )
        }
    }

    fun resolveTarget(target: String, scope: ScopeRef?): String? {
        val (head, rest) = target.substringBefore('.') to target.substringAfter('.', "")
        return when (head) {
            "scope" -> resolveScoped(rest, scope)
            "sender" -> scope?.senderId?.let { "character.$it.$rest" }
            "subject" -> scope?.id?.takeIf { scope.type == EventScope.MINISTER }?.let { "character.$it.$rest" }
            "country" -> scope?.id?.takeIf { scope.type == EventScope.FOREIGN_COUNTRY }?.let { "memory.$it.$rest" }
            "region" -> if (rest == "approval") departmentOf(scope)?.let { dept ->
                ctx.state.territory.departments[dept]?.region?.let { "region.$it.approval" }
            } else target
            else -> target
        }
    }

    private fun resolveScoped(field: String, scope: ScopeRef?): String? = when (field) {
        "approval", "unemployment", "industry", "crime", "healthAccess", "pollution" -> departmentOf(scope)?.let { "dept.$it.$field" }
            ?: if (field == "approval") "opinion.national" else null
        "condition", "offlineDays", "maintenance" ->
            scope?.id?.takeIf { scope.type == EventScope.INFRASTRUCTURE }?.let { "infra.$it.$field" }
        "satisfaction" -> scope?.id?.takeIf { scope.type == EventScope.CITY }?.let { "city.$it.satisfaction" }
        else -> null
    }

    private fun departmentOf(scope: ScopeRef?): String? {
        val id = scope?.id ?: return null
        return when (scope.type) {
            EventScope.DEPARTMENT -> id
            EventScope.CITY -> ctx.state.territory.cities[id]?.department
            EventScope.INFRASTRUCTURE -> ctx.catalog.departmentOf(id)
            else -> null
        }
    }

    /** Applique immédiatement une variation à une cible concrète. */
    fun apply(target: String, delta: Double) {
        val parts = target.split('.')
        val state = ctx.state
        val economy = state.playerCountry.economy
        when (parts[0]) {
            "economy" -> when (parts[1]) {
                "output" -> economy.pendingOutputShock += delta
                "potentialGrowth" -> economy.potentialGrowth += delta
                "naturalUnemployment" -> economy.naturalUnemployment = (economy.naturalUnemployment + delta).coerceAtLeast(MIN_RATE)
                "consumerConfidence" -> economy.consumerConfidence = (economy.consumerConfidence + delta).clamp01()
                "businessConfidence" -> economy.businessConfidence = (economy.businessConfidence + delta).clamp01()
                "inflation" -> economy.inflation += delta
                "unemployment" -> economy.unemployment = (economy.unemployment + delta).coerceAtLeast(MIN_RATE)
            }
            "budget" -> if (parts[1] == "oneOff") economy.pendingOneOffBillions += delta
            "spending" -> economy.budget?.spending?.get(parts[1])?.let { it.policyFactor = (it.policyFactor + delta).coerceAtLeast(0.0) }
            "revenue" -> economy.budget?.revenues?.get(parts[1])?.let { it.rate = (it.rate + delta).coerceAtLeast(0.0) }
            "demography" -> if (parts[1] == "immigration") state.demography.immigrationFactor = (state.demography.immigrationFactor + delta).coerceAtLeast(0.0)
            "energy" -> if (parts[1] == "capacity") state.energy.extraCapacityMW.merge(parts[2], delta, Double::plus)
            "president" -> apply("character.${state.player.presidentId}.${parts[1]}", delta)
            "opinion" -> when (parts[1]) {
                "national" -> state.opinion.groups.values.forEach { it.shock += delta }
                "group" -> state.opinion.groups[parts[2]]?.let { it.shock += delta }
            }
            "dept" -> state.territory.departments[parts[1]]?.let { d ->
                when (parts[2]) {
                    "approval" -> d.localShock += delta
                    "unemployment" -> d.unemploymentOffset += delta
                    "industry" -> d.industryShare = (d.industryShare + delta).coerceIn(0.0, 1.0)
                    "crime" -> d.crime = (d.crime + delta).coerceAtLeast(0.1)
                    "healthAccess" -> d.healthAccess = (d.healthAccess + delta).coerceAtLeast(0.1)
                    "pollution" -> d.pollution = (d.pollution + delta).coerceAtLeast(0.1)
                }
            }
            "region" -> state.territory.departments.values.filter { it.region == parts[1] }
                .forEach { it.localShock += delta }
            "city" -> state.territory.cities[parts[1]]?.let { it.satisfaction = (it.satisfaction + delta).clamp01() }
            "infra" -> state.infrastructure[parts[1]]?.let { infra ->
                when (parts[2]) {
                    "condition" -> infra.condition = (infra.condition + delta).clamp01()
                    "maintenance" -> infra.maintenanceLevel = (infra.maintenanceLevel + delta).coerceAtLeast(0.0)
                    "offlineDays" -> {
                        val until = ctx.now.plusDays(delta)
                        if (infra.offlineUntil?.let { it < until } != false) infra.offlineUntil = until
                    }
                }
            }
            "quality" -> state.playerCountry.services[parts[1]]?.let {
                state.playerCountry.services[parts[1]] = (it + delta).clamp01()
            }
            "character" -> state.characters[parts[1]]?.let { c ->
                when (parts[2]) {
                    "relation" -> c.relationWithPlayer = (c.relationWithPlayer + delta).clamp01()
                    "loyalty" -> c.loyalty = (c.loyalty + delta).clamp01()
                    "popularity" -> c.popularity = (c.popularity + delta).clamp01()
                    "dismiss" -> if (delta > 0) GovernmentChanges(ctx).dismiss(c.id)
                    "scandal" -> if (delta > 0) c.scandals++
                }
            }
            "government" -> if (parts[1] == "parliamentSupport") {
                state.government.parliamentSupport = (state.government.parliamentSupport + delta).clamp01()
            }
            "military" -> when (parts[1]) {
                "readiness" -> state.military.units.values.filter { it.countryId == state.player.countryId }
                    .forEach { it.readiness = (it.readiness + delta).clamp01() }
                "ammoStock" -> state.military.stocks.ammunition = (state.military.stocks.ammunition + delta).clamp01()
                "fuelStock" -> state.military.stocks.fuel = (state.military.stocks.fuel + delta).clamp01()
            }
            "memory" -> state.diplomacy.relation(parts[1], state.player.countryId).memories
                .add(DiplomaticMemory(parts[2], delta, ctx.now))
            else -> ctx.log("effects", "Cible inconnue : $target")
        }
    }

    private companion object {
        const val MIN_RATE = 0.01
    }
}
