package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.inbox.InboxMessage
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/** Messagerie du président : courriers procéduraux et décisions à prendre. */
class InboxPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit, private val onNegotiate: (String) -> Unit) : Panel(ui, onClose) {
    override val title = "Messages"
    private val session get() = nav.session
    var openMessage: String? = null

    override fun build(into: Table) {
        val open = openMessage?.let { id -> session.state.inbox.messages.firstOrNull { it.id == id } }
        if (open != null) {
            message(into, open)
            return
        }
        val messages = session.state.inbox.messages.asReversed()
        val awaiting = messages.filter { it.awaitingAnswer }
        if (awaiting.isNotEmpty()) into.add(ui.label("${awaiting.size} décision(s) en attente", "bold", Theme.warning)).padBottom(GAP).row()
        (awaiting + messages.filter { !it.awaitingAnswer }).take(MAX_LISTED).forEach { m ->
            val row = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(if (m.awaitingAnswer) Theme.panelAlt else Theme.panel)) }
            row.add(ui.label(m.subject, if (m.read) "default" else "bold", wrap = true)).growX().row()
            val due = m.deadline?.takeIf { m.awaitingAnswer }?.let { " · réponse avant le " + Formats.date(it) } ?: ""
            row.add(ui.label("${m.senderLabel} · ${Formats.date(m.time)}$due", "muted", wrap = true)).growX().row()
            row.onClickOpen(m)
            into.add(row).growX().padBottom(4f).row()
        }
    }

    private fun Table.onClickOpen(m: InboxMessage) {
        onClick { openMessage = m.id; measureNote = null; session.markRead(m.id); nav.refresh() }
    }

    private fun message(into: Table, m: InboxMessage) {
        into.add(ui.button("← Tous les messages", "flat") { openMessage = null; nav.refresh() }).left().row()
        val sender = m.senderId?.let { session.state.characters[it] }
        val head = Table()
        sender?.let { head.add(ui.portraits.image(it)).size(PORTRAIT).padRight(8f) }
        head.add(ui.label(m.subject, "large", wrap = true)).growX().left()
        into.add(head).growX().padTop(4f).row()
        into.add(ui.label("${m.senderLabel} · ${Formats.dateTime(m.time)}", "muted", wrap = true)).growX().row()
        into.add(ui.label(m.body, "default", wrap = true)).growX().padTop(GAP).row()
        measureNote?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padTop(4f).row() }
        m.focusId?.let { focus -> into.add(ui.button("Voir sur la carte", "flat") { nav.focusOn(focus) }).left().padTop(4f).row() }
        if (m.awaitingAnswer) {
            into.add(ui.label("Votre décision", "bold")).padTop(GAP).row()
            m.deadline?.let { into.add(ui.label("Sans réponse avant le ${Formats.dateTime(it)}, l'option par défaut sera appliquée.", "muted", wrap = true)).growX().row() }
            // Réponses propres au dossier d'abord, puis les autres leviers du président.
            val (extra, main) = m.options.partition { it.id.startsWith(EXTRA_PREFIX) }
            main.forEach { o -> into.add(optionCard(m, o)).growX().padBottom(4f).row() }
            if (extra.isNotEmpty()) {
                into.add(ui.label("Autres réponses possibles", "bold", Theme.highlight)).padTop(4f).row()
                extra.forEach { o -> into.add(optionCard(m, o)).growX().padBottom(4f).row() }
            }
        } else if (m.chosenOptionId != null) {
            val chosen = m.options.firstOrNull { it.id == m.chosenOptionId }?.label ?: m.chosenOptionId
            into.add(ui.label("Décision : $chosen" + if (m.answeredByDefault) " (appliquée par défaut)" else "", "small", Theme.textMuted, wrap = true)).growX().padTop(GAP).row()
        }
        measures(into, m)
    }

    /** Une réponse : texte complet (qui passe à la ligne) et ce qu'elle implique. */
    private fun optionCard(m: InboxMessage, o: fr.president.engine.inbox.MessageOption): Table {
        val isDefault = o.id == m.defaultOptionId
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label("▶", "bold", if (isDefault) Theme.textMuted else Theme.accent)).top().padRight(6f)
        head.add(ui.label(o.label, "bold", wrap = true)).growX().minWidth(0f)
        card.add(head).growX().row()
        val hint = listOfNotNull(o.hint.ifBlank { null }, if (isDefault) "option appliquée si vous ne répondez pas" else null).joinToString(" · ")
        if (hint.isNotBlank()) card.add(ui.label(hint, "muted", wrap = true)).growX().padLeft(ARROW).row()
        card.onClick {
            if (o.id == DiplomacyService.OPTION_NEGOTIATE) onNegotiate(m.originId ?: "")
            fr.president.game.ui.Sfx.play(fr.president.game.ui.Sfx.Kind.DECISION)
            session.answer(m.id, o.id)
            nav.refresh()
        }
        return card
    }

    /** Mesures d'urgence cumulables avec la réponse : évacuer, confiner, couvre-feu, Vigipirate... */
    private fun measures(into: Table, m: InboxMessage) {
        if (m.measures.isEmpty()) return
        val key = "measures.${m.id}"
        val open = key in expanded || m.awaitingAnswer
        val views = m.measures.mapNotNull { id -> session.measures.definitions.firstOrNull { it.id == id } }
            .map { session.measures.view(it, if (it.local) m.measureDepartment else null) }
        val active = views.count { it.active != null }
        val head = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f) }
        head.add(ui.label("⚠ Mesures d'urgence en complément (${views.size})" + if (active > 0) " · $active en vigueur" else "", "bold", Theme.warning, wrap = true)).growX().minWidth(0f)
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right()
        head.onClick { if (!expanded.remove(key)) expanded += key; nav.refresh() }
        into.add(head).growX().padTop(GAP).row()
        if (!open) return
        into.add(ui.label("Cumulables avec votre réponse : vous pouvez en décréter plusieurs.", "muted", wrap = true)).growX().padBottom(4f).row()
        views.sortedBy { if (it.active != null) 0 else if (it.blocker == null) 1 else 2 }.forEach { v ->
            into.add(measureCards.card(v, if (v.def.local) m.measureDepartment else null, compact = true)).growX().padBottom(4f).row()
        }
        into.add(ui.button("Tous les risques et mesures ▶", "flat") { nav.open(PanelId.CRISIS, m.measureDepartment?.let { "dept:$it" } ?: "active") }).left().row()
    }

    private var measureNote: String? = null
    private val measureCards = fr.president.game.ui.widgets.MeasureCards(ui, nav.session, { nav.refresh() }) { result ->
        measureNote = result
        nav.refresh()
    }

    private companion object {
        const val MAX_LISTED = 60
        const val PORTRAIT = 48f
        const val ARROW = 14f
        const val EXTRA_PREFIX = "x_"
    }
}
