package fr.president.engine.presidency

import fr.president.engine.data.CountryNames
import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- Données

@Serializable
data class TournamentDef(
    val id: String,
    val label: String,
    val team: String,
    val host: String,
    val start: String,
    val stages: List<String> = emptyList(),
    val stageDays: List<Int> = emptyList(),
    val winChance: Double = 0.6,
    val olympics: Boolean = false,
    val durationDays: Int = 16,
    val medals: Int = 30,
    val hosted: Boolean = false,
)

@Serializable
data class DossierChoice(val text: String, val effects: Map<String, Double> = emptyMap(), val readiness: Double = 0.0)

@Serializable
data class DossierDef(val id: String, val event: String = "", val label: String, val from: String, val days: Int = 60, val prompt: String, val choices: List<DossierChoice>)

@Serializable
data class CatastrophePhase(
    val day: Int,
    val headline: String,
    val worldOutput: Double = 0.0,
    val countryOutput: Double = 0.0,
    val effects: Map<String, Double> = emptyMap(),
    val franceEvent: String? = null,
)

@Serializable
data class CatastropheDef(
    val id: String,
    val label: String,
    val chancePerYear: Double,
    val phases: List<CatastrophePhase>,
    val aid: Boolean = false,
    val country: String? = null,
    val countries: List<String> = emptyList(),
)

@Serializable
data class AidDef(val prompt: String = "", val choices: List<DossierChoice> = emptyList())

@Serializable
data class MajorEventsFile(
    val tournaments: List<TournamentDef> = emptyList(),
    val dossiers: List<DossierDef> = emptyList(),
    val catastrophes: List<CatastropheDef> = emptyList(),
    val aid: AidDef = AidDef(),
    val readinessStart: Double = 0.5,
    val cooldownDays: Double = 365.0,
)

// ---------------------------------------------------------------- État

@Serializable
class TournamentState(var stage: Int = 0, var eliminated: Boolean = false, var won: Boolean = false, var done: Boolean = false, var medals: Int = 0, var announced: Boolean = false)

@Serializable
class CatastropheState(val id: String, val startedAt: WorldTime, val country: String? = null, var phase: Int = 0)

@Serializable
class MajorEventsState(
    val tournaments: MutableMap<String, TournamentState> = mutableMapOf(),
    var readiness: Double = -1.0,
    val dossiersDone: MutableSet<String> = mutableSetOf(),
    val active: MutableList<CatastropheState> = mutableListOf(),
    var lastCatastrophe: WorldTime? = null,
    /** Appels à l'aide en attente : identifiant de catastrophe + pays, avec une date limite. */
    val aidRequests: MutableMap<String, WorldTime> = mutableMapOf(),
    /** Champions à recevoir à l'Élysée (compétition gagnée), avec une date limite. */
    val champions: MutableMap<String, WorldTime> = mutableMapOf(),
)

// ---------------------------------------------------------------- Service

