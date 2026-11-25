package com.utkarsh.todo.feature.memory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.offline.cacheFirst
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.ai.domain.MemoryFact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The categories in the order the screen shows them, with their headings. */
private val CATEGORY_ORDER = listOf(
    "ROUTINE" to "Routines",
    "PREFERENCE" to "Preferences",
    "PERSON" to "People",
    "COMMITMENT" to "Commitments",
    "OTHER" to "Other",
)

data class MemoryUiState(
    /** True until the server has answered (a saved copy may already be on screen). */
    val isLoading: Boolean = true,
    val facts: List<MemoryFact> = emptyList(),
    /** Why the list could not be loaded; only set when there is nothing to show. */
    val error: String? = null,
    /** Something that did not work (a refused fact, a failed delete); dismissed by the screen. */
    val message: String? = null,
    /** The id of the fact being edited. */
    val editing: String? = null,
    val confirmingForgetAll: Boolean = false,
) {
    /** Facts under their category heading, in a fixed order; a heading with no facts is left out. */
    val groups: List<Pair<String, List<MemoryFact>>>
        get() {
            val known = CATEGORY_ORDER.map { it.first }.toSet()
            return CATEGORY_ORDER.mapNotNull { (key, heading) ->
                facts.filter { (it.category.takeIf { c -> c in known } ?: "OTHER") == key }.takeIf { it.isNotEmpty() }?.let { heading to it }
            }
        }
}

/** What the assistant remembers about the owner: read it, add to it, correct it, delete any of it. */
@HiltViewModel
class MemoryViewModel @Inject constructor(private val ai: AiRepository) : ViewModel() {
    private val _state = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** The copy this phone already has first (instant), then the server's answer. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            cacheFirst { ai.memories() }.onSuccess { saved -> _state.update { it.copy(facts = saved) } }
            ai.memories()
                .onSuccess { facts -> _state.update { it.copy(isLoading = false, facts = facts, error = null) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = if (it.facts.isEmpty()) e.message ?: "Couldn't load what I remember." else null) } }
        }
    }

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            ai.addMemory(clean)
                .onSuccess { fact -> _state.update { s -> s.copy(facts = listOf(fact) + s.facts.filterNot { it.id == fact.id }) } }
                .onFailure(::say)
        }
    }

    fun startEdit(id: String) = _state.update { it.copy(editing = id) }

    fun cancelEdit() = _state.update { it.copy(editing = null) }

    fun saveEdit(id: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            ai.updateMemory(id, clean)
                .onSuccess { fact -> _state.update { s -> s.copy(facts = s.facts.map { if (it.id == id) fact else it }, editing = null) } }
                .onFailure(::say)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            ai.deleteMemory(id)
                .onSuccess { remove(id) }
                .onFailure { e ->
                    // Already gone on the server: the list was out of date, so just drop it.
                    if ((e as? AiException)?.notFound == true) remove(id) else _state.update { it.copy(message = "Couldn't delete that. Try again.") }
                }
        }
    }

    fun askForgetAll() = _state.update { it.copy(confirmingForgetAll = true) }

    fun cancelForgetAll() = _state.update { it.copy(confirmingForgetAll = false) }

    fun confirmForgetAll() {
        viewModelScope.launch {
            ai.deleteAllMemories()
                .onSuccess { _state.update { it.copy(facts = emptyList(), editing = null, confirmingForgetAll = false) } }
                .onFailure { _state.update { it.copy(confirmingForgetAll = false, message = "Couldn't forget everything. Try again.") } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun remove(id: String) = _state.update { s -> s.copy(facts = s.facts.filterNot { it.id == id }, editing = s.editing.takeIf { it != id }) }

    private fun say(e: Throwable) = _state.update { it.copy(message = e.message ?: "Couldn't do that. Try again.") }
}
