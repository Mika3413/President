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
        // « chain.<événement> » : suite d'une histoire, déclenchée avec une probabilité (amount) après un délai.
        if (spec.target.startsWith(CHAIN_PREFIX)) {
            chain(spec.target.removePrefix(CHAIN_PREFIX), spec, scope, source)
            return
        }
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

    private fun chain(eventId: String, spec: EffectSpec, scope: ScopeRef?, source: String) {
        val def = ctx.db.events.firstOrNull { it.id == eventId } ?: run {
            ctx.log("effects", "Suite inconnue $eventId ($source)")
            return
        }
        if (!ctx.rng.chance(spec.amount.coerceIn(0.0, 1.0))) return
        // La suite garde le même territoire (ou pays) que l'épisode précédent quand c'est possible.
        // Sans territoire hérité, une suite locale (manifestation, émeutes...) éclate dans une grande ville ou un département.
        val scopeId = scope?.id?.takeIf { scope.type == def.scope } ?: when (def.scope) {
            EventScope.CITY -> ctx.playerData.territory?.cities?.filter { it.rank <= def.maxCityRank }?.takeIf { it.isNotEmpty() }?.let { ctx.rng.pick(it).id }
            EventScope.DEPARTMENT -> ctx.state.territory.departments.keys.filter { it.length < 3 }.takeIf { it.isNotEmpty() }?.let { ctx.rng.pick(it) }
            else -> null
        }
        if (scopeId == null && def.scope != EventScope.NATIONAL) {
            ctx.log("effects", "Suite $eventId ignorée : territoire incompatible ($source)")
            return
        }
        val delay = spec.delayDays.coerceAtLeast(MIN_CHAIN_DELAY_DAYS) * ctx.rng.nextDouble(CHAIN_JITTER_MIN, CHAIN_JITTER_MAX)
        ctx.scheduler.schedule(fr.president.engine.simulation.ScheduledAction.EventLaunch(ctx.now.plusDays(delay), def.id, scopeId))
        ctx.log("effects", "Suite programmée : $eventId dans %.1f jours ($source)".format(delay))
    }

    fun resolveTarget(target: String, scope: ScopeRef?): String? {
        val (head, rest) = target.substringBefore('.') to target.substringAfter('.', "")
        return when (head) {
            "scope" -> resolveScoped(rest, scope)
            "sender" -> scope?.senderId?.let { "character.$it.$rest" }
            "subject" -> scope?.id?.takeIf { scope.type == EventScope.MINISTER }?.let { "character.$it.$rest" }
            "country" -> scope?.id?.takeIf { scope.type == EventScope.FOREIGN_COUNTRY }?.let { "memory.$it.$rest" }
            // Conséquences dans le pays de l'événement : « abroad.output », « abroad.trade »...
            "abroad" -> scope?.id?.takeIf { scope.type == EventScope.FOREIGN_COUNTRY }?.let { "abroad.$it.$rest" }
            // Riposte contre le pays de l'événement : « operation.cyber », « operation.sanction ».
            "operation" -> scope?.id?.takeIf { scope.type == EventScope.FOREIGN_COUNTRY }?.let { "operation.$rest.$it" }
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

    /**
     * Conséquences d'un événement dans un pays étranger, qui se propagent :
     * - output / approval : son économie et la popularité de son dirigeant ;
     * - trade : notre activité, à proportion de nos échanges avec lui ;
     * - solidarity : les pays qui lui sont proches l'aident, et il s'en souviendra ;
     * - partners.KIND : ses alliés retiennent notre attitude envers lui.
     */
    private fun abroad(country: String, field: String, kind: String?, delta: Double) {
        val state = ctx.state
        val player = state.player.countryId
        val target = state.countries[country] ?: return
        when (field) {
            "output" -> target.economy.pendingOutputShock += delta
            "approval" -> target.leaderApproval = (target.leaderApproval + delta).clamp01()
            "trade" -> {
                val trade = ctx.playerData.definition.strategic.tradeWithPartnersBillions
                val share = (trade[country] ?: 0.0) / trade.values.sum().coerceAtLeast(1.0)
                state.playerCountry.economy.pendingOutputShock += delta * share
            }
            "solidarity" -> {
                val relations = fr.president.engine.diplomacy.RelationCalculator(ctx)
                val helpers = state.countries.keys.filter { it != player && it != country && relations.score(it, country) > HELPER_RELATION }
                helpers.forEach { state.diplomacy.relation(country, it).memories.add(DiplomaticMemory("CRISIS_SOLIDARITY", delta * HELPER_WEIGHT, ctx.now)) }
                if (helpers.isNotEmpty()) {
                    val name = ctx.db.countries[country]?.definition?.name ?: country
                    ctx.notifications.news(fr.president.engine.notifications.NotificationCategory.DIPLOMACY,
                        "${helpers.size} pays envoient de l'aide : ${name}", country)
                }
            }
            "partners" -> {
                val k = kind ?: return
                val alliances = ctx.db.alliances.filter { country in it.members }
                alliances.flatMap { it.members }.distinct().filter { it != player && it != country && it in state.countries }
                    .forEach { apply("memory.$it.$k", delta) }
            }
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
            "sector" -> fr.president.engine.economy.SectorSystem.shock(ctx, parts[1], delta)
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
                    "scandal" -> if (delta > 0) { c.scandals++; c.scandalDates += ctx.now.seconds }
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
            // Souvenir collectif : tous les membres d'une alliance (UE, OTAN...), sauf le joueur.
            "alliance" -> ctx.db.alliances.firstOrNull { it.id == parts[1] }?.members
                ?.filter { it != state.player.countryId && it in state.countries }
                ?.forEach { apply("memory.$it.${parts[2]}", delta) }
            // Belligérants de la principale guerre étrangère : war.attackers.KIND / war.defenders.KIND.
            "war" -> fr.president.engine.military.Geopolitics(ctx).mainWarWithout(state.player.countryId)?.let { w ->
                val side = if (parts[1] == "attackers") w.attackers else w.defenders
                side.filter { it != state.player.countryId }.forEach { apply("memory.$it.${parts[2]}", delta) }
            }
            "abroad" -> parts.getOrNull(2)?.let { field -> abroad(parts[1], field, parts.getOrNull(3), delta) }
            "actor" -> state.actors.actors[parts[1]]?.let { it.goodwill += delta }
            "unrest" -> when (parts[1]) {
                "armyLoyalty" -> if (state.unrest.armyLoyalty >= 0) state.unrest.armyLoyalty = (state.unrest.armyLoyalty + delta).clamp01()
                else -> if (delta > 0) fr.president.engine.politics.UnrestService(ctx).let { s -> s.cause(parts[1])?.let { s.spark(it, delta) } }
            }
            "intel" -> if (parts[1] == "capacity" && state.intel.capacity >= 0) state.intel.capacity = (state.intel.capacity + delta).clamp01()
            "liberty" -> state.legislation.libertyOffset += delta
            "power" -> if (parts[1] == "article16" && delta > 0) fr.president.engine.legislation.LegislationService(ctx).startArticle16(delta)
            "operation" -> if (delta > 0) parts.getOrNull(2)?.let { country ->
                when (parts[1]) {
                    "cyber" -> fr.president.engine.military.OperationsService(ctx).riposteCyber(country)
                    "sanction" -> fr.president.engine.diplomacy.SanctionsService(ctx).impose(state.player.countryId, country)
                }
            }
            else -> ctx.log("effects", "Cible inconnue : $target")
        }
    }

    private companion object {
        const val CHAIN_PREFIX = "chain."
        const val HELPER_RELATION = 0.5
        const val HELPER_WEIGHT = 0.6
        const val MIN_CHAIN_DELAY_DAYS = 1.0
        const val CHAIN_JITTER_MIN = 0.8
        const val CHAIN_JITTER_MAX = 1.3
        const val MIN_RATE = 0.01
    }
}
