package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.engine.session.GameSession
import fr.president.engine.simulation.Simulator
import fr.president.game.app.GameController
import fr.president.game.map.GeoProjection
import fr.president.game.map.Lod
import fr.president.game.map.LodPolicy
import fr.president.game.map.MapCameraController
import fr.president.game.map.MapData
import fr.president.game.map.MapPicker
import fr.president.game.map.MapRenderer
import fr.president.game.map.MapSelection
import fr.president.game.map.OverlayRenderer
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Ui
import fr.president.game.ui.hud.ActionBar
import fr.president.game.ui.hud.LayerBar
import fr.president.game.ui.hud.Toasts
import fr.president.game.ui.hud.TopBar
import fr.president.game.ui.panels.DiplomacyPanel
import fr.president.game.ui.panels.EconomyPanel
import fr.president.game.ui.panels.ElectionPanel
import fr.president.game.ui.panels.GovernmentPanel
import fr.president.game.ui.panels.InboxPanel
import fr.president.game.ui.panels.Navigator
import fr.president.game.ui.panels.NotificationsPanel
import fr.president.game.ui.panels.Panel
import fr.president.game.ui.panels.PanelId
import fr.president.game.ui.panels.SelectionPanel
import fr.president.game.ui.panels.SettingsPanel

/**
 * Écran principal : la carte au centre, des panneaux contextuels autour.
 * 80 % des actions territoriales partent d'un toucher sur la carte.
 */
