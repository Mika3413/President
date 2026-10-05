package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.diplomacy.Clause
import fr.president.engine.diplomacy.ProposalDescriber
import fr.president.engine.diplomacy.ProposalStatus
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Diplomatie structurée : relations expliquées, accords en vigueur et rédaction
 * de propositions clause par clause. Les réponses arrivent après un délai, par message.
 */
class DiplomacyPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Diplomatie"
    private val session get() = nav.session
    private val player get() = session.state.player.countryId
    var country: String? = null
    private var counterOf: String? = null
    private val draft = mutableListOf<MutableClause>()
    private var years = DEFAULT_YEARS
    private var message: String? = null

    private class MutableClause(val type: String, var giver: String, val params: MutableMap<String, Double>)

    /** Prépare une proposition contenant une clause donnée (ex. cessez-le-feu depuis le panneau Armée). */
    fun prefill(target: String, clauseType: String, params: Map<String, Double>) {
        country = target
        counterOf = null
        draft.clear()
        draft += MutableClause(clauseType, player, params.toMutableMap())
        years = 1
        message = "Vérifiez les termes puis envoyez la proposition."
    }

    /** Prépare une contre-proposition à partir d'une proposition reçue. */
    fun negotiate(proposalId: String) {
        val p = session.diplomacy.proposal(proposalId) ?: return
        country = if (p.from == player) p.to else p.from
        counterOf = p.id
        draft.clear()
        p.clauses.forEach { draft += MutableClause(it.type, it.giver, it.params.toMutableMap()) }
        years = p.durationYears
        message = "Modifiez les termes puis envoyez votre contre-proposition."
    }

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        val id = country
        if (id == null) {
            val euText = session.eu.current()?.second?.title
            into.add(ui.colorButton("★ Union européenne" + (euText?.let { " — au Conseil : $it" } ?: ""), Theme.catDiplomacy) { nav.open(PanelId.EU) }
                .also { it.label.setWrap(true) }).growX().padBottom(GAP).row()
            val latest = session.state.world.entries.lastOrNull()
            into.add(ui.colorButton("◎ Journal du monde" + (latest?.let { " — ${it.headline}" } ?: ""), Theme.catDiplomacy) { nav.open(PanelId.WORLD) }
                .also { it.label.setWrap(true) }).growX().padBottom(GAP).row()
            into.add(ui.label("Choisissez un pays pour consulter vos relations et négocier (ou touchez-le sur la carte).", "muted", wrap = true)).row()
            val list = Table().apply { defaults().growX().uniformX().pad(2f) }
            session.diplomacy.foreignCountries().forEachIndexed { i, c ->
                list.add(ui.button(session.db.country(c).definition.name, "default") {
                    country = c; draft.clear(); counterOf = null; message = null
                    nav.refresh()
                })
                if (i % COUNTRIES_PER_ROW == COUNTRIES_PER_ROW - 1) list.row()
            }
            into.add(list).growX().row()
            return
        }
        into.add(ui.button("◀ Tous les pays", "flat") { country = null; draft.clear(); counterOf = null; message = null; nav.refresh() }).left().row()
        // Une proposition en préparation passe en premier : c'est ce que le joueur vient de demander.
        if (draft.isNotEmpty()) editor(into, id)
        countrySummary(into, id)
        pendingProposals(into, id)
        agreements(into, id)
        crisisActions(into, id)
        if (draft.isEmpty()) editor(into, id)
    }

    private fun countrySummary(into: Table, id: String) {
        val def = session.db.country(id).definition
        val leader = session.state.characters.getValue(session.state.countries.getValue(id).leaderId)
        val relation = session.diplomacy.relation(id)
        into.add(ui.label(def.name, "title")).padTop(GAP).row()
        val head = Table()
        head.add(ui.portraits.image(leader)).size(PORTRAIT).padRight(8f)
        head.add(ui.label("${def.institutions.headOfGovernment(leader.female)} : ${leader.fullName}", "small", wrap = true)).growX().left()
        into.add(head).growX().left().row()
        val traits = session.characters.knownTraits(leader)
        into.add(ui.label(if (traits.isEmpty()) "Tempérament encore mal connu de nos services." else "Réputé " + traits.joinToString(", ") + ".", "muted", wrap = true)).row()
        into.add(ui.label("Relations : ${relation.label} · Confiance : ${relation.trustLabel}", "bold")).padTop(4f).row()
        relation.factors.forEach { f -> into.add(ui.label("• ${f.label}", "small", if (f.weight >= 0) Theme.good else Theme.bad, wrap = true)).row() }
        talks.build(into, leader.id, leader.fullName)
    }

    private val talks = fr.president.game.ui.widgets.ConversationControls(ui, nav)

    private var confirmWar = false

    /** Sanctions, condamnation, ultimatum, guerre : les leviers de crise. */
    private fun crisisActions(into: Table, id: String) {
        val geo = session.military.geo
        into.add(ui.label("Actions", "bold")).padTop(GAP).row()
        if (geo.atWar(player, id)) into.add(ui.label("Nous sommes en guerre avec ce pays.", "small", Theme.bad)).row()
        val row = Table().apply { defaults().padRight(4f).padBottom(4f) }
        val sanctioning = session.diplomacy.isSanctioning(id)
        row.add(ui.button(if (sanctioning) "Lever les sanctions" else "Sanctionner", "default") {
            if (sanctioning) session.diplomacy.liftSanctions(id) else session.diplomacy.sanction(id)
            message = if (sanctioning) "Sanctions levées." else "Sanctions imposées."
            nav.refresh()
        })
        row.add(ui.button("Condamner publiquement", "default") { session.diplomacy.condemn(id); message = "Condamnation publique prononcée."; nav.refresh() })
        into.add(row).left().row()
        if (!geo.atWar(player, id)) {
            into.add(ui.label("Ultimatum — exiger " + fr.president.engine.data.CountryNames(session.db.country(id).definition).of + " :", "muted")).row()
            val demands = Table().apply { defaults().padRight(4f).padBottom(4f) }
            fr.president.engine.diplomacy.Demand.entries.forEachIndexed { i, d ->
                demands.add(ui.button(d.label, "flat") { message = session.diplomacy.ultimatum(id, d).explanation; nav.refresh() })
                if (i % 2 == 1) demands.row()
            }
            into.add(demands).left().row()
            if (!confirmWar) {
                into.add(ui.button("Déclarer la guerre…", "flat") { confirmWar = true; nav.refresh() }).left().row()
            } else {
                into.add(ui.label("Une guerre aura un coût humain, économique et politique considérable.", "small", Theme.warning, wrap = true)).growX().row()
                val confirm = Table().apply { defaults().padRight(4f) }
                confirm.add(ui.button("Confirmer la déclaration de guerre", "accent") {
                    session.diplomacy.declareWar(id); confirmWar = false; message = "La France est en guerre."; nav.refresh()
                })
                confirm.add(ui.button("Annuler") { confirmWar = false; nav.refresh() })
                into.add(confirm).left().row()
            }
        }
    }

    private fun agreements(into: Table, id: String) {
        val agreements = session.diplomacy.agreementsWith(id)
        if (agreements.isEmpty()) return
        into.add(ui.label("Accords en vigueur", "bold")).padTop(GAP).row()
        val describer = ProposalDescriber(session.db)
        agreements.forEach { a ->
            into.add(ui.label(describer.describeAll(a.clauses, a.parties[0], a.parties[1], 0).substringBeforeLast("\n"), "small", wrap = true)).row()
            into.add(ui.label("Jusqu'au ${a.expiresAt.toDateTime().toLocalDate()}", "muted")).row()
            into.add(ui.button("Rompre l'accord (grave)", "flat") { session.diplomacy.breakAgreement(a.id); nav.refresh() }).left().row()
        }
    }

    private var addingClause = false

    private fun editor(into: Table, id: String) {
        into.add(ui.label(if (counterOf != null) "Contre-proposition" else if (draft.isEmpty()) "Proposer un accord" else "Votre proposition", "title", Theme.catDiplomacy)).padTop(GAP).row()
        draft.toList().forEach { c -> clauseEditor(into, c, id) }
        if (draft.isNotEmpty()) {
            val duration = Table().apply { defaults().padRight(4f) }
            duration.add(ui.label("Durée", "muted"))
            duration.add(ui.button("−") { years = (years - 1).coerceAtLeast(1); nav.refresh() })
            duration.add(ui.label("$years an${if (years > 1) "s" else ""}"))
            duration.add(ui.button("+") { years = (years + 1).coerceAtMost(MAX_YEARS); nav.refresh() })
            into.add(duration).left().padTop(4f).row()
            val send = Table().apply { defaults().padRight(4f) }
            send.add(ui.colorButton("✉ Envoyer " + fr.president.engine.data.CountryNames(session.db.country(id).definition).to, Theme.catDiplomacy) {
                val clauses = draft.map { Clause(it.type, it.giver, it.params.toMap()) }
                session.diplomacy.propose(id, clauses, years, counterOf)
                draft.clear(); counterOf = null; addingClause = false
                message = "Proposition transmise. La réponse arrivera dans quelques jours (onglet Messages)."
                nav.refresh()
            })
            send.add(ui.button("Annuler") { draft.clear(); counterOf = null; message = null; nav.refresh() })
            into.add(send).left().padTop(4f).row()
        }
        into.add(ui.button(if (addingClause) "▲ Fermer la liste" else "+ Ajouter une clause", "flat") { addingClause = !addingClause; nav.refresh() }).left().padTop(4f).row()
        if (!addingClause) return
        val types = Table().apply { defaults().growX().uniformX().pad(2f) }
        session.db.diplomacy.clauseTypes.forEachIndexed { i, t ->
            types.add(ui.button(t.label, "default") {
                draft += MutableClause(t.id, player, t.params.associate { it.id to it.default }.toMutableMap())
                addingClause = false
                nav.refresh()
            })
            if (i % 2 == 1) types.row()
        }
        into.add(types).growX().row()
    }

    private fun clauseEditor(into: Table, c: MutableClause, partner: String) {
        val def = session.db.diplomacy.clause(c.type)
        val box = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(Theme.panelAlt)) }
        box.add(ui.label(def.label, "bold")).expandX().left()
        box.add(ui.button("✕", "flat") { draft.remove(c); nav.refresh() }).right().row()
        box.add(ui.label(def.description, "muted", wrap = true)).colspan(2).growX().row()
        if (!def.mutual) {
            val giver = Table().apply { defaults().padRight(4f) }
            giver.add(ui.label("Fournisseur", "muted"))
            listOf(player, partner).forEach { g ->
                giver.add(ui.button(session.db.country(g).definition.name, "toggle") { c.giver = g; nav.refresh() }.also { it.isChecked = c.giver == g })
            }
            box.add(giver).colspan(2).left().row()
        }
        def.params.forEach { p ->
            val v = c.params[p.id] ?: p.default
            val row = Table().apply { defaults().padRight(4f) }
            row.add(ui.label(p.label, "muted"))
            row.add(ui.button("−") { c.params[p.id] = (v - p.step).coerceAtLeast(p.min); nav.refresh() })
            row.add(ui.label("${Formatting.amount(v)} ${p.unit}"))
            row.add(ui.button("+") { c.params[p.id] = (v + p.step).coerceAtMost(p.max); nav.refresh() })
            box.add(row).colspan(2).left().row()
        }
        into.add(box).growX().padBottom(4f).row()
    }

    private fun pendingProposals(into: Table, id: String) {
        val mine = session.state.diplomacy.proposals.filter { (it.to == id || it.from == id) }.takeLast(MAX_HISTORY).reversed()
        if (mine.isEmpty()) return
        into.add(ui.label("Historique des propositions", "bold")).padTop(GAP).row()
        mine.forEach { p ->
            val status = when (p.status) {
                ProposalStatus.PENDING -> if (p.from == player) "En attente de réponse" else "Attend votre réponse"
                ProposalStatus.ACCEPTED -> "Acceptée"
                ProposalStatus.REFUSED -> "Refusée"
                ProposalStatus.COUNTERED -> "Contre-proposition"
                ProposalStatus.EXPIRED -> "Restée sans réponse"
                ProposalStatus.WITHDRAWN -> "Retirée"
            }
            val who = if (p.from == player) "Vous" else session.db.country(p.from).definition.name
            into.add(ui.label("$who · ${p.createdAt.toDateTime().toLocalDate()} · $status", "small")).row()
            if (p.reasons.isNotEmpty()) into.add(ui.label("Motifs : " + p.reasons.joinToString(", "), "muted", wrap = true)).row()
        }
    }

    private companion object {
        const val DEFAULT_YEARS = 5
        const val MAX_YEARS = 15
        const val COUNTRIES_PER_ROW = 3
        const val MAX_HISTORY = 6
        const val PORTRAIT = 48f
    }
}
