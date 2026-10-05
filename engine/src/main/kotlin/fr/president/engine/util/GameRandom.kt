package fr.president.engine.util

import kotlinx.serialization.Serializable

/**
 * Générateur pseudo-aléatoire déterministe (SplitMix64) dont l'état est sérialisable.
 * Toute la simulation passe par lui : une sauvegarde rejouée avec la même graine
 * et les mêmes actions produit exactement le même monde.
 */
@Serializable
class GameRandom(private var state: Long) {

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Valeur uniforme dans [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * DOUBLE_UNIT

    fun nextDouble(min: Double, max: Double): Double = min + (max - min) * nextDouble()

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound doit être positif" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun chance(probability: Double): Boolean = nextDouble() < probability

    /** Approximation gaussienne (somme de 4 uniformes), suffisante pour du bruit de simulation. */
    fun nextGaussian(): Double {
        var sum = 0.0
        repeat(GAUSS_SAMPLES) { sum += nextDouble() }
        return (sum - GAUSS_SAMPLES / 2.0) * GAUSS_SCALE
    }

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    fun <T> pickWeighted(items: List<T>, weight: (T) -> Double): T? {
        val total = items.sumOf { weight(it).coerceAtLeast(0.0) }
        if (total <= 0.0) return null
        var roll = nextDouble() * total
        for (item in items) {
            roll -= weight(item).coerceAtLeast(0.0)
            if (roll <= 0.0) return item
        }
        return items.last()
    }

    /** Crée un générateur indépendant (pour un sous-système) sans perturber la séquence principale. */
    fun <T> shuffled(items: List<T>): List<T> {
        val out = items.toMutableList()
        for (i in out.size - 1 downTo 1) { val j = nextInt(i + 1); val t = out[i]; out[i] = out[j]; out[j] = t }
        return out
    }

    fun fork(): GameRandom = GameRandom(nextLong())

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
        const val GAUSS_SAMPLES = 4
        // Écart-type d'une somme de 4 uniformes = sqrt(4/12) ; on normalise à 1.
        const val GAUSS_SCALE = 1.7320508075688772
    }
}