class MainScreen(
    private val controller: GameController,
    private val ui: Ui,
    private val mapData: MapData,
    private val uiScale: Float,
    private val onGameOver: () -> Unit,
    private val onAbandon: () -> Unit = {},
) : ScreenAdapter(), Navigator, HasStage {
    override val session: GameSession get() = controller.session
    override val platform get() = controller.platform
    private val playerId = session.state.player.countryId
    private val camera = OrthographicCamera()
    override val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })
    private val mapRenderer = MapRenderer(mapData, playerId)
    private val advisor by lazy { fr.president.game.ui.hud.AdvisorCard(ui, session, this) }
    private val overlay = OverlayRenderer(mapData, ui.skin.font("bodyBold"), ui.skin.font("small"), uiScale)
    private val picker = MapPicker(mapData, session.db, playerId)
    private val cameraController = MapCameraController(camera) { x, y -> onMapTap(x, y) }

    private var layer = ThematicLayer.ADMIN
    private var selection: MapSelection? = null
    private var lod = Lod.FRANCE
    private var sinceRefresh = 0f

    private val topBar = TopBar(ui, session) { open(it) }
    private val actionBar = ActionBar(ui, session) { open(it) }
    private val toasts = Toasts(ui) { focusOn(it) }
    private val legend = fr.president.game.ui.hud.Legend(ui)
    private val panelSlot = Container<Table>().fill()
    private var currentPanel: Panel? = null
    private var targeting: Pair<String, fr.president.engine.military.UnitOrder>? = null
    private val targetingBanner = Table()


    private val selectionPanel = SelectionPanel(ui, this) { closePanel() }
    private val diplomacyPanel = DiplomacyPanel(ui, this) { closePanel() }
    private val inboxPanel = InboxPanel(ui, this, { closePanel() }) { proposalId ->
        diplomacyPanel.negotiate(proposalId)
        Gdx.app.postRunnable { open(PanelId.DIPLOMACY) }
    }
    private val panels: Map<PanelId, Panel> = mapOf(
        PanelId.SELECTION to selectionPanel,
        PanelId.GOVERNMENT to GovernmentPanel(ui, this) { closePanel() },
        PanelId.ECONOMY to EconomyPanel(ui, this) { closePanel() },
        PanelId.DIPLOMACY to diplomacyPanel,
        PanelId.INBOX to inboxPanel,
        PanelId.NOTIFICATIONS to NotificationsPanel(ui, this) { closePanel() },
        PanelId.ELECTIONS to ElectionPanel(ui, this) { closePanel() },
        PanelId.ARMY to fr.president.game.ui.panels.ArmyPanel(ui, this) { closePanel() },
        PanelId.SETTINGS to SettingsPanel(ui, this, { closePanel() }, onAbandon),
        PanelId.HELP to fr.president.game.ui.panels.HelpPanel(ui, this) { closePanel() },
    )

    private var cameraReady = false

    init {
        buildLayout()
        mapRenderer.zoneLookup = session.db.zones
        mapRenderer.holderHostile = { session.military.geo.atWar(playerId, it) }
    }

    private fun buildLayout() {
        val root = Table().apply { setFillParent(true) }
        root.add(topBar.root).growX().colspan(3).row()
        val layers = LayerBar(ui, layer) { layer = it }
        val left = Table()
        left.add(layers.root).top().left().growX().row()
        left.add(advisor.root).top().left().growX().padTop(6f).row()
        left.add().growY().row()
        left.add(legend.root).left().bottom().padTop(6f)
        root.add(left).top().left().growY().pad(6f)
        root.add().expand()
        root.add(panelSlot).width(com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth(PANEL_SHARE, root)).maxWidth(PANEL_WIDTH).minHeight(0f).prefHeight(0f).growY().pad(6f).row()
        val actions = ScrollPane(actionBar.root).apply { setScrollingDisabled(false, true) }
        root.add(actions).colspan(3).center().padBottom(6f)
        stage.addActor(root)
        val overlayTable = Table().apply { setFillParent(true); top().padTop(TOAST_TOP) }
        overlayTable.add(targetingBanner).padBottom(6f).row()
        targetingBanner.isVisible = false
        overlayTable.add(toasts.root)
        overlayTable.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
        stage.addActor(overlayTable)
        refresh()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, cameraController.gestureDetector, cameraController.scrollProcessor)
    }

    override fun render(delta: Float) {
        controller.update(delta)
        if (session.isGameOver) {
            onGameOver()
            return
        }
        while (controller.freshNotifications.isNotEmpty()) toasts.show(controller.freshNotifications.removeFirst())
        camera.update()
        lod = LodPolicy.of(camera.viewportWidth * camera.zoom)
        legend.update(layer)
        mapRenderer.render(camera, session.state, layer, lod, selection)
        overlay.render(camera, session, layer, lod, delta, selectedMapId())
        sinceRefresh += delta
        if (sinceRefresh >= REFRESH_SECONDS && !Gdx.input.isTouched) refresh()
        stage.act(delta)
        stage.draw()
    }

    private fun selectedMapId(): String? = when (val s = selection) {
        is MapSelection.City -> s.id
        is MapSelection.Infrastructure -> s.id
        is MapSelection.Base -> s.id
        is MapSelection.Unit -> s.id
        else -> null
    }

    override fun startTargeting(unitId: String, order: fr.president.engine.military.UnitOrder) {
        targeting = unitId to order
        targetingBanner.clearChildren()
        targetingBanner.setBackground(ui.skin.fill(fr.president.game.ui.Theme.accentDark))
        targetingBanner.pad(8f)
        targetingBanner.add(ui.label("${order.label} : touchez la zone cible sur la carte", "bold")).padRight(10f)
        targetingBanner.add(ui.button("Annuler") { stopTargeting() })
        targetingBanner.isVisible = true
    }

    private fun stopTargeting() {
        targeting = null
        targetingBanner.isVisible = false
    }

    override fun prepareProposal(country: String, clauseType: String, params: Map<String, Double>) {
        diplomacyPanel.prefill(country, clauseType, params)
        open(PanelId.DIPLOMACY)
    }

    private fun onMapTap(x: Float, y: Float) {
        targeting?.let { (unitId, order) ->
            val (lon, lat) = picker.lonLat(camera, x, y)
            val zone = session.military.zoneAt(lon, lat)
            val r = session.military.order(unitId, order, zone)
            selectionPanel.unitSheet.message = if (r is fr.president.engine.military.OrderService.Outcome.Refused) r.reason else "Ordre transmis : ${order.label.lowercase()}."
            stopTargeting()
            select(MapSelection.Unit(unitId))
            return
        }
        val picked = picker.pick(camera, overlay, x, y, uiScale, lod) ?: return
        if (picked is MapSelection.Country && picked.id == playerId) {
            cameraController.focus(GeoProjection.x(FRANCE_LON), GeoProjection.y(FRANCE_LAT), FRANCE_VIEW_WIDTH)
            return
        }
        select(picked)
    }

    override fun select(selection: MapSelection) {
        this.selection = selection
        selectionPanel.selection = selection
        open(PanelId.SELECTION)
    }

    override fun open(panel: PanelId, argument: String?) {
        val p = panels.getValue(panel)
        if (panel == PanelId.DIPLOMACY && argument != null) diplomacyPanel.country = argument
        else if (argument != null) p.applyArgument(argument)
        val changed = currentPanel !== p
        currentPanel = p
        panelSlot.actor = p.root
        p.refresh()
        // Apparition en fondu quand on change de panneau (pas lors d'un simple rafraîchissement).
        if (changed) {
            p.root.clearActions()
            p.root.color.a = 0f
            p.root.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(PANEL_FADE_SECONDS, com.badlogic.gdx.math.Interpolation.fade))
        }
    }

    private fun closePanel() {
        currentPanel = null
        panelSlot.actor = null
        selection = null
    }

    override fun focusOn(mapId: String) {
        session.state.military.units[mapId]?.let { u ->
            val z = session.db.zones.zone(u.zoneId)
            cameraController.focus(GeoProjection.x(z.lon), GeoProjection.y(z.lat), UNIT_VIEW_WIDTH)
            select(MapSelection.Unit(u.id))
            return
        }
        session.db.zones.zones[mapId]?.let { z ->
            cameraController.focus(GeoProjection.x(z.lon), GeoProjection.y(z.lat), DEPARTMENT_VIEW_WIDTH)
            return
        }
        val (x, y) = overlay.locate(mapId) ?: return
        val width = when {
            mapData.countriesById.containsKey(mapId) -> COUNTRY_VIEW_WIDTH
            mapData.departmentsById.containsKey(mapId) -> DEPARTMENT_VIEW_WIDTH
            else -> LOCAL_VIEW_WIDTH
        }
        cameraController.focus(x, y, width)
        val target = when {
            mapData.departmentsById.containsKey(mapId) -> MapSelection.Department(mapId)
            mapData.countriesById.containsKey(mapId) -> MapSelection.Country(mapId)
            session.state.territory.cities.containsKey(mapId) -> MapSelection.City(mapId)
            session.state.infrastructure.containsKey(mapId) -> MapSelection.Infrastructure(mapId)
            session.context.catalog.bases.containsKey(mapId) -> MapSelection.Base(mapId)
            else -> null
        }
        target?.let { select(it) }
    }

    override fun refresh() {
        sinceRefresh = 0f
        val player = session.state.player.countryId
        val relations = fr.president.engine.diplomacy.RelationCalculator(session.context)
        mapRenderer.relations = session.state.countries.keys.filter { it != player }.associateWith { relations.score(it, player) }
        mapRenderer.enemies = fr.president.engine.military.Geopolitics(session.context).enemiesOf(player).toSet()
        topBar.refresh()
        actionBar.refresh()
        advisor.refresh()
        currentPanel?.refresh()
    }

    /** Message affiché au retour du joueur après une absence. */
    fun showAbsence(report: Simulator.Report) {
        if (report.days < MIN_ABSENCE_DAYS) return
        val pending = session.state.inbox.messages.count { it.awaitingAnswer }
        toasts.show(fr.president.engine.notifications.GameNotification(
            -1, fr.president.engine.notifications.NotificationCategory.POLITICS, fr.president.engine.notifications.Urgency.IMPORTANT,
            "Pendant votre absence : %.1f jours se sont écoulés".format(report.days),
            "${report.notifications} alerte(s), $pending décision(s) en attente.", session.state.time,
        ))
    }

    /** Accès pour l'automatisation de développement (captures d'écran). */
    fun devZoom(lon: Double, lat: Double, width: Float) = cameraController.focus(GeoProjection.x(lon), GeoProjection.y(lat), width)
    fun devLayer(l: ThematicLayer) { layer = l }
    fun devOpenFirstPendingMessage() {
        inboxPanel.openMessage = session.state.inbox.messages.lastOrNull { it.awaitingAnswer }?.id
        refresh()
    }

    override fun resize(width: Int, height: Int) {
        val x = camera.position.x
        val y = camera.position.y
        val visible = camera.viewportWidth * camera.zoom
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
        if (cameraReady) {
            cameraController.focus(x, y, visible)
        } else {
            cameraController.focus(GeoProjection.x(FRANCE_LON), GeoProjection.y(FRANCE_LAT), FRANCE_VIEW_WIDTH)
            cameraReady = true
        }
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
        mapRenderer.dispose()
        overlay.dispose()
    }

    private companion object {
        const val PANEL_FADE_SECONDS = 0.18f
        const val FRANCE_LON = 2.4
        const val FRANCE_LAT = 46.6
        const val FRANCE_VIEW_WIDTH = 1500f
        const val COUNTRY_VIEW_WIDTH = 2500f
        const val DEPARTMENT_VIEW_WIDTH = 350f
        const val LOCAL_VIEW_WIDTH = 160f
        const val UNIT_VIEW_WIDTH = 600f
        const val PANEL_WIDTH = 420f
        const val PANEL_SHARE = 0.48f
        const val REFRESH_SECONDS = 3f
        const val TOAST_TOP = 70f
        const val MIN_ABSENCE_DAYS = 0.5
    }
}
