package com.utkarsh.todo.feature.focus.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.focus.domain.FocusAlarm
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusPhase { IDLE, RUNNING, PAUSED, FINISHED }

data class FocusUiState(
    /** False for the Timer tab, a timer that is not tied to any task. */
    val hasTask: Boolean = true,
    val taskTitle: String? = null,
    val taskDone: Boolean = false,
    val minutes: Int = DEFAULT_MINUTES,
    val phase: FocusPhase = FocusPhase.IDLE,
    /** Whole seconds left on the clock. */
    val remainingSeconds: Int = DEFAULT_MINUTES * 60,
    val message: String? = null,
) {
    /** `25:00`. */
    val clockLabel: String get() = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)

    /** 0 at the start, 1 when the time is up. */
    val progress: Float get() = 1f - remainingSeconds / (minutes * 60f)
}

const val DEFAULT_MINUTES = 25
val FocusPresets = listOf(15, 25, 45)

/**
 * A countdown for working on one task. The time left is always worked out from the clock, not by counting ticks,
 * so it stays right if the screen is off or the app is busy for a while. While it runs, the system is also asked for an
 * alarm at the end time, so a round that ends with the app closed still tells the person.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TasksRepository,
    private val clock: Clock,
    private val alarm: FocusAlarm,
) : ViewModel() {
    /** Empty when opened from the Timer tab: then it is a free timer with no task. */
    private val taskId: String = savedStateHandle.get<String>("taskId").orEmpty()
    private val hasTask = taskId.isNotEmpty()

    private val _state = MutableStateFlow(FocusUiState(hasTask = hasTask))
    val uiState: StateFlow<FocusUiState> = _state.asStateFlow()

    private var endsAt: Instant? = null
    private var ticker: Job? = null

    init {
        if (hasTask) {
            viewModelScope.launch {
                tasks.get(taskId).onSuccess { task -> _state.update { it.copy(taskTitle = task.title, taskDone = task.isDone) } }
            }
        }
    }

    /** Picks the length; only possible while nothing is running. */
    fun setMinutes(minutes: Int) {
        val phase = _state.value.phase
        if (phase == FocusPhase.RUNNING || phase == FocusPhase.PAUSED || minutes !in FocusPresets) return
        _state.update { it.copy(minutes = minutes, phase = FocusPhase.IDLE, remainingSeconds = minutes * 60) }
    }

    /** Starts, or carries on after a pause or a finished round (which starts a fresh one). */
    fun start() {
        val s = _state.value
        if (s.phase == FocusPhase.RUNNING) return
        val seconds = if (s.phase == FocusPhase.FINISHED) s.minutes * 60 else s.remainingSeconds
        endsAt = clock.instant().plusSeconds(seconds.toLong())
        _state.update { it.copy(phase = FocusPhase.RUNNING, remainingSeconds = seconds, message = null) }
        alarm.schedule(checkNotNull(endsAt), _state.value.taskTitle ?: DEFAULT_ALARM_TITLE)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                if (tick()) return@launch
                delay(TICK_MS)
            }
        }
    }

    fun pause() {
        if (_state.value.phase != FocusPhase.RUNNING) return
        ticker?.cancel()
        alarm.cancel()
        tick()
        if (_state.value.phase == FocusPhase.RUNNING) _state.update { it.copy(phase = FocusPhase.PAUSED) }
    }

    fun reset() {
        ticker?.cancel()
        alarm.cancel()
        endsAt = null
        _state.update { it.copy(phase = FocusPhase.IDLE, remainingSeconds = it.minutes * 60) }
    }

    fun markTaskDone() {
        if (!hasTask) return
        viewModelScope.launch {
            tasks.setDone(taskId, true)
                .onSuccess { _state.update { it.copy(taskDone = true, message = null) } }
                .onFailure { _state.update { it.copy(message = "Couldn't mark the task done") } }
        }
    }

    /** Updates the seconds left from the clock. Returns true once the time is up. */
    private fun tick(): Boolean {
        val end = endsAt ?: return true
        val millis = Duration.between(clock.instant(), end).toMillis()
        if (millis <= 0) {
            alarm.cancel() // the screen is open and shows it: no second ping from the system
            _state.update { it.copy(phase = FocusPhase.FINISHED, remainingSeconds = 0) }
            return true
        }
        val seconds = ((millis + 999) / 1000).toInt()
        _state.update { it.copy(remainingSeconds = seconds) }
        return false
    }

    /** The timer only lives as long as this screen's state does; the system must not announce a round nobody is running. */
    override fun onCleared() = alarm.cancel()

    companion object {
        const val TICK_MS = 250L
        private const val DEFAULT_ALARM_TITLE = "Focus time"
    }
}
