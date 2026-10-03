package fr.president.engine.elections

import fr.president.engine.data.LocalElectionKind
import fr.president.engine.data.LocalElectionsDef
import fr.president.engine.data.LocalLevel
import fr.president.engine.data.PoliticalFamilyDef
import fr.president.engine.events.SenderResolver
import fr.president.engine.government.ParliamentService
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.CharacterRole
import fr.president.engine.setup.PoliticalSetup
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.time.WorldTime
import fr.president.engine.util.clamp01
import kotlin.math.abs

/**
 * Élections locales : chaque exécutif (région, département, ville) est remporté par le camp
 * présidentiel ou par l'opposition selon l'opinion locale. Les élus changent, leur relation au
 * président aussi, et un revers national inquiète les députés de la majorité.
 */
class LocalElectionService(private val ctx: SimulationContext) {
    private val def: LocalElectionsDef? = ctx.playerData.elections?.local
    private val state get() = ctx.state.localElections
    private val parliament = ParliamentService(ctx)

    /** Planifie les prochains scrutins (nouvelle partie ou sauvegarde antérieure). */
    fun ensure() {
        val def = def ?: return
        for (kind in def.kinds) {
            if (kind.id in state.next) continue
            var at = WorldTime.parse(kind.firstDate)
            while (at < ctx.now) at = at.plusYears(kind.termYears)
            state.next[kind.id] = at
            ctx.scheduler.schedule(ScheduledAction.LocalElection(at, kind.id))
        }
    }

    fun upcoming(): List<Pair<LocalElectionKind, WorldTime>> {
        val def = def ?: return emptyList()
        return def.kinds.mapNotNull { k -> state.next[k.id]?.let { k to it } }.sortedBy { it.second }
    }

    /** Le personnage appartient-il au camp présidentiel (famille proche de celle du président) ? */
    private fun inPresidentCamp(characterId: String?): Boolean {
        val c = characterId?.let { ctx.state.characters[it] } ?: return false
        return parliament.affinity(parliament.familyOf(c), parliament.presidentFamily()) >= CAMP_AFFINITY
    }

    private data class Seat(val id: String, val label: String, val weight: Double, val approval: Double, val leaning: Double, val holder: String?)

    private fun seats(level: LocalLevel): List<Seat> {
        val t = ctx.state.territory
        val names = SenderResolver(ctx)
        return when (level) {
            LocalLevel.REGION -> t.regions.values.map { r ->
                val depts = t.departments.values.filter { it.region == r.code }
                val population = depts.sumOf { it.population }.toDouble().coerceAtLeast(1.0)
                Seat(r.code, names.regionName(r.code), population,
                    depts.sumOf { it.approval * it.population } / population,
                    depts.sumOf { it.politicalLeaning * it.population } / population, r.presidentId)
            }
            LocalLevel.DEPARTMENT -> t.departments.values.map { d ->
                Seat(d.code, names.departmentName(d.code), d.population.toDouble(), d.approval, d.politicalLeaning, d.presidentId)
            }
            LocalLevel.CITY -> t.cities.values.map { c ->
                val d = t.departments.getValue(c.department)
                Seat(c.id, names.cityName(c.id), c.population.toDouble(), (d.approval + c.satisfaction) / 2, d.politicalLeaning, c.mayorId)
            }
        }
    }

