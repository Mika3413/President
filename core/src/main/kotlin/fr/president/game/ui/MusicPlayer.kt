package fr.president.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Musique d'ambiance composée par programme (aucun fichier à distribuer) : une boucle calme en
 * majeur pour la conduite des affaires, une boucle tendue en mineur pour les crises, une marche
 * martiale (timbales, caisse claire, cuivres graves) quand nos troupes se battent, et un air de
 * fête (cloches, arpèges vifs) après une victoire sportive, électorale ou militaire.
 * Les boucles sont synthétisées une fois, hors du fil d'affichage ; on passe de l'une à l'autre
 * en fondu. Désactivable dans les réglages ; tout échec audio est ignoré.
 */
object MusicPlayer {
    enum class Mood { CALM, TENSE, WAR, CELEBRATION }

    private val tracks = HashMap<Mood, Music?>()
    private val building = HashSet<Mood>()
    private var wanted: Mood? = null
    private var playing: Mood? = null
    private var fade = 1f

    /** Demande l'ambiance voulue (appelée à chaque rafraîchissement de l'écran de jeu). */
    fun setMood(mood: Mood?) {
        wanted = if (UserSettings.music) mood else null
        val m = wanted ?: return
        if (m !in tracks && building.add(m)) build(m)
    }

    /** À appeler à chaque image : fondu enchaîné entre les ambiances. */
    fun update(delta: Float) {
        updateDuck(delta)
        val current = playing?.let { tracks[it] }
        if (playing != wanted) {
            fade = (fade - delta / FADE_SECONDS).coerceAtLeast(0f)
            current?.volume = volume() * fade
            if (fade <= 0f || current == null) {
                runCatching { current?.pause() }
                playing = null
                val next = wanted?.let { tracks[it] }
                if (next != null) {
                    playing = wanted
                    fade = 0f
                    runCatching { next.isLooping = true; next.volume = 0f; next.play() }
                }
            }
        } else if (current != null && fade < 1f) {
            fade = (fade + delta / FADE_SECONDS).coerceAtMost(1f)
            current.volume = volume() * fade
        }
    }

    private fun volume() = VOLUME * UserSettings.musicVolume * duck

    /** Silence pesant (frappe nucléaire...) : la musique s'efface quelques secondes. */
    private var duck = 1f
    private var duckLeft = 0f
    fun hush(seconds: Float) { duckLeft = seconds }

    private fun updateDuck(delta: Float) {
        duckLeft = (duckLeft - delta).coerceAtLeast(0f)
        duck = if (duckLeft > 0f) (duck - delta * 2).coerceAtLeast(0f) else (duck + delta / 3).coerceAtMost(1f)
        playing?.let { tracks[it] }?.let { if (playing == wanted && fade >= 1f) runCatching { it.volume = volume() } }
    }

    fun stop() {
        wanted = null
        playing?.let { tracks[it] }?.let { runCatching { it.pause() } }
        playing = null
    }

