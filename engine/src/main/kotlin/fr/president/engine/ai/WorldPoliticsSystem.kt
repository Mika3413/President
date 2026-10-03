package fr.president.engine.ai

import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.military.Geopolitics
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.CharacterSpec
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/**
 * Vie politique du reste du monde (mensuelle) : alternances à la tête des pays étrangers,
 * coopérations et tensions entre pays tiers. Le monde évolue sans le joueur.
 */
class WorldPoliticsSystem : SimulationSystem {
    override val name = "world-politics"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val player = ctx.state.player.countryId
        val foreign = ctx.state.countries.values.filter { it.id != player }
        for (country in foreign) {
            val due = country.nextLeadershipChange
            if (due == null) {
                country.nextLeadershipChange = ctx.now.plusDays(ctx.rng.nextDouble(MIN_TERM_DAYS, MAX_TERM_DAYS))
                continue
            }
            if (ctx.now >= due) leadership(ctx, country.id)
        }
        repeat(INTERACTIONS_PER_MONTH) { interaction(ctx, foreign.map { it.id }) }
    }

    private fun leadership(ctx: SimulationContext, id: String) {
        val country = ctx.state.countries.getValue(id)
        country.nextLeadershipChange = ctx.now.plusDays(ctx.rng.nextDouble(MIN_TERM_DAYS, MAX_TERM_DAYS))
        if (Geopolitics(ctx).isAtWar(id)) return
        val reelected = ctx.rng.chance(country.leaderApproval.coerceIn(MIN_REELECTION, MAX_REELECTION))
        val name = ctx.db.country(id).definition.name
        if (reelected) {
            ctx.notifications.news(NotificationCategory.DIPLOMACY, "$name : le chef du gouvernement est reconduit", id)
            return
        }
        val def = ctx.db.country(id).definition
        val old = ctx.state.characters[country.leaderId]
        old?.let { it.role = CharacterRole.FORMER; it.active = false }
        val leader = ctx.characters.generate(
            ctx.state.newId("chr"),
            CharacterSpec(id, CharacterRole.FOREIGN_LEADER, id, ctx.now.toDateTime().year,
                ageRange = def.leader.ageRange[0]..def.leader.ageRange[1],
                economicLeaning = def.leader.economicLeaningRange.average(), traitRanges = def.leader.traitRanges),
            ctx.rng,
        )
        ctx.state.characters[leader.id] = leader
        country.leaderId = leader.id
        country.leaderApproval = NEW_LEADER_APPROVAL
        // Une partie des contentieux personnels s'efface avec l'alternance.
        ctx.state.diplomacy.relations.filterKeys { it.startsWith("$id>") }.values.forEach { r ->
            r.memories.replaceAll { m -> if (m.kind in PERSONAL_KINDS) m.copy(weight = m.weight * RESET_FACTOR) else m }
        }
        val important = def.strategic.militaryBudgetBillions > IMPORTANT_BUDGET || id in NEIGHBOURS
        val text = "${leader.fullName} prend la tête du gouvernement (${def.institutions.headOfGovernmentTitle})."
        if (important) ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.IMPORTANT, "$name : nouveau dirigeant", text, id)
        else ctx.notifications.news(NotificationCategory.DIPLOMACY, "$name : $text", id)
    }

    private fun interaction(ctx: SimulationContext, countries: List<String>) {
        if (countries.size < 2) return
        val a = ctx.rng.pick(countries)
        val b = ctx.rng.pick(countries - a)
        val la = ctx.state.characters[ctx.state.countries.getValue(a).leaderId] ?: return
        val lb = ctx.state.characters[ctx.state.countries.getValue(b).leaderId] ?: return
        val tension = (la.trait(Traits.AGGRESSIVENESS) + lb.trait(Traits.AGGRESSIVENESS)) / 2
        val openness = (la.trait(Traits.OPENNESS) + lb.trait(Traits.OPENNESS)) / 2
        val (na, nb) = ctx.db.country(a).definition.name to ctx.db.country(b).definition.name
        when {
            ctx.rng.chance(TENSION_CHANCE * tension) -> {
                remember(ctx, a, b, "DISAGREEMENT", -INTERACTION_WEIGHT, "différend bilatéral")
                ctx.notifications.news(NotificationCategory.DIPLOMACY, "Regain de tensions entre $na et $nb", a)
            }
            ctx.rng.chance(COOPERATION_CHANCE * openness) -> {
                remember(ctx, a, b, "AGREEMENT_SIGNED", INTERACTION_WEIGHT, "accord bilatéral")
                ctx.notifications.news(NotificationCategory.DIPLOMACY, "$na et $nb signent un accord de coopération", a)
            }
        }
    }

    private fun remember(ctx: SimulationContext, a: String, b: String, kind: String, w: Double, detail: String) {
        ctx.state.diplomacy.relation(a, b).memories += DiplomaticMemory(kind, w, ctx.now, detail)
        ctx.state.diplomacy.relation(b, a).memories += DiplomaticMemory(kind, w, ctx.now, detail)
    }

    private companion object {
        const val MIN_TERM_DAYS = 365.0
        const val MAX_TERM_DAYS = 1825.0
        const val MIN_REELECTION = 0.2
        const val MAX_REELECTION = 0.8
        const val NEW_LEADER_APPROVAL = 0.55
        const val RESET_FACTOR = 0.6
        const val IMPORTANT_BUDGET = 40.0
        const val INTERACTIONS_PER_MONTH = 2
        const val TENSION_CHANCE = 0.4
        const val COOPERATION_CHANCE = 0.5
        const val INTERACTION_WEIGHT = 0.03
        val PERSONAL_KINDS = setOf("PROPOSAL_REFUSED", "PROPOSAL_IGNORED", "ULTIMATUM", "CONDEMNATION", "NEGOTIATION_GOODWILL", "BACKED_DOWN")
        val NEIGHBOURS = setOf("DEU", "BEL", "ESP", "ITA", "CHE", "GBR")
    }
}
