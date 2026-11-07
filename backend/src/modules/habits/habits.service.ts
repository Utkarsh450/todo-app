import { notFound } from '../../lib/errors.js';
import type { Db } from '../../lib/prisma.js';

const isMissing = (e: unknown) => (e as { code?: string } | null)?.code === 'P2025';
const day = (s: string) => new Date(`${s}T00:00:00Z`);
const fmt = (d: Date) => d.toISOString().slice(0, 10);

export class HabitsService {
  constructor(private readonly db: Db) {}

  /** Habits with the days they were kept between `from` and `to` inclusive. */
  async list(from: string, to: string) {
    const habits = await this.db.habit.findMany({
      orderBy: { createdAt: 'asc' },
      include: { logs: { where: { date: { gte: day(from), lte: day(to) } }, orderBy: { date: 'asc' } } },
    });
    return habits.map(({ logs, ...h }) => ({ ...h, days: logs.map((l) => fmt(l.date)) }));
  }

  async create(name: string) {
    const h = await this.db.habit.create({ data: { name } });
    return { ...h, days: [] as string[] };
  }

  async rename(id: string, name: string) {
    try {
      const h = await this.db.habit.update({ where: { id }, data: { name } });
      return { ...h, days: [] as string[] };
    } catch (e) {
      if (isMissing(e)) throw notFound('Habit');
      throw e;
    }
  }

  async remove(id: string) {
    try {
      await this.db.habit.delete({ where: { id } });
    } catch (e) {
      if (isMissing(e)) throw notFound('Habit');
      throw e;
    }
  }

  /** Marks a habit kept (or not) on a given day. Idempotent in both directions. */
  async setDone(id: string, date: string, done: boolean) {
    if (!(await this.db.habit.findUnique({ where: { id }, select: { id: true } }))) throw notFound('Habit');
    const where = { habitId_date: { habitId: id, date: day(date) } };
    if (done) await this.db.habitLog.upsert({ where, create: { habitId: id, date: day(date) }, update: {} });
    else await this.db.habitLog.deleteMany({ where: { habitId: id, date: day(date) } });
    return { habitId: id, date, done };
  }
}
