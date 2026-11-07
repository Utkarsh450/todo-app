import { notFound } from '../../lib/errors.js';
import type { Db } from '../../lib/prisma.js';
import { groupReminders } from './reminder-groups.js';
import type { CreateReminderInput, SnoozeInput, UpdateReminderInput } from './reminders.schema.js';

const isMissing = (e: unknown) => (e as { code?: string } | null)?.code === 'P2025';

export class RemindersService {
  constructor(
    private readonly db: Db,
    private readonly now: () => Date = () => new Date(),
  ) {}

  list() {
    return this.db.reminder.findMany({ orderBy: { remindAt: 'asc' } });
  }

  async grouped(bounds: { now: Date; endOfToday: Date; endOfWeek: Date }) {
    return groupReminders(await this.list(), bounds);
  }

  create(input: CreateReminderInput) {
    return this.db.reminder.create({ data: input });
  }

  async update(id: string, input: UpdateReminderInput) {
    try {
      return await this.db.reminder.update({ where: { id }, data: input });
    } catch (e) {
      if (isMissing(e)) throw notFound('Reminder');
      throw e;
    }
  }

  async snooze(id: string, input: SnoozeInput) {
    const until = 'minutes' in input ? new Date(this.now().getTime() + input.minutes * 60_000) : input.until;
    try {
      return await this.db.reminder.update({ where: { id }, data: { snoozedUntil: until, enabled: true } });
    } catch (e) {
      if (isMissing(e)) throw notFound('Reminder');
      throw e;
    }
  }

  async remove(id: string) {
    try {
      await this.db.reminder.delete({ where: { id } });
    } catch (e) {
      if (isMissing(e)) throw notFound('Reminder');
      throw e;
    }
  }
}
