import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { buildAgenda } from '../src/modules/calendar/agenda.js';
import { createEventBody, rangeQuery } from '../src/modules/calendar/calendar.schema.js';
import { createTestApp, resetDatabase, type TestContext } from './helpers.js';

const d = (iso: string) => new Date(iso);
const id = (n: number) => `00000000-0000-4000-8000-00000000000${n}`;

describe('buildAgenda (unit)', () => {
  it('merges events and dated tasks chronologically and skips undated tasks', () => {
    const items = buildAgenda(
      [{ id: id(1), title: 'Standup', startAt: d('2026-11-07T09:30:00Z'), endAt: d('2026-11-07T10:00:00Z'), allDay: false, projectId: null }],
      [
        { id: id(2), title: 'Review PR', dueAt: d('2026-11-07T09:00:00Z'), status: 'TODO', projectId: null },
        { id: id(3), title: 'Someday', dueAt: null, status: 'TODO', projectId: null },
      ],
    );
    expect(items.map((i) => `${i.kind}:${i.title}`)).toEqual(['task:Review PR', 'event:Standup']);
  });

  it('puts all-day items before timed items on the same day and flags done tasks', () => {
    const items = buildAgenda(
      [{ id: id(1), title: 'Release', startAt: d('2026-11-07T12:00:00Z'), endAt: d('2026-11-07T23:59:00Z'), allDay: true, projectId: null }],
      [{ id: id(2), title: 'Early', dueAt: d('2026-11-07T06:00:00Z'), status: 'DONE', projectId: null }],
    );
    expect(items.map((i) => i.title)).toEqual(['Release', 'Early']);
    expect(items[1]?.done).toBe(true);
  });
});

describe('calendar schemas (unit)', () => {
  it('rejects an event that ends before it starts', () => {
    const bad = createEventBody.safeParse({ title: 'x', startAt: '2026-11-07T10:00:00Z', endAt: '2026-11-07T09:00:00Z' });
    expect(bad.success).toBe(false);
  });
  it('requires a positive range', () => {
    expect(rangeQuery.safeParse({ from: '2026-11-08T00:00:00Z', to: '2026-11-07T00:00:00Z' }).success).toBe(false);
  });
});

