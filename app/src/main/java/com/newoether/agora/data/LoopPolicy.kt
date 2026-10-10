package com.newoether.agora.data

/**
 * Value clamps for the legacy `loops` rows still carried by the `.agora` archive format.
 *
 * The automation subsystem is gone, so nothing schedules a loop any more. These bounds only
 * exist so an archive written by an older build still imports into a schema-valid row instead of
 * failing on an out-of-range value.
 */
internal object LoopPolicy {
    const val MIN_INTERVAL_SECONDS = 10L
    const val MAX_INTERVAL_SECONDS = 604_800L // 7 days
    const val MIN_INTERVAL_MS = MIN_INTERVAL_SECONDS * 1_000L
    const val MAX_INTERVAL_MS = MAX_INTERVAL_SECONDS * 1_000L

    const val DEFAULT_MAX_CYCLES = 10
    const val MIN_MAX_CYCLES = 1
    const val MAX_MAX_CYCLES = 100

    const val DEFAULT_PROMPT = "Continue."

    fun normalizePrompt(prompt: String?): String? = prompt?.trim()?.takeIf { it.isNotEmpty() }
}
