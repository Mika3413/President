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
        onClick { openMessage = m.id; session.markRead(m.id); nav.refresh() }
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
        m.focusId?.let { focus -> into.add(ui.button("Voir sur la carte", "flat") { nav.focusOn(focus) }).left().padTop(4f).row() }
        if (m.awaitingAnswer) {
            into.add(ui.label("Votre décision", "bold")).padTop(GAP).row()
            m.deadline?.let { into.add(ui.label("Sans réponse avant le ${Formats.dateTime(it)}, l'option par défaut sera appliquée.", "muted", wrap = true)).growX().row() }
            m.options.forEach { o ->
                val box = Table().apply { defaults().left() }
                box.add(ui.button(o.label, if (o.id == m.defaultOptionId) "default" else "accent") {
                    if (o.id == DiplomacyService.OPTION_NEGOTIATE) onNegotiate(m.originId ?: "")
                    session.answer(m.id, o.id)
                    nav.refresh()
                }).left().row()
                if (o.hint.isNotBlank()) box.add(ui.label(o.hint, "muted", wrap = true)).growX().row()
                into.add(box).growX().padBottom(6f).row()
            }
        } else if (m.chosenOptionId != null) {
            val chosen = m.options.firstOrNull { it.id == m.chosenOptionId }?.label ?: m.chosenOptionId
            into.add(ui.label("Décision : $chosen" + if (m.answeredByDefault) " (appliquée par défaut)" else "", "small", Theme.textMuted, wrap = true)).growX().padTop(GAP).row()
        }
    }

    private companion object {
        const val MAX_LISTED = 60
        const val PORTRAIT = 48f
    }
}
