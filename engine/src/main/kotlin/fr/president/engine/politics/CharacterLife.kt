package fr.president.engine.politics

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.util.Formatting

/**
 * Des personnages qui ont une âme : ils se souviennent de ce que vous leur avez fait (projet
 * soutenu ou enterré, désaveu, soutien dans la tourmente, guerre déclarée...), le rappellent dans
 * leurs lettres, en gardent rancune ou reconnaissance, et cela se lit sur leur visage. Les
 * ministres ambitieux se dressent quand vous faiblissez ; les fidèles montent au créneau.
 */
class CharacterLife(private val ctx: SimulationContext) {

    /** Retient un fait : [text] complète « vous avez... » (« soutenu mon projet de... »). */
    fun remember(c: Character, text: String, weight: Double) {
        c.memories += CharacterMemory(ctx.now.seconds, text, weight)
        // On oublie d'abord les petites choses anciennes.
        while (c.memories.size > MAX_MEMORIES) c.memories.remove(c.memories.minBy { Math.abs(it.weight) * 2 + it.time / SECONDS_PER_YEAR })
        c.mood = (c.mood + weight * MOOD_FACTOR).coerceIn(-1.0, 1.0)
        c.relationWithPlayer = (c.relationWithPlayer + weight * RELATION_FACTOR).coerceIn(0.0, 1.0)
    }

    fun rememberById(id: String?, text: String, weight: Double) {
        val c = id?.let { ctx.state.characters[it] } ?: return
        if (c.role == CharacterRole.PRESIDENT) return
        remember(c, text, weight)
    }

    /** Une phrase de lettre qui rappelle le souvenir le plus fort (passé d'au moins quelques jours). */
    fun recall(c: Character): String? {
        val m = c.memories.filter { ctx.now.seconds - it.time >= MIN_RECALL_SECONDS && Math.abs(it.weight) >= MIN_RECALL_WEIGHT }
            .maxByOrNull { Math.abs(it.weight) } ?: return null
        val date = Formatting.monthYear(fr.president.engine.time.WorldTime(m.time))
        return if (m.weight > 0) "Je n'oublie pas qu'en $date, vous avez ${m.text}. Je vous en reste reconnaissant${if (c.female) "e" else ""}."
        else "Je n'ai pas oublié qu'en $date, vous avez ${m.text}."
    }

    fun recallById(id: String?): String? = id?.let { ctx.state.characters[it] }?.let { recall(it) }

    fun expression(c: Character): Int = expressionOf(c)

    fun feeling(c: Character): String = when (expression(c)) {
        2 -> if (c.female) "Vous est très attachée" else "Vous est très attaché"
        1 -> "Bien disposé${if (c.female) "e" else ""} envers vous"
        -1 -> if (c.female) "Réservée, voire rancunière" else "Réservé, voire rancunier"
        -2 -> "Vous en veut"
        else -> "Neutre"
    }

    /** Ce que la fiche dit du personnage : son sentiment envers vous, ses ambitions, ses souvenirs. */
    fun sheet(c: Character, minister: Boolean): List<Pair<String, Int>> {
        val lines = ArrayList<Pair<String, Int>>()
        lines += "Envers vous : ${feeling(c).replaceFirstChar { it.lowercase() }}" to expressionOf(c)
        if (minister) lines += "Ambition : ${ambitionLabel(c).replaceFirstChar { it.lowercase() }}" to (if (c.rivalry > 0) -1 else 0)
        c.memories.sortedByDescending { Math.abs(it.weight) }.take(SHOWN_MEMORIES).sortedBy { it.time }.forEach { m ->
            val date = Formatting.monthYear(fr.president.engine.time.WorldTime(m.time))
            lines += "Se souvient : vous avez ${m.text} ($date)" to (if (m.weight >= 0) 1 else -1)
        }
        return lines
    }

    fun ambition(c: Character): Double = c.trait(Traits.EGO) * AMBITION_EGO + c.trait(Traits.CHARISMA) * AMBITION_CHARISMA + c.popularity * AMBITION_POPULARITY

    fun ambitionLabel(c: Character): String = when {
        c.rivalry >= 2 -> "Joue sa propre carte"
        c.rivalry == 1 -> "Ambitions affichées"
        ambition(c) > HIGH_AMBITION -> if (c.female) "Ambitieuse" else "Ambitieux"
        else -> if (c.female) "Discrète" else "Discret"
    }

    // --- Vie du gouvernement, chaque mois ---------------------------------------------------------

    fun monthly() {
        val gov = ctx.state.government
        val approval = ctx.state.opinion.nationalApproval
        ctx.state.characters.values.filter { it.active }.forEach { c -> c.mood *= MOOD_DECAY }
        if (ctx.state.player.gameOver != null) return
        for ((_, id) in gov.ministers.toList()) {
            val m = ctx.state.characters[id] ?: continue
            if (!m.active || id == gov.primeMinisterId) continue
            rivalry(m, approval)
            defend(m, approval)
        }
    }

