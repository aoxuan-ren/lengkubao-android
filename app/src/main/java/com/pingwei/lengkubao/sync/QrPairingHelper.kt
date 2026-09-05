package com.pingwei.lengkubao.sync

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 解析 PC 端配对二维码 JSON。
 * 格式：{"v":1,"type":"lengkubao_pair","ip":"...","port":8080,"code":"123456","name":"PC"}
 */
object QrPairingHelper {
    const val PAIR_TYPE = "lengkubao_pair"

    data class PairingPayload(
        val v: Int = 1,
        val type: String = PAIR_TYPE,
        val ip: String = "",
        val port: Int = 8080,
        val code: String = "",
        val name: String = ""
    )

    fun parse(raw: String?): PairingPayload? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        if (trimmed.startsWith("lengkubao://pair", ignoreCase = true)) {
            return parseLegacyUri(trimmed)
        }

        return try {
            val json = JsonParser.parseString(trimmed).asJsonObject
            val type = json.get("type")?.asString ?: return null
            if (!type.equals(PAIR_TYPE, ignoreCase = true)) return null
            PairingPayload(
                v = json.get("v")?.asInt ?: 1,
                type = type,
                ip = json.get("ip")?.asString?.trim().orEmpty(),
                port = json.get("port")?.asInt ?: 8080,
                code = json.get("code")?.asString?.trim().orEmpty(),
                name = json.get("name")?.asString?.trim().orEmpty()
            ).takeIf { it.ip.isNotBlank() && it.code.isNotBlank() && it.port > 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLegacyUri(uri: String): PairingPayload? {
        return try {
            val query = uri.substringAfter('?', "")
            var code = ""
            var name = ""
            var port = 8080
            query.split('&').forEach { part ->
                val kv = part.split('=', limit = 2)
                if (kv.size == 2) {
                    when (kv[0]) {
                        "code" -> code = kv[1]
                        "name" -> name = kv[1]
                        "port" -> port = kv[1].toIntOrNull() ?: 8080
                    }
                }
            }
            if (code.isBlank()) return null
            PairingPayload(code = code, name = name, port = port, ip = "")
        } catch (_: Exception) {
            null
        }
    }

    fun toJson(payload: PairingPayload): String = Gson().toJson(payload)
}
