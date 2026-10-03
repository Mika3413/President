package fr.president.engine.util

fun Double.clamp01(): Double = coerceIn(0.0, 1.0)

fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

/** Position normalisée de [value] entre [from] et [to], bornée à [0, 1]. */
fun inverseLerp(from: Double, to: Double, value: Double): Double =
    if (from == to) 0.0 else ((value - from) / (to - from)).clamp01()

/** Rapproche [current] de [target] d'une fraction [rate] (lissage exponentiel). */
fun approach(current: Double, target: Double, rate: Double): Double =
    current + (target - current) * rate.clamp01()

/** Convertit un taux de convergence mensuel en taux équivalent pour [steps] pas par mois. */
fun perStepRate(monthlyRate: Double, steps: Double): Double =
    1.0 - Math.pow(1.0 - monthlyRate.clamp01(), 1.0 / steps)
