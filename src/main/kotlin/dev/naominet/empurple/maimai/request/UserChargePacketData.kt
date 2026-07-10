package dev.naominet.empurple.maimai.request

import com.alibaba.fastjson2.annotation.JSONField
import dev.naominet.empurple.maimai.IApiRequest

data class UserChargePacketData(
    @JSONField(name = "userId") val userId: Long,
    @JSONField(name = "userChargelog") val userChargelog: UserChargeLogData,
    @JSONField(name = "userCharge") val userCharge: UserChargePacketItem,
    @JSONField(name = "loginDateTime") val loginDateTime: Long
): IApiRequest

data class UserChargeLogData(
    @JSONField(name = "chargeId") val chargeId: Int,
    @JSONField(name = "price") val price: Int,
    @JSONField(name = "purchaseDate") val purchaseDate: String,
    @JSONField(name = "playCount") val playCount: Long,
    @JSONField(name = "playerRating") val playerRating: Int,
    @JSONField(name = "placeId") val placeId: Int,
    @JSONField(name = "regionId") val regionId: Int,
    @JSONField(name = "clientId") val clientId: String
)

data class UserChargePacketItem(
    @JSONField(name = "chargeId") val chargeId: Int,
    @JSONField(name = "stock") val stock: Int,
    @JSONField(name = "purchaseDate") val purchaseDate: String,
    @JSONField(name = "validDate") val validDate: String
)
