package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/**
 * Menu « ☰ Plus » : les écrans moins fréquents en grandes tuiles faciles à toucher, chacune
 * avec une phrase qui dit à quoi elle sert et une pastille quand quelque chose attend.
 */
class MenuPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "☰ Tous les écrans"
    private val session get() = nav.session

    private data class Entry(val icon: String, val label: String, val text: String, val color: Color, val panel: PanelId?, val badge: () -> Int = { 0 }, val key: String? = null, val action: (() -> Unit)? = null)

    private class Section(val title: String, val entries: List<Entry>)

    private val sections = listOf(
        Section("Présidence", listOf(
            Entry("✪", "Bureau du président", "Note du jour, rapports des ministres, préfets, renseignement, prévisions de Bercy.", Theme.highlightDark, PanelId.OFFICE) {
                session.state.consequences.current.values.count { it.active && it.severity >= 1 } },
            Entry("★", "Moments présidentiels", "Allocution phrase par phrase, interview du 20 heures, débat, sommets.", Theme.highlightDark, PanelId.MOMENTS),
            Entry("◷", "Agenda", "Votre semaine : déplacements, sommets, visites, entretiens.", Theme.catStats, PanelId.AGENDA),
            Entry("✉", "Messages", "Les décisions qui attendent votre réponse.", Theme.catInbox, PanelId.INBOX) { session.state.inbox.messages.count { it.awaitingAnswer } },
            Entry("⚑", "Alertes", "Toutes les notifications récentes.", Theme.catAlerts, PanelId.NOTIFICATIONS) { session.state.notifications.unreadCount },
        )),
        Section("Le pays", listOf(
            Entry("▲", "Bilan", "Courbes, causes, conséquences en chaîne, groupes sociaux, journal.", Theme.catStats, PanelId.STATS),
            Entry("⚠", "Crises et risques", "Risques à 30 jours, prévention, confinement, couvre-feu, ORSEC...", Theme.warning, PanelId.CRISIS) { session.risks.unattended().size },
            Entry("⚑", "La rue et l'armée", "Manifestations en direct, grèves, émeutes, loyauté de l'armée.", Theme.bad, PanelId.UNREST) { session.state.unrest.movements.size },
            Entry("▤", "Presse", "Unes du jour, sondages, climat médiatique.", Theme.catPress, PanelId.PRESS),
            Entry("▤", "Le livre de votre présidence", "Un chapitre par année : ce qui l'a marquée, ses chiffres, puis le jugement de l'Histoire.", Theme.catPress, null, key = "BOOK", action = { session.state.player.newScreens -= "BOOK"; nav.openBook() }),
            Entry("▶", "Relecture du mandat", "Votre mandat en accéléré : la carte semaine après semaine, les chiffres, les grands titres.", Theme.catStats, null, key = "REPLAY", action = { session.state.player.newScreens -= "REPLAY"; nav.startReplay() }),
        )),
        Section("Lois et gouvernement", listOf(
            Entry("⚖", "Lois et budget", "Budget annuel, projets de loi, mesures sur mesure, décrets.", Theme.catGovernment, PanelId.LEGISLATION) { session.legislation.pendingBills().size },
            Entry("⚖", "Lois et Constitution", "Société, justice, travail, libertés, mandat, référendum.", Theme.catGovernment, PanelId.LAWS),
            Entry("⌂", "Gouvernement", "Ministres, Assemblée, Sénat, réformes.", Theme.catGovernment, PanelId.GOVERNMENT),
            Entry("⚒", "Société civile", "Syndicats, patronat, cultes, lobbies, associations.", Theme.catElections, PanelId.ACTORS) { session.actors.rows().count { it.satisfaction < 0.3 } },
            Entry("✔", "Élections", "Sondages, candidats, promesses.", Theme.catElections, PanelId.ELECTIONS),
        )),
        Section("Économie", listOf(
            Entry("€", "Économie", "Impôts, dépenses, services publics.", Theme.catEconomy, PanelId.ECONOMY),
            Entry("⛁", "Commerce et matières premières", "Pétrole, gaz, blé, contrats, exportations, FMI, OMC.", Theme.catEconomy, PanelId.TRADE) { session.state.trade.tenders.size },
        )),
        Section("Le monde", listOf(
            Entry("☎", "Diplomatie", "Accords, sanctions, ultimatums.", Theme.catDiplomacy, PanelId.DIPLOMACY),
            Entry("★", "Union européenne", "Textes au Conseil, votes, alliés à Bruxelles.", Theme.catDiplomacy, PanelId.EU) { if (session.state.eu.current?.francePosition == fr.president.engine.diplomacy.EuVote.ABSTAIN) 1 else 0 },
            Entry("◎", "ONU", "Conseil de sécurité : résolutions, votes, veto.", Theme.catDiplomacy, PanelId.UN) { session.state.un.drafts.count { it.sponsor != session.state.player.countryId && it.france == fr.president.engine.diplomacy.UnVote.ABSTAIN } },
            Entry("◎", "Journal du monde", "Élections, crises, accords, guerres et catastrophes ailleurs.", Theme.catDiplomacy, PanelId.WORLD),
        )),
        Section("Défense", listOf(
            Entry("⚔", "Armées", "Unités, logistique, conflits, opérations.", Theme.catArmy, PanelId.ARMY),
            Entry("✪", "Défense", "Armement, ventes d'armes, bases à l'étranger, dissuasion nucléaire.", Theme.catArmy, PanelId.DEFENSE),
            Entry("⊘", "Renseignement", "Espionnage, coups d'État, groupes armés, contre-espionnage.", Theme.catArmy, PanelId.INTEL) { session.state.intel.operations.size },
        )),
        Section("Le jeu", listOf(
            Entry("✎", "Académie", "Apprendre à jouer en profondeur : modules guidés.", Theme.catHelp, PanelId.ACADEMY),
            Entry("⚙", "Réglages", "Texte, couleurs, sons, rythme, notifications.", Theme.catSettings, PanelId.SETTINGS),
            Entry("?", "Aide", "Guide, glossaire, tutoriel.", Theme.catHelp, PanelId.HELP),
        )),
    )

    override fun build(into: Table) {
        val onboarding = fr.president.engine.presidency.OnboardingService(session.context)
        val hidden = ArrayList<String>()
        sections.forEach { section ->
            val shown = section.entries.filter { e -> onboarding.unlocked(e.key ?: e.panel!!.name).also { if (!it) hidden += e.label } }
            if (shown.isEmpty()) return@forEach
            into.add(ui.label(section.title.uppercase(), "small", Theme.textMuted)).left().padTop(6f).padBottom(2f).row()
            val grid = Table().apply { defaults().growX().uniformX().pad(3f) }
            shown.forEachIndexed { i, e ->
                grid.add(tile(e)).fill().minWidth(0f)
                if (i % 2 == 1) grid.row()
            }
            if (shown.size % 2 == 1) grid.add().fill()
            into.add(grid).growX().row()
        }
        if (hidden.isNotEmpty()) {
            val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
            box.add(ui.label("${hidden.size} écrans s'ouvriront au fil du mandat, quand ils deviendront utiles : ${hidden.joinToString(", ")}.", "muted", wrap = true)).growX().row()
            box.add(ui.button("Tout afficher maintenant", "flat") { onboarding.showAll(); nav.refresh() }).left().padTop(3f).row()
            into.add(box).growX().padTop(8f).row()
        }
    }

    private fun tile(e: Entry): Table {
        val tile = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        val head = Table()
        head.add(Table().apply { setBackground(ui.skin.fill(e.color)) }.also { it.add(ui.label(e.icon, "value", Color.WHITE)) }).size(ICON).padRight(8f)
        head.add(ui.label(e.label, "bold", wrap = true)).left().growX().minWidth(0f)
        val n = e.badge()
        val key = e.key ?: e.panel?.name
        if (key != null && key in session.state.player.newScreens) head.add(ui.label("● Nouveau", "small", Theme.highlight)).right()
        else if (n > 0) head.add(ui.label("● $n", "small", Theme.warning)).right()
        tile.add(head).growX().row()
        tile.add(ui.label(e.text, "muted", wrap = true)).growX().padTop(3f).row()
        tile.onClick { e.action?.invoke() ?: e.panel?.let { nav.open(it) } }
        return tile
    }

    private companion object {
        const val ICON = 30f
    }
}
