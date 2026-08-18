package dev.naominet.empurple.utils

import kotlin.math.roundToInt

object RatingCalculator {
    data class RaResult(val ra: Int, val rate: String)

    fun computeRa(ds: Double, achievement: Double): Int = compute(ds, achievement).ra

    fun computeRaRate(ds: Double, achievement: Double): String = compute(ds, achievement).rate

    fun computeRaWithRate(ds: Double, achievement: Double): RaResult = compute(ds, achievement)

    private fun compute(ds: Double, achievement: Double): RaResult {
        val achievementRaw = (achievement * 10_000.0).roundToInt().coerceIn(0, 1_005_000)
        val (offset, rate) = when {
            achievementRaw < 100_000 -> 0 to "D"
            achievementRaw < 200_000 -> 16 to "D"
            achievementRaw < 300_000 -> 32 to "D"
            achievementRaw < 400_000 -> 48 to "D"
            achievementRaw < 500_000 -> 64 to "D"
            achievementRaw < 600_000 -> 80 to "C"
            achievementRaw < 700_000 -> 96 to "B"
            achievementRaw < 750_000 -> 112 to "BB"
            achievementRaw < 799_999 -> 120 to "BBB"
            achievementRaw < 800_000 -> 128 to "BBB"
            achievementRaw < 900_000 -> 136 to "A"
            achievementRaw < 940_000 -> 152 to "AA"
            achievementRaw < 969_999 -> 168 to "AAA"
            achievementRaw < 970_000 -> 176 to "AAA"
            achievementRaw < 980_000 -> 200 to "S"
            achievementRaw < 989_999 -> 203 to "Sp"
            achievementRaw < 990_000 -> 206 to "Sp"
            achievementRaw < 995_000 -> 208 to "SS"
            achievementRaw < 999_999 -> 211 to "SSp"
            achievementRaw < 1_000_000 -> 214 to "SSp"
            achievementRaw < 1_004_999 -> 216 to "SSS"
            achievementRaw < 1_005_000 -> 222 to "SSS"
            else -> 224 to "SSSp"
        }
        val scoreRate = (ds * 10.0).roundToInt()
        val ra = scoreRate.toLong() * achievementRaw * offset / 100_000_000L
        return RaResult(ra.toInt(), rate)
    }
}