    private fun rivalry(m: Character, approval: Double) {
        val gov = ctx.state.government
        val ambitious = ambition(m) > HIGH_AMBITION
        val cold = m.relationWithPlayer < COLD_RELATION
        when {
            // Un président fort et un ministre bien traité : l'ambition se tait.
            m.rivalry > 0 && (approval > STRONG_APPROVAL || m.relationWithPlayer > WARM_RELATION) -> m.rivalry -= 1
            ambitious && cold && approval < WEAK_APPROVAL && ctx.rng.chance(RIVALRY_CHANCE) -> {
                m.rivalry += 1
                when (m.rivalry) {
                    1 -> {
                        ctx.notifications.news(NotificationCategory.GOVERNMENT, "${m.fullName} ne cache plus ses ambitions", null)
                        JournalService(ctx).add("Gouvernement", "${m.fullName} affiche ses ambitions", Tone.WARNING)
                    }
                    2 -> if (gov.affairs.none { it.ministerId == m.id }) fr.president.engine.government.CabinetService(ctx).raise(fr.president.engine.government.AffairKind.RIVALRY, m)
                    else -> betray(m)
                }
            }
        }
    }

    /** La rupture : le ministre claque la porte et attaque l'Élysée. */
    fun betray(m: Character) {
        m.rivalry = 3
        fr.president.engine.government.GovernmentChanges(ctx).dismiss(m.id)
        ctx.effects.trigger(fr.president.engine.effects.EffectSpec("opinion.national", -BETRAYAL_OPINION), null, emptyMap(), "rivalry")
        ctx.effects.trigger(fr.president.engine.effects.EffectSpec("government.parliamentSupport", -BETRAYAL_SUPPORT), null, emptyMap(), "rivalry")
        ctx.notifications.post(NotificationCategory.GOVERNMENT, fr.president.engine.notifications.Urgency.URGENT, "${m.fullName} claque la porte du gouvernement",
            "« Je ne peux plus cautionner cette politique. » Le ministre démissionne avec fracas et entraîne une partie de la majorité.", null)
        JournalService(ctx).add("Gouvernement", "Trahison : ${m.fullName} quitte le gouvernement avec fracas", Tone.BAD)
    }

    /** Un fidèle monte au créneau quand le président est dans la tourmente. */
    private fun defend(m: Character, approval: Double) {
        if (approval > TROUBLE_APPROVAL || m.loyalty < LOYAL || m.relationWithPlayer < WARM_RELATION) return
        if (ctx.now.seconds - m.lastDefense < DEFENSE_COOLDOWN_SECONDS || !ctx.rng.chance(DEFENSE_CHANCE)) return
        m.lastDefense = ctx.now.seconds
        ctx.effects.trigger(fr.president.engine.effects.EffectSpec("opinion.national", DEFENSE_OPINION), null, emptyMap(), "loyalty")
        ctx.notifications.news(NotificationCategory.GOVERNMENT, "${m.fullName} monte au créneau pour défendre le président", null)
        JournalService(ctx).add("Gouvernement", "${m.fullName} prend publiquement votre défense", Tone.GOOD)
    }

    companion object {
        /** Visage : -2 furieux, -1 fermé, 0 neutre, 1 aimable, 2 rayonnant. */
        fun expressionOf(c: Character): Int {
            val v = (c.relationWithPlayer - 0.5) * 2 * RELATION_SHARE + c.mood * (1 - RELATION_SHARE)
            return when {
                v >= 0.45 -> 2
                v >= 0.15 -> 1
                v <= -0.45 -> -2
                v <= -0.15 -> -1
                else -> 0
            }
        }

        private const val MAX_MEMORIES = 10
        private const val SHOWN_MEMORIES = 2
        private const val SECONDS_PER_YEAR = 31_557_600.0
        private const val MOOD_FACTOR = 1.2
        private const val RELATION_FACTOR = 0.25
        private const val MOOD_DECAY = 0.7
        private const val MIN_RECALL_SECONDS = 5L * 86_400
        private const val MIN_RECALL_WEIGHT = 0.12
        private const val RELATION_SHARE = 0.6
        private const val AMBITION_EGO = 0.55
        private const val AMBITION_CHARISMA = 0.25
        private const val AMBITION_POPULARITY = 0.2
        const val HIGH_AMBITION = 0.58
        private const val COLD_RELATION = 0.45
        private const val WARM_RELATION = 0.62
        private const val WEAK_APPROVAL = 0.42
        private const val STRONG_APPROVAL = 0.52
        private const val RIVALRY_CHANCE = 0.35
        private const val BETRAYAL_OPINION = 0.008
        private const val BETRAYAL_SUPPORT = 0.03
        private const val TROUBLE_APPROVAL = 0.4
        private const val LOYAL = 0.72
        private const val DEFENSE_CHANCE = 0.3
        private const val DEFENSE_OPINION = 0.003
        private const val DEFENSE_COOLDOWN_SECONDS = 120L * 86_400
    }
}

/** Chaque mois : l'humeur retombe, les ambitieux avancent leurs pions, les fidèles vous défendent. */
class CharacterLifeSystem : SimulationSystem {
    override val name = "characterLife"
    override val cadence = Cadence.MONTHLY
    override fun run(ctx: SimulationContext) = CharacterLife(ctx).monthly()
}
