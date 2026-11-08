package com.utkarsh.todo.feature.customize.data

import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.TodayBlock

/**
 * Turns settings into the plain strings stored on disk and back. Reading is forgiving: a missing, old or
 * hand-edited value falls back to the default instead of crashing the app at start-up.
 */
internal object SettingsCodec {
    private val defaults = AppSettings()

    fun encodeOrder(order: List<TodayBlock>): String = order.joinToString(",") { it.name }

    /** Unknown names and repeats are dropped; blocks added in a later version are appended. */
    fun decodeOrder(raw: String?): List<TodayBlock> {
        val seen = raw.orEmpty().split(",").mapNotNull { name -> TodayBlock.entries.firstOrNull { it.name == name.trim() } }.distinct()
        return if (seen.isEmpty()) defaults.blockOrder else seen + TodayBlock.entries.filter { it !in seen }
    }

    fun encodeHidden(hidden: Set<TodayBlock>): Set<String> = hidden.mapTo(sortedSetOf()) { it.name }

    /** `null` means "never saved" (use the design default); an empty set means "nothing is hidden". */
    fun decodeHidden(raw: Set<String>?): Set<TodayBlock> =
        if (raw == null) defaults.hiddenBlocks else raw.mapNotNull { name -> TodayBlock.entries.firstOrNull { it.name == name } }.toSet()

    fun <E : Enum<E>> decodeEnum(values: Array<E>, raw: String?, default: E): E = values.firstOrNull { it.name == raw } ?: default
}
