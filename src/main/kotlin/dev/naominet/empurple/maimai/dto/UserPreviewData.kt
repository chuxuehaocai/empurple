package dev.naominet.empurple.maimai.dto

import com.alibaba.fastjson2.annotation.JSONField

data class UserPreviewData(
    @JSONField(name = "userId") val userId: Long = 0,
    @JSONField(name = "userName") val userName: String = "",
    @JSONField(name = "isLogin") val isLogin: Int = 0,
    @JSONField(name = "lastGameId") val lastGameId: String? = null,
    @JSONField(name = "lastRomVersion") val lastRomVersion: String = "",
    @JSONField(name = "lastDataVersion") val lastDataVersion: String = "",
    @JSONField(name = "lastLoginDate") val lastLoginDate: String = "",
    @JSONField(name = "lastPlayDate") val lastPlayDate: String = "",
    @JSONField(name = "playerRating") val playerRating: Int = 0,
    @JSONField(name = "nameplateId") val nameplateId: Int = 0,
    @JSONField(name = "iconId") val iconId: Int = 0,
    @JSONField(name = "trophyId") val trophyId: Int = 0,
    @JSONField(name = "isNetMember") val isNetMember: Int = 0,
    @JSONField(name = "isInherit") val isInherit: Boolean = false,
    @JSONField(name = "totalAwake") val totalAwake: Int = 0,
    @JSONField(name = "dispRate") val dispRate: Int = 0,
    @JSONField(name = "dailyBonusDate") val dailyBonusDate: String = "",
    @JSONField(name = "headPhoneVolume") val headPhoneVolume: String? = null,
    @JSONField(name = "banState") val banState: Int = 0,
    @JSONField(name = "errorId") val errorId: Int = 0
)
