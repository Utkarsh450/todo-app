import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { effectiveTime, groupReminders } from '../src/modules/reminders/reminder-groups.js';
import { RemindersService } from '../src/modules/reminders/reminders.service.js';
import { createReminderBody, snoozeBody } from '../src/modules/reminders/reminders.schema.js';
import { createTestApp, resetDatabase, type TestContext } from './helpers.js';

const at = (iso: string) => new Date(iso);
const r = (name: string, remindAt: string, extra: object = {}) => ({ name, remindAt: at(remindAt), snoozedUntil: null, enabled: true, ...extra });
const bounds = { now: at('2026-11-07T10:00:00Z'), endOfToday: at('2026-11-08T00:00:00Z'), endOfWeek: at('2026-11-14T00:00:00Z') };

describe('groupReminders (unit)', () => {
  it('buckets into overdue / today / this week / later, each sorted by time', () => {
    const g = groupReminders(
      [
        r('later', '2026-12-01T09:00:00Z'),
        r('week', '2026-11-10T09:00:00Z'),
        r('today-pm', '2026-11-07T17:00:00Z'),
        r('today-am', '2026-11-07T11:00:00Z'),
        r('overdue', '2026-11-06T20:00:00Z'),
      ],
      bounds,
    );
    expect(g.overdue.map((x) => x.name)).toEqual(['overdue']);
    expect(g.today.map((x) => x.name)).toEqual(['today-am', 'today-pm']);
    expect(g.thisWeek.map((x) => x.name)).toEqual(['week']);
    expect(g.later.map((x) => x.name)).toEqual(['later']);
  });

  it('uses the snooze time instead of the original time', () => {
    const snoozed = r('snoozed', '2026-11-06T08:00:00Z', { snoozedUntil: at('2026-11-07T18:00:00Z') });
    expect(effectiveTime(snoozed)).toEqual(at('2026-11-07T18:00:00Z'));
    expect(groupReminders([snoozed], bounds).today).toHaveLength(1);
  });

  it('never marks a disabled reminder as overdue', () => {
    const g = groupReminders([r('off', '2026-11-06T08:00:00Z', { enabled: false })], bounds);
    expect(g.overdue).toHaveLength(0);
    expect(g.today).toHaveLength(1);
  });
});

describe('alert style (unit)', () => {
  it('is NOTIFICATION or ALARM, nothing else', () => {
    expect(createReminderBody.safeParse({ title: 'x', remindAt: '2026-11-08T09:00:00Z', style: 'ALARM' }).success).toBe(true);
    expect(createReminderBody.safeParse({ title: 'x', remindAt: '2026-11-08T09:00:00Z', style: 'NOTIFICATION' }).success).toBe(true);
    expect(createReminderBody.safeParse({ title: 'x', remindAt: '2026-11-08T09:00:00Z', style: 'LOUD' }).success).toBe(false);
    expect(createReminderBody.safeParse({ title: 'x', remindAt: '2026-11-08T09:00:00Z' }).success).toBe(true);
  });
});

describe('snoozeBody (unit)', () => {
  it('accepts minutes or an explicit time and rejects neither', () => {
    expect(snoozeBody.safeParse({ minutes: 60 }).success).toBe(true);
    expect(snoozeBody.safeParse({ until: '2026-11-08T09:00:00Z' }).success).toBe(true);
    expect(snoozeBody.safeParse({}).success).toBe(false);
    expect(snoozeBody.safeParse({ minutes: 0 }).success).toBe(false);
  });
});

