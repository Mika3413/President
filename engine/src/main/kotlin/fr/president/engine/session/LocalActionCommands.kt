package fr.president.engine.session

import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.LocalActionDef
import fr.president.engine.territory.ProjectState
import fr.president.engine.territory.ProjectStatus
import fr.president.engine.util.Formatting
import kotlin.math.abs
import kotlin.math.ceil

/** Actions directes du président sur un département (chantiers, visites, plans locaux). */
class LocalActionCommands(private val ctx: SimulationContext) {

    /** Effet résumé pour l'interface : « Santé +15 », bon ou mauvais. */
    data class EffectLine(val text: String, val good: Boolean)

    data class ActionView(
        val def: LocalActionDef,
        /** Raison pour laquelle l'action est indisponible, ou null. */
        val blocker: String?,
        val effects: List<EffectLine>,
        val costText: String,
        val durationText: String,
    )

    val definitions: List<LocalActionDef> get() = ctx.playerData.localActions?.actions.orEmpty()

    fun actionsFor(departmentCode: String): List<ActionView> = definitions.map { def ->
        ActionView(def, blocker(departmentCode, def), summarize(def), costText(def), durationText(def))
    }

    fun perform(departmentCode: String, actionId: String): Result<String> = runCatching {
        val def = definitions.first { it.id == actionId }
        blocker(departmentCode, def)?.let { error(it) }
        val dept = ctx.state.territory.departments.getValue(departmentCode)
        val place = ctx.playerData.territory!!.departments.first { it.code == dept.code }.name
        def.immediate.forEach { ctx.effects.trigger(resolve(it, departmentCode), null, emptyMap(), "local:${def.id}") }
        if (def.costBillions > 0) {
            ctx.effects.trigger(EffectSpec("budget.oneOff", def.costBillions, days = def.durationDays.coerceAtLeast(1).toDouble()), null, emptyMap(), "local:${def.id}")
        }
        ctx.state.localActions[key(departmentCode, def.id)] = ctx.now
        if (def.globalCooldownDays > 0) ctx.state.localActions[key(ANY, def.id)] = ctx.now
        if (def.durationDays > 0) {
            val project = ProjectState(
                id = ctx.state.newId("prj"),
                name = "${def.label} — $place",
                kind = kind(def.id),
                locationId = departmentCode,
                startedAt = ctx.now,
                completesAt = ctx.now.plusDays(def.durationDays.toLong()),
                costBillions = def.costBillions,
                onCompletion = def.onCompletion.map { resolve(it, departmentCode) },
            )
            ctx.state.projects += project
            ctx.scheduler.schedule(ScheduledAction.ProjectCompletion(project.completesAt, project.id))
            ctx.notifications.post(NotificationCategory.PROJECTS, Urgency.INFO, project.name,
                "Chantier lancé : fin prévue dans ${durationText(def)}.", departmentCode)
            "Chantier lancé : fin prévue dans ${durationText(def)}."
        } else {
            def.onCompletion.forEach { ctx.effects.trigger(resolve(it, departmentCode), null, emptyMap(), "local:${def.id}") }
            "${def.label} : c'est fait."
        }
    }

    private fun blocker(code: String, def: LocalActionDef): String? {
        if (ctx.state.player.gameOver != null) return "La partie est terminée."
        val running = ctx.state.projects.any { it.locationId == code && it.kind == kind(def.id) && it.status == ProjectStatus.IN_PROGRESS }
        if (running) return "Chantier déjà en cours ici."
        wait(key(code, def.id), def.cooldownDays)?.let { return "Possible à nouveau ici dans $it." }
        wait(key(ANY, def.id), def.globalCooldownDays)?.let { return "Possible à nouveau dans $it." }
        return null
    }

    private fun wait(key: String, cooldown: Int): String? {
        if (cooldown <= 0) return null
        val last = ctx.state.localActions[key] ?: return null
        val left = cooldown - last.daysUntil(ctx.now)
        return if (left > 0) days(ceil(left).toInt()) else null
    }

    private fun resolve(spec: EffectSpec, code: String): EffectSpec =
        if (spec.target.startsWith(LOCAL)) spec.copy(target = "dept.$code." + spec.target.removePrefix(LOCAL)) else spec

    private fun summarize(def: LocalActionDef): List<EffectLine> =
        (def.onCompletion + def.immediate).groupBy { it.target }.mapNotNull { (target, specs) ->
            val amount = specs.sumOf { it.amount * it.factor }
            val meta = EFFECT_LABELS[target] ?: return@mapNotNull null
            val shown = amount * meta.scale
            val text = "${meta.label} ${if (shown >= 0) "+" else "−"}${short(abs(shown))}${meta.unit}"
            EffectLine(text, (amount > 0) == meta.higherIsBetter)
        }

    /** Une décimale au plus : « 4 », « 1,5 », « 0,2 ». */
    private fun short(v: Double): String {
        val r = Math.round(v * TENTHS) / TENTHS
        return if (r == Math.floor(r)) r.toLong().toString() else String.format(java.util.Locale.FRENCH, "%.1f", r)
    }

    private fun costText(def: LocalActionDef) = if (def.costBillions <= 0) "Gratuit" else Formatting.billions(def.costBillions)

    private fun durationText(def: LocalActionDef): String = if (def.durationDays <= 0) "immédiat" else days(def.durationDays)

    private fun days(n: Int): String = when {
        n >= DAYS_PER_YEAR && n % DAYS_PER_YEAR == 0 -> "${n / DAYS_PER_YEAR} an${if (n >= 2 * DAYS_PER_YEAR) "s" else ""}"
        n >= DAYS_PER_MONTH * 2 -> "${Math.round(n / DAYS_PER_MONTH.toDouble())} mois"
        else -> "$n jour${if (n > 1) "s" else ""}"
    }

    private data class EffectMeta(val label: String, val scale: Double, val unit: String, val higherIsBetter: Boolean)

    private companion object {
        const val LOCAL = "local."
        const val TENTHS = 10.0
        const val ANY = "*"
        const val DAYS_PER_YEAR = 365
        const val DAYS_PER_MONTH = 30
        fun key(code: String, id: String) = "$code|$id"
        fun kind(id: String) = "local:$id"
        val EFFECT_LABELS = mapOf(
            "local.approval" to EffectMeta("Popularité locale", 100.0, " pts", true),
            "local.healthAccess" to EffectMeta("Santé", 100.0, "", true),
            "local.crime" to EffectMeta("Délinquance", 100.0, "", false),
            "local.unemployment" to EffectMeta("Chômage local", 100.0, " pt", false),
            "local.pollution" to EffectMeta("Pollution", 100.0, "", false),
            "local.industry" to EffectMeta("Emplois industriels", 100.0, " %", true),
            "opinion.national" to EffectMeta("Popularité nationale", 100.0, " pt", true),
        )
    }
}
