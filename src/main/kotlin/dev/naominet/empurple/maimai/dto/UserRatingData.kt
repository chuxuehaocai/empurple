package dev.naominet.empurple.maimai.dto

import com.alibaba.fastjson2.annotation.JSONField
import java.util.Locale

data class UserRatingResponse(
    @JSONField(name = "userId") val userId: Long = 0,
    @JSONField(name = "userRating") val userRating: UserRating = UserRating()
)

data class UserRating(
    @JSONField(name = "ratingList") val ratingList: List<UserRatingData> = emptyList(),
    @JSONField(name = "newRatingList") val newRatingList: List<UserRatingData> = emptyList()
)

data class UserRatingData(
    @JSONField(name = "musicName") var musicName: String? = null,
    @JSONField(name = "level") var level: Int = 0,
    @JSONField(name = "romVersion") var romVersion: Int = 0,
    @JSONField(name = "achievement") var achievement: Int = 0,
    @JSONField(name = "musicId") var musicId: Int = 0,
    @JSONField(name = "comboStatus") var comboStatus: Int = 0,
    @JSONField(name = "syncStatus") var syncStatus: Int = 0
) {
    fun formatRatingSimple(raw: Long): String =
        String.format(Locale.US, "%.4f%%", raw / 10000.0)
}

enum class MusicLevel {
    Basic, Advanced, Expert, Master, ReMaster;

    companion object {
        fun fromInt(level: Int): MusicLevel = entries.getOrElse(level) { Basic }
    }
}
