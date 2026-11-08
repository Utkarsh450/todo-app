import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app.js';
import { parseEnv } from '../src/config/env.js';
import { databaseProblem } from '../src/lib/errors.js';
import { createLogger } from '../src/lib/logger.js';
import { createPrisma } from '../src/lib/prisma.js';
import { createTestApp, resetDatabase, type TestContext } from './helpers.js';

const MISSING = '99999999-9999-4999-8999-999999999999';

describe('databaseProblem (unit)', () => {
  it.each([
    ['P2003', 400, 'INVALID_REFERENCE'],
    ['P2025', 404, 'NOT_FOUND'],
    ['P2002', 409, 'CONFLICT'],
  ])('maps %s', (code, statusCode, errorCode) => {
    expect(databaseProblem({ code })).toMatchObject({ statusCode, code: errorCode });
  });

  it.each([[null], [undefined], [new Error('boom')], [{ code: 'P9999' }], ['text']])('ignores %j', (value) => {
    expect(databaseProblem(value)).toBeNull();
  });
});

describe('references to things that do not exist', () => {
  let ctx: TestContext;
  beforeAll(async () => {
    ctx = await createTestApp();
  });
  afterAll(async () => {
    await ctx.app.close();
  });
  beforeEach(async () => {
    await resetDatabase(ctx.db);
  });

  const post = (url: string, payload: object) => ctx.app.inject({ method: 'POST', url, payload });

  it('a task in an unknown project is a 400, not a 500', async () => {
    const res = await post('/api/tasks', { title: 'x', projectId: MISSING });
    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('INVALID_REFERENCE');
  });

  it('moving a task into an unknown project is a 400', async () => {
    const task = (await post('/api/tasks', { title: 'x' })).json();
    const res = await ctx.app.inject({ method: 'PATCH', url: `/api/tasks/${task.id}`, payload: { projectId: MISSING } });
    expect(res.statusCode).toBe(400);
  });

  it('a reminder for an unknown task is a 400', async () => {
    const res = await post('/api/reminders', { title: 'x', remindAt: '2030-01-01T09:00:00Z', taskId: MISSING });
    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('INVALID_REFERENCE');
  });

  it('an event in an unknown project is a 400', async () => {
    const res = await post('/api/events', { title: 'x', startAt: '2030-01-01T09:00:00Z', endAt: '2030-01-01T10:00:00Z', projectId: MISSING });
    expect(res.statusCode).toBe(400);
  });

  it('a task that does not exist is still a 404', async () => {
    const res = await ctx.app.inject({ method: 'GET', url: `/api/tasks/${MISSING}` });
    expect(res.statusCode).toBe(404);
  });
});

describe('network defaults', () => {
  const base = { DATABASE_URL: 'postgresql://u@localhost/db' };

  it('listens on loopback unless told otherwise', () => {
    expect(parseEnv(base).HOST).toBe('127.0.0.1');
    expect(parseEnv({ ...base, HOST: '0.0.0.0' }).HOST).toBe('0.0.0.0');
  });

  it('allows no browser origins by default and reads a comma separated list', () => {
    expect(parseEnv(base).CORS_ORIGIN).toEqual([]);
    expect(parseEnv({ ...base, CORS_ORIGIN: '' }).CORS_ORIGIN).toEqual([]);
    expect(parseEnv({ ...base, CORS_ORIGIN: ' http://a.test , http://b.test ' }).CORS_ORIGIN).toEqual(['http://a.test', 'http://b.test']);
  });

  describe('CORS headers', () => {
    const appWith = async (corsOrigin: string | undefined): Promise<FastifyInstance> => {
      const env = parseEnv({ ...process.env, CORS_ORIGIN: corsOrigin });
      const app = await buildApp({ env, db: createPrisma(env.DATABASE_URL), logger: createLogger('error', 'test') });
      await app.ready();
      return app;
    };
    const ask = (app: FastifyInstance) =>
      app.inject({ method: 'GET', url: '/api/health', headers: { origin: 'http://evil.test' } });

    it('are not sent by default', async () => {
      const app = await appWith(undefined);
      try {
        expect((await ask(app)).headers['access-control-allow-origin']).toBeUndefined();
      } finally {
        await app.close();
      }
    });

    it('are sent only to the configured origin', async () => {
      const app = await appWith('http://evil.test');
      try {
        expect((await ask(app)).headers['access-control-allow-origin']).toBe('http://evil.test');
        const other = await app.inject({ method: 'GET', url: '/api/health', headers: { origin: 'http://other.test' } });
        expect(other.headers['access-control-allow-origin']).toBeUndefined();
      } finally {
        await app.close();
      }
    });
  });
});