    fun run(kindId: String, at: WorldTime) {
        val def = def ?: return
        val kind = def.kinds.firstOrNull { it.id == kindId } ?: return
        if (state.next[kindId] != at || ctx.state.player.gameOver != null) return
        val presidentFamily = parliament.presidentFamily()
        val opposition = parliament.families.filter { parliament.affinity(it, presidentFamily) < CAMP_AFFINITY }
        val gains = mutableListOf<Seat>()
        val losses = mutableListOf<Seat>()
        var won = 0
        val all = seats(kind.level)
        for (seat in all) {
            val incumbentCamp = inPresidentCamp(seat.holder)
            val share = NEUTRAL + def.swing * (seat.approval - NEUTRAL) +
                (if (incumbentCamp) def.incumbentBonus else -def.incumbentBonus) + ctx.rng.nextGaussian() * def.noise
            val campWins = share > NEUTRAL
            if (campWins) won++
            if (campWins && !incumbentCamp) gains += seat
            if (!campWins && incumbentCamp) losses += seat
            val keepHolder = campWins == incumbentCamp && seat.holder != null && ctx.rng.nextDouble() < def.incumbentRerunChance
            if (!keepHolder) {
                val family = if (campWins) presidentFamily else closestOpposition(opposition, seat.leaning) ?: presidentFamily
                install(kind.level, seat.id, family, campWins)
            }
        }
        state.next[kindId] = at.plusYears(kind.termYears)
        ctx.scheduler.schedule(ScheduledAction.LocalElection(state.next.getValue(kindId), kindId))
        state.results += LocalElectionResult(ctx.now, kindId, all.size, won, gains.map { it.id }, losses.map { it.id })

        // Un revers local rend les députés de la majorité plus indociles (et inversement).
        val ratio = won.toDouble() / all.size.coerceAtLeast(1)
        ctx.state.government.parliamentSupport = (ctx.state.government.parliamentSupport + def.parliamentImpact * (ratio - NEUTRAL)).clamp01()

        val highlight = { list: List<Seat> -> list.sortedByDescending { it.weight }.take(HIGHLIGHTS).joinToString(", ") { it.label } }
        val details = buildString {
            append("Votre camp remporte $won ${unit(kind.level, won)} sur ${all.size}.")
            if (gains.isNotEmpty()) append(" Conquêtes : ${highlight(gains)}.")
            if (losses.isNotEmpty()) append(" Pertes : ${highlight(losses)}.")
            append(if (ratio >= NEUTRAL) " Un résultat encourageant pour la majorité." else " La majorité s'inquiète de ce revers.")
        }
        ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.IMPORTANT, "${kind.label} : résultats", details)
        ctx.log("local", "${kind.id} : $won/${all.size}, gains ${gains.size}, pertes ${losses.size}")
    }

    private fun closestOpposition(opposition: List<PoliticalFamilyDef>, leaning: Double): PoliticalFamilyDef? =
        opposition.minByOrNull { abs(it.economicPosition - leaning) }

    private fun install(level: LocalLevel, id: String, family: PoliticalFamilyDef, presidentCamp: Boolean) {
        val t = ctx.state.territory
        val role = when (level) {
            LocalLevel.REGION -> CharacterRole.REGION_PRESIDENT
            LocalLevel.DEPARTMENT -> CharacterRole.DEPARTMENT_PRESIDENT
            LocalLevel.CITY -> CharacterRole.MAYOR
        }
        val previous = when (level) {
            LocalLevel.REGION -> t.regions[id]?.presidentId
            LocalLevel.DEPARTMENT -> t.departments[id]?.presidentId
            LocalLevel.CITY -> t.cities[id]?.mayorId
        }
        previous?.let { ctx.state.characters[it] }?.let { it.role = CharacterRole.FORMER; it.active = false }
        val person = PoliticalSetup(ctx).person(ctx.state.player.countryId, role, id, family.economicPosition, NEWCOMER_SPREAD, family.socialPosition)
        person.relationWithPlayer = if (presidentCamp) {
            (person.relationWithPlayer + ALLY_RELATION_BONUS).coerceAtMost(1.0)
        } else {
            (person.relationWithPlayer - OPPONENT_RELATION_MALUS).coerceAtLeast(0.0)
        }
        when (level) {
            LocalLevel.REGION -> t.regions[id]?.presidentId = person.id
            LocalLevel.DEPARTMENT -> t.departments[id]?.presidentId = person.id
            LocalLevel.CITY -> t.cities[id]?.mayorId = person.id
        }
    }

    private fun unit(level: LocalLevel, n: Int): String = when (level) {
        LocalLevel.REGION -> if (n > 1) "régions" else "région"
        LocalLevel.DEPARTMENT -> if (n > 1) "départements" else "département"
        LocalLevel.CITY -> if (n > 1) "grandes villes" else "grande ville"
    }

    private companion object {
        const val NEUTRAL = 0.5
        const val CAMP_AFFINITY = 0.5
        const val HIGHLIGHTS = 4
        const val NEWCOMER_SPREAD = 0.1
        const val ALLY_RELATION_BONUS = 0.2
        const val OPPONENT_RELATION_MALUS = 0.2
    }
}