describe('calendar API (integration)', () => {
  let ctx: TestContext;
  beforeAll(async () => { ctx = await createTestApp(); });
  beforeEach(async () => { await resetDatabase(ctx.db); });
  afterAll(async () => { await ctx.app.close(); });

  it('keeps an event\'s location, video link, guests and busy/free, with sensible defaults', async () => {
    const plain = (await ctx.app.inject({ method: 'POST', url: '/api/events', payload: { title: 'Plain', startAt: '2026-11-07T10:00:00Z', endAt: '2026-11-07T11:00:00Z' } })).json();
    expect(plain).toMatchObject({ location: null, videoLink: null, busy: true, guests: [] });
    const rich = (await ctx.app.inject({
      method: 'POST', url: '/api/events',
      payload: { title: 'Sync', startAt: '2026-11-07T12:00:00Z', endAt: '2026-11-07T13:00:00Z', location: 'Room 4', videoLink: 'https://meet.example/abc', guests: ['sam@example.com', 'priya@example.com'], busy: false },
    })).json();
    expect(rich).toMatchObject({ location: 'Room 4', videoLink: 'https://meet.example/abc', busy: false, guests: ['sam@example.com', 'priya@example.com'] });
    const bad = await ctx.app.inject({ method: 'POST', url: '/api/events', payload: { title: 'x', startAt: '2026-11-07T12:00:00Z', endAt: '2026-11-07T13:00:00Z', guests: [''] } });
    expect(bad.statusCode).toBe(400);
  });

  it('creates, updates and deletes an event', async () => {
    const created = await ctx.app.inject({ method: 'POST', url: '/api/events', payload: { title: 'Sprint planning', startAt: '2026-11-07T10:00:00Z', endAt: '2026-11-07T11:00:00Z' } });
    expect(created.statusCode).toBe(201);
    const { id: eventId } = created.json();
    const updated = await ctx.app.inject({ method: 'PATCH', url: `/api/events/${eventId}`, payload: { title: 'Planning' } });
    expect(updated.json().title).toBe('Planning');
    expect((await ctx.app.inject({ method: 'DELETE', url: `/api/events/${eventId}` })).statusCode).toBe(204);
    expect((await ctx.app.inject({ method: 'DELETE', url: `/api/events/${eventId}` })).statusCode).toBe(404);
  });

  it('rejects invalid event times with 400', async () => {
    const res = await ctx.app.inject({ method: 'POST', url: '/api/events', payload: { title: 'x', startAt: '2026-11-07T10:00:00Z', endAt: '2026-11-07T09:00:00Z' } });
    expect(res.statusCode).toBe(400);
  });

  it('returns a merged agenda for a day, excluding archived tasks and other days', async () => {
    await ctx.app.inject({ method: 'POST', url: '/api/events', payload: { title: '1:1', startAt: '2026-11-07T15:00:00Z', endAt: '2026-11-07T15:45:00Z' } });
    await ctx.app.inject({ method: 'POST', url: '/api/events', payload: { title: 'Tomorrow event', startAt: '2026-11-08T15:00:00Z', endAt: '2026-11-08T16:00:00Z' } });
    await ctx.app.inject({ method: 'POST', url: '/api/tasks', payload: { title: 'Review PR', dueAt: '2026-11-07T09:00:00Z' } });
    const hidden = (await ctx.app.inject({ method: 'POST', url: '/api/tasks', payload: { title: 'Archived', dueAt: '2026-11-07T10:00:00Z' } })).json();
    await ctx.app.inject({ method: 'PATCH', url: `/api/tasks/${hidden.id}`, payload: { archived: true } });

    const res = await ctx.app.inject({ method: 'GET', url: '/api/calendar/agenda?from=2026-11-07T00:00:00Z&to=2026-11-08T00:00:00Z' });
    expect(res.statusCode).toBe(200);
    expect(res.json().map((i: { kind: string; title: string }) => `${i.kind}:${i.title}`)).toEqual(['task:Review PR', 'event:1:1']);
  });

  it('lists stand-alone reminders on the agenda, but not task reminders, event alerts or disabled ones', async () => {
    const post = (url: string, payload: object) => ctx.app.inject({ method: 'POST', url, payload });
    const event = (await post('/api/events', { title: 'Dentist', startAt: '2026-11-07T15:00:00Z', endAt: '2026-11-07T16:00:00Z' })).json();
    const task = (await post('/api/tasks', { title: 'Pay bill' })).json();
    await post('/api/reminders', { title: 'Stretch', remindAt: '2026-11-07T08:00:00Z' });
    await post('/api/reminders', { title: 'Dentist alert', remindAt: '2026-11-07T14:30:00Z', eventId: event.id });
    await post('/api/reminders', { title: 'Pay bill alert', remindAt: '2026-11-07T09:00:00Z', taskId: task.id });
    await post('/api/reminders', { title: 'Off', remindAt: '2026-11-07T10:00:00Z', enabled: false });
    await post('/api/reminders', { title: 'Other day', remindAt: '2026-11-09T10:00:00Z' });

    const res = await ctx.app.inject({ method: 'GET', url: '/api/calendar/agenda?from=2026-11-07T00:00:00Z&to=2026-11-08T00:00:00Z' });
    expect(res.json().map((i: { kind: string; title: string }) => `${i.kind}:${i.title}`)).toEqual(['reminder:Stretch', 'event:Dentist']);
  });

  it('shows a snoozed reminder at its snooze time', async () => {
    const r = (await ctx.app.inject({ method: 'POST', url: '/api/reminders', payload: { title: 'Call', remindAt: '2026-11-07T08:00:00Z' } })).json();
    await ctx.app.inject({ method: 'POST', url: `/api/reminders/${r.id}/snooze`, payload: { until: '2026-11-07T12:00:00Z' } });
    const res = await ctx.app.inject({ method: 'GET', url: '/api/calendar/agenda?from=2026-11-07T00:00:00Z&to=2026-11-08T00:00:00Z' });
    expect(res.json()[0]).toMatchObject({ kind: 'reminder', title: 'Call', startAt: '2026-11-07T12:00:00.000Z' });
  });

  it("deleting an event deletes the alert that belongs to it", async () => {
    const event = (await ctx.app.inject({ method: 'POST', url: '/api/events', payload: { title: 'Dentist', startAt: '2026-11-07T15:00:00Z', endAt: '2026-11-07T16:00:00Z' } })).json();
    const alert = (await ctx.app.inject({ method: 'POST', url: '/api/reminders', payload: { title: 'Dentist', remindAt: '2026-11-07T14:30:00Z', eventId: event.id } })).json();
    expect(alert.eventId).toBe(event.id);

    await ctx.app.inject({ method: 'DELETE', url: `/api/events/${event.id}` });

    expect((await ctx.app.inject({ method: 'GET', url: '/api/reminders' })).json()).toEqual([]);
  });
});
