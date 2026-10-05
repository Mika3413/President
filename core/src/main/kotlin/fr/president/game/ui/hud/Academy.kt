package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.session.GameSession
import fr.president.game.map.MapSelection
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.panels.Navigator
import fr.president.game.ui.panels.PanelId

/**
 * L'Académie : apprendre en profondeur, module par module. Chaque leçon explique un mécanisme,
 * puis demande de le pratiquer ; l'étape n'est validée que lorsque le joueur l'a vraiment fait
 * (panneau ouvert, décision prise, mesure décrétée...). « Montrez-moi » ouvre le bon écran.
 */
object AcademyCourse {
    /** Une étape : texte, élément à entourer, écran à ouvrir sur demande, condition de réussite. */
    class Step(
        val text: String,
        val target: String? = null,
        val show: PanelId? = null,
        val showArg: String? = null,
        /** null : étape d'explication, validée par « Compris ». */
        val done: (Check.() -> Boolean)? = null,
    )

    class Module(val id: String, val icon: String, val title: String, val goal: String, val steps: List<Step>)

    /** Ce qu'une étape peut vérifier : l'écran et l'état de la partie au début de l'étape. */
    class Check(val host: TourHost, val session: GameSession, private val start: Snapshot) {
        fun panel(p: PanelId) = host.openPanel == p
        fun journal(kind: String) = session.state.stats.journal.count { it.kind == kind } > (start.journal[kind] ?: 0)
        val measures get() = session.state.measures.active.size > start.measures
        val talks get() = session.state.dialogue.lastConversation.size > start.talks || session.state.dialogue.lastConversation.values.any { it > start.time }
        val proposals get() = session.state.policy.proposals.size > start.proposals
        val agendaBooked get() = session.state.agenda.entries.size > start.agenda
        val ordered get() = session.state.military.units.values.any { it.countryId == session.state.player.countryId && it.path.isNotEmpty() }
    }

    class Snapshot(val journal: Map<String, Int>, val measures: Int, val talks: Int, val proposals: Int, val agenda: Int, val time: fr.president.engine.time.WorldTime)

    fun snapshot(s: GameSession) = Snapshot(
        s.state.stats.journal.groupingBy { it.kind }.eachCount(), s.state.measures.active.size, s.state.dialogue.lastConversation.size,
        s.state.policy.proposals.size, s.state.agenda.entries.size, s.state.time,
    )

