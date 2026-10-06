package fr.president.game.app

import com.badlogic.gdx.Gdx
import fr.president.engine.notifications.GameNotification
import fr.president.engine.save.SaveRepository
import fr.president.engine.session.GameSession
import fr.president.engine.simulation.Simulator
import fr.president.game.platform.PlatformServices

/**
 * Pilote une partie en cours côté application : avance le monde au rythme du temps réel,
 * sauvegarde automatiquement et relaie les notifications. Ne contient aucune règle de jeu.
 */
class GameController(
    val session: GameSession,
    private val saves: SaveRepository,
    val platform: PlatformServices,
) {
    private var sinceTick = 0f
    private var sinceSave = 0f
    private var lastSeenNotification: Long = session.state.notifications.feed.lastOrNull()?.id ?: 0L
    var foreground = true
        private set

    /** Notifications apparues depuis le dernier appel (affichées en bandeau). */
    val freshNotifications = ArrayDeque<GameNotification>()

    fun update(delta: Float) {
        sinceTick += delta
        sinceSave += delta
        if (sinceTick >= TICK_SECONDS) {
            sinceTick = 0f
            advance()
        }
        if (sinceSave >= AUTOSAVE_SECONDS) {
            sinceSave = 0f
            save()
        }
    }

    fun advance(): Simulator.Report {
        val report = session.advanceToNow()
        collectNotifications()
        return report
    }

    /** Heure réelle de la dernière sauvegarde écrite par ce contrôleur. */
    var lastSaveMillis: Long = 0L
        private set

    fun save() {
        try {
            val file = session.toSaveFile(GAME_VERSION)
            saves.write(file)
            saves.writeMeta(saves.activeSlot, meta(file.savedAtRealUtcMillis))
            lastSaveMillis = file.savedAtRealUtcMillis
            if (foreground) platform.onHeartbeat()
        } catch (e: Exception) {
            Gdx.app?.error(TAG, "Échec de la sauvegarde", e)
        }
    }

    /** Résumé affiché dans la liste des parties : président, date du jeu, popularité. */
    private fun meta(savedAt: Long): SaveRepository.SlotMeta {
        val s = session.state
        val president = s.characters[s.player.presidentId]?.fullName ?: "Président"
        val scenario = s.player.scenario?.let { id -> session.db.scenarios.firstOrNull { it.id == id }?.label }
        val detail = listOfNotNull(fr.president.engine.util.Formatting.date(s.time),
            "popularité ${fr.president.engine.util.Formatting.wholePercent(s.opinion.nationalApproval)}", scenario,
            s.player.gameOver?.let { "mandat terminé" }).joinToString(" · ")
        val title = if (saves.activeSlot == "academie") "Entraînement · $president" else president
        return SaveRepository.SlotMeta(title, detail, savedAt)
    }

    fun onPause() {
        foreground = false
        advance()
        save()
        platform.onBackgrounded(session.nextKeyMomentRealMillis())
    }

    fun onResume(): Simulator.Report {
        foreground = true
        platform.onForegrounded()
        return advance()
    }

    /** Le bilan d'absence a tout résumé : les alertes accumulées sont considérées comme vues. */
    fun markAllSeen() {
        freshNotifications.clear()
        lastSeenNotification = session.state.notifications.feed.lastOrNull()?.id ?: lastSeenNotification
    }

    private fun collectNotifications() {
        val feed = session.state.notifications.feed
        feed.filter { it.id > lastSeenNotification }.forEach { freshNotifications.addLast(it) }
        lastSeenNotification = feed.lastOrNull()?.id ?: lastSeenNotification
        // Au premier plan, le jeu affiche lui-même les alertes : la file système est vidée.
        val pending = session.context.notifications.drainPlatformQueue()
        if (!foreground) pending.forEach { platform.postSystemNotification(it) }
        while (freshNotifications.size > MAX_FRESH) freshNotifications.removeFirst()
    }

    companion object {
        const val GAME_VERSION = "0.2.0"
        private const val TAG = "President"
        private const val TICK_SECONDS = 1f
        private const val AUTOSAVE_SECONDS = 60f
        private const val MAX_FRESH = 20
    }
}
