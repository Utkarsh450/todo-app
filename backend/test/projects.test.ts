import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_PROJECTS, createProjectBody } from '../src/modules/projects/projects.schema.js';
import { ProjectsService } from '../src/modules/projects/projects.service.js';
import { createTestApp, resetDatabase, type TestContext } from './helpers.js';

describe('project schema (unit)', () => {
  it('accepts a trimmed name and #RRGGBB colour', () => {
    expect(createProjectBody.parse({ name: '  Home ', color: '#AABBCC' })).toEqual({ name: 'Home', color: '#AABBCC' });
  });
  it.each(['red', '#FFF', '#GGGGGG'])('rejects invalid colour %s', (color) => {
    expect(createProjectBody.safeParse({ name: 'x', color }).success).toBe(false);
  });
  it('ships the five design projects with unique names', () => {
    expect(new Set(DEFAULT_PROJECTS.map((p) => p.name)).size).toBe(5);
  });
});

describe('projects API (integration)', () => {
  let ctx: TestContext;
  beforeAll(async () => { ctx = await createTestApp(); });
  beforeEach(async () => { await resetDatabase(ctx.db); });
  afterAll(async () => { await ctx.app.close(); });

  it('creates, lists, renames and deletes a project', async () => {
    const created = await ctx.app.inject({ method: 'POST', url: '/api/projects', payload: { name: 'Home', color: '#112233' } });
    expect(created.statusCode).toBe(201);
    const { id } = created.json();

    const renamed = await ctx.app.inject({ method: 'PATCH', url: `/api/projects/${id}`, payload: { name: 'House' } });
    expect(renamed.json()).toMatchObject({ id, name: 'House', color: '#112233' });

    const list = await ctx.app.inject({ method: 'GET', url: '/api/projects' });
    expect(list.json().map((p: { name: string }) => p.name)).toEqual(['House']);

    expect((await ctx.app.inject({ method: 'DELETE', url: `/api/projects/${id}` })).statusCode).toBe(204);
    expect((await ctx.app.inject({ method: 'GET', url: '/api/projects' })).json()).toEqual([]);
  });

  it('answers 409 for a duplicate name and 404 for an unknown id', async () => {
    const body = { name: 'Dup', color: '#112233' };
    await ctx.app.inject({ method: 'POST', url: '/api/projects', payload: body });
    expect((await ctx.app.inject({ method: 'POST', url: '/api/projects', payload: body })).statusCode).toBe(409);
    expect((await ctx.app.inject({ method: 'DELETE', url: '/api/projects/7b3c9f5e-0c1e-4d9a-9d55-6f1f0f1a2b3c' })).statusCode).toBe(404);
  });

  it('validates input with a 400', async () => {
    const res = await ctx.app.inject({ method: 'POST', url: '/api/projects', payload: { name: '', color: 'nope' } });
    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('VALIDATION_ERROR');
  });

  it('seeds defaults idempotently', async () => {
    const service = new ProjectsService(ctx.db);
    expect(await service.seedDefaults()).toBe(5);
    expect(await service.seedDefaults()).toBe(0);
  });
});