    val modules = listOf(
        Module("bases", "★", "1 · Les bases", "Lire l'écran, comprendre vos chiffres et suivre le monde.", listOf(
            Step("Bienvenue à l'Académie. En haut, vos six chiffres clés : popularité, chômage, croissance, budget, dette et jours avant l'élection. Vert = bien, orange = à surveiller, rouge = danger ; la flèche donne la tendance."),
            Step("Touchez votre popularité (le premier chiffre) : sa courbe et ses causes s'affichent.", "chip.approval", PanelId.STATS) { panel(PanelId.STATS) },
            Step("Le bouton « Pourquoi ? » sous une courbe détaille ce qui fait monter ou baisser le chiffre : chaque ligne est une cause, en points. C'est l'outil pour savoir où agir."),
            Step("Dans le Bilan, l'onglet « Groupes » montre l'opinion de 13 groupes sociaux : jeunes, retraités, ruraux, cadres... Chaque décision plaît à certains et déplaît à d'autres.", show = PanelId.STATS, showArg = "groups"),
            Step("Le temps ne s'arrête jamais : le monde avance même application fermée. Les courriers attendent votre réponse avant une échéance, sinon vos services appliquent l'option par défaut. Ouvrez « ✉ Messages ».", "bar.inbox", PanelId.INBOX) { panel(PanelId.INBOX) },
            Step("Enfin, l'encart « À faire » à gauche vous dit toujours quoi faire ensuite : touchez une ligne pour aller directement au bon écran. Module terminé !"),
        )),
        Module("decider", "★", "2 · Décider et gouverner", "Prendre des décisions nationales, gérer son agenda et son gouvernement.", listOf(
            Step("« ★ Décider » rassemble vos décisions directes : plans, décrets, annonces, déplacements. Ouvrez-le.", "bar.decide", PanelId.DECISIONS) { panel(PanelId.DECISIONS) },
            Step("Chaque carte indique le coût, la durée, les effets (vert = gain, rouge = perte) et la prévision sur vos chiffres. Choisissez une rubrique puis lancez une décision.", null, PanelId.DECISIONS) { journal("Décision") },
            Step("Votre temps est compté : déplacements, sommets et visites occupent l'agenda (5 jours par semaine). Ouvrez l'agenda avec le bouton ◷ à côté de la carte.", "agenda", PanelId.AGENDA) { panel(PanelId.AGENDA) },
            Step("Le gouvernement : votre Premier ministre et vos ministres. Leur loyauté compte ; ils proposent leurs propres projets et se disputent parfois. Ouvrez « Gouvernement » depuis « Plus ».", "bar.more", PanelId.GOVERNMENT) { panel(PanelId.GOVERNMENT) },
            Step("Les grandes réformes passent par l'Assemblée : la chance d'adoption dépend de votre majorité. Proposez une loi ou une réforme dans le panneau Gouvernement ou Économie.", null, PanelId.GOVERNMENT) { proposals },
            Step("Bravo. Retenez : décisions directes = rapides mais coûteuses ; lois = durables mais votées. Surveillez la loyauté des ministres et votre agenda."),
        )),
        Module("territoire", "⌂", "3 · La carte et le territoire", "Lire la carte et agir dans les départements.", listOf(
            Step("La carte se lit par couches. Touchez « ☰ Carte » et choisissez une autre couche (chômage, santé, sécurité...).", "layers") { host.layer != ThematicLayer.ADMIN },
            Step("Chaque département affiche son chiffre et sa couleur. Touchez un département de métropole pour ouvrir sa fiche.") { host.selection is MapSelection.Department },
            Step("Onglet « ▶ Agir » : chantiers et plans locaux (hôpital, usine, police...). Lancez une action locale.", "tab.act") { journal("Territoire") },
            Step("Onglet « ⚠ Crise » de la fiche : mesures d'urgence pour ce seul département (évacuation, confinement local, Canadair...). Allez y jeter un œil, puis touchez « Compris »."),
            Step("Les élus locaux vous écrivent et se souviennent de vos réponses. Une visite présidentielle (fiche d'un élu → « Annoncer une visite ») remonte votre popularité locale. Module terminé !"),
        )),
        Module("economie", "€", "4 · Économie et budget", "Impôts, dépenses, secteurs et entreprises.", listOf(
            Step("Ouvrez « Économie » depuis « Plus ».", "bar.more", PanelId.ECONOMY) { panel(PanelId.ECONOMY) },
            Step("Onglet « Impôts » : chaque taux se règle par crans ; la hausse rapporte mais pèse sur le moral et certains groupes. Onglet « Dépenses » : même principe pour chaque ministère. Les deux passent par un vote du Parlement."),
            Step("Le déficit = dépenses − recettes ; il alimente la dette, dont les intérêts réduisent vos marges. Au-delà de 3 % du PIB, Bruxelles s'inquiète ; au-delà de 6 %, les marchés aussi."),
            Step("Onglet « Entreprises » : 12 secteurs et 19 grandes entreprises. Leur activité suit le moral, l'énergie, les taux... Soutenez une entreprise ou convoquez un PDG.", null, PanelId.ECONOMY) { journal("Économie") },
            Step("Un krach boursier fait chuter le moral ; un choc sectoriel (attentat = tourisme, droits de douane = luxe) freine la croissance. Module terminé !"),
        )),
        Module("crises", "⚠", "5 · Crises et risques", "Anticiper et affronter feux, épidémies, attentats.", listOf(
            Step("Ouvrez « ⚠ Crises et risques » (en haut de « Décider », ou dans « Plus »).", "decide.crisis", PanelId.CRISIS) { panel(PanelId.CRISIS) },
            Step("Chaque risque donne sa probabilité sur 30 jours, calculée avec la saison et l'état du pays, et le lieu le plus exposé. Touchez un risque pour voir les mesures qui le réduisent."),
            Step("Décrétez une mesure de prévention ou de crise (onglet Risques ou Boîte à outils).", "crisis.toolbox", PanelId.CRISIS) { measures },
            Step("Une mesure coûte au lancement et chaque mois. Les mesures contraignantes s'usent : le respect baisse, elles protègent moins. Au-delà de 12 jours, un régime d'exception doit être voté par le Parlement ; le Conseil d'État peut suspendre une mesure disproportionnée."),
            Step("Lors d'un drame, le courrier propose sa réponse principale, d'autres leviers (aller sur place, armée, aide européenne...) et des mesures d'urgence cumulables. Pensez à les combiner. Module terminé !"),
        )),
        Module("monde", "☎", "6 · Diplomatie et Europe", "Négocier, faire pression, peser à Bruxelles.", listOf(
            Step("Dézoomez et touchez un pays étranger : sa couleur dit votre relation.") { host.selection is MapSelection.Country },
            Step("Appelez son dirigeant (fiche du pays → entretien) : renforcer les liens, rassurer, solliciter un soutien, mettre en garde.") { talks },
            Step("Le panneau « Diplomatie » permet de proposer des accords clause par clause, des sanctions ou un ultimatum. L'IA accepte, refuse ou fait une contre-proposition selon ses intérêts et sa mémoire de vos actes."),
            Step("Ouvrez « Union européenne » (depuis « Diplomatie » ou « Plus »).", null, PanelId.EU) { panel(PanelId.EU) },
            Step("Quand un texte est en discussion, choisissez la position de la France, ralliez des partenaires et amendez le texte. À l'unanimité, un seul veto bloque ; à la majorité qualifiée, il faut 55 % des États et 65 % de la population. Module terminé !"),
        )),
        Module("armees", "⚔", "7 · Armées et guerre", "Commander les unités comme dans Supremacy.", listOf(
            Step("Ouvrez « ⚔ Armées » : vos unités, leur préparation, les stocks et les guerres en cours.", null, PanelId.ARMY) { panel(PanelId.ARMY) },
            Step("Touchez une unité sur la carte (ou dans la liste) pour ouvrir sa fiche.") { host.selection is MapSelection.Unit },
            Step("Donnez-lui un ordre : déplacer, puis touchez la zone visée sur la carte. L'aperçu indique la durée du trajet et le rapport de forces.") { ordered },
            Step("En guerre : attaque, soutien aérien, débarquement, parachutage, frappes et cyberattaques. Le ravitaillement et le moral comptent ; les flèches et les épées sur la carte montrent les mouvements et les batailles. Module terminé !"),
        )),
        Module("legislation", "⚖", "8 · Faire la loi et le budget", "Budget annuel, projets de loi, mesures sur mesure, décrets.", listOf(
            Step("Ouvrez « Lois et budget » depuis « ☰ Plus ». C'est la fabrique de la loi : tout ce qui change une règle passe par ici.", "bar.more", PanelId.LEGISLATION) { panel(PanelId.LEGISLATION) },
            Step("Onglet « Budget » : les impôts, les dépenses, la fiscalité fine et les allocations. Augmenter un impôt n'est pas une loi à part : vous bougez le curseur, le changement entre dans le projet de budget. Rien ne change avant le vote."),
            Step("Chaque automne, le gouvernement dépose la loi de finances de l'année suivante : vos réglages y sont repris et le Parlement vote tout d'un coup en décembre. Pressé ? Faites voter un budget rectificatif à tout moment. Réglez un impôt ou une dépense.", null, PanelId.LEGISLATION, "budget") {
                session.state.legislation.budgetDraft.isNotEmpty() || (session.legislation.openPlf?.changes?.isNotEmpty() == true) },
            Step("Onglet « Projet de loi » : tout le reste (société, justice, travail, libertés, Constitution, retraites...). Réunissez plusieurs changements dans un même texte ; un nom est proposé, vous pouvez le changer. Ajoutez un changement à votre projet.", null, PanelId.LEGISLATION, "law") {
                session.legislation.lawChanges().isNotEmpty() },
            Step("Onglet « Créer une mesure » : composez une phrase. Action (taxer, primes, recruter, interdire, obliger, plafonner...), cible (médecins, 1 % les plus riches, banques, loyers...), valeur au chiffre près (5 %, 6 %, 6,5 %...) et conditions (seuil de revenus, zone, exception rurale, durée). Tout est chiffré : recettes, départs, gagnants, perdants, services, risque de censure.", null, PanelId.LEGISLATION, "builder"),
            Step("Onglet « Décrets » : le gouvernement règle seul certaines choses (coup de pouce au SMIC, vitesse sur les routes), tout de suite. Trop brutal, un décret peut être annulé par le Conseil d'État.", null, PanelId.LEGISLATION, "decree"),
            Step("Onglet « Au Parlement » : les textes en discussion (chances, amendements, 49.3 si rejet), les textes votés (le Conseil constitutionnel peut censurer des articles) et l'abrogation. Chaque réglage n'a qu'une valeur : tous les écrans montrent la même.", null, PanelId.LEGISLATION, "parliament"),
            Step("Tout a des conséquences : au-delà de certains seuils (budget de la police coupé, RSA trop proche du SMIC, impôts trop lourds, dette...), le pays réagit chaque mois — grèves, criminalité sur la carte, chômage, émeutes. L'aperçu d'un réglage les annonce en rouge ; le Bilan, onglet « Conséquences », liste ce qui frappe le pays et ce qui menace. Module terminé !", null, PanelId.STATS, "consequences") { panel(PanelId.STATS) },
        )),
        Module("puissance", "◎", "9 · Puissance : commerce, défense, renseignement, ONU", "Peser dans le monde avec tous les leviers d'un État.", listOf(
            Step("Ouvrez « Commerce et matières premières » depuis « Plus ». Les cours du pétrole, du gaz ou du blé font votre prix de l'énergie et votre inflation.", null, PanelId.TRADE) { panel(PanelId.TRADE) },
            Step("Contrats à long terme et stocks stratégiques amortissent une flambée ; l'onglet « Exportations » vous fait vendre avions, centrales ou TGV face à la concurrence ; le FMI, l'OMC et la Banque mondiale sont dans le troisième onglet."),
            Step("Ouvrez « Défense » : capacités des armées, catalogue d'armement, ventes d'armes, bases à l'étranger et dissuasion nucléaire.", null, PanelId.DEFENSE) { panel(PanelId.DEFENSE) },
            Step("Commandez un équipement : il livre une unité ou renforce une capacité (défense aérienne, frappe, cyber...).", null, PanelId.DEFENSE) { journal("Défense") },
            Step("Ouvrez « Renseignement » : opérations de la DGSE pays par pays, groupes armés et terroristes, contre-espionnage. Un échec peut éclater au grand jour.", null, PanelId.INTEL) { panel(PanelId.INTEL) },
            Step("Ouvrez « ONU » : 15 membres, 9 voix et aucun veto pour adopter une résolution. Votez, opposez le veto de la France, ou déposez votre propre texte et convainquez les indécis.", null, PanelId.UN) { panel(PanelId.UN) },
            Step("Retenez : l'économie, l'armée, les services secrets et la diplomatie se répondent. Un contrat d'armement soigne une relation ; une opération ratée la détruit. Module terminé !"),
        )),
        Module("societe", "⚑", "10 · Lois, société civile et la rue", "Changer la société sans mettre le pays dans la rue.", listOf(
            Step("Ouvrez « Lois et Constitution » : fin de vie, cannabis, durée du travail, libertés, mandat présidentiel... Chaque changement passe au Parlement ou par référendum.", null, PanelId.LAWS) { panel(PanelId.LAWS) },
            Step("Ouvrez « Société civile » : syndicats, patronat, cultes, lobbies. Un acteur influent en colère mobilise.", null, PanelId.ACTORS) { panel(PanelId.ACTORS) },
            Step("Ouvrez « La rue et l'armée ». Une réforme qui fâche fait descendre les Français dans la rue : manifestations, blocages, émeutes, insurrection.", null, PanelId.UNREST) { panel(PanelId.UNREST) },
            Step("Face à un mouvement : parler aux Français, recevoir les organisateurs, céder, encadrer les cortèges ou réprimer (au risque d'une bavure). Une insurrection qui dure peut vous renverser."),
            Step("L'armée aussi a son humeur : budget rogné, invasion ou chaos la rendent moins loyale ; sous 50 %, des officiers complotent. La DGSI peut vous prévenir. Module terminé !"),
        )),
        Module("opinion", "▤", "11 · Opinion, presse et élections", "Gagner la confiance et la réélection.", listOf(
            Step("Ouvrez « Presse » depuis « Plus » : les unes du jour, le climat médiatique et les sondages.", null, PanelId.PRESS) { panel(PanelId.PRESS) },
            Step("Ouvrez « Élections » : intentions de vote au premier et au second tour, et vos promesses de campagne.", null, PanelId.ELECTIONS) { panel(PanelId.ELECTIONS) },
            Step("Les promesses tenues ou rompues pèsent sur le vote ; chaque groupe social juge votre bilan sur ce qui le touche."),
            Step("Dans le Bilan, l'onglet « Héritage » donne la note que l'Histoire vous attribuerait aujourd'hui, sur 20.", null, PanelId.STATS, "legacy") { panel(PanelId.STATS) },
            Step("Vous avez terminé l'Académie : vous connaissez tous les leviers du président. Bonne chance pour votre mandat !"),
        )),
    )
}

