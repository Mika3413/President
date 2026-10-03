package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.util.Formatting
import fr.president.game.ui.Formats
import fr.president.engine.government.MinistryEffectiveness
import fr.president.engine.government.Priority
import fr.president.engine.politics.Character
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Composition du gouvernement : Premier ministre, ministres, priorités, remplacements. */
class GovernmentPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Gouvernement"
    private var replacing: String? = null
    private var message: String? = null
    private val session get() = nav.session

    private enum class Tab(val label: String) { TEAM("Équipe"), ASSEMBLY("Assemblée"), REFORMS("Réformes") }
    private var tab = Tab.TEAM
    private var confirmDissolution = false
    private var confirmReferendum: String? = null

    override fun applyArgument(argument: String) {
        Tab.entries.firstOrNull { it.name == argument }?.let { tab = it }
    }

    override fun build(into: Table) {
        val tabs = Table().apply { defaults().padRight(4f) }
        Tab.entries.forEach { t ->
            tabs.add(ui.button(t.label, "toggle") { tab = t; message = null; nav.refresh() }.also { it.isChecked = tab == t })
        }
        into.add(tabs).left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        when (tab) {
            Tab.REFORMS -> { reforms(into); return }
            Tab.ASSEMBLY -> { assembly(into); return }
            Tab.TEAM -> Unit
        }
        into.add(com.badlogic.gdx.scenes.scene2d.ui.Table().also { t ->
            val ind = session.national.parliament()
            t.add(fr.president.game.ui.widgets.IndicatorView(ui, ind, expanded)).growX()
        }).row()
        val def = session.db.country(session.state.player.countryId).government!!
        val effectiveness = MinistryEffectiveness(session.context)
        for (ministry in def.ministries) {
            val minister = effectiveness.ministerOf(ministry.id)
            val box = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(Theme.panelAlt)) }
            box.add(ui.label(ministry.title, "muted", wrap = true)).growX().row()
            val who = Table()
            minister?.let { who.add(ui.portraits.image(it)).size(PORTRAIT).padRight(6f) }
            who.add(ui.label(minister?.fullName ?: "Vacant — intérim", "bold", if (minister == null) Theme.warning else null)).left().growX()
            box.add(who).left().growX().row()
            minister?.let { m ->
                val summary = session.characters.summary(m).filter { it.first in SHOWN }.joinToString(" · ") { "${it.first} : ${it.second}" }
                box.add(ui.label(summary, "small", wrap = true)).growX().row()
                val traits = session.characters.knownTraits(m)
                if (traits.isNotEmpty()) box.add(ui.label("Tempérament : " + traits.joinToString(", "), "muted", wrap = true)).growX().row()
            }
            if (!ministry.isPrimeMinister) box.add(priorityRow(ministry.id)).left().padTop(4f).row()
            box.add(ui.button(if (replacing == ministry.id) "Annuler" else if (minister == null) "Nommer…" else "Remplacer…", "flat") {
                replacing = if (replacing == ministry.id) null else ministry.id
                nav.refresh()
            }).left().row()
            if (replacing == ministry.id) candidates(box, ministry.id)
            into.add(box).growX().padBottom(GAP).row()
        }
    }

    private fun reforms(into: Table) {
        into.add(ui.label("Une réforme est votée par le Parlement après un mois ; les plus difficiles exigent une majorité plus large. " +
            "Vous pouvez aussi la soumettre directement aux Français par référendum : le vote portera autant sur vous que sur le texte.", "muted", wrap = true)).growX().padBottom(GAP).row()
        into.add(fr.president.game.ui.widgets.IndicatorView(ui, session.national.parliament(), expanded)).growX().padBottom(GAP).row()
        val parliament = session.state.parliament
        parliament.referendumReform?.let { id ->
            val title = session.policy.reforms().firstOrNull { it.id == id }?.title ?: id
            val (yes, _) = session.parliament.referendumEstimate(id)
            val box = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(Theme.panelAlt)) }
            box.add(ui.label("Référendum le ${Formats.date(parliament.referendumAt!!)}", "bold", Theme.highlight)).row()
            box.add(ui.label(title, "small", wrap = true)).growX().row()
            box.add(ui.label("Sondage : ${pollLabel(yes)}", "small", if (yes > 0.5) Theme.good else Theme.bad)).row()
            into.add(box).growX().padBottom(GAP).row()
        }
        session.policy.reforms().groupBy { it.category }.forEach { (category, list) ->
            into.add(ui.label(category, "bold")).padTop(4f).row()
            list.forEach { r ->
                val box = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(Theme.panelAlt)) }
                box.add(ui.label(r.title, "bold", wrap = true)).growX().row()
                box.add(ui.label(r.summary, "small", wrap = true)).growX().row()
                if (r.id in expanded) box.add(ui.label(r.description, "muted", wrap = true)).growX().row()
                val actions = Table().apply { defaults().padRight(4f) }
                actions.add(ui.button(if (r.id in expanded) "Moins" else "Détails", "flat") { if (r.id in expanded) expanded -= r.id else expanded += r.id; nav.refresh() })
                val adopted = session.state.policy.adoptedReforms[r.id]
                val blocker = session.policy.reformBlocker(r.id)
                when {
                    adopted != null -> actions.add(ui.label("Adoptée le ${Formats.date(adopted)}", "small", Theme.good))
                    blocker != null -> actions.add(ui.label(blocker, "small", Theme.textMuted))
                    else -> {
                        actions.add(ui.button("Déposer au Parlement", "accent") {
                            message = session.policy.proposeReform(r.id).fold({ "Réforme déposée : vote dans un mois." }, { it.message }); nav.refresh()
                        })
                        if (session.parliament.referendumBlocker(r.id) == null) {
                            actions.add(ui.button(if (confirmReferendum == r.id) "Annuler" else "Référendum…", "flat") {
                                confirmReferendum = if (confirmReferendum == r.id) null else r.id; nav.refresh()
                            })
                        }
                    }
                }
                box.add(actions).left().row()
                if (r.difficulty > 0.03 && adopted == null) box.add(ui.label("Adoption difficile : majorité élargie nécessaire", "small", Theme.warning)).left().row()
                if (confirmReferendum == r.id && adopted == null) {
                    val (yes, _) = session.parliament.referendumEstimate(r.id)
                    box.add(ui.label("Sondage avant campagne : ${pollLabel(yes)}. Un « non » serait un désaveu personnel et bloquerait le texte pendant trois ans.", "small", Theme.warning, wrap = true)).growX().row()
                    box.add(ui.button("Convoquer le référendum", "accent") {
                        message = session.parliament.callReferendum(r.id).fold({ "Référendum convoqué : vote dans un mois." }, { it.message })
                        confirmReferendum = null
                        nav.refresh()
                    }).left().row()
                }
                into.add(box).growX().padBottom(4f).row()
            }
        }
    }

    private fun assembly(into: Table) {
        val p = session.parliament
        val state = session.state.parliament
        into.add(fr.president.game.ui.widgets.IndicatorView(ui, session.national.parliament(), expanded)).growX().padBottom(GAP).row()
        if (!p.isActive) {
            into.add(ui.label("Pas de données parlementaires détaillées pour ce pays.", "muted", wrap = true)).growX().row()
            return
        }
        val status = p.majorityStatus()
        into.add(ui.label(status.label, "large", when (status) {
            fr.president.engine.government.MajorityStatus.ABSOLUTE -> Theme.good
            fr.president.engine.government.MajorityStatus.RELATIVE -> Theme.warning
            fr.president.engine.government.MajorityStatus.COHABITATION -> Theme.bad
        })).left().padTop(4f).row()
        val total = p.totalSeats().coerceAtLeast(1)
        val ordered = p.families.sortedBy { it.economicPosition }
        // Hémicycle simplifié : une barre, de la gauche à la droite.
        val bar = Table().left()
        ordered.forEach { f ->
            val seats = state.seats[f.id] ?: 0
            if (seats > 0) bar.add(Table().apply { setBackground(ui.skin.fill(Color.valueOf(f.color))) })
                .width(BAR_WIDTH * seats / total).height(BAR_HEIGHT)
        }
        into.add(bar).growX().padTop(4f).padBottom(4f).row()
        into.add(ui.label("Majorité absolue : ${total / 2 + 1} sièges sur $total", "muted")).left().row()
        val own = p.presidentFamily().id
        val pmFamily = p.primeMinisterFamily().id
        ordered.sortedByDescending { state.seats[it.id] ?: 0 }.forEach { f ->
            val row = Table()
            row.add(Table().apply { setBackground(ui.skin.fill(Color.valueOf(f.color))) }).size(12f).padRight(6f)
            val tag = when (f.id) {
                own -> if (own == pmFamily) " (président, PM)" else " (président)"
                pmFamily -> " (Premier ministre)"
                else -> ""
            }
            row.add(ui.label(f.name + tag, if (f.id == own) "bold" else "small")).left().expandX()
            row.add(ui.label("${state.seats[f.id] ?: 0}", "small")).right().padRight(8f)
            val support = p.familySupport(f)
            row.add(ui.label(stanceLabel(support), "muted", stanceColor(support))).right().width(STANCE_WIDTH)
            into.add(row).growX().row()
        }
        if (status == fr.president.engine.government.MajorityStatus.COHABITATION || session.state.government.primeMinisterId == null) {
            val largest = p.families.first { it.id == state.seats.maxBy { e -> e.value }.key }
            into.add(ui.label("Pour gouverner, nommez à Matignon une personnalité proche de : ${largest.name}.", "small", Theme.warning, wrap = true)).growX().padTop(GAP).row()
        }

        into.add(ui.label("Calendrier", "bold")).left().padTop(GAP).row()
        state.nextLegislative?.let { into.add(ui.label((if (state.dissolutionPending) "Législatives anticipées : " else "Prochaines législatives : ") + Formats.date(it), "small")).left().row() }
        if (state.censurePending) into.add(ui.label("Une motion de censure sera votée dans les prochains jours.", "small", Theme.bad, wrap = true)).growX().row()
        if (state.governmentsFallen > 0 || state.censuresSurvived > 0) {
            into.add(ui.label("Motions de censure : ${state.censuresSurvived} rejetée(s), ${state.governmentsFallen} adoptée(s)", "muted")).left().row()
        }
        state.legislativeResults.lastOrNull()?.let { r ->
            into.add(ui.label("Dernier scrutin (${Formats.date(r.time)}) — participation ${Formatting.percent(r.turnout)}", "muted", wrap = true)).growX().row()
        }

        into.add(ui.label("Dissolution", "bold")).left().padTop(GAP).row()
        into.add(ui.label("Dissoudre l'Assemblée provoque des législatives dans trois semaines. Les électeurs jugent votre bilan : " +
            "le pari peut renforcer votre majorité… ou vous imposer une cohabitation.", "muted", wrap = true)).growX().row()
        val blocker = p.dissolutionBlocker()
        when {
            blocker != null -> into.add(ui.label(blocker, "small", Theme.textMuted, wrap = true)).growX().row()
            confirmDissolution -> {
                val row = Table().apply { defaults().padRight(4f) }
                row.add(ui.button("Confirmer la dissolution", "accent") {
                    message = p.dissolve().fold({ "L'Assemblée est dissoute." }, { it.message })
                    confirmDissolution = false
                    nav.refresh()
                })
                row.add(ui.button("Annuler", "flat") { confirmDissolution = false; nav.refresh() })
                into.add(row).left().row()
            }
            else -> into.add(ui.button("Dissoudre l'Assemblée…", "flat") { confirmDissolution = true; nav.refresh() }).left().row()
        }
    }

    private fun stanceLabel(support: Double): String = when {
        support >= STRONG_SUPPORT -> "Soutien"
        support >= WEAK_SUPPORT -> "Au cas par cas"
        else -> "Opposition"
    }

    private fun stanceColor(support: Double): Color = when {
        support >= STRONG_SUPPORT -> Theme.good
        support >= WEAK_SUPPORT -> Theme.warning
        else -> Theme.bad
    }

    private fun pollLabel(yes: Double): String = when {
        yes >= CLEAR_WIN -> "le oui l'emporterait nettement"
        yes > 0.5 -> "le oui l'emporterait de peu"
        yes > CLEAR_LOSS -> "le non l'emporterait de peu"
        else -> "le non l'emporterait nettement"
    } + " (${Formatting.percent(yes)} de oui)"

    private fun priorityRow(ministryId: String): Table {
        val row = Table().apply { defaults().padRight(4f) }
        row.add(ui.label("Priorité", "muted")).padRight(6f)
        val current = session.state.government.priorities[ministryId] ?: Priority.NORMAL
        Priority.entries.forEach { p ->
            val b = ui.button(p.label, "toggle") {
                message = session.government.setPriority(ministryId, p).fold({ null }, { it.message })
                nav.refresh()
            }
            b.isChecked = p == current
            row.add(b)
        }
        return row
    }

    private fun candidates(box: Table, ministryId: String) {
        session.government.candidates(ministryId).forEach { c: Character ->
            val t = Table().apply { defaults().left(); pad(4f); setBackground(ui.skin.fill(Theme.panel)) }
            val head = Table()
            head.add(ui.portraits.image(c)).size(PORTRAIT).padRight(6f)
            head.add(ui.label(c.fullName, "bold")).left()
            t.add(head).left().row()
            t.add(ui.label(session.characters.summary(c).filter { it.first in CANDIDATE_SHOWN }.joinToString(" · ") { "${it.first} : ${it.second}" }, "small", wrap = true)).growX().row()
            if (session.parliament.isActive) {
                val family = session.parliament.familyOf(c)
                t.add(ui.label("Proche de : ${family.name}", "muted")).left().row()
            }
            t.add(ui.button("Nommer", "accent") {
                message = session.government.appoint(ministryId, c.id).fold({ "${c.fullName} est nommé(e)." }, { it.message })
                replacing = null
                nav.refresh()
            }).left()
            box.add(t).growX().padTop(4f).row()
        }
    }

    private companion object {
        val SHOWN = setOf("Compétence", "Loyauté", "Popularité")
        const val PORTRAIT = 40f
        val CANDIDATE_SHOWN = setOf("Âge", "Compétence", "Gestion", "Expérience", "Popularité", "Sensibilité")
        const val BAR_WIDTH = 300f
        const val BAR_HEIGHT = 18f
        const val STANCE_WIDTH = 90f
        const val STRONG_SUPPORT = 0.6
        const val WEAK_SUPPORT = 0.2
        const val CLEAR_WIN = 0.55
        const val CLEAR_LOSS = 0.45
    }
}
