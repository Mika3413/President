package fr.president.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sons de l'interface, générés par programme (aucun fichier audio à distribuer) : un « clic »
 * discret, un carillon pour une décision, deux tons pour une alerte, un roulement pour un message.
 * Désactivables dans les réglages ; tout échec audio est ignoré (machine sans son, tests).
 */
object Sfx {
    enum class Kind { CLICK, DECISION, ALERT, MESSAGE }

    private val sounds = HashMap<Kind, Sound?>()

    fun play(kind: Kind) {
        if (!UserSettings.sound) return
        val sound = sounds.getOrPut(kind) { runCatching { load(kind) }.getOrNull() } ?: return
        runCatching { sound.play(VOLUME[kind] ?: 0.5f) }
    }

    private fun load(kind: Kind): Sound? {
        val audio = Gdx.audio ?: return null
        val tones = when (kind) {
            Kind.CLICK -> listOf(Tone(1400.0, 0.025, 0.0))
            Kind.DECISION -> listOf(Tone(660.0, 0.18, 0.0), Tone(880.0, 0.22, 0.09), Tone(1320.0, 0.3, 0.18))
            Kind.ALERT -> listOf(Tone(880.0, 0.14, 0.0), Tone(660.0, 0.18, 0.16))
            Kind.MESSAGE -> listOf(Tone(990.0, 0.08, 0.0), Tone(1180.0, 0.1, 0.07))
        }
        // Fichier temporaire : rien n'est écrit dans les données du jeu.
        val temp = java.io.File.createTempFile("president-${kind.name.lowercase()}", ".wav").apply { deleteOnExit() }
        val file: FileHandle = Gdx.files.absolute(temp.absolutePath)
        file.writeBytes(wav(tones), false)
        return audio.newSound(file)
    }

    private data class Tone(val hz: Double, val seconds: Double, val start: Double)

    /** Sinusoïdes avec enveloppe exponentielle, en WAV 16 bits mono. */
    private fun wav(tones: List<Tone>): ByteArray {
        val total = tones.maxOf { it.start + it.seconds }
        val n = (total * RATE).toInt()
        val samples = DoubleArray(n)
        tones.forEach { t ->
            val from = (t.start * RATE).toInt()
            val len = (t.seconds * RATE).toInt()
            for (i in 0 until len) {
                if (from + i >= n) break
                val time = i / RATE
                val env = exp(-time * DECAY / t.seconds) * minOf(1.0, i / ATTACK)
                samples[from + i] += sin(2 * PI * t.hz * time) * env * AMPLITUDE
            }
        }
        val data = ByteArrayOutputStream()
        samples.forEach { v ->
            val s = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
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
    private const val AMPLITUDE = 0.35
    private const val HEADER = 36
    private const val FMT_SIZE = 16
    private const val BITS = 16
    private val VOLUME = mapOf(Kind.CLICK to 0.25f, Kind.DECISION to 0.6f, Kind.ALERT to 0.7f, Kind.MESSAGE to 0.5f)
}
