package fr.president.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sons du jeu, générés par programme (aucun fichier audio à distribuer) : interface (clic,
 * décision, alerte, message), guerre (canonnade, victoire, défaite, prise de ville, frappe
 * nucléaire, ordre de marche) et vie du pays (foule qui applaudit, fanfare).
 * Désactivables et réglables dans les réglages ; tout échec audio est ignoré (machine sans son, tests).
 */
object Sfx {
    enum class Kind { CLICK, DECISION, ALERT, MESSAGE, BATTLE, VICTORY, DEFEAT, CAPTURE, NUCLEAR, CROWD, MARCH, FANFARE }

    private val sounds = HashMap<Kind, Sound?>()
    private val lastPlayed = HashMap<Kind, Long>()

    fun play(kind: Kind, volume: Float = 1f) {
        if (!UserSettings.sound) return
        // Pas de mitraille : un même son ne se répète pas trop vite.
        val now = System.currentTimeMillis()
        val gap = MIN_GAP_MS[kind] ?: 0L
        if (now - (lastPlayed[kind] ?: 0L) < gap) return
        lastPlayed[kind] = now
        val sound = sounds.getOrPut(kind) { runCatching { load(kind) }.getOrNull() } ?: return
        runCatching { sound.play((VOLUME[kind] ?: 0.5f) * volume * UserSettings.soundVolume) }
    }

    private fun load(kind: Kind): Sound? {
        val audio = Gdx.audio ?: return null
        // Fichier temporaire : rien n'est écrit dans les données du jeu.
        val temp = java.io.File.createTempFile("president-${kind.name.lowercase()}", ".wav").apply { deleteOnExit() }
        val file: FileHandle = Gdx.files.absolute(temp.absolutePath)
        file.writeBytes(wav(compose(kind)), false)
        return audio.newSound(file)
    }

    // --- Composition des sons ----------------------------------------------------------------------

    private fun compose(kind: Kind): DoubleArray = when (kind) {
        Kind.CLICK -> Synth(0.05).apply { tone(1400.0, 0.025, 0.0) }.out
        Kind.DECISION -> Synth(0.5).apply { tone(660.0, 0.18, 0.0); tone(880.0, 0.22, 0.09); tone(1320.0, 0.3, 0.18) }.out
        Kind.ALERT -> Synth(0.4).apply { tone(880.0, 0.14, 0.0); tone(660.0, 0.18, 0.16) }.out
        Kind.MESSAGE -> Synth(0.2).apply { tone(990.0, 0.08, 0.0); tone(1180.0, 0.1, 0.07) }.out
        // Canonnade lointaine : deux détonations sourdes.
        Kind.BATTLE -> Synth(1.4).apply {
            boom(0.0, 0.9, 1.0); boom(0.38, 0.7, 0.55); noise(0.05, 0.25, 0.25, 0.25, decay = 14.0)
        }.out
        // Victoire : appel de cuivres montant, accord tenu, coup de timbale.
        Kind.VICTORY -> Synth(1.6).apply {
            brass(392.0, 0.14, 0.0); brass(523.3, 0.14, 0.15); brass(659.3, 0.14, 0.3)
            brass(784.0, 0.9, 0.45); brass(523.3, 0.9, 0.45, 0.5); brass(659.3, 0.9, 0.45, 0.4); timpani(0.45, 0.8)
        }.out
        // Défaite : cuivres graves qui descendent.
        Kind.DEFEAT -> Synth(1.9).apply {
            brass(329.6, 0.35, 0.0, 0.7); brass(293.7, 0.35, 0.35, 0.7); brass(261.6, 0.35, 0.7, 0.7); brass(246.9, 1.0, 1.05, 0.8)
            brass(155.6, 1.0, 1.05, 0.5); timpani(1.05, 0.6)
        }.out
        // Prise de ville : roulement de caisse claire puis accord de cuivres.
        Kind.CAPTURE -> Synth(1.5).apply {
            var t = 0.0; while (t < 0.5) { snare(t, 0.35 + t); t += 0.055 }
            brass(587.3, 0.8, 0.55); brass(740.0, 0.8, 0.55, 0.6); brass(880.0, 0.8, 0.55, 0.5); timpani(0.55, 0.7)
        }.out
        // Frappe nucléaire : éclair sec puis grondement interminable.
        Kind.NUCLEAR -> Synth(4.5).apply {
            noise(0.0, 0.4, 0.6, 0.5, decay = 9.0); rumble(0.05, 4.4, 1.0)
        }.out
        // Foule : applaudissements et clameur qui enfle puis retombe.
        Kind.CROWD -> Synth(2.8).apply { applause(0.0, 2.7, 1.0); noise(0.1, 2.5, 0.12, 0.25, swell = true) }.out
        // Ordre de marche : « ra-ta-plan ».
        Kind.MARCH -> Synth(0.6).apply { snare(0.0, 0.6); snare(0.1, 0.6); snare(0.2, 0.9); timpani(0.2, 0.35) }.out
        // Fanfare de fête : thème plus long, timbales et foule.
        Kind.FANFARE -> Synth(3.2).apply {
            val theme = listOf(523.3 to 0.0, 659.3 to 0.18, 784.0 to 0.36, 1046.5 to 0.54, 784.0 to 0.9, 1046.5 to 1.08)
            theme.forEachIndexed { i, (hz, at) -> brass(hz, if (i == theme.size - 1) 1.6 else 0.3, at) }
            brass(523.3, 1.6, 1.08, 0.5); brass(659.3, 1.6, 1.08, 0.4); timpani(0.54, 0.6); timpani(1.08, 0.8)
            applause(1.2, 2.0, 0.5)
        }.out
    }

