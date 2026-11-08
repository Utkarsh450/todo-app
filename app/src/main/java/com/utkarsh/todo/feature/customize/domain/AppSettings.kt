package com.utkarsh.todo.feature.customize.domain

/** The blocks the Today screen can show. Declaration order is the default order. */
enum class TodayBlock(val label: String, val hint: String) {
    PINNED("Pinned", "Your one big task"),
    TASKS("Today's list", "Everything due today"),
    HABITS("Habits", "This week's streaks"),
    SCRATCHPAD("Scratchpad", "A daily free-form note"),
}

/** The paper texture behind every screen. */
enum class PaperBackground(val label: String) { DOTS("Dots"), LINES("Lines"), PLAIN("Plain") }

/** How tightly rows are packed. */
enum class RowDensity(val label: String) { COZY("Cozy"), COMPACT("Compact") }

/**
 * Everything the Customize screen edits. [blockOrder] always lists every block exactly once, so a hidden
 * block keeps its place and comes back where it was when it is switched on again.
 */
data class AppSettings(
    val blockOrder: List<TodayBlock> = TodayBlock.entries.toList(),
    /** The design ships with the scratchpad off. */
    val hiddenBlocks: Set<TodayBlock> = setOf(TodayBlock.SCRATCHPAD),
    val background: PaperBackground = PaperBackground.PLAIN,
    val density: RowDensity = RowDensity.COZY,
) {
    init {
        require(blockOrder.size == TodayBlock.entries.size && blockOrder.toSet() == TodayBlock.entries.toSet()) {
            "blockOrder must list every block exactly once: $blockOrder"
        }
    }

    /** The blocks to draw on Today, in order. */
    val visibleBlocks: List<TodayBlock> get() = blockOrder.filter { it !in hiddenBlocks }

    fun isVisible(block: TodayBlock): Boolean = block !in hiddenBlocks

    fun withVisible(block: TodayBlock, visible: Boolean): AppSettings =
        copy(hiddenBlocks = if (visible) hiddenBlocks - block else hiddenBlocks + block)

    fun canMoveUp(block: TodayBlock): Boolean = blockOrder.first() != block

    fun canMoveDown(block: TodayBlock): Boolean = blockOrder.last() != block

    /** Swaps [block] with its neighbour: [delta] -1 moves it up, +1 down. Moving past an end does nothing. */
    fun moved(block: TodayBlock, delta: Int): AppSettings {
        val from = blockOrder.indexOf(block)
        val to = from + delta
        if (delta !in -1..1 || delta == 0 || to !in blockOrder.indices) return this
        return copy(blockOrder = blockOrder.toMutableList().also { it[from] = it[to]; it[to] = block })
    }
}
