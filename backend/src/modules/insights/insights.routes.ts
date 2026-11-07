import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';
import { dayString } from '../habits/habits.schema.js';
import { countByDay, fillDays, localDay, localDayStart, summarize } from './heatmap.js';

const query = z
  .object({
    mode: z.enum(['tasks', 'habits']).default('tasks'),
    from: dayString,
    to: dayString,
    tzOffsetMinutes: z.coerce.number().int().min(-840).max(840).default(0),
  })
  .refine((v) => v.to >= v.from, { message: 'to must not be before from', path: ['to'] })
  .refine((v) => Date.parse(v.to) - Date.parse(v.from) <= 400 * 86_400_000, { message: 'window is limited to 400 days', path: ['to'] });

const response = z.object({
  mode: z.enum(['tasks', 'habits']),
  days: z.array(z.object({ date: z.string(), count: z.number().int() })),
  summary: z.object({ total: z.number(), streak: z.number(), best: z.number(), rhythm: z.array(z.number()) }),
});

/** Heat-map data for the Insights screen: tasks completed or habits kept per day. */
export const insightRoutes: FastifyPluginAsyncZod = async (app) => {
  app.get('/insights/heatmap', { schema: { querystring: query, response: { 200: response } } }, async (req) => {
    const { mode, from, to, tzOffsetMinutes } = req.query;
    let dates: string[];
    if (mode === 'tasks') {
      const done = await app.db.task.findMany({
        where: { completedAt: { gte: localDayStart(from, tzOffsetMinutes), lt: new Date(localDayStart(to, tzOffsetMinutes).getTime() + 86_400_000) } },
        select: { completedAt: true },
      });
      dates = done.flatMap((t) => (t.completedAt ? [localDay(t.completedAt, tzOffsetMinutes)] : []));
    } else {
      const logs = await app.db.habitLog.findMany({
        where: { date: { gte: new Date(`${from}T00:00:00Z`), lte: new Date(`${to}T00:00:00Z`) } },
        select: { date: true },
      });
      dates = logs.map((l) => l.date.toISOString().slice(0, 10));
    }
    const days = fillDays(from, to, countByDay(dates));
    return { mode, days, summary: summarize(days) };
  });
};
