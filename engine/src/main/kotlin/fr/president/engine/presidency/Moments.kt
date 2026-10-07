package fr.president.engine.presidency

import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.readout.Tone
import fr.president.engine.session.AgendaCost
import fr.president.engine.session.AgendaService
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- Données

@Serializable
data class MomentChoice(val text: String, val tone: String = "", val effects: Map<String, Double> = emptyMap(), val `when`: List<String> = emptyList(), val fits: List<String> = emptyList())

@Serializable
data class SpeechSection(val id: String, val label: String, val prompt: String, val choices: List<MomentChoice>)

@Serializable
data class SpeechTheme(val id: String, val label: String, val description: String = "", val `when`: List<String> = emptyList(), val fits: List<String> = emptyList())

@Serializable
data class SpeechDef(val cooldownDays: Double = 30.0, val themes: List<SpeechTheme> = emptyList(), val sections: List<SpeechSection> = emptyList(), val headlines: Map<String, List<String>> = emptyMap())

@Serializable
data class InterviewAnswer(val text: String, val style: String, val effects: Map<String, Double> = emptyMap())

@Serializable
data class InterviewQuestion(val id: String, val text: String, val `when`: List<String> = emptyList(), val answers: List<InterviewAnswer>)

@Serializable
data class InterviewDef(val cooldownDays: Double = 21.0, val journalists: List<String> = emptyList(), val questions: List<InterviewQuestion> = emptyList())

@Serializable
data class DebateChoice(val text: String, val style: String, val needs: String = "always")

@Serializable
data class DebateRound(val id: String, val label: String, val prompt: String, val choices: List<DebateChoice>)

@Serializable
data class DebateDef(val bonusMax: Double = 0.04, val rounds: List<DebateRound> = emptyList())

@Serializable
data class SummitChoice(val text: String, val effects: Map<String, Double> = emptyMap())

@Serializable
data class SummitExchange(val speaker: String, val prompt: String, val choices: List<SummitChoice>)

@Serializable
data class SummitDef(val label: String, val exchanges: List<SummitExchange>)

@Serializable
data class MomentsFile(val speech: SpeechDef = SpeechDef(), val interview: InterviewDef = InterviewDef(), val debate: DebateDef = DebateDef(), val summits: Map<String, SummitDef> = emptyMap())

// ---------------------------------------------------------------- État

@Serializable
enum class MomentKind(val label: String, val icon: String) {
    SPEECH("Allocution télévisée", "☎"), INTERVIEW("Interview du 20 heures", "✎"), DEBATE("Débat d'entre-deux-tours", "⚖"), SUMMIT("Sommet", "✪")
}

/** Moment en cours : étape, choix proposés (indices dans les données) et choix faits. */
@Serializable
class ActiveMoment(
    val kind: MomentKind,
    val ref: String = "",
    var step: Int = 0,
    var theme: String = "",
    val questions: MutableList<String> = mutableListOf(),
    var journalist: String = "",
    val offered: MutableList<Int> = mutableListOf(),
    val picks: MutableList<Int> = mutableListOf(),
    val tones: MutableList<String> = mutableListOf(),
    val effects: MutableMap<String, Double> = mutableMapOf(),
    var feedback: String = "",
    var wins: Int = 0,
    var losses: Int = 0,
)

@Serializable
class MomentRecord(val kind: MomentKind, val title: String, val headline: String, val verdict: String, val at: WorldTime, val tone: Tone, val lines: List<String> = emptyList())

@Serializable
class MomentsState(
    var active: ActiveMoment? = null,
    var lastSpeech: WorldTime? = null,
    var lastInterview: WorldTime? = null,
    /** Date de l'élection pour laquelle le débat a eu lieu. */
    var debateFor: WorldTime? = null,
    /** Points gagnés (ou perdus) au second tour grâce au débat. */
    var debateBonus: Double = 0.0,
    val summitsDone: MutableMap<String, WorldTime> = mutableMapOf(),
    val history: MutableList<MomentRecord> = mutableListOf(),
)

// ---------------------------------------------------------------- Service

/**
 * Les grands moments d'une présidence, joués pas à pas : l'allocution composée phrase par phrase,
 * l'interview du 20 heures, le débat d'entre-deux-tours et les sommets internationaux.
 */
