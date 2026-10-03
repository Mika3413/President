package fr.president.engine.util

import kotlinx.serialization.Serializable

/** Série temporelle bornée (valeurs mensuelles) servant aux tendances et aux graphiques. */
@Serializable
class History(private val capacity: Int = DEFAULT_CAPACITY) {
    private val values: MutableList<Double> = mutableListOf()

    fun push(value: Double) {
        values.add(value)
        while (values.size > capacity) values.removeAt(0)
    }

    fun all(): List<Double> = values
    fun last(): Double? = values.lastOrNull()
    val size: Int get() = values.size

    /** Valeur il y a [steps] pas (0 = dernière). */
    fun ago(steps: Int): Double? = values.getOrNull(values.size - 1 - steps)

    /** Nombre de pas consécutifs (depuis la fin) où la valeur a augmenté. */
    fun risingStreak(): Int {
        var streak = 0
        for (i in values.size - 1 downTo 1) {
            if (values[i] > values[i - 1]) streak++ else break
        }
        return streak
    }

    fun fallingStreak(): Int {
        var streak = 0
        for (i in values.size - 1 downTo 1) {
            if (values[i] < values[i - 1]) streak++ else break
        }
        return streak
    }

    companion object {
        const val DEFAULT_CAPACITY = 120
    }
}
