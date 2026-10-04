package fr.president.engine.crisis

import fr.president.engine.effects.EffectSpec
import fr.president.engine.readout.Tone
import fr.president.engine.session.ActionPresenter
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalService
import fr.president.engine.territory.LocalActionDef

/** Lancer, suivre et lever les mesures de crise et de prévention. */
class MeasureCommands(private val ctx: SimulationContext) {
    private val presenter = ActionPresenter(ctx)
    private val state get() = ctx.state.measures

    data class View(
        val def: MeasureDef,
        val blocker: String?,
        val locked: Boolean,
        val active: ActiveMeasure?,
        /** Effets au lancement et effets cumulés sur un mois. */
        val startEffects: List<ActionPresenter.EffectLine>,
        val monthlyEffects: List<ActionPresenter.EffectLine>,
        val costText: String,
        val durationText: String,
        /** Ce que la mesure change sur les risques (« Incendies : probabilité −60 % »). */
        val impacts: List<String>,
    )

    val families get() = ctx.playerData.measures?.families.orEmpty()
    val definitions get() = ctx.playerData.measures?.measures.orEmpty()

    fun active(): List<ActiveMeasure> = state.active.toList()

    fun views(family: String? = null, department: String? = null): List<View> =
        definitions.filter { family == null || it.family == family }.map { view(it, department) }

    fun view(def: MeasureDef, department: String? = null): View {
        val active = state.active.firstOrNull { it.id == def.id && (!def.local || department == null || it.department == department) }
        val start = presenter.summarize(asAction(def, def.start))
        val monthly = presenter.summarize(asAction(def, def.daily.map { it.copy(amount = it.amount * MeasureSystem.DAYS_PER_MONTH) }))
            .map { it.copy(text = it.text + " /mois") }
        val cost = buildList {
            if (def.costBillions > 0) add(fr.president.engine.util.Formatting.billions(def.costBillions))
            if (def.costPerMonthBillions > 0) add(fr.president.engine.util.Formatting.billions(def.costPerMonthBillions) + "/mois")
        }.ifEmpty { listOf("Gratuit") }.joinToString(" + ")
        val duration = if (def.defaultDays <= 0) "jusqu'à levée" else presenter.days(def.defaultDays)
        return View(def, blocker(def, department), !presenter.unlocked(asAction(def, emptyList()).copy(requires = def.requires)), active,
            start, monthly, cost, duration, impacts(def))
    }

    private fun impacts(def: MeasureDef): List<String> = def.affects.mapNotNull { i ->
        val name = ctx.db.events.firstOrNull { it.id == i.event }?.let { eventName(it.id) } ?: return@mapNotNull null
        val parts = buildList {
            if (i.probability != 1.0) add("risque ${pct(i.probability)}")
            if (i.intensity != 1.0) add("ampleur ${pct(i.intensity)}")
        }
        if (parts.isEmpty()) null else "$name : ${parts.joinToString(", ")}"
    }.distinctBy { it.substringBefore(" :") }

    private fun pct(f: Double) = (if (f < 1) "−" else "+") + Math.round(kotlin.math.abs(1 - f) * PERCENT) + " %"

    /** Nom court d'un événement (pour les risques), tiré de son titre sans les lieux. */
    fun eventName(id: String): String = ctx.db.events.firstOrNull { it.id == id }?.headline
        ?.substringBefore(" :")?.replace(Regex("""\s*\{[^\}]+\}"""), "")?.trim() ?: id

    fun blocker(def: MeasureDef, department: String? = null): String? {
        if (ctx.state.player.gameOver != null) return "La partie est terminée."
        if (def.local && department == null) return "Choisissez un département sur la carte."
        if (!presenter.unlocked(asAction(def, emptyList()).copy(requires = def.requires))) return "Seulement ${def.requiresText.ifBlank { "dans certaines situations" }}."
        if (state.active.any { it.id == def.id && (it.department == department || !def.local) }) return "Déjà en vigueur."
        def.exclusive.firstOrNull { ex -> state.active.any { it.id == ex } }?.let { ex ->
            return "Incompatible avec « ${definitions.firstOrNull { it.id == ex }?.label ?: ex} » en vigueur."
        }
        state.lastEnded[key(def, department)]?.let { last ->
            val left = def.cooldownDays - last.daysUntil(ctx.now)
            if (left > 0) return "Possible à nouveau dans ${presenter.days(kotlin.math.ceil(left).toInt())}."
        }
        return null
    }

    fun activate(id: String, department: String? = null, days: Int? = null): Result<String> = runCatching {
        val def = definitions.first { it.id == id }
        blocker(def, department)?.let { error(it) }
        val length = days ?: def.defaultDays
        val m = ActiveMeasure(id, ctx.now, if (length > 0) ctx.now.plusDays(length.toLong()) else null, if (def.local) department else null)
        state.active += m
        def.start.forEach { ctx.effects.trigger(MeasureSystem.resolve(it, m.department), null, emptyMap(), "measure:$id") }
        if (def.costBillions > 0) ctx.effects.trigger(EffectSpec("budget.oneOff", def.costBillions, days = 1.0), null, emptyMap(), "measure:$id")
        val where = m.department?.let { place(it) }?.let { " ($it)" } ?: ""
        JournalService(ctx).add("Mesure", "${def.label}$where", Tone.NEUTRAL)
        ctx.notifications.news(fr.president.engine.notifications.NotificationCategory.SECURITY, "Le président décrète : ${def.label.lowercase()}$where", m.department)
        "${def.label}$where : en vigueur" + (m.endsAt?.let { " pour ${presenter.days(length)}." } ?: " jusqu'à nouvel ordre.")
    }

    fun lift(id: String, department: String? = null): Result<String> = runCatching {
        val m = state.active.firstOrNull { it.id == id && (department == null || it.department == department) } ?: error("Cette mesure n'est pas en vigueur.")
        end(m, expired = false)
        "${definitions.first { it.id == id }.label} : levée."
    }

    fun end(m: ActiveMeasure, expired: Boolean) {
        val def = definitions.firstOrNull { it.id == m.id } ?: return
        state.active.remove(m)
        state.lastEnded[key(def, m.department)] = ctx.now
        def.end.forEach { ctx.effects.trigger(MeasureSystem.resolve(it, m.department), null, emptyMap(), "measure:${def.id}") }
        val where = m.department?.let { place(it) }?.let { " ($it)" } ?: ""
        JournalService(ctx).add("Mesure", "${def.label}$where : ${if (expired) "fin" else "levée"}", Tone.NEUTRAL)
        if (expired) MeasureSystem.notify(ctx, "${def.label} : fin de la mesure$where", "La mesure arrive à son terme. Vous pouvez la relancer si besoin.", m.department)
    }

    private fun key(def: MeasureDef, department: String?) = if (def.local && department != null) "${def.id}|$department" else def.id

    private fun place(code: String) = ctx.playerData.territory?.departments?.firstOrNull { it.code == code }?.name

    private fun asAction(def: MeasureDef, effects: List<EffectSpec>) =
        LocalActionDef(def.id, def.label, def.icon, def.family, def.description, immediate = effects)

    private companion object {
        const val PERCENT = 100
    }
}
