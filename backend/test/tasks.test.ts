import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { statusFields } from '../src/modules/tasks/task-status.js';
import { createTaskBody, listTasksQuery } from '../src/modules/tasks/tasks.schema.js';
import { createTestApp, resetDatabase, type TestContext } from './helpers.js';

describe('statusFields (unit)', () => {
  const now = new Date('2026-11-07T10:00:00Z');
  it('stamps completedAt when a task becomes DONE', () => {
    expect(statusFields('DONE', now)).toEqual({ status: 'DONE', completedAt: now });
  });
  it('keeps the original completion time when already DONE', () => {
    const earlier = new Date('2026-11-06T10:00:00Z');
    expect(statusFields('DONE', now, earlier).completedAt).toEqual(earlier);
  });
  it('clears completedAt on reopen or progress', () => {
    expect(statusFields('TODO', now, now).completedAt).toBeNull();
    expect(statusFields('IN_PROGRESS', now, now).completedAt).toBeNull();
  });
});

describe('task schemas (unit)', () => {
  it('trims titles and rejects empty ones', () => {
    expect(createTaskBody.parse({ title: '  Run  ' }).title).toBe('Run');
    expect(createTaskBody.safeParse({ title: '   ' }).success).toBe(false);
  });
  it('defaults the list filter to non-archived tasks', () => {
    expect(listTasksQuery.parse({}).archived).toBe(false);
    expect(listTasksQuery.parse({ archived: 'true' }).archived).toBe(true);
  });
});

describe('tasks API (integration)', () => {
  let ctx: TestContext;
  beforeAll(async () => { ctx = await createTestApp(); });
  beforeEach(async () => { await resetDatabase(ctx.db); });
  afterAll(async () => { await ctx.app.close(); });

  const post = (payload: object) => ctx.app.inject({ method: 'POST', url: '/api/tasks', payload });

  it('creates a task with subtasks and returns them in order', async () => {
    const res = await post({ title: 'Ship redesign', priority: 'URGENT', subtasks: ['Audit', 'Wireframe'] });
    expect(res.statusCode).toBe(201);
    const task = res.json();
    expect(task).toMatchObject({ title: 'Ship redesign', priority: 'URGENT', status: 'TODO', completedAt: null });
    expect(task.subtasks.map((s: { title: string }) => s.title)).toEqual(['Audit', 'Wireframe']);
  });

  it('completes and reopens a task, maintaining completedAt', async () => {
    const { id } = (await post({ title: 'Run' })).json();
    const done = (await ctx.app.inject({ method: 'POST', url: `/api/tasks/${id}/complete` })).json();
    expect(done.status).toBe('DONE');
    expect(done.completedAt).not.toBeNull();
    const reopened = (await ctx.app.inject({ method: 'POST', url: `/api/tasks/${id}/reopen` })).json();
    expect(reopened).toMatchObject({ status: 'TODO', completedAt: null });
  });

  it('filters by due-date range, excludes archived, and orders by due time', async () => {
    await post({ title: 'Later today', dueAt: '2026-11-07T15:00:00Z' });
    await post({ title: 'Early today', dueAt: '2026-11-07T07:00:00Z' });
    await post({ title: 'Tomorrow', dueAt: '2026-11-08T07:00:00Z' });
    const archived = (await post({ title: 'Old', dueAt: '2026-11-07T08:00:00Z' })).json();
    await ctx.app.inject({ method: 'PATCH', url: `/api/tasks/${archived.id}`, payload: { archived: true } });

    const res = await ctx.app.inject({ method: 'GET', url: '/api/tasks?from=2026-11-07T00:00:00Z&to=2026-11-08T00:00:00Z' });
    expect(res.json().map((t: { title: string }) => t.title)).toEqual(['Early today', 'Later today']);

    const archivedRes = await ctx.app.inject({ method: 'GET', url: '/api/tasks?archived=true' });
    expect(archivedRes.json().map((t: { title: string }) => t.title)).toEqual(['Old']);
  });

  it('searches titles case-insensitively', async () => {
    await post({ title: 'Review PR #482' });
    await post({ title: 'Groceries' });
    const res = await ctx.app.inject({ method: 'GET', url: '/api/tasks?q=review' });
    expect(res.json()).toHaveLength(1);
  });

  it('manages subtasks and cascades on task delete', async () => {
    const { id } = (await post({ title: 'Parent' })).json();
    const sub = (await ctx.app.inject({ method: 'POST', url: `/api/tasks/${id}/subtasks`, payload: { title: 'Child' } })).json();
    expect(sub.position).toBe(0);
    const toggled = await ctx.app.inject({ method: 'PATCH', url: `/api/subtasks/${sub.id}`, payload: { done: true } });
    expect(toggled.json().done).toBe(true);
    await ctx.app.inject({ method: 'DELETE', url: `/api/tasks/${id}` });
    expect(await ctx.db.subtask.count()).toBe(0);
  });

  it('returns 404 for unknown tasks and 400 for bad input', async () => {
    const missing = '7b3c9f5e-0c1e-4d9a-9d55-6f1f0f1a2b3c';
    expect((await ctx.app.inject({ method: 'GET', url: `/api/tasks/${missing}` })).statusCode).toBe(404);
    expect((await ctx.app.inject({ method: 'PATCH', url: `/api/tasks/${missing}`, payload: { title: 'x' } })).statusCode).toBe(404);
    expect((await post({ title: '' })).statusCode).toBe(400);
    expect((await ctx.app.inject({ method: 'PATCH', url: `/api/tasks/${missing}`, payload: {} })).statusCode).toBe(400);
  });

  it('links a task to a project and unlinks it when the project is deleted', async () => {
    const project = (await ctx.app.inject({ method: 'POST', url: '/api/projects', payload: { name: 'Home', color: '#112233' } })).json();
    const task = (await post({ title: 'Fix tap', projectId: project.id })).json();
    expect(task.projectId).toBe(project.id);
    await ctx.app.inject({ method: 'DELETE', url: `/api/projects/${project.id}` });
    expect((await ctx.app.inject({ method: 'GET', url: `/api/tasks/${task.id}` })).json().projectId).toBeNull();
  });
});