class MomentService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.moments
    private val state get() = ctx.state.moments
    private val e get() = ctx.state.playerCountry.economy
    private val geo get() = fr.president.engine.military.Geopolitics(ctx)

    data class Offer(val kind: MomentKind, val ref: String, val title: String, val description: String, val blocker: String?)
    data class ChoiceView(val index: Int, val text: String, val hint: String = "")
    data class StepView(val kind: MomentKind, val title: String, val step: Int, val total: Int, val heading: String, val prompt: String, val choices: List<ChoiceView>, val feedback: String)

    val available: Boolean get() = file != null

    // ---- Conditions ----

    fun condition(key: String): Boolean {
        if (key.startsWith("!")) return !condition(key.drop(1))
        val player = ctx.state.player.countryId
        return when (key) {
            "war" -> geo.isAtWar(player)
            "foreignWar" -> geo.mainWarWithout(player) != null
            "unrest" -> ctx.state.unrest.movements.isNotEmpty()
            "crisis" -> ctx.state.consequences.current.values.any { it.active && it.severity >= 1.0 } || ctx.state.measures.active.isNotEmpty()
            "unemploymentHigh" -> e.unemployment > 0.085
            "inflationHigh" -> e.inflation > 0.035
            "deficitHigh" -> e.deficitRatio > 0.05
            "growthWeak" -> e.realGrowth < 0.01
            "lowApproval" -> ctx.state.opinion.nationalApproval < 0.4
            "highApproval" -> ctx.state.opinion.nationalApproval > 0.55
            "election" -> ctx.now.daysUntil(ctx.state.elections.nextElection) < 120
            else -> true
        }
    }

    private fun ok(whenList: List<String>) = whenList.all { condition(it) }

    // ---- Offres ----

    fun offers(): List<Offer> {
        val f = file ?: return emptyList()
        val list = mutableListOf<Offer>()
        list += Offer(MomentKind.SPEECH, "", "Allocution télévisée", "Composez votre discours phrase par phrase : un discours cohérent et en phase avec le pays porte, un discours décousu tombe à plat.",
            cooldown(state.lastSpeech, f.speech.cooldownDays) ?: AgendaService(ctx).blocker(SPEECH_AGENDA))
        list += Offer(MomentKind.INTERVIEW, "", "Interview du 20 heures", "Quatre questions tirées de l'actualité. Assumer, promettre, esquiver ou attaquer : chaque réponse compte.",
            cooldown(state.lastInterview, f.interview.cooldownDays) ?: AgendaService(ctx).blocker(INTERVIEW_AGENDA))
        if (debateOpen()) list += Offer(MomentKind.DEBATE, "", "Débat d'entre-deux-tours", "Cinq manches face à votre adversaire, devant 20 millions de téléspectateurs. Le vainqueur gagne des points au second tour.", null)
        summitsOpen().forEach { (id, def) -> list += Offer(MomentKind.SUMMIT, id, def.label, "Les dirigeants vous attendent : chaque prise de parole change vos relations.", null) }
        return list
    }

    private fun cooldown(last: WorldTime?, days: Double): String? =
        last?.let { val left = days - it.daysUntil(ctx.now); if (left > 0) "Encore ${left.toInt().coerceAtLeast(1)} j avant de reprendre la parole ainsi." else null }

    /** Le second tour est programmé et le débat n'a pas eu lieu pour cette élection. */
    fun debateOpen(): Boolean {
        val second = ctx.state.scheduler.actions.any { it is ScheduledAction.ElectionRound && it.round == 2 }
        return second && ctx.state.elections.pendingFirstRound != null && state.debateFor != ctx.state.elections.nextElection
    }

    fun summitsOpen(): List<Pair<String, SummitDef>> {
        val f = file ?: return emptyList()
        return f.summits.filter { (id, _) ->
            val fired = ctx.state.events.lastFired[id] ?: return@filter false
            fired.daysUntil(ctx.now) < SUMMIT_WINDOW && state.summitsDone[id]?.let { it >= fired } != true
        }.toList()
    }

    // ---- Déroulé ----

    fun start(kind: MomentKind, ref: String = ""): Result<StepView> = runCatching {
        val offer = offers().firstOrNull { it.kind == kind && it.ref == ref } ?: error("Ce moment n'est pas disponible.")
        offer.blocker?.let { error(it) }
        val f = file!!
        val m = ActiveMoment(kind, ref)
        when (kind) {
            MomentKind.SPEECH -> AgendaService(ctx).book("Allocution télévisée", SPEECH_AGENDA)
            MomentKind.INTERVIEW -> {
                AgendaService(ctx).book("Interview télévisée", INTERVIEW_AGENDA)
                m.journalist = ctx.rng.pick(f.interview.journalists.ifEmpty { listOf("Le 20 heures") })
                val topical = f.interview.questions.filter { it.`when`.isNotEmpty() && ok(it.`when`) }.shuffled(java.util.Random(ctx.now.dayIndex))
                val general = f.interview.questions.filter { it.`when`.isEmpty() }.shuffled(java.util.Random(ctx.now.dayIndex + 1))
                m.questions += (topical + general).take(QUESTIONS).map { it.id }
            }
            MomentKind.DEBATE -> state.debateFor = ctx.state.elections.nextElection
            MomentKind.SUMMIT -> Unit
        }
        state.active = m
        view()!!
    }

    fun cancel() { state.active = null }

    /** L'étape en cours, avec les choix possibles (ceux qui ont un sens dans la situation). */
    fun view(): StepView? {
        val m = state.active ?: return null
        val f = file ?: return null
        m.offered.clear()
        return when (m.kind) {
            MomentKind.SPEECH -> if (m.step == 0) {
                val themes = f.speech.themes.withIndex().filter { ok(it.value.`when`) }
                m.offered += themes.map { it.index }
                StepView(m.kind, "Allocution télévisée", 0, f.speech.sections.size + 1, "Le thème", "De quoi voulez-vous parler aux Français ce soir ?",
                    themes.map { (i, t) -> ChoiceView(i, t.label, t.description + if (t.fits.any { condition(it) }) " — en phase avec l'actualité" else "") }, m.feedback)
            } else {
                val section = f.speech.sections[m.step - 1]
                val choices = section.choices.withIndex().filter { ok(it.value.`when`) }
                m.offered += choices.map { it.index }
                StepView(m.kind, "Allocution : ${f.speech.themes.firstOrNull { it.id == m.theme }?.label.orEmpty()}", m.step, f.speech.sections.size + 1, section.label, section.prompt,
                    choices.map { (i, ch) -> ChoiceView(i, ch.text, TONES[ch.tone].orEmpty()) }, m.feedback)
            }
            MomentKind.INTERVIEW -> {
                val q = f.interview.questions.first { it.id == m.questions[m.step] }
                m.offered += q.answers.indices
                StepView(m.kind, "Interview — ${m.journalist}", m.step, m.questions.size, "Question ${m.step + 1}", fill(q.text),
                    q.answers.mapIndexed { i, a -> ChoiceView(i, "« ${a.text} »", STYLES[a.style].orEmpty()) }, m.feedback)
            }
            MomentKind.DEBATE -> {
                val r = f.debate.rounds[m.step]
                m.offered += r.choices.indices
                StepView(m.kind, "Débat face à ${opponentName()}", m.step, f.debate.rounds.size, r.label, r.prompt,
                    r.choices.mapIndexed { i, ch -> ChoiceView(i, ch.text, STYLES[ch.style].orEmpty()) }, m.feedback)
            }
            MomentKind.SUMMIT -> {
                val s = f.summits.getValue(m.ref)
                val x = s.exchanges[m.step]
                m.offered += x.choices.indices
                StepView(m.kind, s.label, m.step, s.exchanges.size, ctx.db.countries[x.speaker]?.definition?.name ?: "Un dirigeant", x.prompt,
                    x.choices.mapIndexed { i, ch -> ChoiceView(i, ch.text) }, m.feedback)
            }
        }
    }

    /** Choix d'une réponse : étape suivante, ou bilan du moment s'il est terminé. */
    fun choose(index: Int): Result<MomentRecord?> = runCatching {
        val m = state.active ?: error("Aucun moment en cours.")
        view()
        require(index in m.offered) { "Choix impossible." }
        val f = file!!
        m.picks += index
        when (m.kind) {
            MomentKind.SPEECH -> if (m.step == 0) {
                m.theme = f.speech.themes[index].id
                m.feedback = "Thème choisi : ${f.speech.themes[index].label}."
            } else {
                val ch = f.speech.sections[m.step - 1].choices[index]
                m.tones += ch.tone
                ch.effects.forEach { (k, v) -> m.effects.merge(k, v, Double::plus) }
                if (ch.fits.any { condition(it) }) m.tones += "FIT"
                m.feedback = ""
            }
            MomentKind.INTERVIEW -> {
                val a = f.interview.questions.first { it.id == m.questions[m.step] }.answers[index]
                m.tones += a.style
                a.effects.forEach { (k, v) -> m.effects.merge(k, v, Double::plus) }
                m.feedback = REACTIONS[a.style].orEmpty()
            }
            MomentKind.DEBATE -> {
                val ch = f.debate.rounds[m.step].choices[index]
                val chance = debateChance(ch)
                val roll = ctx.rng.nextDouble()
                m.feedback = when {
                    roll < chance - 0.15 -> { m.wins++; "✔ Manche gagnée : ${WIN_LINES[ch.style].orEmpty()}" }
                    roll > chance + 0.15 -> { m.losses++; "✕ Manche perdue : ${LOSE_LINES[ch.style].orEmpty()}" }
                    else -> "≈ Match nul : aucun des deux ne prend l'ascendant."
                }
                m.tones += ch.style
            }
            MomentKind.SUMMIT -> {
                val ch = f.summits.getValue(m.ref).exchanges[m.step].choices[index]
                ch.effects.forEach { (k, v) -> ctx.effects.trigger(EffectSpec(k, v), null, emptyMap(), "summit:${m.ref}") }
                m.feedback = "Votre réponse : ${ch.text}"
            }
        }
        m.step++
        val total = when (m.kind) {
            MomentKind.SPEECH -> f.speech.sections.size + 1
            MomentKind.INTERVIEW -> m.questions.size
            MomentKind.DEBATE -> f.debate.rounds.size
            MomentKind.SUMMIT -> f.summits.getValue(m.ref).exchanges.size
        }
        if (m.step < total) null else finish(m)
    }

    private fun finish(m: ActiveMoment): MomentRecord {
        state.active = null
        val record = when (m.kind) {
            MomentKind.SPEECH -> finishSpeech(m)
            MomentKind.INTERVIEW -> finishInterview(m)
            MomentKind.DEBATE -> finishDebate(m)
            MomentKind.SUMMIT -> finishSummit(m)
        }
        state.history += record
        while (state.history.size > MAX_HISTORY) state.history.removeAt(0)
        ctx.notifications.news(NotificationCategory.POLITICS, record.headline, null, world = false)
        JournalService(ctx).add("Président", "${record.title} : ${record.verdict.substringBefore(" :")}", record.tone)
        return record
    }

    private fun finishSpeech(m: ActiveMoment): MomentRecord {
        val f = file!!
        state.lastSpeech = ctx.now
        val tones = m.tones.filter { it != "FIT" }
        val dominant = tones.groupingBy { it }.eachCount().maxByOrNull { it.value }
        val coherence = (dominant?.value ?: 0).toDouble() / tones.size.coerceAtLeast(1)
        val theme = f.speech.themes.firstOrNull { it.id == m.theme }
        var fit = m.tones.count { it == "FIT" } + if (theme?.fits?.any { condition(it) } == true) 1 else 0
        // Déconnecté : parler de rigueur ou de vision quand le pays souffre, sans un mot pour lui.
        val suffering = condition("inflationHigh") || condition("unemploymentHigh") || condition("unrest")
        val heard = tones.any { it == "EMPATHY" || it == "SOCIAL" || it == "UNITY" }
        if (suffering && !heard) fit -= 2
        val factor = ((0.5 + coherence) * (1 + FIT_WEIGHT * fit)).coerceIn(0.3, 1.8)
        val lines = apply(m.effects, factor)
        ctx.effects.trigger(EffectSpec("president.popularity", POPULARITY_SWING * (factor - 1)), null, emptyMap(), "moment")
        ctx.effects.trigger(EffectSpec("media.climate", CLIMATE_SWING * (factor - 1)), null, emptyMap(), "moment")
        val audience = 6.0 + (if (condition("war") || condition("crisis")) 8.0 else 0.0) + (if (condition("unrest")) 3.0 else 0.0) + ctx.rng.nextDouble() * 3
        val headline = dominant?.key?.let { f.speech.headlines[it]?.let { h -> ctx.rng.pick(h) } } ?: "Allocution du président"
        val (verdict, tone) = when {
            factor >= 1.25 -> "Allocution réussie : ${fmt(audience)} millions de téléspectateurs, un discours jugé clair et juste." to Tone.GOOD
            factor >= 0.95 -> "Allocution correcte : ${fmt(audience)} millions de téléspectateurs, sans grand effet." to Tone.NEUTRAL
            suffering && !heard -> "Allocution ratée : ${fmt(audience)} millions de téléspectateurs, un président jugé déconnecté." to Tone.BAD
            else -> "Allocution ratée : ${fmt(audience)} millions de téléspectateurs, un discours jugé décousu." to Tone.BAD
        }
        return MomentRecord(MomentKind.SPEECH, "Allocution", headline, verdict, ctx.now, tone,
            listOf("Cohérence du discours : ${Math.round(coherence * 100)} %", "En phase avec l'actualité : " + if (fit > 0) "oui" else if (fit < 0) "non" else "moyennement") + lines)
    }

    private fun finishInterview(m: ActiveMoment): MomentRecord {
        state.lastInterview = ctx.now
        val frank = m.tones.count { it == "ASSUME" || it == "PROMISE" }
        val dodge = m.tones.count { it == "DEFLECT" }
        val factor = (1.0 + 0.12 * frank - 0.2 * dodge).coerceIn(0.5, 1.5)
        val lines = apply(m.effects, factor)
        val (verdict, tone) = when {
            factor >= 1.25 -> "Interview convaincante : un président direct, qui répond aux questions." to Tone.GOOD
            factor >= 0.95 -> "Interview sans surprise : les éditorialistes restent partagés." to Tone.NEUTRAL
            else -> "Interview ratée : trop d'esquives, la presse dénonce la langue de bois." to Tone.BAD
        }
        ctx.effects.trigger(EffectSpec("media.climate", CLIMATE_SWING * (factor - 1)), null, emptyMap(), "moment")
        val headline = when (tone) { Tone.GOOD -> "Au 20 heures, le président convainc"; Tone.BAD -> "Au 20 heures, le président esquive"; else -> "Le président s'explique au 20 heures" }
        return MomentRecord(MomentKind.INTERVIEW, "Interview — ${m.journalist}", headline, verdict, ctx.now, tone, lines)
    }

    private fun finishDebate(m: ActiveMoment): MomentRecord {
        val f = file!!
        val balance = (m.wins - m.losses).toDouble() / f.debate.rounds.size
        state.debateBonus = (f.debate.bonusMax * balance).coerceIn(-f.debate.bonusMax, f.debate.bonusMax)
        val convincing = (50 + balance * 25).coerceIn(15.0, 85.0)
        val (verdict, tone) = when {
            balance > 0.15 -> "Vous remportez le débat : ${convincing.toInt()} % des téléspectateurs vous jugent plus convaincant." to Tone.GOOD
            balance < -0.15 -> "Votre adversaire remporte le débat : seuls ${convincing.toInt()} % vous jugent plus convaincant." to Tone.BAD
            else -> "Débat serré : ${convincing.toInt()} % vous jugent plus convaincant." to Tone.NEUTRAL
        }
        val bonus = Math.round(state.debateBonus * 1000) / 10.0
        return MomentRecord(MomentKind.DEBATE, "Débat face à ${opponentName()}", if (tone == Tone.GOOD) "Débat : le président domine son adversaire" else if (tone == Tone.BAD) "Débat : le président en difficulté" else "Un débat sans vainqueur net",
            verdict, ctx.now, tone, listOf("Manches gagnées : ${m.wins}, perdues : ${m.losses}", "Effet attendu au second tour : ${if (bonus >= 0) "+" else ""}${fmt(bonus)} point(s)"))
    }

    private fun finishSummit(m: ActiveMoment): MomentRecord {
        state.summitsDone[m.ref] = ctx.now
        val def = file!!.summits.getValue(m.ref)
        return MomentRecord(MomentKind.SUMMIT, def.label, "${def.label} : la France fait entendre sa voix", "Vos prises de position ont été notées par chaque délégation.", ctx.now, Tone.NEUTRAL)
    }

    /** Applique les effets cumulés, multipliés par la réussite, et les résume en clair. */
    private fun apply(effects: Map<String, Double>, factor: Double): List<String> {
        effects.forEach { (k, v) -> ctx.effects.trigger(EffectSpec(k, v * factor), null, emptyMap(), "moment") }
        return effects.entries.sortedByDescending { kotlin.math.abs(it.value) }.take(MAX_LINES).mapNotNull { (k, v) ->
            val label = when {
                k.startsWith("opinion.group.") -> ctx.playerData.socialGroups?.groups?.firstOrNull { it.id == k.removePrefix("opinion.group.") }?.label
                k == "opinion.national" -> "Opinion"
                k == "president.popularity" -> "Popularité"
                k.startsWith("economy.") -> "Confiance économique"
                else -> null
            } ?: return@mapNotNull null
            "$label ${if (v * factor >= 0) "▲" else "▼"}"
        }
    }

    private fun debateChance(ch: DebateChoice): Double {
        val approval = ctx.state.opinion.nationalApproval
        val president = ctx.state.characters[ctx.state.player.presidentId]
        val base = when (ch.needs) {
            "economy" -> 0.5 + (if (e.realGrowth > 0.01) 0.15 else -0.1) + (if (e.unemployment < 0.08) 0.1 else -0.1)
            "approval" -> 0.5 + (approval - 0.45) * 1.5
            "competence" -> 0.4 + (president?.competence ?: 0.5) * 0.4
            "security" -> 0.5 + ((ctx.state.playerCountry.services["security"] ?: 0.5) - 0.5)
            "diplomacy" -> 0.55
            else -> 0.5
        }
        // L'attaque est un pari : elle paie si l'adversaire est fragile, sinon elle se retourne.
        val opponent = ctx.state.elections.candidates.firstOrNull { it.characterId == opponentId() }
        val variance = if (ch.style == "ATTACK") (0.1 - (opponent?.momentum ?: 0.0)) else 0.0
        return (base + variance).coerceIn(0.15, 0.85)
    }

    private fun opponentId(): String? {
        val first = ctx.state.elections.pendingFirstRound ?: return null
        return first.shares.entries.sortedByDescending { it.value }.map { it.key }.firstOrNull { it != ctx.state.player.presidentId }
    }

    private fun opponentName(): String = opponentId()?.let { ctx.state.characters[it]?.fullName } ?: "votre adversaire"

    private fun fill(text: String): String = text
        .replace("{unemployment}", Formatting.percent(e.unemployment))
        .replace("{inflation}", Formatting.percent(e.inflation))
        .replace("{deficit}", Formatting.percent(e.deficitRatio))
        .replace("{approval}", Formatting.wholePercent(ctx.state.opinion.nationalApproval))

    private fun fmt(v: Double) = String.format(java.util.Locale.FRENCH, "%.1f", v)

    companion object {
        val SPEECH_AGENDA = AgendaCost(0.5)
        val INTERVIEW_AGENDA = AgendaCost(0.25)
        private const val QUESTIONS = 4
        private const val SUMMIT_WINDOW = 15.0
        private const val MAX_HISTORY = 30
        private const val MAX_LINES = 5
        private const val FIT_WEIGHT = 0.1
        private const val POPULARITY_SWING = 0.02
        private const val CLIMATE_SWING = 0.15
        val TONES = mapOf("UNITY" to "rassembleur", "EMPATHY" to "proche des gens", "REFORM" to "réformateur", "FIRM" to "ferme", "PATRIOT" to "patriote",
            "SOCIAL" to "social", "VISION" to "visionnaire", "COMBAT" to "offensif")
        val STYLES = mapOf("ASSUME" to "assumer", "PROMISE" to "promettre", "DEFLECT" to "esquiver", "ATTACK" to "attaquer", "FACTS" to "les chiffres",
            "EMPATHY" to "l'émotion", "HUMILITY" to "l'humilité", "PROJECT" to "le projet")
        val REACTIONS = mapOf("ASSUME" to "La journaliste note votre franchise.", "PROMISE" to "« Vous l'avez déjà promis », relance-t-elle.",
            "DEFLECT" to "La journaliste insiste : « Vous ne répondez pas à la question. »", "ATTACK" to "Le ton monte sur le plateau.")
        val WIN_LINES = mapOf("FACTS" to "vos chiffres font mouche.", "EMPATHY" to "les téléspectateurs sont touchés.", "ATTACK" to "votre adversaire perd pied.",
            "HUMILITY" to "votre sincérité désarme.", "PROJECT" to "votre projet paraît solide.")
        val LOSE_LINES = mapOf("FACTS" to "vos chiffres sont contestés en direct.", "EMPATHY" to "on vous reproche de jouer la comédie.", "ATTACK" to "l'attaque se retourne contre vous.",
            "HUMILITY" to "votre adversaire exploite vos aveux.", "PROJECT" to "votre adversaire demande comment vous le financerez.")
    }
}
