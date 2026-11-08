package com.utkarsh.todo.feature.reminders

import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.platform.FileReminderAlarmStore
import java.io.File
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileReminderAlarmStoreTest {
    private lateinit var dir: File
    private lateinit var file: File

    @Before
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("alarm-store").toFile()
        file = File(dir, "scheduled.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun reminder(id: String, at: String = "2026-11-08T09:00:00Z", repeat: String? = null, snoozed: String? = null) =
        Reminder(id, "task-$id", "Title $id", Instant.parse(at), repeat, null, true, snoozed?.let(Instant::parse))

    @Test
    fun `an empty or missing store reads as empty`() {
        assertTrue(FileReminderAlarmStore(file).all().isEmpty())
    }

    @Test
    fun `what is put in comes back, including after a restart`() {
        FileReminderAlarmStore(file).put(reminder("a", repeat = "Every Day", snoozed = "2026-11-08T10:00:00Z"))
        FileReminderAlarmStore(file).put(reminder("b"))

        val back = FileReminderAlarmStore(file).all().associateBy { it.id }
        assertEquals(setOf("a", "b"), back.keys)
        assertEquals("Title a", back.getValue("a").title)
        assertEquals("Every Day", back.getValue("a").repeatRule)
        assertEquals(Instant.parse("2026-11-08T10:00:00Z"), back.getValue("a").snoozedUntil)
        assertEquals("task-a", back.getValue("a").taskId)
    }

    @Test
    fun `putting the same reminder again replaces it`() {
        val store = FileReminderAlarmStore(file)
        store.put(reminder("a", at = "2026-11-08T09:00:00Z"))
        store.put(reminder("a", at = "2026-11-09T09:00:00Z"))

        assertEquals(listOf(Instant.parse("2026-11-09T09:00:00Z")), store.all().map { it.remindAt })
    }

    @Test
    fun `removing forgets it and removing something unknown is harmless`() {
        val store = FileReminderAlarmStore(file)
        store.put(reminder("a"))
        store.put(reminder("b"))

        store.remove("a")
        store.remove("nope")

        assertEquals(listOf("b"), store.all().map { it.id })
    }

    @Test
    fun `a damaged file reads as empty and is replaced on the next write`() {
        file.writeText("{ this is not json")
        val store = FileReminderAlarmStore(file)
        assertTrue(store.all().isEmpty())

        store.put(reminder("a"))

        assertEquals(listOf("a"), FileReminderAlarmStore(file).all().map { it.id })
    }

    @Test
    fun `no temporary file is left behind`() {
        FileReminderAlarmStore(file).put(reminder("a"))
        assertEquals(listOf("scheduled.json"), dir.list()!!.toList())
    }
}
