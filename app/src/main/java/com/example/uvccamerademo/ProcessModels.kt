package com.example.uvccamerademo

data class ProcessItem(
    val id: String,
    val name: String
)

enum class ProcessSource {
    NONE,
    LIVE,
    CACHE,
    TEMPORARY,
    UNKNOWN_ONLY
}