/** Calendrier sportif, organisation des JO 2030, catastrophes mondiales et dossiers qui en découlent. */
class MajorEventsService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.majorEvents
    private val state get() = ctx.state.majorEvents
    private val player get() = ctx.state.player.countryId

    fun date(iso: String): WorldTime = WorldTime.parse("${iso}T00:00:00Z")

    fun readiness(): Double {
        if (state.readiness < 0) state.readiness = file?.readinessStart ?: 0.5
        return state.readiness
    }

    /** Prochaines compétitions (et en cours), pour l'agenda et la note du jour. */
    fun upcoming(limit: Int = 3): List<Pair<TournamentDef, WorldTime>> = file?.tournaments.orEmpty()
        .map { it to date(it.start) }
        .filter { (t, at) -> state.tournaments[t.id]?.done != true && at.daysUntil(ctx.now) < t.stageDays.lastOrNull()?.plus(1) ?: t.durationDays }
        .sortedBy { it.second }.take(limit)

    // ---- Dossiers (joués comme des moments à une seule étape) ----

    data class Dossier(val id: String, val label: String, val prompt: String, val choices: List<DossierChoice>)

    fun openDossiers(): List<Dossier> {
        val f = file ?: return emptyList()
        val list = mutableListOf<Dossier>()
        f.dossiers.filter { it.id !in state.dossiersDone }.forEach { d ->
            val from = date(d.from)
            if (ctx.now >= from && from.daysUntil(ctx.now) < d.days) list += Dossier(d.id, d.label, d.prompt + "\nPréparation des Jeux : ${Math.round(readiness() * 100)} %.", d.choices)
        }
        state.aidRequests.filter { it.value > ctx.now }.keys.forEach { key ->
            val (catId, country) = key.split('|').let { it[0] to it.getOrNull(1).orEmpty() }
            val c = ctx.db.countries[country] ?: return@forEach
            val names = CountryNames(c.definition)
            val label = f.catastrophes.firstOrNull { it.id == catId }?.label ?: "Catastrophe"
            list += Dossier("aid|$key", "Aide internationale : ${label.lowercase()} ${names.inside}",
                f.aid.prompt.replace("{The}", names.the.replaceFirstChar { it.uppercase() }),
                f.aid.choices.map { ch -> ch.copy(effects = ch.effects.mapKeys { (k, _) -> k.replace("{c}", country) }) })
        }
        state.champions.filter { it.value > ctx.now }.keys.forEach { id ->
            val t = f.tournaments.firstOrNull { it.id == id } ?: return@forEach
            list += Dossier("champions|$id", "Recevoir les champions à l'Élysée", "Après leur victoire (${t.label}), ${t.team} défile sur les Champs-Élysées. Comment les accueillez-vous ?", listOf(
                DossierChoice("Grande réception à l'Élysée et décorations.", mapOf("president.popularity" to 0.012, "opinion.national" to 0.006)),
                DossierChoice("Un salut discret : la victoire leur appartient.", mapOf("president.popularity" to 0.004)),
                DossierChoice("Offrir une prime aux clubs amateurs de toute la France.", mapOf("budget.oneOff" to 0.3, G_YOUNG to 0.006, "opinion.national" to 0.004)),
            ))
        }
        return list
    }

    fun decide(id: String, choice: DossierChoice) {
        choice.effects.forEach { (k, v) -> ctx.effects.trigger(EffectSpec(k, v), null, emptyMap(), "major:$id") }
        when {
            id.startsWith("aid|") -> state.aidRequests.remove(id.removePrefix("aid|"))
            id.startsWith("champions|") -> state.champions.remove(id.removePrefix("champions|"))
            else -> {
                state.dossiersDone += id
                state.readiness = (readiness() + choice.readiness).coerceIn(0.0, 1.0)
            }
        }
    }

    // ---- Chaque jour ----

    fun daily() {
        val f = file ?: return
        f.tournaments.forEach { tournament(it) }
        // Dossiers non tranchés à temps : on décide sans vous, et mal.
        f.dossiers.filter { it.id !in state.dossiersDone && date(it.from).daysUntil(ctx.now) >= it.days }.forEach { d ->
            state.dossiersDone += d.id
            state.readiness = (readiness() - LATE_PENALTY).coerceAtLeast(0.0)
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.INFO, "${d.label} : faute d'arbitrage", "Le dossier a été tranché sans vous ; la préparation des Jeux en souffre.", null)
        }
        state.aidRequests.entries.removeAll { it.value <= ctx.now }
        state.champions.entries.removeAll { it.value <= ctx.now }
        catastrophes(f)
    }

    private fun tournament(t: TournamentDef) {
        val start = date(t.start)
        if (ctx.now < start) return
        val s = state.tournaments.getOrPut(t.id) { TournamentState() }
        if (s.done) return
        if (!s.announced) {
            s.announced = true
            ctx.notifications.news(NotificationCategory.POLITICS, "${t.label} ${t.host} : c'est parti pour ${t.team}", null, world = false)
            if (t.hosted) JournalService(ctx).add("Société", "Ouverture des ${t.label}", Tone.GOOD)
        }
        if (t.olympics) {
            if (start.daysUntil(ctx.now) < t.durationDays) return
            s.done = true
            val boost = if (t.hosted) 0.8 + 0.5 * readiness() else 1.0
            s.medals = (t.medals * boost * (0.85 + ctx.rng.nextDouble() * 0.3)).toInt()
            val proud = s.medals >= t.medals
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "${t.label} : ${s.medals} médailles pour la France",
                if (proud) "Un bilan historique : le pays vibre." else "Un bilan en demi-teinte.", null, world = false)
            ctx.effects.trigger(EffectSpec("opinion.national", if (proud) OLYMPIC_JOY else -0.002), null, emptyMap(), "major:${t.id}")
            if (t.hosted) hostedOutcome(t)
            if (proud) state.champions[t.id] = ctx.now.plusDays(CHAMPIONS_WINDOW)
            return
        }
        val day = start.daysUntil(ctx.now)
        val next = t.stageDays.getOrNull(s.stage) ?: return
        if (day < next + RESULT_DELAY) return
        // Le tour se joue : qualification ou élimination.
        val stageName = t.stages.getOrElse(s.stage) { "Tour" }
        val win = ctx.rng.chance(t.winChance)
        if (!win) {
            s.eliminated = true; s.done = true
            ctx.notifications.news(NotificationCategory.POLITICS, "${t.label} : ${t.team} éliminé${if (t.team.startsWith("les")) "s" else ""} (${stageName.lowercase()})", null, world = false)
            ctx.effects.trigger(EffectSpec("opinion.national", -0.002 * (s.stage + 1)), null, emptyMap(), "major:${t.id}")
            return
        }
        s.stage++
        if (s.stage >= t.stages.size) {
            s.won = true; s.done = true
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "${t.label} : ${t.team.replaceFirstChar { it.uppercase() }} champion${if (t.team.startsWith("les")) "s" else ""} du monde !".replace("du monde !", if (t.label.startsWith("Euro")) "d'Europe !" else "du monde !"),
                "Des millions de Français dans les rues : une liesse nationale.", null, world = false)
            ctx.effects.trigger(EffectSpec("opinion.national", VICTORY_JOY), null, emptyMap(), "major:${t.id}")
            ctx.effects.trigger(EffectSpec("economy.consumerConfidence", 0.03), null, emptyMap(), "major:${t.id}")
            JournalService(ctx).add("Société", "Victoire : ${t.label}", Tone.GOOD)
            state.champions[t.id] = ctx.now.plusDays(CHAMPIONS_WINDOW)
        } else {
            ctx.notifications.news(NotificationCategory.POLITICS, "${t.label} : ${t.team} en ${t.stages[s.stage].lowercase()}", null, world = false)
            ctx.effects.trigger(EffectSpec("opinion.national", 0.001 * s.stage), null, emptyMap(), "major:${t.id}")
        }
    }

    private fun hostedOutcome(t: TournamentDef) {
        val r = readiness()
        when {
            r >= 0.65 -> {
                ctx.effects.trigger(EffectSpec("sector.tourism", 0.04), null, emptyMap(), "major:${t.id}")
                ctx.effects.trigger(EffectSpec("alliance.EU.NEGOTIATION_GOODWILL", 0.02), null, emptyMap(), "major:${t.id}")
                ctx.effects.trigger(EffectSpec("president.popularity", 0.02), null, emptyMap(), "major:${t.id}")
                ctx.notifications.news(NotificationCategory.POLITICS, "Des Jeux réussis : le monde salue l'organisation française", null, world = false)
            }
            r < 0.4 -> {
                ctx.effects.trigger(EffectSpec("president.popularity", -0.02), null, emptyMap(), "major:${t.id}")
                ctx.notifications.news(NotificationCategory.POLITICS, "Jeux chaotiques : transports saturés, sites inachevés, la presse étrangère se moque", null, world = false)
            }
            else -> ctx.notifications.news(NotificationCategory.POLITICS, "Des Jeux corrects, sans éclat", null, world = false)
        }
    }

    private fun catastrophes(f: MajorEventsFile) {
        // Vagues des catastrophes en cours.
        for (c in state.active.toList()) {
            val def = f.catastrophes.firstOrNull { it.id == c.id }
            val phase = def?.phases?.getOrNull(c.phase)
            if (def == null || phase == null) { state.active.remove(c); continue }
            if (c.startedAt.daysUntil(ctx.now) < phase.day) continue
            wave(def, phase, c)
            c.phase++
            if (c.phase >= def.phases.size) state.active.remove(c)
        }
        if (state.active.isNotEmpty()) return
        state.lastCatastrophe?.let { if (it.daysUntil(ctx.now) < f.cooldownDays) return }
        for (def in f.catastrophes) {
            if (!ctx.rng.chance(def.chancePerYear / DAYS_PER_YEAR)) continue
            val country = def.country ?: def.countries.filter { it in ctx.state.countries }.takeIf { it.isNotEmpty() }?.let { ctx.rng.pick(it) }
            val c = CatastropheState(def.id, ctx.now, country)
            state.active += c
            state.lastCatastrophe = ctx.now
            wave(def, def.phases.first(), c)
            c.phase = 1
            if (def.aid && country != null) state.aidRequests["${def.id}|$country"] = ctx.now.plusDays(AID_WINDOW)
            if (c.phase >= def.phases.size) state.active.remove(c)
            return
        }
    }

    private fun wave(def: CatastropheDef, phase: CatastrophePhase, c: CatastropheState) {
        val names = c.country?.let { ctx.db.countries[it] }?.let { CountryNames(it.definition) }
        val headline = phase.headline.replace("{in}", names?.inside ?: "").replace("{The}", names?.the?.replaceFirstChar { it.uppercase() } ?: "")
        if (phase.worldOutput != 0.0) ctx.state.countries.values.forEach { it.economy.pendingOutputShock += phase.worldOutput }
        c.country?.let { ctx.state.countries[it]?.economy?.let { e -> e.pendingOutputShock += phase.countryOutput } }
        phase.effects.forEach { (k, v) -> ctx.effects.trigger(EffectSpec(k, v), null, emptyMap(), "catastrophe:${def.id}") }
        phase.franceEvent?.let { id -> if (ctx.db.events.any { it.id == id }) ctx.scheduler.schedule(ScheduledAction.EventLaunch(ctx.now.plusHours(3), id, null)) }
        ctx.notifications.post(NotificationCategory.ECONOMY, if (c.phase == 0) Urgency.IMPORTANT else Urgency.INFO, headline,
            "${def.label} : l'économie mondiale encaisse le choc" + if (def.aid && c.phase == 0) ". La France peut envoyer de l'aide (Moments présidentiels)." else ".", null, world = true)
    }

    companion object {
        private const val G_YOUNG = "opinion.group.young"
        private const val LATE_PENALTY = 0.08
        private const val OLYMPIC_JOY = 0.008
        private const val VICTORY_JOY = 0.02
        private const val CHAMPIONS_WINDOW = 10.0
        private const val RESULT_DELAY = 1.0
        private const val DAYS_PER_YEAR = 365.0
        private const val AID_WINDOW = 15.0
    }
}

class MajorEventsSystem : SimulationSystem {
    override val name = "major-events"
    override val cadence = Cadence.DAILY
    override fun run(ctx: SimulationContext) = MajorEventsService(ctx).daily()
}
