package fr.president.desktop

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import fr.president.engine.setup.NewGameOptions
import fr.president.engine.simulation.Simulator
import fr.president.game.PresidentGame
import fr.president.game.map.MapSelection
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.panels.PanelId

/**
 * Automatisation de développement : exécute un script (captures d'écran, zoom, sélection)
 * pour vérifier le rendu sans interaction. Exemple :
 * new;wait:30;shot:/tmp/a.png;zoom:3.9,43.6,300;layer:OPINION;select:dept:34;wait:10;shot:/tmp/b.png;quit
 */
class DevScriptDriver(private val game: PresidentGame, script: String) : ApplicationListener by game {
    private val steps = ArrayDeque(script.split(';').map { it.trim() }.filter { it.isNotEmpty() })
    private var waitFrames = INITIAL_WAIT

    override fun render() {
        game.render()
        if (waitFrames-- > 0) return
        val step = steps.removeFirstOrNull() ?: return
        val (cmd, arg) = step.substringBefore(':') to step.substringAfter(':', "")
        when (cmd) {
            "new" -> game.newGame(NewGameOptions("normal", SEED, System.currentTimeMillis(), countryId = arg.ifBlank { null }))
            "wait" -> waitFrames = arg.toInt()
            "shot" -> screenshot(arg)
            "zoom" -> arg.split(',').let { (lon, lat, w) -> game.mainScreen?.devZoom(lon.toDouble(), lat.toDouble(), w.toFloat()) }
            "tap" -> arg.split(',').let { (lon, lat) -> game.mainScreen?.devTap(lon.toDouble(), lat.toDouble()) }
            "layer" -> game.mainScreen?.devLayer(ThematicLayer.valueOf(arg))
            "click" -> click(arg)
            "open" -> arg.split('/').let { parts -> game.mainScreen?.open(PanelId.valueOf(parts[0]), parts.getOrNull(1)) }
            "select" -> select(arg)
            // Débogage uniquement : avance le monde sans attendre le temps réel.
            "skip" -> game.controller?.session?.context?.let { ctx -> Simulator(ctx).advanceTo(ctx.now.plusDays(arg.toLong())) }
            // Simule une absence : avance le monde puis affiche le bilan de retour.
            "away" -> game.controller?.session?.context?.let { ctx ->
                val report = Simulator(ctx).advanceTo(ctx.now.plusDays(arg.toLong()))
                game.mainScreen?.showAbsence(report)
            }
            "inbox" -> game.mainScreen?.let { screen -> screen.open(PanelId.INBOX); screen.devOpenFirstPendingMessage() }
            "war" -> game.controller?.session?.context?.let { ctx -> arg.split(',').let { (a, b) -> fr.president.engine.military.WarService(ctx).declare(a, b, "Script de test") } }
            "order" -> game.controller?.session?.let { s -> arg.split(',').let { (u, o, lon, lat) -> s.military.order(u, fr.president.engine.military.UnitOrder.valueOf(o), s.military.zoneAt(lon.toDouble(), lat.toDouble())) } }
            "event" -> game.controller?.session?.context?.let { ctx ->
                val (id, scopeId) = arg.split(',').let { it[0] to it.getOrNull(1) }
                val def = ctx.db.event(id)
                fr.president.engine.events.EventLauncher(ctx).launch(def, fr.president.engine.events.ScopeRef(def.scope, scopeId))
            }
            "measure" -> game.controller?.session?.let { s -> arg.split(',').let { s.measures.activate(it[0], it.getOrNull(1)) } }
            "quit" -> Gdx.app.exit()
        }
        if (cmd != "wait") waitFrames = STEP_FRAMES
    }

    private fun select(arg: String) {
        val (type, id) = arg.split(':', limit = 2)
        val selection = when (type) {
            "dept" -> MapSelection.Department(id)
            "region" -> MapSelection.Region(id)
            "city" -> MapSelection.City(id)
            "infra" -> MapSelection.Infrastructure(id)
            "base" -> MapSelection.Base(id)
            "unit" -> MapSelection.Unit(id)
            "fcity" -> MapSelection.ForeignCity(id)
            else -> MapSelection.Country(id)
        }
        game.mainScreen?.select(selection)
    }

    private fun screenshot(path: String) {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, -1, true)
        pixmap.dispose()
        println("Capture : $path")
    }

    private companion object {
        const val INITIAL_WAIT = 20
        const val STEP_FRAMES = 3
        const val SEED = 2026L
    }

    /** Simule un vrai toucher (appui puis relâchement) sur le bouton portant ce texte. */
    private fun click(text: String) {
        val stage = (game.screen as? fr.president.game.screens.HasStage)?.stage ?: run { println("click : écran sans scène"); return }
        val button = findButton(stage.root, text) ?: run { println("click : bouton « $text » introuvable"); return }
        // Comme un joueur : fait défiler jusqu'au bouton s'il est hors de l'écran.
        var parent = button.parent
        while (parent != null) {
            if (parent is com.badlogic.gdx.scenes.scene2d.ui.ScrollPane) {
                val pos = button.localToActorCoordinates(parent.actor, com.badlogic.gdx.math.Vector2())
                parent.scrollTo(pos.x, pos.y, button.width, button.height, true, true)
                parent.updateVisualScroll()
                parent.validate()
            }
            parent = parent.parent
        }
        val center = button.localToStageCoordinates(com.badlogic.gdx.math.Vector2(button.width / 2, button.height / 2))
        val screen = stage.stageToScreenCoordinates(center)
        stage.touchDown(screen.x.toInt(), screen.y.toInt(), 0, 0)
        stage.touchUp(screen.x.toInt(), screen.y.toInt(), 0, 0)
        println("click : « $text »")
    }

    private fun findButton(actor: com.badlogic.gdx.scenes.scene2d.Actor, text: String): com.badlogic.gdx.scenes.scene2d.ui.TextButton? {
        if (actor is com.badlogic.gdx.scenes.scene2d.ui.TextButton && actor.text.toString().contains(text)) return actor
        if (actor is com.badlogic.gdx.scenes.scene2d.Group) actor.children.forEach { c -> findButton(c, text)?.let { return it } }
        return null
    }
}
