import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { dayString, windowQuery } from '../src/modules/habits/habits.schema.js';
import { createTestApp, resetDatabase, type TestContext } from './helpers.js';

describe('dayString (unit)', () => {
  it('accepts real dates only', () => {
    expect(dayString.safeParse('2026-11-07').success).toBe(true);
    expect(dayString.safeParse('2026-02-30').success).toBe(false);
    expect(dayString.safeParse('07/11/2026').success).toBe(false);
  });
  it('requires an ordered window', () => {
    expect(windowQuery.safeParse({ from: '2026-11-07', to: '2026-11-01' }).success).toBe(false);
  });
});

describe('habits API (integration)', () => {
  let ctx: TestContext;
  beforeAll(async () => { ctx = await createTestApp(); });
  beforeEach(async () => { await resetDatabase(ctx.db); });
  afterAll(async () => { await ctx.app.close(); });

  const createHabit = async (name: string) => (await ctx.app.inject({ method: 'POST', url: '/api/habits', payload: { name } })).json();
  const setDone = (id: string, date: string, done: boolean) =>
    ctx.app.inject({ method: 'PUT', url: `/api/habits/${id}/logs/${date}`, payload: { done } });

  it('records kept days idempotently and lists them inside the window', async () => {
    const habit = await createHabit('Read 20 min');
    await setDone(habit.id, '2026-11-05', true);
    await setDone(habit.id, '2026-11-06', true);
    await setDone(habit.id, '2026-11-06', true); // repeat: still one log
    await setDone(habit.id, '2026-10-01', true); // outside window

    const list = await ctx.app.inject({ method: 'GET', url: '/api/habits?from=2026-11-01&to=2026-11-07' });
    expect(list.json()).toHaveLength(1);
    expect(list.json()[0].days).toEqual(['2026-11-05', '2026-11-06']);
  });

  it('un-marks a day', async () => {
    const habit = await createHabit('Water');
    await setDone(habit.id, '2026-11-07', true);
    await setDone(habit.id, '2026-11-07', false);
    const list = await ctx.app.inject({ method: 'GET', url: '/api/habits?from=2026-11-07&to=2026-11-07' });
    expect(list.json()[0].days).toEqual([]);
  });

  it('renames, deletes and 404s for unknown habits', async () => {
    const habit = await createHabit('Old');
    const renamed = await ctx.app.inject({ method: 'PATCH', url: `/api/habits/${habit.id}`, payload: { name: 'New' } });
    expect(renamed.json().name).toBe('New');
    await ctx.app.inject({ method: 'DELETE', url: `/api/habits/${habit.id}` });
    expect((await setDone(habit.id, '2026-11-07', true)).statusCode).toBe(404);
    expect((await ctx.app.inject({ method: 'DELETE', url: `/api/habits/${habit.id}` })).statusCode).toBe(404);
  });

  it('rejects malformed days with 400', async () => {
    const habit = await createHabit('x');
    expect((await setDone(habit.id, '2026-13-40', true)).statusCode).toBe(400);
  });
});

describe('notes API (integration)', () => {
  let ctx: TestContext;
  beforeAll(async () => { ctx = await createTestApp(); });
  beforeEach(async () => { await resetDatabase(ctx.db); });
  afterAll(async () => { await ctx.app.close(); });

  it('reads an empty note for an untouched day, then saves and updates it', async () => {
    expect((await ctx.app.inject({ method: 'GET', url: '/api/notes/2026-11-07' })).json()).toEqual({ date: '2026-11-07', text: '' });
    await ctx.app.inject({ method: 'PUT', url: '/api/notes/2026-11-07', payload: { text: 'Ask Sam about Room' } });
    await ctx.app.inject({ method: 'PUT', url: '/api/notes/2026-11-07', payload: { text: 'Ask Sam about Room v3' } });
    expect((await ctx.app.inject({ method: 'GET', url: '/api/notes/2026-11-07' })).json().text).toBe('Ask Sam about Room v3');
    expect(await ctx.db.dailyNote.count()).toBe(1);
  });
});
