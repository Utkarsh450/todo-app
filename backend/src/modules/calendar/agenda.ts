import type { AgendaItem } from './calendar.schema.js';

interface EventLike { id: string; title: string; startAt: Date; endAt: Date; allDay: boolean; projectId: string | null }
interface ReminderLike { id: string; title: string; remindAt: Date; snoozedUntil: Date | null }
interface TaskLike { id: string; title: string; dueAt: Date | null; status: string; projectId: string | null }

/**
 * Merges events, dated tasks and stand-alone reminders into a single chronological agenda.
 * All-day items sort before timed items on the same day; ties fall back to the title.
 */
export function buildAgenda(events: readonly EventLike[], tasks: readonly TaskLike[], reminders: readonly ReminderLike[] = []): AgendaItem[] {
  const items: AgendaItem[] = [
    ...events.map((e): AgendaItem => ({ kind: 'event', id: e.id, title: e.title, startAt: e.startAt, endAt: e.endAt, allDay: e.allDay, projectId: e.projectId, done: false })),
    ...tasks.flatMap((t): AgendaItem[] =>
      t.dueAt ? [{ kind: 'task', id: t.id, title: t.title, startAt: t.dueAt, endAt: null, allDay: false, projectId: t.projectId, done: t.status === 'DONE' }] : [],
    ),
    ...reminders.map((r): AgendaItem => ({ kind: 'reminder', id: r.id, title: r.title, startAt: r.snoozedUntil ?? r.remindAt, endAt: null, allDay: false, projectId: null, done: false })),
  ];
  return items.sort((a, b) => {
    const day = (d: Date) => d.toISOString().slice(0, 10);
    if (day(a.startAt) === day(b.startAt) && a.allDay !== b.allDay) return a.allDay ? -1 : 1;
    return a.startAt.getTime() - b.startAt.getTime() || a.title.localeCompare(b.title);
  });
}
