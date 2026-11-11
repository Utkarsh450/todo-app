import { notFound } from '../../lib/errors.js';
import type { Db } from '../../lib/prisma.js';
import { buildAgenda } from './agenda.js';
import type { CreateEventInput, UpdateEventInput } from './calendar.schema.js';

const isMissing = (e: unknown) => (e as { code?: string } | null)?.code === 'P2025';

export class CalendarService {
  constructor(private readonly db: Db) {}

  listEvents(from: Date, to: Date) {
    return this.db.calendarEvent.findMany({ where: { startAt: { gte: from, lt: to } }, orderBy: { startAt: 'asc' } });
  }

  createEvent(input: CreateEventInput) {
    return this.db.calendarEvent.create({ data: input });
  }

  async getEvent(id: string) {
    const event = await this.db.calendarEvent.findUnique({ where: { id } });
    if (!event) throw notFound('Event');
    return event;
  }

  /** Moving an event moves its alerts by the same amount, so "30 min before" stays 30 min before. */
  async updateEvent(id: string, input: UpdateEventInput) {
    try {
      const before = input.startAt ? await this.db.calendarEvent.findUnique({ where: { id }, select: { startAt: true } }) : null;
      const updated = await this.db.calendarEvent.update({ where: { id }, data: input });
      if (before && input.startAt) {
        const delta = input.startAt.getTime() - before.startAt.getTime();
        if (delta !== 0) {
          const alerts = await this.db.reminder.findMany({ where: { eventId: id } });
          await Promise.all(alerts.map((a) => this.db.reminder.update({
            where: { id: a.id },
            data: { remindAt: new Date(a.remindAt.getTime() + delta), snoozedUntil: null },
          })));
        }
      }
      return updated;
    } catch (e) {
      if (isMissing(e)) throw notFound('Event');
      throw e;
    }
  }

  async removeEvent(id: string) {
    try {
      await this.db.calendarEvent.delete({ where: { id } });
    } catch (e) {
      if (isMissing(e)) throw notFound('Event');
      throw e;
    }
  }

  /**
   * Events, non-archived dated tasks and stand-alone reminders within [from, to). A reminder that belongs to a task
   * or is an event's alert is not listed on its own: it would only repeat the task or the event.
   */
  async agenda(from: Date, to: Date) {
    const [events, tasks, reminders] = await Promise.all([
      this.listEvents(from, to),
      this.db.task.findMany({ where: { archived: false, dueAt: { gte: from, lt: to } } }),
      this.db.reminder.findMany({
        where: {
          enabled: true,
          taskId: null,
          eventId: null,
          OR: [{ snoozedUntil: null, remindAt: { gte: from, lt: to } }, { snoozedUntil: { gte: from, lt: to } }],
        },
      }),
    ]);
    return buildAgenda(events, tasks, reminders);
  }
}