    private fun build(mood: Mood) {
        Thread({
            val bytes = runCatching { compose(mood) }.getOrNull()
            Gdx.app?.postRunnable {
                tracks[mood] = bytes?.let { b ->
                    runCatching {
                        val temp = java.io.File.createTempFile("president-music-${mood.name.lowercase()}", ".wav").apply { deleteOnExit() }
                        temp.writeBytes(b)
                        Gdx.audio?.newMusic(Gdx.files.absolute(temp.absolutePath))
                    }.getOrNull()
                }
            }
        }, "president-music").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    // --- Composition ------------------------------------------------------------------------------

    /** Suite d'accords (demi-tons au-dessus de ré), arpège, mélodie et percussions, selon l'ambiance. */
    private fun compose(mood: Mood): ByteArray {
        val chords = when (mood) {
            // Ré majeur : I – vi – IV – V – I – iii – IV – V
            Mood.CALM -> listOf(listOf(0, 4, 7), listOf(9, 12, 16), listOf(5, 9, 12), listOf(7, 11, 14),
                listOf(0, 4, 7), listOf(4, 7, 11), listOf(5, 9, 12), listOf(7, 11, 14))
            // Ré mineur : i – VI – III – VII – i – iv – V – i
            Mood.TENSE -> listOf(listOf(0, 3, 7), listOf(-4, 0, 3), listOf(3, 7, 10), listOf(-2, 2, 5),
                listOf(0, 3, 7), listOf(5, 8, 12), listOf(7, 11, 14), listOf(0, 3, 7))
            // Marche en ré mineur : i – i – VI – VII – i – iv – V – V
            Mood.WAR -> listOf(listOf(0, 3, 7), listOf(0, 3, 7), listOf(-4, 0, 3), listOf(-2, 2, 5),
                listOf(0, 3, 7), listOf(5, 8, 12), listOf(7, 11, 14), listOf(7, 11, 14))
            // Fête en ré majeur : I – IV – V – I – vi – IV – V – I
            Mood.CELEBRATION -> listOf(listOf(0, 4, 7), listOf(5, 9, 12), listOf(7, 11, 14), listOf(0, 4, 7),
                listOf(9, 12, 16), listOf(5, 9, 12), listOf(7, 11, 14), listOf(0, 4, 7))
        }
        val tempo = when (mood) { Mood.CALM -> 0.5; Mood.TENSE -> 0.33; Mood.WAR -> 0.25; Mood.CELEBRATION -> 0.2 }
        val chordSeconds = when (mood) { Mood.WAR, Mood.CELEBRATION -> 4.0; else -> CHORD_SECONDS }
        val total = chords.size * chordSeconds
        val n = (total * RATE).toInt()
        val out = DoubleArray(n)
        val random = java.util.Random(mood.ordinal.toLong() + 3)
        chords.forEachIndexed { i, chord ->
            val start = i * chordSeconds
            // Nappe : trois voix douces, attaque et extinction lentes qui se chevauchent (boucle sans couture).
            val padLevel = if (mood == Mood.WAR) PAD_LEVEL * 0.7 else PAD_LEVEL
            chord.forEach { semi -> pad(out, freq(semi - 12), start, chordSeconds, padLevel) }
            if (mood != Mood.WAR) pad(out, freq(chord[0] - 24), start, chordSeconds, BASS_LEVEL)
            // Arpège pincé (cloches pour la fête).
            var t = start
            var k = 0
            while (t < start + chordSeconds - 0.01) {
                val semi = chord[ARPEGGIO[k % ARPEGGIO.size]] + if (k % 8 >= 4) 12 else 0
                when (mood) {
                    Mood.CELEBRATION -> bell(out, freq(semi + 12), t, PLUCK_LEVEL * 0.7)
                    Mood.WAR -> if (k % 2 == 0) brassNote(out, freq(chord[0] - 24 + if (k % 8 == 6) 7 else 0), t, tempo * 1.6, BRASS_LEVEL)
                    else -> pluck(out, freq(semi), t, PLUCK_SECONDS, if (mood == Mood.CALM) PLUCK_LEVEL else PLUCK_LEVEL * 0.8)
                }
                t += tempo; k++
            }
            // Mélodie : une note de l'accord par temps fort, parfois une note de passage, quelques silences.
            if (mood == Mood.CALM || mood == Mood.CELEBRATION || mood == Mood.WAR) {
                val beat = chordSeconds / 4
                for (b in 0 until 4) {
                    if (random.nextDouble() < 0.2 && b != 0) continue
                    val semi = chord[random.nextInt(3)] + 12 + if (b == 3 && random.nextBoolean()) 2 else 0
                    val at = start + b * beat
                    if (mood == Mood.WAR) brassNote(out, freq(semi - 12), at, beat * 0.9, BRASS_LEVEL * 0.8)
                    else lead(out, freq(semi), at, beat * (if (random.nextDouble() < 0.3) 2.0 else 1.0), LEAD_LEVEL)
                }
            }
            // Percussions : timbale (tension), marche (timbales + caisse claire), charleston (fête).
            when (mood) {
                Mood.TENSE -> { var b = start; while (b < start + chordSeconds - 0.01) { drum(out, b); b += 1.0 } }
                Mood.WAR -> {
                    var b = start; var beat = 0
                    while (b < start + chordSeconds - 0.01) {
                        if (beat % 2 == 0) drum(out, b)
                        snare(out, b + 0.25, random, if (beat % 4 == 3) 0.5 else 0.3)
                        if (beat % 4 == 3) { snare(out, b + 0.375, random, 0.25); snare(out, b + 0.4375, random, 0.25) }
                        b += 0.5; beat++
                    }
                }
                Mood.CELEBRATION -> { var b = start + 0.2; while (b < start + chordSeconds - 0.01) { hat(out, b, random); b += 0.4 } }
                else -> {}
            }
        }
        return wav(out)
    }

    private fun lead(out: DoubleArray, hz: Double, start: Double, length: Double, level: Double) {
        val from = (start * RATE).toInt()
        val len = (length * RATE).toInt()
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val time = i / RATE
            val env = minOf(1.0, time / 0.04) * exp(-time * 1.8 / length)
            val vib = 1 + 0.003 * sin(2 * PI * 5.0 * time)
            out[idx] += (sin(2 * PI * hz * vib * time) + 0.2 * sin(2 * PI * hz * 2 * time)) * env * level
        }
    }

