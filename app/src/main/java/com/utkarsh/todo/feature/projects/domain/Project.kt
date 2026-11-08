package com.utkarsh.todo.feature.projects.domain

/** A label that groups tasks (Design, Engineering, ...). [colorHex] is `#RRGGBB`. */
data class Project(val id: String, val name: String, val colorHex: String)

/** Colours offered when making a project: the five from the design, then a few more. */
val ProjectPalette: List<String> = listOf("#935EDF", "#2784D5", "#DF911A", "#2FA465", "#E05D3D", "#D9548B", "#1F9AA5", "#6E675C")
