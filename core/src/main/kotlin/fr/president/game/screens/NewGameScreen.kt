package fr.president.game.screens

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

    init {
        regenerateName()
        val content = Table().apply { pad(24f); defaults().left().padBottom(8f) }
        content.add(ui.label("PRÉSIDENT", "headline")).row()
        content.add(ui.label("Vous venez d'être élu(e) à la tête de la ${db.country(db.snapshot.playableCountries.first()).definition.name}. Le monde ne s'arrêtera pas pour vous attendre.", "default", wrap = true)).width(CONTENT_WIDTH).row()
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
            val b = ui.button(label, "toggle") { female = f; regenerateName() }
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

        content.add(ui.label("Famille politique", "title")).padTop(12f).row()
        content.add(ui.label("Elle détermine votre gouvernement, vos alliés à l'Assemblée et les électorats qui vous soutiennent. " +
            "Les extrêmes mobilisent un noyau fidèle mais peinent à rassembler au second tour.", "muted", wrap = true)).width(CONTENT_WIDTH).row()
        val families = db.country(db.snapshot.playableCountries.first()).elections?.families.orEmpty()
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
        val promiseFile = db.country(db.snapshot.playableCountries.first()).promises
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
            onStart(NewGameOptions(pace, seed, nowMillis(), first.text, last.text, female, leaning, socialLeaning = social, promises = promises.toList(), scenarioId = scenario?.takeIf { it != STANDARD }))
        }).padTop(16f).row()
        content.add(ui.label("Pays, institutions et données de départ inspirés du monde réel (${db.snapshot.label}). Tous les personnages sont fictifs.", "muted", wrap = true)).width(CONTENT_WIDTH).row()

        val root = Table().apply { setFillParent(true) }
        root.add(ScrollPane(content, ui.s)).grow()
        stage.addActor(root)
    }

    private fun regenerateName() {
        val country = db.snapshot.playableCountries.first()
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
