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

  async updateEvent(id: string, input: UpdateEventInput) {
    try {
      return await this.db.calendarEvent.update({ where: { id }, data: input });
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

  /** Events plus non-archived dated tasks within [from, to). */
  async agenda(from: Date, to: Date) {
    const [events, tasks] = await Promise.all([
      this.listEvents(from, to),
      this.db.task.findMany({ where: { archived: false, dueAt: { gte: from, lt: to } } }),
    ]);
    return buildAgenda(events, tasks);
  }
}