describe('reminders API (integration)', () => {
  let ctx: TestContext;
  beforeAll(async () => { ctx = await createTestApp(); });
  beforeEach(async () => { await resetDatabase(ctx.db); });
  afterAll(async () => { await ctx.app.close(); });

  it('an alert keeps the style it was made with, defaults to ALARM, and the style can be changed', async () => {
    const plain = await create({ title: 'Old style', remindAt: '2026-11-07T14:00:00Z' });
    expect(plain.style).toBe('ALARM');
    const soft = await create({ title: 'Gentle', remindAt: '2026-11-07T15:00:00Z', style: 'NOTIFICATION' });
    expect(soft.style).toBe('NOTIFICATION');
    const changed = await ctx.app.inject({ method: 'PATCH', url: `/api/reminders/${soft.id}`, payload: { style: 'ALARM' } });
    expect(changed.json().style).toBe('ALARM');
    const bad = await ctx.app.inject({ method: 'POST', url: '/api/reminders', payload: { title: 'x', remindAt: '2026-11-07T14:00:00Z', style: 'LOUD' } });
    expect(bad.statusCode).toBe(400);
  });

  const create = async (payload: object) => (await ctx.app.inject({ method: 'POST', url: '/api/reminders', payload })).json();

  it('creates, toggles and deletes a reminder', async () => {
    const rem = await create({ title: 'Drink water', remindAt: '2026-11-07T14:00:00Z', repeatRule: 'Every 2 hours' });
    expect(rem).toMatchObject({ title: 'Drink water', enabled: true, repeatRule: 'Every 2 hours' });
    const off = await ctx.app.inject({ method: 'PATCH', url: `/api/reminders/${rem.id}`, payload: { enabled: false } });
    expect(off.json().enabled).toBe(false);
    expect((await ctx.app.inject({ method: 'DELETE', url: `/api/reminders/${rem.id}` })).statusCode).toBe(204);
  });

  it('snoozes by minutes relative to the injected clock', async () => {
    const rem = await create({ title: 'Standup', remindAt: '2026-11-07T09:00:00Z' });
    const service = new RemindersService(ctx.db, () => at('2026-11-07T10:00:00Z'));
    const snoozed = await service.snooze(rem.id, { minutes: 60 });
    expect(snoozed.snoozedUntil).toEqual(at('2026-11-07T11:00:00Z'));
  });

  it('snoozes until an explicit time through the API', async () => {
    const rem = await create({ title: 'Plan', remindAt: '2026-11-07T09:00:00Z' });
    const res = await ctx.app.inject({ method: 'POST', url: `/api/reminders/${rem.id}/snooze`, payload: { until: '2026-11-08T09:00:00Z' } });
    expect(res.json().snoozedUntil).toBe('2026-11-08T09:00:00.000Z');
  });

  it('returns reminders grouped for the client day boundaries', async () => {
    await create({ title: 'Pay bill', remindAt: '2026-11-06T20:00:00Z' });
    await create({ title: 'Write notes', remindAt: '2026-11-07T11:00:00Z' });
    await create({ title: 'Weekly review', remindAt: '2026-11-10T18:00:00Z' });
    const qs = new URLSearchParams({ now: '2026-11-07T10:00:00Z', endOfToday: '2026-11-08T00:00:00Z', endOfWeek: '2026-11-14T00:00:00Z' });
    const g = (await ctx.app.inject({ method: 'GET', url: `/api/reminders/grouped?${qs}` })).json();
    expect(g.overdue.map((x: { title: string }) => x.title)).toEqual(['Pay bill']);
    expect(g.today.map((x: { title: string }) => x.title)).toEqual(['Write notes']);
    expect(g.thisWeek.map((x: { title: string }) => x.title)).toEqual(['Weekly review']);
  });

  it('rolls a repeating reminder forward and clears its snooze', async () => {
    const rem = await create({ title: 'Drink water', remindAt: '2026-11-07T14:00:00Z', repeatRule: 'Every 2 Hours' });
    await ctx.app.inject({ method: 'POST', url: `/api/reminders/${rem.id}/snooze`, payload: { minutes: 30 } });
    const res = await ctx.app.inject({ method: 'PATCH', url: `/api/reminders/${rem.id}`, payload: { remindAt: '2026-11-07T16:00:00Z', snoozedUntil: null } });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({ remindAt: '2026-11-07T16:00:00.000Z', snoozedUntil: null });
  });

  it('cascades when the linked task is deleted and 404s for unknown ids', async () => {
    const task = (await ctx.app.inject({ method: 'POST', url: '/api/tasks', payload: { title: 'Ship' } })).json();
    await create({ title: '1h before', remindAt: '2026-11-07T13:00:00Z', taskId: task.id });
    await ctx.app.inject({ method: 'DELETE', url: `/api/tasks/${task.id}` });
    expect(await ctx.db.reminder.count()).toBe(0);
    const missing = '7b3c9f5e-0c1e-4d9a-9d55-6f1f0f1a2b3c';
    expect((await ctx.app.inject({ method: 'PATCH', url: `/api/reminders/${missing}`, payload: { enabled: false } })).statusCode).toBe(404);
  });
});
