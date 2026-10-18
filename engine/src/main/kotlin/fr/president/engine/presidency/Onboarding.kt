package fr.president.engine.presidency

import fr.president.engine.data.PlaceNames
import fr.president.engine.events.EventLauncher
import fr.president.engine.events.EventScope
import fr.president.engine.events.ScopeRef
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/**
 * Les premières minutes : au lieu d'un cours, une vraie crise dès le premier matin (une crue
 * dans un département), décidée par le joueur, dont il voit aussitôt les suites. Puis les écrans
 * s'ouvrent au fil du mandat, quand ils deviennent utiles, au lieu d'être tous là dès le jour 1.
 */
class OnboardingService(private val ctx: SimulationContext) {
    private val player get() = ctx.state.player

    data class Morning(val messageId: String, val department: String, val place: String, val title: String, val text: String)
    data class Line(val text: String, val tone: Tone)
    data class Outcome(val choice: String, val lines: List<Line>, val headline: String, val tone: Tone, val department: String)

    /** Le premier matin reste à jouer (nouvelle partie, tutoriel en cours). */
    fun firstMorningDue(): Boolean = player.firstCrisis == null && player.tourStep == 0 && player.academyModule == null

    /** Déclenche la crue du premier matin et renvoie de quoi la présenter. */
    fun startFirstMorning(): Morning? {
        if (!firstMorningDue()) return morning()
        val def = ctx.db.events.firstOrNull { it.id == FIRST_EVENT } ?: return null
        val dept = department() ?: return null
        val instance = EventLauncher(ctx).launch(def, ScopeRef(EventScope.DEPARTMENT, dept))
        val message = ctx.state.inbox.messages.lastOrNull { it.originId == instance.id } ?: return null
        player.firstCrisis = message.id
        player.firstCrisisDept = dept
        player.firstCrisisAmount = instance.params["amount"] ?: 0.0
        return morning()
    }

    fun morning(): Morning? {
        val id = player.firstCrisis ?: return null
        val dept = player.firstCrisisDept ?: return null
        val place = placeIn(dept)
        val people = ctx.state.territory.departments[dept]?.population?.let { (it * EVACUATED).toLong().coerceAtLeast(MIN_EVACUATED) } ?: MIN_EVACUATED
        val text = "6 h 12. Le téléphone sonne : c'est le préfet. Une crue éclair a frappé dans la nuit $place. " +
            "${Formatting.integer(people)} habitants ont été évacués, des routes sont coupées, les caméras sont déjà sur place. " +
            "Tout le pays attend de voir comment le nouveau président réagit. Votre première décision."
        return Morning(id, dept, place, "Votre premier matin", text)
    }

    fun message(): InboxMessage? = player.firstCrisis?.let { id -> ctx.state.inbox.messages.firstOrNull { it.id == id } }

    /** Bilan de la première décision, une fois prise et pas encore montré. */
    fun outcome(): Outcome? {
        if (player.firstCrisisShown) return null
        val m = message() ?: return null
        val chosen = m.chosenOptionId ?: return null
        val dept = player.firstCrisisDept ?: return null
        val option = ctx.db.events.firstOrNull { it.id == FIRST_EVENT }?.message?.options?.firstOrNull { it.id == chosen }
        val label = m.options.firstOrNull { it.id == chosen }?.label ?: chosen
        val params = mapOf("amount" to player.firstCrisisAmount)
        val lines = option?.effects.orEmpty().mapNotNull { e ->
            val value = e.param?.let { (params[it] ?: 0.0) * e.factor } ?: e.amount
            val over = if (e.days > 0) " (sur ${e.days.toInt()} jours)" else ""
            when {
                e.target == "scope.approval" -> Line("${points(value)} de popularité ${placeIn(dept)}$over", if (value >= 0) Tone.GOOD else Tone.BAD)
                e.target == "budget.oneOff" -> Line("Coût pour le budget : ${Formatting.billions(value)}", Tone.WARNING)
                e.target == "sender.relation" -> Line(if (value >= 0) "Le préfet et les élus locaux vous en savent gré" else "Le préfet est déçu", if (value >= 0) Tone.GOOD else Tone.BAD)
                e.target.startsWith("opinion.group.") -> Line("${points(value)} chez ${groupName(e.target.removePrefix("opinion.group."))}", if (value >= 0) Tone.GOOD else Tone.BAD)
                e.target == "opinion.national" -> Line("${points(value)} de popularité nationale", if (value >= 0) Tone.GOOD else Tone.BAD)
                else -> null
            }
        }
        val generous = lines.any { it.tone == Tone.GOOD && it.text.contains("popularité") }
        val tone = if (generous) Tone.GOOD else Tone.WARNING
        val headline = if (generous) "« Le président au chevet des sinistrés »" else "« Crue : les sinistrés attendaient davantage »"
        return Outcome(label, lines, headline, tone, dept)
    }

    fun outcomeSeen() { player.firstCrisisShown = true }

    // --- Écrans ouverts au fil du mandat ---------------------------------------------------------

    data class Unlock(val screen: String, val label: String, val why: String)

