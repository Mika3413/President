package fr.president.engine.military

import fr.president.engine.data.ZoneDef
import fr.president.engine.simulation.SimulationContext

/**
 * Règles de souveraineté et d'hostilité : qui possède et contrôle une zone, qui est en guerre
 * avec qui, qui est allié, qui peut traverser quel territoire.
 */
class Geopolitics(private val ctx: SimulationContext) {
    private val military get() = ctx.state.military
    private val zones get() = ctx.db.zones

    fun ownerOf(zoneId: String): String = military.annexed[zoneId] ?: zones.zone(zoneId).owner
    fun controllerOf(zoneId: String): String = military.occupied[zoneId] ?: ownerOf(zoneId)

    /** Zones possédées par un pays, en tenant compte des annexions. */
    fun territoryOf(country: String): List<String> {
        val base = zones.ownedBy(country).map { it.id }.filter { military.annexed[it] == null || military.annexed[it] == country }
        return base + military.annexed.filter { it.value == country && zones.zone(it.key).owner != country }.keys
    }

    fun activeWars(): List<War> = military.wars.filter { it.status == WarStatus.ACTIVE }
    fun ongoingWars(): List<War> = military.wars.filter { it.status != WarStatus.ENDED }

    fun atWar(a: String, b: String): Boolean = activeWars().any { w ->
        (a in w.attackers && b in w.defenders) || (a in w.defenders && b in w.attackers)
    }

    fun warBetween(a: String, b: String): War? = ongoingWars().firstOrNull { w ->
        (a in w.attackers && b in w.defenders) || (a in w.defenders && b in w.attackers)
    }

    fun enemiesOf(country: String): Set<String> = activeWars().flatMap { w ->
        when (country) {
            in w.attackers -> w.defenders
            in w.defenders -> w.attackers
            else -> emptyList()
        }
    }.toSet()

    fun coBelligerents(country: String): Set<String> = ongoingWars().flatMap { w ->
        when (country) {
            in w.attackers -> w.attackers
            in w.defenders -> w.defenders
            else -> emptyList()
        }
    }.toSet() - country

    fun isAtWar(country: String): Boolean = enemiesOf(country).isNotEmpty()

    /** Alliances défensives communes (OTAN, UE...) et pactes bilatéraux. */
    fun allied(a: String, b: String): Boolean {
        if (a == b) return true
        if (ctx.db.alliances.any { it.defensive && a in it.members && b in it.members }) return true
        return agreementClause(a, b, DEFENSIVE_PACT)
    }

    fun defensivePartners(country: String): Set<String> {
        val fromAlliances = ctx.db.alliances.filter { it.defensive && country in it.members }.flatMap { it.members }
        val pacts = ctx.state.diplomacy.agreements.filter { it.active && country in it.parties && it.clauses.any { c -> c.type == DEFENSIVE_PACT } }
            .flatMap { it.parties }
        val guarantors = ctx.state.diplomacy.agreements.filter { it.active && country in it.parties }
            .flatMap { a -> a.clauses.filter { it.type == GUARANTEE && it.giver != country }.map { it.giver } }
        return (fromAlliances + pacts + guarantors).toSet() - country
    }

    fun hasPassage(country: String, territoryOwner: String): Boolean =
        ctx.state.diplomacy.agreements.any { a ->
            a.active && country in a.parties && territoryOwner in a.parties &&
                a.clauses.any { it.type == PASSAGE && it.giver == territoryOwner }
        }

    /** Une unité de [country] peut-elle entrer dans cette zone ? [attacking] autorise l'entrée chez l'ennemi. */
    fun canEnter(country: String, zone: ZoneDef, attacking: Boolean): Boolean {
        if (zone.sea) return true
        val controller = controllerOf(zone.id)
        return when {
            controller == country -> true
            atWar(country, controller) -> attacking
            controller in coBelligerents(country) -> true
            allied(country, controller) -> true
            hasPassage(country, controller) -> true
            else -> false
        }
    }

    /** Puissance militaire terrestre réelle d'un pays (somme des unités actives). */
    fun landPower(country: String): Double = ctx.state.military.units.values
        .filter { it.countryId == country && !it.destroyed }
        .sumOf { u ->
            val t = ctx.db.unitTypes[u.type] ?: return@sumOf 0.0
            if (t.domain == fr.president.engine.data.Domain.LAND) (t.attack + t.defense) / 2 * u.strength * u.readiness else 0.0
        }

    fun isNuclear(country: String): Boolean = ctx.db.country(country).definition.strategic.nuclear

    /** Pays voisins par la terre (zones adjacentes). */
    fun landNeighbors(country: String): Set<String> = territoryOf(country).flatMap { id ->
        zones.neighbors(id).filter { !it.sea }.map { ownerOf(it.id) }
    }.toSet() - country - ""

    private fun agreementClause(a: String, b: String, type: String) = ctx.state.diplomacy.agreements.any { ag ->
        ag.active && a in ag.parties && b in ag.parties && ag.clauses.any { it.type == type }
    }

    companion object {
        const val DEFENSIVE_PACT = "DEFENSIVE_ALLIANCE"
        const val GUARANTEE = "SECURITY_GUARANTEE"
        const val PASSAGE = "PASSAGE_RIGHTS"
    }
}
