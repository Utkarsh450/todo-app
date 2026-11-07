import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { countByDay, fillDays, localDay, localDayStart, summarize } from '../src/modules/insights/heatmap.js';
import { createTestApp, resetDatabase, type TestContext } from './helpers.js';

describe('heatmap helpers (unit)', () => {
  it('converts an instant to the local day for the zone offset', () => {
    const instant = new Date('2026-11-07T20:00:00Z');
    expect(localDay(instant, 0)).toBe('2026-11-07');
    expect(localDay(instant, 330)).toBe('2026-11-08'); // IST rolls past midnight
    expect(localDay(instant, -480)).toBe('2026-11-07');
    expect(localDayStart('2026-11-08', 330).toISOString()).toBe('2026-11-07T18:30:00.000Z');
  });

  it('zero-fills the whole window inclusively', () => {
    const days = fillDays('2026-11-05', '2026-11-08', countByDay(['2026-11-06', '2026-11-06', '2026-11-08']));
    expect(days).toEqual([
      { date: '2026-11-05', count: 0 },
      { date: '2026-11-06', count: 2 },
      { date: '2026-11-07', count: 0 },
      { date: '2026-11-08', count: 1 },
    ]);
  });

  it('computes streak, best, total and weekday rhythm', () => {
    // 2026-11-01 is a Sunday.
    const days = fillDays('2026-11-01', '2026-11-08', countByDay(['2026-11-01', '2026-11-06', '2026-11-07', '2026-11-07', '2026-11-08']));
    const s = summarize(days);
    expect(s.total).toBe(5);
    expect(s.best).toBe(2);
    expect(s.streak).toBe(3); // Nov 6, 7, 8 consecutive
    expect(s.rhythm).toHaveLength(7);
    expect(s.rhythm[0]).toBe(1); // Sundays: Nov 1 (1) and Nov 8 (1) -> avg 1
    expect(s.rhythm[6]).toBe(2); // the only Saturday (Nov 7) has 2
  });

  it('has a zero streak when the latest day is empty', () => {
    expect(summarize(fillDays('2026-11-01', '2026-11-03', countByDay(['2026-11-01']))).streak).toBe(0);
  });
});

describe('insights API (integration)', () => {
  let ctx: TestContext;
  beforeAll(async () => { ctx = await createTestApp(); });
  beforeEach(async () => { await resetDatabase(ctx.db); });
  afterAll(async () => { await ctx.app.close(); });

  it('counts completed tasks per local day', async () => {
    const make = async (title: string) => (await ctx.app.inject({ method: 'POST', url: '/api/tasks', payload: { title } })).json();
    const a = await make('a');
    const b = await make('b');
    await make('never done');
    await ctx.db.task.update({ where: { id: a.id }, data: { status: 'DONE', completedAt: new Date('2026-11-07T20:00:00Z') } });
    await ctx.db.task.update({ where: { id: b.id }, data: { status: 'DONE', completedAt: new Date('2026-11-07T05:00:00Z') } });

    const utc = await ctx.app.inject({ method: 'GET', url: '/api/insights/heatmap?mode=tasks&from=2026-11-06&to=2026-11-08' });
    expect(utc.json().days).toEqual([
      { date: '2026-11-06', count: 0 },
      { date: '2026-11-07', count: 2 },
      { date: '2026-11-08', count: 0 },
    ]);

    const ist = await ctx.app.inject({ method: 'GET', url: '/api/insights/heatmap?mode=tasks&from=2026-11-06&to=2026-11-08&tzOffsetMinutes=330' });
    expect(ist.json().days.map((d: { count: number }) => d.count)).toEqual([0, 1, 1]); // 20:00Z is Nov 8 in IST
    expect(ist.json().summary).toMatchObject({ total: 2, best: 1, streak: 2 });
  });

  it('counts kept habit days', async () => {
    const habit = (await ctx.app.inject({ method: 'POST', url: '/api/habits', payload: { name: 'Read' } })).json();
    for (const d of ['2026-11-06', '2026-11-07']) await ctx.app.inject({ method: 'PUT', url: `/api/habits/${habit.id}/logs/${d}`, payload: { done: true } });
    const res = await ctx.app.inject({ method: 'GET', url: '/api/insights/heatmap?mode=habits&from=2026-11-05&to=2026-11-07' });
    expect(res.json().days.map((d: { count: number }) => d.count)).toEqual([0, 1, 1]);
    expect(res.json().summary.streak).toBe(2);
  });

  it('validates the window', async () => {
    expect((await ctx.app.inject({ method: 'GET', url: '/api/insights/heatmap?from=2026-11-08&to=2026-11-01' })).statusCode).toBe(400);
    expect((await ctx.app.inject({ method: 'GET', url: '/api/insights/heatmap?from=2024-01-01&to=2026-11-01' })).statusCode).toBe(400);
  });
});
