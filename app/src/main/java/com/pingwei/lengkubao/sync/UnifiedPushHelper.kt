package com.pingwei.lengkubao.sync

import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * 统一 Outbox 推送：PUSH|{op_id,type,payload}，等待 ACK|ok|{op_id}。
 */
object UnifiedPushHelper {
    private val gson = Gson()

    fun buildPushMessage(opId: String, type: String, payloadJson: String): String {
        val payload = JsonParser.parseString(payloadJson)
        val root = mapOf(
            "op_id" to opId,
            "type" to type,
            "payload" to payload,
        )
        return "PUSH|${gson.toJson(root)}"
    }

    /** 解析 ACK|ok|opId 或 ACK|fail|opId|reason */
    fun parseAck(message: String): Pair<String, Boolean>? {
        val trimmed = message.trim()
        if (!trimmed.startsWith("ACK|")) return null
        val parts = trimmed.substring("ACK|".length).split("|", limit = 3)
        if (parts.size < 2) return null
        val status = parts[0]
        val opId = parts[1]
        if (opId.isBlank()) return null
        return opId to (status.equals("ok", ignoreCase = true))
    }
}
