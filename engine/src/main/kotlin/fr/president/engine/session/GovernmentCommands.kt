package fr.president.engine.session

import fr.president.engine.government.Priority
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.Character
import fr.president.engine.politics.CharacterRole
import fr.president.engine.setup.PoliticalSetup
import fr.president.engine.simulation.SimulationContext

/** Actions du président sur son gouvernement. */
class GovernmentCommands(private val ctx: SimulationContext) {
    private val def get() = ctx.playerData.government!!

    fun candidates(ministryId: String): List<Character> =
        ctx.state.government.candidates[ministryId].orEmpty().mapNotNull { ctx.state.characters[it] }

    fun appoint(ministryId: String, candidateId: String): Result<Unit> = runCatching {
        val ministry = def.ministries.first { it.id == ministryId }
        val gov = ctx.state.government
        require(candidateId in gov.candidates[ministryId].orEmpty()) { "Cette personnalité n'est pas disponible" }
        val newcomer = ctx.state.characters.getValue(candidateId)
        val previousId = if (ministry.isPrimeMinister) gov.primeMinisterId else gov.ministers[ministryId]
        previousId?.let { ctx.state.characters[it] }?.let { dismissed ->
            dismissed.role = CharacterRole.FORMER
            dismissed.active = false
            dismissed.relationWithPlayer = (dismissed.relationWithPlayer - DISMISSAL_RESENTMENT).coerceAtLeast(0.0)
            // Limoger une personnalité populaire a un coût dans l'opinion.
            val cost = (dismissed.popularity - newcomer.popularity) * POPULARITY_SWING
            ctx.state.opinion.groups.values.forEach { it.shock -= cost }
        }
        newcomer.role = if (ministry.isPrimeMinister) CharacterRole.PRIME_MINISTER else CharacterRole.MINISTER
        newcomer.roleRef = ministryId
        newcomer.relationWithPlayer = (newcomer.relationWithPlayer + APPOINTMENT_GRATITUDE).coerceAtMost(1.0)
        if (ministry.isPrimeMinister) gov.primeMinisterId = newcomer.id else gov.ministers[ministryId] = newcomer.id
        gov.candidates[ministryId]?.remove(candidateId)
        val president = ctx.state.characters.getValue(ctx.state.player.presidentId)
        PoliticalSetup(ctx).refreshCandidates(ctx.playerData, ministryId, president.economicLeaning)
        ctx.notifications.post(NotificationCategory.GOVERNMENT, Urgency.IMPORTANT, "Nomination : ${newcomer.fullName}",
            "${newcomer.fullName} devient ${ministry.title}.")
    }

    fun setPriority(ministryId: String, priority: Priority): Result<Unit> = runCatching {
        val gov = ctx.state.government
        if (priority == Priority.HIGH) {
            val highs = gov.priorities.count { it.value == Priority.HIGH && it.key != ministryId }
            require(highs < def.maxHighPriorities) { "Au plus ${def.maxHighPriorities} priorités hautes : le gouvernement ne peut pas tout faire à la fois" }
        }
        gov.priorities[ministryId] = priority
    }

    private companion object {
        const val DISMISSAL_RESENTMENT = 0.3
        const val POPULARITY_SWING = 0.03
        const val APPOINTMENT_GRATITUDE = 0.15
    }
}