    private fun bell(out: DoubleArray, hz: Double, start: Double, level: Double) {
        val from = (start * RATE).toInt()
        val len = (1.2 * RATE).toInt()
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val time = i / RATE
            out[idx] += (sin(2 * PI * hz * time) + 0.4 * sin(2 * PI * hz * 2.76 * time) * exp(-time * 6)) * exp(-time * 3.5) * minOf(1.0, i / 40.0) * level
        }
    }

    private fun brassNote(out: DoubleArray, hz: Double, start: Double, length: Double, level: Double) {
        val from = (start * RATE).toInt()
        val len = ((length + 0.05) * RATE).toInt()
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val time = i / RATE
            val env = minOf(1.0, time / 0.03) * if (time > length) exp(-(time - length) * 50) else 1.0
            var v = 0.0
            for (h in 1..5) v += sin(2 * PI * hz * h * time) / (h * 1.4)
            out[idx] += v * env * level
        }
    }

    private fun snare(out: DoubleArray, start: Double, random: java.util.Random, level: Double) {
        val from = (start * RATE).toInt()
        val len = (0.12 * RATE).toInt()
        var lp = 0.0
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val time = i / RATE
            lp += (random.nextDouble() * 2 - 1 - lp) * 0.55
            out[idx] += (lp + 0.3 * sin(2 * PI * 185 * time)) * exp(-time * 32) * level * SNARE_LEVEL
        }
    }

    private fun hat(out: DoubleArray, start: Double, random: java.util.Random) {
        val from = (start * RATE).toInt()
        val len = (0.05 * RATE).toInt()
        var prev = 0.0; var hp = 0.0
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val n = random.nextDouble() * 2 - 1
            hp = 0.6 * (hp + n - prev); prev = n
            out[idx] += hp * exp(-i / RATE * 70) * HAT_LEVEL
        }
    }

    private fun freq(semitonesFromD: Int) = D4 * Math.pow(2.0, semitonesFromD / 12.0)

    private fun pad(out: DoubleArray, hz: Double, start: Double, length: Double, level: Double) {
        val from = ((start - OVERLAP) * RATE).toInt()
        val len = ((length + 2 * OVERLAP) * RATE).toInt()
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val time = i / RATE
            val env = sin(PI * time / (length + 2 * OVERLAP)).let { it * it }
            val tone = sin(2 * PI * hz * time) + 0.3 * sin(2 * PI * hz * 2 * time) + 0.12 * sin(2 * PI * hz * 3.01 * time)
            out[idx] += tone * env * level
        }
    }

    private fun pluck(out: DoubleArray, hz: Double, start: Double, length: Double, level: Double) {
        val from = (start * RATE).toInt()
        val len = (length * RATE).toInt()
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val time = i / RATE
            val env = exp(-time * 5.0) * minOf(1.0, i / 80.0)
            out[idx] += (sin(2 * PI * hz * time) + 0.25 * sin(2 * PI * hz * 2 * time)) * env * level
        }
    }

    private fun drum(out: DoubleArray, start: Double) {
        val from = (start * RATE).toInt()
        val len = (DRUM_SECONDS * RATE).toInt()
        for (i in 0 until len) {
            val idx = Math.floorMod(from + i, out.size)
            val time = i / RATE
            val hz = 70.0 * exp(-time * 3)
            out[idx] += sin(2 * PI * hz * time) * exp(-time * 7) * DRUM_LEVEL
        }
    }

    private fun wav(samples: DoubleArray): ByteArray {
        val peak = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        val gain = MASTER / peak
        val pcm = ByteArrayOutputStream(samples.size * 2)
        samples.forEach { v ->
            val s = (v * gain).coerceIn(-1.0, 1.0).times(Short.MAX_VALUE).toInt()
            pcm.write(s and 0xff); pcm.write((s shr 8) and 0xff)
        }
        val data = pcm.toByteArray()
        val out = ByteArrayOutputStream(data.size + HEADER + 8)
        fun int(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
        fun short(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        out.write("RIFF".toByteArray()); int(HEADER + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(FMT_SIZE); short(1); short(1); int(RATE.toInt()); int(RATE.toInt() * 2); short(2); short(BITS)
        out.write("data".toByteArray()); int(data.size); out.write(data)
        return out.toByteArray()
    }

    private val ARPEGGIO = intArrayOf(0, 1, 2, 1)
    private const val RATE = 22050.0
    private const val D4 = 293.66
    private const val CHORD_SECONDS = 6.0
    private const val OVERLAP = 1.5
    private const val PAD_LEVEL = 0.12
    private const val BASS_LEVEL = 0.16
    private const val PLUCK_SECONDS = 1.4
    private const val PLUCK_LEVEL = 0.1
    private const val DRUM_SECONDS = 0.6
    private const val DRUM_LEVEL = 0.3
    private const val LEAD_LEVEL = 0.07
    private const val BRASS_LEVEL = 0.05
    private const val SNARE_LEVEL = 0.22
    private const val HAT_LEVEL = 0.06
    private const val MASTER = 0.6
    private const val VOLUME = 0.3f
    private const val FADE_SECONDS = 2.5f
    private const val HEADER = 36
    private const val FMT_SIZE = 16
    private const val BITS = 16
}