    /** Petit synthétiseur : sinusoïdes, cuivres (harmoniques), bruit filtré, percussions. */
    private class Synth(seconds: Double) {
        val out = DoubleArray((seconds * RATE).toInt())
        private val random = java.util.Random(11)

        fun tone(hz: Double, seconds: Double, start: Double, level: Double = 1.0) = each(start, seconds) { i, t ->
            sin(2 * PI * hz * t) * exp(-t * DECAY / seconds) * minOf(1.0, i / ATTACK) * level
        }

        fun brass(hz: Double, seconds: Double, start: Double, level: Double = 0.8) = each(start, seconds + 0.08) { _, t ->
            val env = minOf(1.0, t / 0.03) * (if (t > seconds) exp(-(t - seconds) * 40) else 1.0) * (0.85 + 0.15 * exp(-t * 6))
            val vibrato = 1 + 0.004 * sin(2 * PI * 5.5 * t)
            var v = 0.0
            for (h in 1..6) v += sin(2 * PI * hz * h * vibrato * t) / (h * 1.3)
            v * env * level * 0.35
        }

        fun timpani(start: Double, level: Double) = each(start, 0.9) { _, t ->
            val hz = 95.0 * exp(-t * 1.5)
            (sin(2 * PI * hz * t) + 0.3 * sin(2 * PI * hz * 1.5 * t)) * exp(-t * 4.5) * level
        }

        fun snare(start: Double, level: Double) {
            var lp = 0.0
            each(start, 0.12) { _, t ->
                lp += (random.nextDouble() * 2 - 1 - lp) * 0.6
                (lp * 0.8 + sin(2 * PI * 190 * t) * 0.4) * exp(-t * 35) * level * 0.7
            }
        }

        fun boom(start: Double, seconds: Double, level: Double) {
            var lp = 0.0
            each(start, seconds) { _, t ->
                lp += (random.nextDouble() * 2 - 1 - lp) * 0.05
                val hz = 55.0 * exp(-t * 2.5) + 25
                (lp * 3.0 + sin(2 * PI * hz * t) * 0.8) * exp(-t * 5 / seconds) * minOf(1.0, t / 0.004) * level
            }
        }