    /** Écran ouvert ? Les anciennes parties et l'option « Tout afficher » voient tout. */
    fun unlocked(screen: String): Boolean = !player.progressiveScreens || screen !in RULES || screen in player.unlockedScreens

    fun locked(): List<String> = RULES.keys.filter { !unlocked(it) }

    /** Ouvre les écrans devenus utiles ; à appeler chaque jour. Renvoie ceux qui viennent de s'ouvrir. */
    fun updateUnlocks(): List<Unlock> {
        if (!player.progressiveScreens) return emptyList()
        val days = player.termStart.daysUntil(ctx.now)
        val opened = RULES.filter { (screen, rule) -> screen !in player.unlockedScreens && (days >= rule.days || rule.now(ctx)) }
        opened.forEach { (screen, rule) ->
            player.unlockedScreens += screen
            player.newScreens += screen
            ctx.notifications.post(NotificationCategory.GOVERNMENT, Urgency.INFO, "Nouvel écran : ${rule.label}", rule.why, null, journal = false)
        }
        return opened.map { (screen, rule) -> Unlock(screen, rule.label, rule.why) }
    }

    fun showAll() {
        player.progressiveScreens = false
    }

    private fun placeIn(dept: String): String {
        val def = ctx.playerData.territory?.departments?.firstOrNull { it.code == dept } ?: return dept
        return PlaceNames.department(def.name, def.article).getValue("departmentIn")
    }

    private fun points(v: Double): String = (if (v >= 0) "+" else "−") + Formatting.amount(Math.abs(v) * PERCENT) + " pt"

    private fun groupName(id: String): String = ctx.playerData.socialGroups?.groups?.firstOrNull { it.id == id }?.label?.lowercase() ?: id

    /** Département de la crue : un département connu pour ses crues s'il existe, sinon un grand département de province. */
    private fun department(): String? {
        val depts = ctx.state.territory.departments
        FLOOD_PRONE.firstOrNull { it in depts }?.let { return it }
        val capital = ctx.playerData.territory?.cities?.firstOrNull { it.id == ctx.playerData.definition.capitalCityId }?.department
        return depts.values.filter { it.code != capital }.sortedByDescending { it.population }.getOrNull(PROVINCE_RANK)?.code ?: depts.keys.firstOrNull()
    }

    private class Rule(val label: String, val days: Double, val why: String, val now: (SimulationContext) -> Boolean = { false })

    companion object {
        const val FIRST_EVENT = "flood"
        private val FLOOD_PRONE = listOf("30")
        private const val PROVINCE_RANK = 4
        private const val EVACUATED = 0.0015
        private const val MIN_EVACUATED = 400L
        private const val PERCENT = 100.0

        /** Écrans ouverts plus tard (clé : nom du panneau), et ce qui peut les ouvrir plus tôt. */
        private val RULES: Map<String, Rule> = linkedMapOf(
            "PRESS" to Rule("Presse", 4.0, "Les unes du jour, les sondages et le climat médiatique : voyez ce qu'on dit de vous."),
            "WORLD" to Rule("Journal du monde", 6.0, "Ce qui se passe dans les autres pays et entre eux."),
            "UNREST" to Rule("La rue et l'armée", 10.0, "Manifestations en direct, grèves, émeutes, loyauté de l'armée.") { it.state.unrest.movements.isNotEmpty() },
            "ACTORS" to Rule("Société civile", 14.0, "Syndicats, patronat, cultes, lobbies : leurs attentes et leur colère."),
            "EU" to Rule("Union européenne", 14.0, "Les textes au Conseil européen : votez, rassemblez vos alliés.") { it.state.eu.current != null },
            "TRADE" to Rule("Commerce et matières premières", 21.0, "Pétrole, gaz, blé, grands contrats à l'export.") { it.state.trade.tenders.isNotEmpty() },
            "DEFENSE" to Rule("Défense", 21.0, "Armement, ventes d'armes, bases à l'étranger, dissuasion.") { fr.president.engine.military.Geopolitics(it).isAtWar(it.state.player.countryId) },
            "LAWS" to Rule("Lois et Constitution", 30.0, "Société, justice, travail, libertés, mandat, référendum."),
            "UN" to Rule("ONU", 30.0, "Le Conseil de sécurité : résolutions, votes, veto.") { it.state.un.drafts.isNotEmpty() },
            "INTEL" to Rule("Renseignement", 45.0, "Espionnage, contre-espionnage, opérations clandestines.") { it.state.intel.operations.isNotEmpty() },
            "ELECTIONS" to Rule("Élections", 90.0, "Sondages, candidats, promesses : préparez la prochaine échéance."),
            "BOOK" to Rule("Le livre de votre présidence", 300.0, "Votre première année racontée comme un chapitre d'histoire."),
            "REPLAY" to Rule("Relecture du mandat", 56.0, "Votre mandat en accéléré, semaine après semaine."),
        )
    }
}

/** Ouvre chaque jour les écrans devenus utiles (nouvelles parties). */
class OnboardingSystem : fr.president.engine.simulation.SimulationSystem {
    override val name = "onboarding"
    override val cadence = fr.president.engine.simulation.Cadence.DAILY
    override fun run(ctx: SimulationContext) { OnboardingService(ctx).updateUnlocks() }
}
