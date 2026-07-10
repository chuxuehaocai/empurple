package dev.naominet.empurple.config

import dev.naominet.purple.framework.config.IConfig


data class EmpurpleConfig(
    override val configId: String = "empurple-config",
    val titleServerUrl: String = "",
    val aesKey: String = "",
    val aesIv: String = "",
    val aimeUrl: String = "",
    val keychipId: String = "",
    val obfuscateParam: String = "",
    val apiVersion: String = "",
    val clientId: String = "",
    val aimeSalt: String = "",
    val regionId: Int = 0,
    val regionName: String = "",
    val placeId: Int = 0,
    val placeName: String = "",
): IConfig