/**
 * Coach de l'Académie : affiche l'étape en cours (carte de consigne et cadre lumineux), la valide
 * quand le joueur l'a faite, et passe à la suivante.
 */
class AcademyCoach(private val ui: Ui, private val session: GameSession, private val host: TourHost, private val nav: Navigator) {
    val card: Table = ui.panelTable()
    val highlight: Actor = Glow()
    private var shown: Pair<String, Int>? = null
    private var start = AcademyCourse.snapshot(session)

    val active: Boolean get() = session.state.player.academyModule != null

    fun startModule(id: String) {
        session.state.player.academyModule = id
        session.state.player.academyStep = 0
        // Le tutoriel d'accueil laisse la place à l'Académie.
        session.state.player.tourStep = -1
        shown = null
    }

    fun stop() {
        session.state.player.academyModule = null
        hide()
    }

    fun update() {
        val id = session.state.player.academyModule ?: run { hide(); return }
        val module = AcademyCourse.modules.firstOrNull { it.id == id } ?: run { stop(); return }
        val i = session.state.player.academyStep
        val step = module.steps.getOrNull(i)
        if (step == null) { finish(module); return }
        if (shown != id to i) { start = AcademyCourse.snapshot(session); build(module, i) }
        val done = step.done ?: return
        if (AcademyCourse.Check(host, session, start).done()) next()
    }

