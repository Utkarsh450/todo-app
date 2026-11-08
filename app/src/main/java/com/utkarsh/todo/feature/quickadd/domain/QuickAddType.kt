package com.utkarsh.todo.feature.quickadd.domain

/** What the quick-add sheet is creating. */
enum class QuickAddType {
    TASK,
    NOTE,
    EVENT;

    /**
     * The token kinds typed text is scanned for. A note is taken exactly as written, and an event only
     * understands a day/time and a project, so other words stay in what you typed.
     */
    val kinds: Set<TokenKind>
        get() = when (this) {
            TASK -> TokenKind.entries.toSet()
            EVENT -> setOf(TokenKind.DAY, TokenKind.PROJECT)
            NOTE -> emptySet()
        }
}
