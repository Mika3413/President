package fr.president.game.screens

import fr.president.game.ui.tolerant
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.engine.data.GameDatabase
import fr.president.engine.politics.CharacterGenerator
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.CharacterSpec
import fr.president.engine.setup.NewGameOptions
import fr.president.engine.util.GameRandom
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/**
 * Lancement d'une partie : le joueur choisit son rythme (verrouillé ensuite)
 * et l'identité fictive de son président. Pas de campagne : il est déjà élu.
 */
class NewGameScreen(
    private val ui: Ui,
    private val db: GameDatabase,
    uiScale: Float,
    private val nowMillis: () -> Long,
    private val errorMessage: String?,
    /** Pays joué (la France par défaut) ; en changer reconstruit l'écran. */
    private val countryId: String = db.snapshot.playableCountries.first(),
    private val onCountry: (String) -> Unit = {},
    private val onStart: (NewGameOptions) -> Unit,
) : ScreenAdapter(), HasStage {
    override val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })
    private var pace = db.config.defaultPace
    private var scenario: String? = db.scenarios.firstOrNull()?.id
    private var female = false
    private var leaning = 0.0
    private var social: Double? = null
    private val first = TextField("", ui.s)
    private val last = TextField("", ui.s)
    private var seed = nowMillis()
    private val promises = linkedSetOf<String>()
    private var age = DEFAULT_AGE
    private var career: String? = null
    private val traits = mutableMapOf<String, Double>()
    private var appearance = fr.president.engine.politics.Appearance()
    private val preview = Table()
    private val ageLabel = ui.label("", "bold")

    init {
        // Par défaut, le président sortant (nom inspiré du réel) ; « Autre nom » en tire un autre.
        val figure = db.country(countryId).definition.leader.figure
        first.text = if (countryId == FRANCE) INCUMBENT_FIRST else figure?.firstName ?: INCUMBENT_FIRST
        last.text = if (countryId == FRANCE) INCUMBENT_LAST else figure?.lastName ?: INCUMBENT_LAST
        val content = Table().apply { pad(24f); defaults().left().padBottom(8f) }
        content.add(ui.label("PRÉSIDENT", "headline")).row()
        content.add(ui.label("Vous venez d'être élu(e) à la tête ${fr.president.engine.data.CountryNames(db.country(countryId).definition).of}. Le monde ne s'arrêtera pas pour vous attendre.", "default", wrap = true)).width(CONTENT_WIDTH).row()
        // Le pays à gouverner.
        if (db.snapshot.playableCountries.size > 1) {
            content.add(ui.label("Pays", "title")).padTop(12f).row()
            val countries = Table().apply { defaults().padRight(6f).padBottom(4f).left() }
            val group = ButtonGroup<TextButton>()
            db.snapshot.playableCountries.forEachIndexed { i, id ->
                val b = ui.button(db.country(id).definition.name, "toggle") { if (id != countryId) onCountry(id) }
                group.add(b)
                if (id == countryId) b.isChecked = true
                countries.add(b)
                if (i % FAMILY_COLUMNS == FAMILY_COLUMNS - 1) countries.row()
            }
            content.add(countries).row()
            db.country(countryId).definition.institutions.let { inst ->
                content.add(ui.label("Vous serez : ${inst.headOfGovernmentTitle.takeIf { countryId != FRANCE && countryId != "USA" } ?: inst.headOfStateTitle}.", "muted", wrap = true)).width(CONTENT_WIDTH).row()
            }
        }
        errorMessage?.let { content.add(ui.label(it, "small", Theme.bad, wrap = true)).width(CONTENT_WIDTH).row() }

        if (db.scenarios.isNotEmpty()) {
            content.add(ui.label("Situation de départ", "title")).padTop(12f).row()
            content.add(ui.label("La France réelle, ou une crise qui éclate dès votre prise de fonctions.", "muted", wrap = true)).width(CONTENT_WIDTH).row()
            val scenarioGroup = ButtonGroup<TextButton>()
            db.scenarios.forEach { sc ->
                val b = ui.button("${sc.icon} ${sc.label}", "toggle") { scenario = sc.id }
                scenarioGroup.add(b)
                if (sc.id == scenario) b.isChecked = true
                val row = Table()
                row.add(b).width(PACE_BUTTON_WIDTH * SCENARIO_WIDTH).left()
                row.add(ui.label(sc.description, "small", wrap = true)).width(CONTENT_WIDTH - PACE_BUTTON_WIDTH * SCENARIO_WIDTH - 10f).padLeft(10f)
                content.add(row).padBottom(2f).row()
            }
        }

        content.add(ui.label("Rythme de la partie", "title")).padTop(12f).row()
        content.add(ui.label("Le temps s'écoule même lorsque l'application est fermée. Ce choix est définitif.", "muted", wrap = true)).width(CONTENT_WIDTH).row()
        val paceGroup = ButtonGroup<TextButton>()
        db.config.paces.forEach { p ->
            val b = ui.button(p.label, "toggle") { pace = p.id }
            paceGroup.add(b)
            if (p.id == pace) b.isChecked = true
            val row = Table()
            row.add(b).width(PACE_BUTTON_WIDTH).left()
            row.add(ui.label(p.description, "small", wrap = true)).width(CONTENT_WIDTH - PACE_BUTTON_WIDTH - 10f).padLeft(10f)
            content.add(row).row()
        }

        content.add(ui.label("Votre président(e)", "title")).padTop(12f).row()
        val genderGroup = ButtonGroup<TextButton>()
        val gender = Table()
        listOf("Président" to false, "Présidente" to true).forEach { (label, f) ->
            val b = ui.button(label, "toggle") { female = f; regenerateName(); randomFace(); refreshPreview() }
            genderGroup.add(b)
            if (f == female) b.isChecked = true
            gender.add(b).padRight(6f)
        }
        content.add(gender).row()
        val names = Table()
        names.add(ui.label("Prénom", "muted")).padRight(6f)
        names.add(first).width(NAME_WIDTH).padRight(12f)
        names.add(ui.label("Nom", "muted")).padRight(6f)
        names.add(last).width(NAME_WIDTH).padRight(12f)
        names.add(ui.button("Autre nom") { seed++; regenerateName() })
        content.add(names).row()
        personalization(content)

        content.add(ui.label("Famille politique", "title")).padTop(12f).row()
        content.add(ui.label("Elle détermine votre gouvernement, vos alliés à l'Assemblée et les électorats qui vous soutiennent. " +
            "Les extrêmes mobilisent un noyau fidèle mais peinent à rassembler au second tour.", "muted", wrap = true)).width(CONTENT_WIDTH).row()
        val families = db.country(countryId).elections?.families.orEmpty()
        val familyGroup = ButtonGroup<TextButton>()
        val familyGrid = Table().apply { defaults().padRight(6f).padBottom(4f).left() }
        val familyInfo = ui.label("", "muted", wrap = true)
        families.forEachIndexed { i, f ->
            val b = ui.button(f.name, "toggle") {
                leaning = f.economicPosition
                social = f.socialPosition
                familyInfo.setText(describe(f.economicPosition, f.socialPosition))
            }
            familyGroup.add(b)
            familyGrid.add(b).fillX()
            if (i % FAMILY_COLUMNS == FAMILY_COLUMNS - 1) familyGrid.row()
        }
        // Par défaut : le centre (ou la première famille si le pays n'en a pas).
        families.indexOfFirst { it.id == DEFAULT_FAMILY }.coerceAtLeast(0).takeIf { families.isNotEmpty() }?.let { i ->
            familyGroup.buttons[i].isChecked = true
            leaning = families[i].economicPosition
            social = families[i].socialPosition
            familyInfo.setText(describe(families[i].economicPosition, families[i].socialPosition))
        }
        content.add(familyGrid).row()
        content.add(familyInfo).width(CONTENT_WIDTH).row()
        val promiseFile = db.country(countryId).promises
        if (promiseFile != null) {
            content.add(ui.label("Vos promesses de campagne", "title")).padTop(12f).row()
            content.add(ui.label("Choisissez jusqu'à ${promiseFile.maxPromises} engagements : les électeurs les jugeront à la prochaine élection.", "muted", wrap = true)).width(CONTENT_WIDTH).row()
            val grid = Table().apply { defaults().padRight(6f).padBottom(4f).left() }
            promiseFile.promises.forEachIndexed { i, p ->
                val b = ui.button(p.label, "toggle") {}
                b.onClick {
                    if (b.isChecked && promises.size >= promiseFile.maxPromises) b.isChecked = false
                    if (b.isChecked) promises += p.id else promises -= p.id
                }
                grid.add(b)
                if (i % 2 == 1) grid.row()
            }
            content.add(grid).row()
        }
        content.add(ui.button("Prendre ses fonctions", "accent") {
            onStart(NewGameOptions(pace, seed, nowMillis(), first.text, last.text, female, leaning, socialLeaning = social, promises = promises.toList(), scenarioId = scenario?.takeIf { it != STANDARD }, countryId = countryId,
                presidentAge = age, careerId = career, presidentTraits = traits.toMap(), appearance = appearance))
        }).padTop(16f).row()
        content.add(ui.label("Pays, institutions et données de départ inspirés du monde réel (${db.snapshot.label}). Personnages fictifs : les noms des dirigeants s'inspirent de personnalités réelles sans les reprendre.", "muted", wrap = true)).width(CONTENT_WIDTH).row()

        val root = Table().apply { setFillParent(true) }
        root.add(ScrollPane(content, ui.s).tolerant()).grow()
        stage.addActor(root)
    }

    /** Visage, âge, parcours et personnalité du président ou de la présidente. */
    private fun personalization(content: Table) {
        // Visage : aperçu et réglages élément par élément.
        val face = Table()
        face.add(preview).size(PORTRAIT).padRight(12f).top()
        val controls = Table().apply { defaults().left().padBottom(2f) }
        fun cycler(label: String, key: String, get: () -> Int, set: (Int) -> Unit) {
            val n = fr.president.game.ui.widgets.Portraits.CHOICES[key] ?: 1
            val row = Table()
            row.add(ui.label(label, "muted")).width(LABEL_WIDTH).left()
            row.add(ui.button("◀", "flat") { set(Math.floorMod(get() - 1, n)); refreshPreview() })
            row.add(ui.button("▶", "flat") { set(Math.floorMod(get() + 1, n)); refreshPreview() })
            controls.add(row).row()
        }
        cycler("Teint", "skin", { appearance.skin }) { appearance = appearance.copy(skin = it) }
        cycler("Cheveux", "hair", { appearance.hair }) { appearance = appearance.copy(hair = it, grey = false) }
        cycler("Coiffure", "hairStyle", { appearance.hairStyle }) { appearance = appearance.copy(hairStyle = it) }
        cycler("Tenue", "suit", { appearance.suit }) { appearance = appearance.copy(suit = it) }
        cycler("Cravate / détail", "accent", { appearance.accent }) { appearance = appearance.copy(accent = it) }
        cycler("Fond", "background", { appearance.background }) { appearance = appearance.copy(background = it) }
        val toggles = Table().apply { defaults().padRight(4f) }
        toggles.add(ui.button("Lunettes", "toggle") { appearance = appearance.copy(glasses = !appearance.glasses); refreshPreview() }.also { it.isChecked = appearance.glasses })
        toggles.add(ui.button("Cheveux gris", "toggle") { appearance = appearance.copy(grey = !appearance.grey); refreshPreview() }.also { it.isChecked = appearance.grey })
        toggles.add(ui.button("Au hasard", "flat") { randomFace(); refreshPreview() })
        controls.add(toggles).row()
        face.add(controls).left().top()
        content.add(ui.label("Visage", "bold")).padTop(8f).row()
        content.add(face).left().row()

        // Âge.
        val ageRow = Table()
        ageRow.add(ui.label("Âge", "bold")).padRight(10f)
        ageRow.add(ui.button("◀", "flat") { age = (age - 1).coerceAtLeast(MIN_AGE); refreshPreview() })
        ageRow.add(ageLabel).width(70f).center()
        ageRow.add(ui.button("▶", "flat") { age = (age + 1).coerceAtMost(MAX_AGE); refreshPreview() })
        ageRow.add(ui.label("Plus jeune : énergie et image neuve ; plus âgé : expérience.", "muted", wrap = true)).width(CONTENT_WIDTH - 220f).padLeft(10f)
        content.add(ageRow).left().padTop(6f).row()

        // Parcours.
        val careers = db.country(countryId).careers
        if (careers.isNotEmpty()) {
            content.add(ui.label("Parcours avant l'élection", "bold")).padTop(8f).row()
            val info = ui.label("Chaque parcours change vos compétences, votre tempérament et vos soutiens de départ.", "muted", wrap = true)
            val group = ButtonGroup<TextButton>().apply { setMinCheckCount(0) }
            val grid = Table().apply { defaults().padRight(4f).padBottom(4f).fillX() }
            careers.forEachIndexed { i, c ->
                val b = ui.button(c.label, "toggle") { career = c.id; info.setText(c.description) }
                group.add(b)
                grid.add(b)
                if (i % CAREER_COLUMNS == CAREER_COLUMNS - 1) grid.row()
            }
            content.add(grid).left().row()
            content.add(info).width(CONTENT_WIDTH).row()
        }

        // Personnalité : quatre axes, au choix ou laissés au hasard.
        content.add(ui.label("Personnalité", "bold")).padTop(8f).row()
        content.add(ui.label("Elle change la façon dont ministres, élus et dirigeants étrangers vous perçoivent.", "muted", wrap = true)).width(CONTENT_WIDTH).row()
        PERSONALITY.forEach { axis ->
            val row = Table().apply { defaults().padRight(4f) }
            row.add(ui.label(axis.label, "muted")).width(LABEL_WIDTH).left()
            val group = ButtonGroup<TextButton>().apply { setMinCheckCount(0); setMaxCheckCount(1) }
            axis.options.forEach { (label, values) ->
                val b = ui.button(label, "toggle") { values.forEach { (t, v) -> traits[t] = v } }
                group.add(b)
                row.add(b)
            }
            content.add(row).left().padBottom(2f).row()
        }
        randomFace()
        refreshPreview()
    }

    private fun randomFace() {
        val r = GameRandom(seed)
        val n = fr.president.game.ui.widgets.Portraits.CHOICES
        appearance = fr.president.engine.politics.Appearance(
            skin = r.nextInt(n.getValue("skin")), hair = r.nextInt(n.getValue("hair")),
            hairStyle = if (female) (if (r.chance(0.6)) 1 else if (r.chance(0.5)) 3 else 0) else (if (r.chance(0.2)) 2 else 0),
            glasses = r.chance(0.3), suit = r.nextInt(n.getValue("suit")), accent = r.nextInt(n.getValue("accent")),
            background = r.nextInt(n.getValue("background")), grey = age > GREY_AGE && r.chance(0.5),
        )
    }

    private fun refreshPreview() {
        ageLabel.setText("$age ans")
        val country = countryId
        val c = CharacterGenerator(db).generate("apercu", CharacterSpec(country, CharacterRole.PRESIDENT, null, PREVIEW_YEAR, female = female), GameRandom(seed))
        c.female = female
        c.birthYear = PREVIEW_YEAR - age
        c.appearance = appearance
        preview.clearChildren()
        preview.add(ui.portraits.image(c, PORTRAIT)).size(PORTRAIT)
    }

    private class Axis(val label: String, val options: List<Pair<String, Map<String, Double>>>)

    private fun regenerateName() {
        val country = countryId
        val c = CharacterGenerator(db).generate("preview", CharacterSpec(country, CharacterRole.PRESIDENT, null, PREVIEW_YEAR, female = female), GameRandom(seed))
        first.text = c.firstName
        last.text = c.lastName
    }

    override fun show() { Gdx.input.inputProcessor = stage }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(Theme.background.r, Theme.background.g, Theme.background.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)
    override fun dispose() = stage.dispose()

    private companion object {
        const val DEFAULT_AGE = 52
        const val MIN_AGE = 35
        const val MAX_AGE = 80
        const val GREY_AGE = 55
        const val PORTRAIT = 110f
        const val LABEL_WIDTH = 120f
        const val CAREER_COLUMNS = 3
        val PERSONALITY = listOf(
            Axis("Charisme", listOf("Charismatique" to mapOf("charisma" to 0.85), "Réservé(e)" to mapOf("charisma" to 0.3))),
            Axis("Fermeté", listOf("Ferme" to mapOf("toughness" to 0.85, "aggressiveness" to 0.6), "Conciliant(e)" to mapOf("toughness" to 0.3, "aggressiveness" to 0.25))),
            Axis("Éthique", listOf("Intègre" to mapOf("integrity" to 0.9, "pragmatism" to 0.4), "Pragmatique" to mapOf("integrity" to 0.5, "pragmatism" to 0.9))),
            Axis("Prudence", listOf("Prudent(e)" to mapOf("caution" to 0.85), "Audacieux(se)" to mapOf("caution" to 0.25))),
            Axis("Ouverture", listOf("Européen(ne) convaincu(e)" to mapOf("openness" to 0.85, "nationalism" to 0.25), "Souverainiste" to mapOf("openness" to 0.35, "nationalism" to 0.8))),
        )
        const val FRANCE = "FRA"
        const val INCUMBENT_FIRST = "Emmanuel"
        const val INCUMBENT_LAST = "Macrin"
        const val SCENARIO_WIDTH = 2.1f
        const val STANDARD = "standard"
        const val CONTENT_WIDTH = 640f
        const val PACE_BUTTON_WIDTH = 120f
        const val NAME_WIDTH = 160f
        const val PREVIEW_YEAR = 2026
        const val FAMILY_COLUMNS = 3
        const val DEFAULT_FAMILY = "centre"
        const val NATIONALIST_SOCIAL = 0.6

        /** Résumé lisible d'une position sur les deux axes. */
        fun describe(economic: Double, social: Double): String {
            val eco = when {
                economic <= -0.65 -> "Économie : rupture avec le marché, nationalisations, forte redistribution."
                economic <= -0.2 -> "Économie : État protecteur, services publics, redistribution."
                social >= NATIONALIST_SOCIAL && economic < 0.35 -> "Économie : protectionnisme, préférence nationale, pouvoir d'achat."
                economic < 0.35 -> "Économie : libérale et pragmatique, équilibre des comptes."
                else -> "Économie : baisse des impôts et des dépenses, entreprises d'abord."
            }
            val soc = when {
                social <= -0.5 -> "Société : très progressiste, priorité au climat et aux libertés."
                social <= 0.0 -> "Société : progressiste, attachée aux libertés publiques."
                social < NATIONALIST_SOCIAL -> "Société : ordre, autorité, tradition."
                else -> "Société : souverainiste, immigration et sécurité au premier plan."
            }
            return "$eco $soc"
        }
    }
}
