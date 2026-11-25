package com.utkarsh.todo.feature.focus.domain

import java.time.Instant

/** Tells the person that a focus round is over even when the app is closed or the phone is locked. */
interface FocusAlarm {
    /** Replaces any earlier alarm: there is only ever one timer. [title] names what was being worked on. */
    fun schedule(at: Instant, title: String)

    fun cancel()
}
