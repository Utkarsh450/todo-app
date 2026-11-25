package com.utkarsh.todo.feature.reminders

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import com.utkarsh.todo.core.offline.CacheFirst
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderGroups
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.reminders.ui.RemindersViewModel
import com.utkarsh.todo.feature.reminders.ui.SectionKind
import com.utkarsh.todo.feature.reminders.ui.SnoozeOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-11-07T05:30:00Z") // Sat 7 Nov 11:00 IST
    private val clock = Clock.fixed(now, zone)
    private val repository = mockk<RemindersRepository>()

    private fun reminder(id: String, at: String, enabled: Boolean = true, snoozed: String? = null, repeat: String? = null, location: String? = null) =
        Reminder(id, null, "Title $id", Instant.parse(at), repeat, location, enabled, snoozed?.let(Instant::parse))

    private val endOfToday = Instant.parse("2026-11-07T18:30:00Z")
    private val endOfWeek = Instant.parse("2026-11-08T18:30:00Z") // next Monday 00:00 IST is Nov 9 -> 18:30Z Nov 8

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun load(groups: ReminderGroups): RemindersViewModel {
        coEvery { repository.grouped(now, endOfToday, endOfWeek) } returns Result.success(groups)
        return RemindersViewModel(repository, clock)
    }

    @Test
    fun `the saved list shows at once and the server's list then replaces it`() = runTest {
        val gate = CompletableDeferred<Result<ReminderGroups>>()
        coEvery { repository.grouped(now, endOfToday, endOfWeek) } coAnswers {
            if (CacheFirst.isActive()) Result.success(ReminderGroups(today = listOf(reminder("old", "2026-11-07T08:30:00Z")))) else gate.await()
        }

        val vm = RemindersViewModel(repository, clock)

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, vm.uiState.value.sections.sumOf { it.rows.size })

        gate.complete(Result.success(ReminderGroups(today = listOf(reminder("a", "2026-11-07T08:30:00Z"), reminder("b", "2026-11-07T09:30:00Z")))))
        assertEquals(2, vm.uiState.value.sections.sumOf { it.rows.size })
    }

    @Test
    fun `builds sections in order and drops empty ones`() {
        val vm = load(
            ReminderGroups(
                overdue = listOf(reminder("o", "2026-11-06T14:30:00Z")),
                today = listOf(reminder("t", "2026-11-07T08:30:00Z", repeat = "Every 2 Hours")),
                later = listOf(reminder("l", "2026-12-01T03:30:00Z")),
            ),
        )

        val sections = vm.uiState.value.sections
        assertEquals(listOf(SectionKind.OVERDUE, SectionKind.TODAY, SectionKind.LATER), sections.map { it.kind })
        val today = sections[1].rows.single()
        assertEquals("2:00", today.time)
        assertEquals("PM", today.meridiem)
        assertEquals("↻ Every 2 Hours", today.repeat)
        assertNull(today.meta) // later today: the time column already says it
        assertEquals("Yesterday", sections[0].rows.single().meta)
        assertEquals("Tue 1 Dec", sections[2].rows.single().meta)
    }

    @Test
    fun `hero is the first enabled upcoming reminder with a relative subtitle`() {
        val vm = load(
            ReminderGroups(
                overdue = listOf(reminder("o", "2026-11-06T14:30:00Z")),
                today = listOf(reminder("off", "2026-11-07T05:50:00Z", enabled = false), reminder("next", "2026-11-07T05:49:00Z")),
            ),
        )

        val hero = vm.uiState.value.hero!!
        assertEquals("next", hero.id)
        assertEquals("In 19 min · 11:19 AM", hero.subtitle)
        assertFalse(hero.snoozed)
    }

    @Test
    fun `a snoozed reminder shows the snoozed tag and its snooze time`() {
        val vm = load(ReminderGroups(today = listOf(reminder("s", "2026-11-06T03:30:00Z", snoozed = "2026-11-07T07:30:00Z"))))

        val state = vm.uiState.value
        assertTrue(state.hero!!.snoozed)
        assertEquals("Snoozed", state.sections.single().rows.single().meta)
        assertEquals("1:00", state.sections.single().rows.single().time)
    }

    @Test
    fun `no hero when nothing is upcoming`() {
        assertNull(load(ReminderGroups(overdue = listOf(reminder("o", "2026-11-06T14:30:00Z")))).uiState.value.hero)
    }

    @Test
    fun `location triggers are shown`() {
        val vm = load(ReminderGroups(today = listOf(reminder("g", "2026-11-07T12:00:00Z", location = "When I leave the office"))))
        assertEquals("⌖ When I leave the office", vm.uiState.value.sections.single().rows.single().location)
    }

    @Test
    fun `load failure shows an error`() {
        coEvery { repository.grouped(any(), any(), any()) } returns Result.failure(RuntimeException("offline"))
        assertEquals("offline", RemindersViewModel(repository, clock).uiState.value.error)
    }

    @Test
    fun `toggling asks the repository and reloads`() {
        val vm = load(ReminderGroups(today = listOf(reminder("t", "2026-11-07T08:30:00Z"))))
        coEvery { repository.setEnabled("t", false) } returns Result.success(reminder("t", "2026-11-07T08:30:00Z", enabled = false))

        vm.setEnabled("t", false)

        coVerify { repository.setEnabled("t", false) }
        coVerify(exactly = 3) { repository.grouped(now, endOfToday, endOfWeek) } // first load asks twice (saved copy, server), then the reload
    }

    @Test
    fun `hero snooze options map to minutes or tomorrow 9am`() {
        val vm = load(ReminderGroups(today = listOf(reminder("n", "2026-11-07T05:49:00Z"))))
        val until = slot<Instant>()
        coEvery { repository.snooze("n", any()) } returns Result.success(reminder("n", "2026-11-07T05:49:00Z"))
        coEvery { repository.snoozeUntil("n", capture(until)) } returns Result.success(reminder("n", "2026-11-07T05:49:00Z"))

        vm.snoozeHero(SnoozeOption.TEN_MINUTES)
        vm.snoozeHero(SnoozeOption.ONE_HOUR)
        vm.snoozeHero(SnoozeOption.TOMORROW)

        coVerify { repository.snooze("n", 10) }
        coVerify { repository.snooze("n", 60) }
        assertEquals(Instant.parse("2026-11-08T03:30:00Z"), until.captured) // Sun 9:00 IST
    }

    @Test
    fun `swipe right snoozes the row for an hour`() {
        val vm = load(ReminderGroups(today = listOf(reminder("t", "2026-11-07T08:30:00Z"))))
        coEvery { repository.snooze("t", 60) } returns Result.success(reminder("t", "2026-11-07T08:30:00Z"))

        vm.snoozeRow("t")

        coVerify { repository.snooze("t", 60) }
    }

    @Test
    fun `delete shows undo and undo recreates the reminder`() {
        val original = reminder("t", "2026-11-07T08:30:00Z", repeat = "Every Day")
        val vm = load(ReminderGroups(today = listOf(original)))
        coEvery { repository.delete("t") } returns Result.success(Unit)
        val created = slot<NewReminder>()
        coEvery { repository.create(capture(created)) } returns Result.success(original)

        vm.delete("t")
        assertEquals("Reminder deleted", vm.uiState.value.undo?.message)

        vm.undoDelete()

        assertNull(vm.uiState.value.undo)
        assertEquals(NewReminder("Title t", original.remindAt, null, "Every Day", null), created.captured)
    }

    @Test
    fun `dismissing the undo forgets the deleted reminder`() {
        val vm = load(ReminderGroups(today = listOf(reminder("t", "2026-11-07T08:30:00Z"))))
        coEvery { repository.delete("t") } returns Result.success(Unit)
        vm.delete("t")

        vm.dismissUndo()
        vm.undoDelete() // nothing to restore

        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun `failed delete reports an error and offers no undo`() {
        val vm = load(ReminderGroups(today = listOf(reminder("t", "2026-11-07T08:30:00Z"))))
        coEvery { repository.delete("t") } returns Result.failure(RuntimeException("x"))

        vm.delete("t")

        assertEquals("Couldn't delete reminder", vm.uiState.value.error)
        assertNull(vm.uiState.value.undo)
    }
}
