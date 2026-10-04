package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.NotificationLevel
import fr.president.game.ui.Formats
import fr.president.game.ui.Ui

/** Réglages : accessibilité, notifications par catégorie, rythme de la partie, journal de debug. */
class SettingsPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit, private val onAbandon: () -> Unit) : Panel(ui, onClose) {
    private var confirmAbandon = false
    override val title = "Réglages"
    private val session get() = nav.session
    private var showDebug = false

    private var pendingPace: String? = null

    override fun build(into: Table) {
        accessibility(into)
        val settings = session.state.notifications.settings
        into.add(ui.label("Notifications Android", "bold")).row()
        into.add(ui.button(if (settings.enabled) "Activées — tout désactiver" else "Désactivées — réactiver", "toggle") {
            settings.enabled = !settings.enabled; nav.refresh()
        }.also { it.isChecked = settings.enabled }).left().padBottom(GAP).row()
        into.add(ui.label("La partie continue quand le jeu est fermé : vous êtes prévenu des événements marquants. " +
            "Si le téléphone ne fait rien en arrière-plan (fréquent sur certaines marques), autorisez le jeu à ignorer l'optimisation de la batterie.",
            "muted", wrap = true)).growX().row()
        if (nav.platform.backgroundRestricted) {
            into.add(ui.button("Autoriser l'activité en arrière-plan", "accent") { nav.platform.requestBackgroundExemption() }).left().padBottom(GAP).row()
        }
        NotificationCategory.entries.forEach { c ->
            val row = Table()
            row.add(ui.label(c.label, "small")).left().expandX()
            row.add(ui.button(settings.level(c).label, "default") {
                val levels = NotificationLevel.entries
                settings.levels[c] = levels[(levels.indexOf(settings.level(c)) + 1) % levels.size]
                nav.refresh()
            }).right()
            into.add(row).growX().padBottom(2f).row()
        }
        val meta = session.state.meta
        val pace = session.db.config.paces.firstOrNull { it.id == meta.clock.paceId }
        into.add(ui.label("Partie", "bold")).padTop(GAP).row()
        into.add(ui.label("Rythme : ${pace?.label} — ${pace?.description}", "small", wrap = true)).growX().row()
        val paces = com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        session.db.config.paces.forEach { p ->
            paces.addActor(ui.button(p.label, "toggle") { pendingPace = p.id.takeIf { it != meta.clock.paceId }; nav.refresh() }
                .also { it.isChecked = (pendingPace ?: meta.clock.paceId) == p.id })
        }
        into.add(paces).growX().left().padTop(2f).row()
        pendingPace?.let { id ->
            val p = session.db.config.paces.first { it.id == id }
            into.add(ui.label("Passer au rythme « ${p.label} » ? ${p.description}", "small", fr.president.game.ui.Theme.warning, wrap = true)).growX().row()
            val row = Table().apply { defaults().padRight(4f) }
            row.add(ui.button("Confirmer", "accent") { session.changePace(id); pendingPace = null; nav.refresh() })
            row.add(ui.button("Annuler") { pendingPace = null; nav.refresh() })
            into.add(row).left().row()
        }
        into.add(ui.label("Début : ${Formats.date(meta.startTime)} · Snapshot : ${meta.snapshotId} · Graine : ${meta.seed}", "muted", wrap = true)).growX().row()
        into.add(ui.button(if (showDebug) "Masquer le journal de debug" else "Journal de debug (IA, économie)", "flat") { showDebug = !showDebug; nav.refresh() }).left().padTop(GAP).row()
        into.add(ui.button("★ Relancer le tutoriel guidé", "flat") { nav.session.state.player.tourStep = 0; nav.refresh() }).left().padTop(GAP).row()
        into.add(ui.label("Astuce : laissez le doigt appuyé (ou la souris) sur un chiffre, une pastille ou un bouton pour avoir son explication.", "muted", wrap = true)).growX().row()
        into.add(ui.label("Partie en cours", "bold")).padTop(GAP).row()
        if (!confirmAbandon) {
            into.add(ui.button("Abandonner et recommencer…", "flat") { confirmAbandon = true; nav.refresh() }).left().row()
        } else {
            into.add(ui.label("La partie actuelle sera définitivement perdue.", "small", fr.president.game.ui.Theme.bad)).row()
            val row = Table().apply { defaults().padRight(4f) }
            row.add(ui.button("Confirmer l'abandon", "accent") { onAbandon() })
            row.add(ui.button("Annuler") { confirmAbandon = false; nav.refresh() })
            into.add(row).left().row()
        }
        if (showDebug) {
            session.context.debug.recent().takeLast(DEBUG_LINES).asReversed().forEach {
                into.add(ui.label("[${it.category}] ${it.message}", "muted", wrap = true)).growX().row()
            }
        }
    }

    private companion object {
        const val DEBUG_LINES = 60
    }

    /** Taille du texte, palette pour daltoniens, sons : réglages de l'appareil. */
    private fun accessibility(into: Table) {
        val settings = fr.president.game.ui.UserSettings
        into.add(ui.label("Accessibilité", "bold")).row()
        into.add(ui.label("Taille du texte", "small")).padTop(2f).row()
        val sizes = Table().apply { defaults().padRight(4f) }
        settings.textScales.forEach { (scale, label) ->
            sizes.add(ui.button(label, "toggle") {
                if (settings.textScale != scale) { settings.textScale = scale; nav.applyDisplaySettings() }
            }.also { it.isChecked = kotlin.math.abs(settings.textScale - scale) < 0.01f })
        }
        into.add(sizes).left().row()
        into.add(ui.button(if (settings.colorblind) "✔ Couleurs pour daltoniens (bleu / orange)" else "Couleurs pour daltoniens", "toggle") {
            settings.colorblind = !settings.colorblind; nav.applyDisplaySettings()
        }.also { it.isChecked = settings.colorblind }).left().padTop(4f).row()
        into.add(ui.button(if (settings.sound) "✔ Sons activés" else "Sons désactivés", "toggle") {
            settings.sound = !settings.sound; nav.refresh()
        }.also { it.isChecked = settings.sound }).left().padTop(4f).row()
        into.add(ui.button(if (settings.music) "✔ Musique activée" else "Musique désactivée", "toggle") {
            settings.music = !settings.music
            if (!settings.music) fr.president.game.ui.MusicPlayer.stop()
            nav.refresh()
        }.also { it.isChecked = settings.music }).left().padTop(4f).row()
        into.add(ui.label("Les flèches ▲ ▼ et les mots (« élevé », « favorable »...) doublent toujours les couleurs.", "muted", wrap = true)).growX().padBottom(GAP).row()
    }
}
