package dev.naominet.empurple.utils

import kotlin.math.floor
import kotlin.math.min

object RatingCalculator {
    data class RaResult(val ra: Int, val rate: String)

    fun computeRa(ds: Double, achievement: Double): Int = compute(ds, achievement).ra

    fun computeRaRate(ds: Double, achievement: Double): String = compute(ds, achievement).rate

    fun computeRaWithRate(ds: Double, achievement: Double): RaResult = compute(ds, achievement)

    private fun compute(ds: Double, achievement: Double): RaResult {
        val (baseRa, rate) = when {
            achievement < 50 -> 7.0 to "D"
            achievement < 60 -> 8.0 to "C"
            achievement < 70 -> 9.6 to "B"
            achievement < 75 -> 11.2 to "BB"
            achievement < 80 -> 12.0 to "BBB"
            achievement < 90 -> 13.6 to "A"
            achievement < 94 -> 15.2 to "AA"
            achievement < 97 -> 16.8 to "AAA"
            achievement < 98 -> 20.0 to "S"
            achievement < 99 -> 20.3 to "Sp"
            achievement < 99.5 -> 20.8 to "SS"
            achievement < 100 -> 21.1 to "SSp"
            achievement < 100.5 -> 21.6 to "SSS"
            else -> 22.4 to "SSSp"
        }
        val capped = min(100.5, achievement)
        return RaResult(floor(ds * (capped / 100.0) * baseRa).toInt(), rate)
    }
}