    private fun next() {
        session.state.player.academyStep++
    }

    private fun finish(module: AcademyCourse.Module) {
        session.state.player.academyDone += module.id
        session.state.player.academyModule = null
        hide()
        nav.open(PanelId.ACADEMY)
    }

    private fun build(module: AcademyCourse.Module, i: Int) {
        shown = module.id to i
        val step = module.steps[i]
        (highlight as Glow).target = step.target
        card.clearChildren()
        card.pad(8f, 12f, 8f, 12f)
        card.isVisible = true
        card.add(ui.label("${module.icon} ${module.title} · ${i + 1}/${module.steps.size}", "bold", Theme.highlight)).left().row()
        card.add(ui.label(step.text, "small", wrap = true)).width(TEXT_WIDTH).left().row()
        val buttons = Table().apply { defaults().padRight(4f) }
        if (step.done == null) buttons.add(ui.colorButton("Compris ▶", Theme.accentDark) { next() })
        else step.show?.let { p -> buttons.add(ui.colorButton("Montrez-moi", Theme.accentDark) { nav.open(p, step.showArg) }) }
        if (step.done != null) buttons.add(ui.button("Passer", "flat") { next() })
        buttons.add(ui.button("Quitter", "flat") { stop() })
        card.add(buttons).left().padTop(3f)
    }

