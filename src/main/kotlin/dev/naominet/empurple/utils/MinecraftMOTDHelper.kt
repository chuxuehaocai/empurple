package dev.naominet.empurple.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.annotation.JSONField
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Minecraft Server List Ping (protocol 1.7+)
 * https://wiki.vg/Server_List_Ping
 */
object MinecraftMOTDHelper {
    data class MOTDBean(
        @JSONField(name = "version") val version: VersionInfo,
        @JSONField(name = "players") val players: PlayersInfo,
        @JSONField(name = "description") val description: Any,
        @JSONField(name = "favicon") val favicon: String? = null
    )

    data class VersionInfo(
        @JSONField(name = "name") val name: String,
        @JSONField(name = "protocol") val protocol: Int
    )

    data class PlayersInfo(
        @JSONField(name = "max") val max: Int,
        @JSONField(name = "online") val online: Int,
        @JSONField(name = "sample") val sample: List<PlayerSample>? = null
    )

    data class PlayerSample(
        @JSONField(name = "name") val name: String,
        @JSONField(name = "id") val id: String
    )

    fun getMOTD(host: String = "127.0.0.1", port: Int = 25565, timeout: Int = 5000): MOTDBean {
        Socket().use { socket ->
            socket.soTimeout = timeout
            socket.connect(InetSocketAddress(host, port), timeout)

            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // 1. Handshake packet
            writeHandshake(out, host, port)

            // 2. Status Request packet
            writeStatusRequest(out)

            // 3. Read Status Response
            val responseLength = readVarInt(input)
            val packetId = readVarInt(input)
            require(packetId == 0x00) { "Expected packet ID 0x00, got 0x${packetId.toString(16)}" }

            val jsonLength = readVarInt(input)
            val jsonBytes = ByteArray(jsonLength)
            input.readFully(jsonBytes)
            val json = String(jsonBytes, StandardCharsets.UTF_8)

            return JSON.parseObject(json, MOTDBean::class.java)
        }
    }

    private fun writeHandshake(out: DataOutputStream, host: String, port: Int) {
        val hostBytes = host.toByteArray(StandardCharsets.UTF_8)
        val buffer = mutableListOf<Byte>()

        // Packet ID: 0x00
        buffer.addAll(encodeVarInt(0x00))
        // Protocol version: -1 (status)
        buffer.addAll(encodeVarInt(-1))
        // Server address length + string
        buffer.addAll(encodeVarInt(hostBytes.size))
        buffer.addAll(hostBytes.toList())
        // Server port (unsigned short)
        buffer.add((port ushr 8).toByte())
        buffer.add(port.toByte())
        // Next state: 1 (status)
        buffer.addAll(encodeVarInt(1))

        // Write packet length + packet
        out.write(encodeVarInt(buffer.size).toByteArray())
        out.write(buffer.toByteArray())
        out.flush()
    }

    private fun writeStatusRequest(out: DataOutputStream) {
        // Packet length: 1 (only packet ID)
        out.write(encodeVarInt(1).toByteArray())
        // Packet ID: 0x00
        out.write(encodeVarInt(0x00).toByteArray())
        out.flush()
    }

    private fun encodeVarInt(value: Int): List<Byte> {
        var v = value
        val result = mutableListOf<Byte>()
        do {
            var temp = (v and 0x7F).toByte()
            v = v ushr 7
            if (v != 0) {
                temp = (temp.toInt() or 0x80).toByte()
            }
            result.add(temp)
        } while (v != 0)
        return result
    }

    private fun readVarInt(input: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Byte
        do {
            read = input.readByte()
            val value = (read.toInt() and 0x7F)
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) throw RuntimeException("VarInt is too big")
        } while ((read.toInt() and 0x80) != 0)
        return result
    }
}