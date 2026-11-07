package com.utkarsh.todo.feature.projects.domain

/** A label that groups tasks (Design, Engineering, ...). [colorHex] is `#RRGGBB`. */
data class Project(val id: String, val name: String, val colorHex: String)
