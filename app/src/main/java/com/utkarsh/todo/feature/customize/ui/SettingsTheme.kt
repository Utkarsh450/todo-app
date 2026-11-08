package com.utkarsh.todo.feature.customize.ui

import com.utkarsh.todo.core.designsystem.theme.Density
import com.utkarsh.todo.core.designsystem.theme.PaperPattern
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity

/** Maps the saved choices (domain) onto the design system's theme parameters (UI). */
fun RowDensity.toDensity(): Density = when (this) {
    RowDensity.COZY -> Density.Cozy
    RowDensity.COMPACT -> Density.Compact
}

fun PaperBackground.toPattern(): PaperPattern = when (this) {
    PaperBackground.DOTS -> PaperPattern.Dots
    PaperBackground.LINES -> PaperPattern.Lines
    PaperBackground.PLAIN -> PaperPattern.Plain
}
