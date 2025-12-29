package com.example.uvccamerademo

data class QrWorkInfo(
    val model: String,
    val serial: String
)

fun parseQrPayload(payload: String): QrWorkInfo? {
    val trimmed = payload.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val normalized = trimmed.replace("\r", "\n")
    val keyValues = parseKeyValues(normalized)
    val model = keyValues["model"] ?: keyValues["type"] ?: keyValues["m"]
    val serial = keyValues["serial"] ?: keyValues["sn"] ?: keyValues["s"]
    if (!model.isNullOrBlank() && !serial.isNullOrBlank()) {
        return QrWorkInfo(model = model, serial = serial)
    }
    val tokens = normalized.split(Regex("[,;|\\n]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (tokens.size >= 2) {
        return QrWorkInfo(model = tokens[0], serial = tokens[1])
    }
    val parts = normalized.split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.size >= 2) {
        return QrWorkInfo(model = parts[0], serial = parts[1])
    }
    return null
}

private fun parseKeyValues(raw: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val tokens = raw.split(Regex("[;,&\\n]"))
    tokens.forEach { token ->
        val pair = token.split("=", ":", limit = 2)
        if (pair.size == 2) {
            val key = pair[0].trim().lowercase()
            val value = pair[1].trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }
    }
    return map
}