    private fun hide() {
        card.isVisible = false
        (highlight as Glow).target = null
        shown = null
    }

    /** Cadre pulsant autour de l'élément à toucher. */
    private inner class Glow : Actor() {
        var target: String? = null
        private var time = 0f
        private val pos = Vector2()
        private val c = Color()

        init { touchable = Touchable.disabled }

        override fun act(delta: Float) { super.act(delta); time += delta }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val name = target ?: return
            val actor = stage?.root?.findActor<Actor>(name) ?: return
            if (!actor.isVisible || actor.stage == null) return
            actor.localToStageCoordinates(pos.set(0f, 0f))
            val pulse = (MathUtils.sin(time * 5f) + 1f) / 2f
            val m = 3f + pulse * 4f
            val x = pos.x - m
            val y = pos.y - m
            val w = actor.width + 2 * m
            val h = actor.height + 2 * m
            batch.color = c.set(Theme.highlight).also { it.a = (0.35f + 0.65f * pulse) * parentAlpha }
            batch.draw(ui.skin.white, x, y, w, 3f)
            batch.draw(ui.skin.white, x, y + h - 3f, w, 3f)
            batch.draw(ui.skin.white, x, y, 3f, h)
            batch.draw(ui.skin.white, x + w - 3f, y, 3f, h)
            batch.color = Color.WHITE
        }
    }

    private companion object {
        const val TEXT_WIDTH = 380f
    }
}
