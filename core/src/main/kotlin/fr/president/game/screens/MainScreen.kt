package fr.president.game.screens

import fr.president.game.ui.tolerant
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
    private val onDisplayChange: () -> Unit = {},
    private val onAbandon: () -> Unit = {},
) : ScreenAdapter(), Navigator, HasStage, fr.president.game.ui.hud.TourHost {
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

    override var layer = ThematicLayer.ADMIN
        private set
    override var selection: MapSelection? = null
        private set
    override var openPanel: PanelId? = null
        private set
    private val hints = fr.president.game.ui.Hints(ui).also { ui.hints = it }
    private val tour by lazy { fr.president.game.ui.hud.GuidedTour(ui, session, this) }
    val academy by lazy { fr.president.game.ui.hud.AcademyCoach(ui, session, this, this) }
    private var lod = Lod.FRANCE
    private var sinceRefresh = 0f
    /** Rafraîchissement automatique (et non suite à une action du joueur). */
    private var periodic = false

    private val topBar = TopBar(ui, session) { panel, arg -> open(panel, arg) }
    private val actionBar = ActionBar(ui, session) { open(it) }
    private val toasts = Toasts(ui) { focusOn(it) }
    private val legend = fr.president.game.ui.hud.Legend(ui)
    private val panelSlot = Container<Table>().fill()
    private var currentPanel: Panel? = null
    private var targeting: Pair<String, fr.president.engine.military.UnitOrder>? = null
    private val targetingBanner = Table()
    private var pendingInspect: MapSelection? = null
    private val quickOrders by lazy {
        fr.president.game.ui.hud.QuickOrders(ui, session, { unitId, msg ->
            selectionPanel.unitSheet.message = msg
            select(MapSelection.Unit(unitId))
        }) { pendingInspect?.let { select(it) } }
    }
    private val briefing = fr.president.game.ui.hud.BriefingDialog(ui) { open(PanelId.INBOX) }


    private val selectionPanel = SelectionPanel(ui, this) { closePanel() }
    private val diplomacyPanel = DiplomacyPanel(ui, this) { closePanel() }
    private val inboxPanel = InboxPanel(ui, this, { closePanel() }) { proposalId ->
        diplomacyPanel.negotiate(proposalId)
        Gdx.app.postRunnable { open(PanelId.DIPLOMACY) }
    }
    private val panels: Map<PanelId, Panel> = mapOf(
        PanelId.SELECTION to selectionPanel,
        PanelId.MENU to fr.president.game.ui.panels.MenuPanel(ui, this) { closePanel() },
        PanelId.DECISIONS to fr.president.game.ui.panels.DecisionPanel(ui, this) { closePanel() },
        PanelId.STATS to fr.president.game.ui.panels.StatsPanel(ui, this) { closePanel() },
        PanelId.PRESS to fr.president.game.ui.panels.PressPanel(ui, this) { closePanel() },
        PanelId.CRISIS to fr.president.game.ui.panels.CrisisPanel(ui, this) { closePanel() },
        PanelId.EU to fr.president.game.ui.panels.EuPanel(ui, this) { closePanel() },
        PanelId.AGENDA to fr.president.game.ui.panels.AgendaPanel(ui, this) { closePanel() },
        PanelId.ACADEMY to fr.president.game.ui.panels.AcademyPanel(ui, this, { academy }) { closePanel() },
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
    /** Bandeaux et consigne du tutoriel : en haut, ou en bas quand un panneau occupe l'écran du téléphone. */
    private lateinit var overlayTable: Table
    private var overlayAtBottom = false
    private val layers by lazy { LayerBar(ui, layer) { layer = it } }
    /** Accès direct à l'agenda de la semaine, à côté du choix de la carte. */
    private val agendaButton by lazy { ui.colorButton("◷", fr.president.game.ui.Theme.catStats) { open(PanelId.AGENDA) }.also { it.name = "agenda" } }

    init {
        buildLayout()
        mapRenderer.zoneLookup = session.db.zones
        mapRenderer.holderHostile = { session.military.geo.atWar(playerId, it) }
    }

    private fun buildLayout() {
        val root = Table().apply { setFillParent(true) }
        root.add(topBar.root).growX().colspan(2).row()
        layers.topOffset = { topBar.root.height + layers.root.height + 10f }
        val left = Table()
        val mapRow = Table()
        mapRow.add(layers.root).growX()
        mapRow.add(agendaButton).padLeft(4f)
        left.add(mapRow).top().left().growX().row()
        left.add(advisor.root).top().left().growX().padTop(6f).row()
        left.add().growY().row()
        left.add(legend.root).left().bottom().padTop(6f)
        root.add(left).top().left().growY().pad(6f)
        root.add().expand().row()
        val actions = ScrollPane(actionBar.root).apply { setScrollingDisabled(false, true) }.tolerant()
        root.add(actions).colspan(2).center().padBottom(6f)
        stage.addActor(root)
        // Les panneaux ont leur propre calque : à droite sur grand écran, plein écran sur téléphone
        // en portrait (la carte reste visible dès qu'on les ferme).
        val panelLayer = Table().apply { setFillParent(true); top().right() }
        panelLayer.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
        panelLayer.add(panelSlot)
            .width(dyn { panelWidth() })
            .height(dyn { (stage.height - topBar.root.height - actions.height - PANEL_MARGINS).coerceAtLeast(MIN_PANEL_HEIGHT) })
            .padTop(dyn { topBar.root.height + 6f }).padRight(6f)
        stage.addActor(panelLayer)
        overlayTable = Table().apply { setFillParent(true); top().padTop(TOAST_TOP) }
        overlayTable.add(tour.card).padBottom(6f).row()
        overlayTable.add(academy.card).padBottom(6f).row()
        overlayTable.add(targetingBanner).padBottom(6f).row()
        targetingBanner.isVisible = false
        overlayTable.add(toasts.root)
        overlayTable.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
        stage.addActor(overlayTable)
        stage.addActor(briefing.root)
        stage.addActor(tour.highlight)
        stage.addActor(academy.highlight)
        stage.addActor(quickOrders.root)
        stage.addActor(hints.bubble)
        // La grille des couches passe au-dessus de tout (tutoriel compris) quand elle est ouverte.
        stage.addActor(layers.popup)
        stage.addListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
            override fun keyDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, keycode: Int): Boolean = onBack(keycode)
        })
        refresh()
    }

    private fun dyn(f: () -> Float) = object : com.badlogic.gdx.scenes.scene2d.ui.Value() {
        override fun get(context: com.badlogic.gdx.scenes.scene2d.Actor?) = f()
    }

    /** Écran étroit (téléphone en portrait) : le panneau prend toute la largeur. */
    private fun panelWidth(): Float {
        val w = stage.width
        return if (w < NARROW) w - 12f else minOf(w * PANEL_SHARE, PANEL_WIDTH)
    }

    /** Retour (Android) ou Échap : ferme ce qui est ouvert, du plus récent au plus ancien. */
    private fun onBack(keycode: Int): Boolean {
        if (keycode != com.badlogic.gdx.Input.Keys.BACK && keycode != com.badlogic.gdx.Input.Keys.ESCAPE) return false
        when {
            layers.isOpen -> layers.close()
            quickOrders.root.isVisible -> quickOrders.hide()
            briefing.root.isVisible -> briefing.hide()
            targeting != null -> stopTargeting()
            currentPanel != null -> closePanel()
            else -> return false
        }
        return true
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, cameraController.gestureDetector, cameraController.scrollProcessor)
        Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, true)
    }

    override fun render(delta: Float) {
        fr.president.game.ui.MusicPlayer.update(delta)
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
        if (sinceRefresh >= REFRESH_SECONDS && !Gdx.input.isTouched) { periodic = true; refresh(); periodic = false }
        tour.update()
        academy.update()
        stage.act(delta)
        stage.draw()
    }

    private fun selectedMapId(): String? = when (val s = selection) {
        is MapSelection.City -> s.id
        is MapSelection.Infrastructure -> s.id
        is MapSelection.Base -> s.id
        is MapSelection.Unit -> s.id
        is MapSelection.ForeignCity -> s.id
        else -> null
    }

    override fun applyDisplaySettings() = onDisplayChange()

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
        quickOrders.hide()
        val picked = picker.pick(camera, overlay, x, y, uiScale, lod)
        // Une de nos unités est sélectionnée : toucher ailleurs propose directement des ordres.
        val current = selection
        val commanded = (current as? MapSelection.Unit)?.let { session.state.military.units[it.id] }
            ?.takeIf { it.countryId == playerId && !it.destroyed && session.db.unitType(it.type).domain != fr.president.engine.data.Domain.STRATEGIC }
        if (commanded != null) {
            val ownUnit = (picked as? MapSelection.Unit)?.let { session.state.military.units[it.id] }?.takeIf { it.countryId == playerId }
            if (ownUnit == null) {
                val (lon, lat) = picker.lonLat(camera, x, y)
                val zone = session.military.zoneAt(lon, lat)
                if (zone != null && zone != commanded.zoneId) {
                    pendingInspect = picked
                    val p = stage.screenToStageCoordinates(com.badlogic.gdx.math.Vector2(x, y))
                    quickOrders.show(commanded.id, zone, p.x, p.y, stage.width, stage.height)
                    return
                }
            }
        }
        picked ?: return
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
        openPanel = panel
        placeOverlay()
        panelSlot.actor = p.root
        p.refresh()
        // Apparition en fondu quand on change de panneau (pas lors d'un simple rafraîchissement).
        if (changed) {
            p.root.clearActions()
            p.root.color.a = 0f
            p.root.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(PANEL_FADE_SECONDS, com.badlogic.gdx.math.Interpolation.fade))
        }
    }

    override fun closePanels() = closePanel()

    private fun closePanel() {
        currentPanel = null
        openPanel = null
        panelSlot.actor = null
        selection = null
        placeOverlay()
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
            mapData.foreignCountry.containsKey(mapId) -> DEPARTMENT_VIEW_WIDTH * 2
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
            mapData.foreignCountry.containsKey(mapId) -> MapSelection.ForeignCity(mapId)
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
        placeOverlay()
        session.agenda.summary().let { a -> agendaButton.setText("◷ ${fmtDays(a.usedDays)}/${fmtDays(a.capacity)} j") }
        fr.president.game.ui.MusicPlayer.setMood(mood())
        currentPanel?.let { if (periodic) it.refreshIfIdle() else it.refresh() }
    }

    /** Bilan affiché au retour du joueur après une absence. */
    fun showAbsence(report: Simulator.Report) {
        if (report.days < MIN_ABSENCE_DAYS) return
        briefing.show(session.briefing.since(report.from), session.media.headline()?.headline)
    }

    /** Accès pour l'automatisation de développement (captures d'écran). */
    fun devZoom(lon: Double, lat: Double, width: Float) = cameraController.focus(GeoProjection.x(lon), GeoProjection.y(lat), width)
    fun devLayer(l: ThematicLayer) { layer = l }
    /** Simule un toucher sur la carte à une position géographique. */
    fun devTap(lon: Double, lat: Double) {
        val v = com.badlogic.gdx.math.Vector3(GeoProjection.x(lon), GeoProjection.y(lat), 0f)
        camera.project(v)
        onMapTap(v.x, camera.viewportHeight - v.y)
    }
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
        val narrow = stage.width < NARROW
        topBar.compact = narrow
        actionBar.setCompact(narrow)
        advisor.compact = narrow
        layers.compact = narrow
        refresh()
    }

    /** Sur téléphone, un panneau ouvert prend tout l'écran : la consigne du tutoriel passe en bas pour ne rien cacher. */
    private fun placeOverlay() {
        val bottom = currentPanel != null && stage.width < NARROW
        if (bottom == overlayAtBottom) return
        overlayAtBottom = bottom
        overlayTable.clearChildren()
        if (bottom) {
            overlayTable.bottom().padTop(0f).padBottom(BOTTOM_OVERLAY)
            overlayTable.add(toasts.root).padBottom(6f).row()
            overlayTable.add(targetingBanner).padBottom(6f).row()
            overlayTable.add(tour.card).row()
            overlayTable.add(academy.card)
        } else {
            overlayTable.top().padBottom(0f).padTop(TOAST_TOP)
            overlayTable.add(tour.card).padBottom(6f).row()
            overlayTable.add(academy.card).padBottom(6f).row()
            overlayTable.add(targetingBanner).padBottom(6f).row()
            overlayTable.add(toasts.root)
        }
    }

    private fun fmtDays(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.FRENCH, "%.1f", v)

    /** Ambiance musicale : tendue en guerre, en crise grave ou quand le pays gronde. */
    private fun mood(): fr.president.game.ui.MusicPlayer.Mood {
        val s = session.state
        val war = fr.president.engine.military.Geopolitics(session.context).enemiesOf(s.player.countryId).isNotEmpty()
        val emergency = s.measures.active.any { m -> session.measures.definitions.firstOrNull { it.id == m.id }?.emergency == true }
        val calm = !war && !emergency && s.opinion.nationalApproval >= TENSE_APPROVAL
        return if (calm) fr.president.game.ui.MusicPlayer.Mood.CALM else fr.president.game.ui.MusicPlayer.Mood.TENSE
    }

    override fun dispose() {
        fr.president.game.ui.MusicPlayer.stop()
        stage.dispose()
        mapRenderer.dispose()
        overlay.dispose()
    }

    private companion object {
        const val BOTTOM_OVERLAY = 90f
        const val TENSE_APPROVAL = 0.3
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
        const val NARROW = 700f
        const val PANEL_MARGINS = 24f
        const val MIN_PANEL_HEIGHT = 200f
        const val REFRESH_SECONDS = 3f
        const val TOAST_TOP = 70f
        const val MIN_ABSENCE_DAYS = 0.5
    }
}