        fun rumble(start: Double, seconds: Double, level: Double) {
            var lp = 0.0; var lp2 = 0.0
            each(start, seconds) { _, t ->
                lp += (random.nextDouble() * 2 - 1 - lp) * 0.02
                lp2 += (lp - lp2) * 0.1
                val env = minOf(1.0, t / 0.15) * exp(-t * 1.1)
                (lp2 * 9.0 + sin(2 * PI * (32 + 6 * sin(t * 3)) * t) * 0.5) * env * level
            }
        }

        /** Bruit passe-bas ; [cutoff] entre 0 (sourd) et 1 (brillant). */
        fun noise(start: Double, seconds: Double, cutoff: Double, level: Double, decay: Double = 0.0, swell: Boolean = false) {
            var lp = 0.0
            each(start, seconds) { _, t ->
                lp += (random.nextDouble() * 2 - 1 - lp) * cutoff
                val env = if (swell) sin(PI * t / seconds) else exp(-t * decay) * minOf(1.0, t / 0.005)
                lp * env * level
            }
        }

        /** Applaudissements : une pluie de claquements brefs, plus dense au milieu. */
        fun applause(start: Double, seconds: Double, level: Double) {
            val claps = (seconds * 140).toInt()
            repeat(claps) {
                val at = start + random.nextDouble() * seconds
                val dens = sin(PI * (at - start) / seconds)
                if (random.nextDouble() > dens) return@repeat
                var hp = 0.0; var prev = 0.0
                val gain = (0.4 + random.nextDouble() * 0.6) * level * 0.35
                each(at, 0.03) { _, t ->
                    val n = random.nextDouble() * 2 - 1
                    hp = 0.7 * (hp + n - prev); prev = n
                    hp * exp(-t * 160) * gain
                }
            }
        }

        private inline fun each(start: Double, seconds: Double, f: (Int, Double) -> Double) {
            val from = (start * RATE).toInt()
            val len = (seconds * RATE).toInt()
            for (i in 0 until len) {
                if (from + i >= out.size) break
                out[from + i] += f(i, i / RATE)
            }
        }
    }

    /** WAV 16 bits mono, normalisé. */
    private fun wav(samples: DoubleArray): ByteArray {
        val peak = samples.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1e-6) ?: 1.0
        val gain = minOf(AMPLITUDE / peak, MAX_GAIN)
        val data = ByteArrayOutputStream()
        samples.forEach { v ->
            val s = ((v * gain).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            data.write(s and 0xff); data.write((s shr 8) and 0xff)
        }
        val pcm = data.toByteArray()
        val out = ByteArrayOutputStream()
        fun int(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
        fun short(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        out.write("RIFF".toByteArray()); int(HEADER + pcm.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(FMT_SIZE); short(1); short(1); int(RATE.toInt()); int(RATE.toInt() * 2); short(2); short(BITS)
        out.write("data".toByteArray()); int(pcm.size); out.write(pcm)
        return out.toByteArray()
    }

    private const val RATE = 22050.0
    private const val DECAY = 4.0
    private const val ATTACK = 60.0
    private const val AMPLITUDE = 0.8
    private const val MAX_GAIN = 2.3
    private const val HEADER = 36
    private const val FMT_SIZE = 16
    private const val BITS = 16
    private val VOLUME = mapOf(
        Kind.CLICK to 0.12f, Kind.DECISION to 0.3f, Kind.ALERT to 0.35f, Kind.MESSAGE to 0.25f,
        Kind.BATTLE to 0.45f, Kind.VICTORY to 0.5f, Kind.DEFEAT to 0.45f, Kind.CAPTURE to 0.5f,
        Kind.NUCLEAR to 0.8f, Kind.CROWD to 0.4f, Kind.MARCH to 0.35f, Kind.FANFARE to 0.55f,
    )
    private val MIN_GAP_MS = mapOf(
        Kind.BATTLE to 2500L, Kind.CROWD to 3000L, Kind.MARCH to 400L, Kind.VICTORY to 1500L, Kind.DEFEAT to 1500L,
        Kind.CAPTURE to 1500L, Kind.NUCLEAR to 5000L, Kind.FANFARE to 4000L, Kind.ALERT to 600L, Kind.MESSAGE to 300L,
    )
}
