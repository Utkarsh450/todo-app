import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app.js';
import { parseEnv } from '../src/config/env.js';
import { apiKeyMatches } from '../src/lib/api-key.js';
import { createLogger } from '../src/lib/logger.js';
import { createPrisma } from '../src/lib/prisma.js';

const KEY = 'correct-horse-battery-staple';

describe('apiKeyMatches (unit)', () => {
  it('accepts only the exact key', () => {
    expect(apiKeyMatches(KEY, KEY)).toBe(true);
    expect(apiKeyMatches('correct-horse-battery-stapl', KEY)).toBe(false);
    expect(apiKeyMatches(`${KEY}x`, KEY)).toBe(false);
    expect(apiKeyMatches('', KEY)).toBe(false);
    expect(apiKeyMatches(undefined, KEY)).toBe(false);
    expect(apiKeyMatches([KEY, KEY], KEY)).toBe(false);
  });
});

describe('a protected API (integration)', () => {
  let app: FastifyInstance;
  beforeAll(async () => {
    const env = parseEnv({ ...process.env, API_KEY: KEY });
    if (!env.DATABASE_URL.includes('_test')) throw new Error('Refusing to run tests against a non-test database');
    app = await buildApp({ env, db: createPrisma(env.DATABASE_URL), logger: createLogger('error', 'test') });
    await app.ready();
  });
  afterAll(() => app.close());

  it('answers 401 without a key and with a wrong one, and says nothing else', async () => {
    for (const headers of [{}, { 'x-api-key': 'nope' }]) {
      const res = await app.inject({ method: 'GET', url: '/api/tasks', headers });
      expect(res.statusCode).toBe(401);
      expect(res.json()).toEqual({ error: 'UNAUTHORIZED', message: 'A valid API key is required.' });
    }
  });

  it('protects the AI endpoints too, so nobody else can spend the AI key', async () => {
    const res = await app.inject({ method: 'POST', url: '/api/ai/assist', payload: { message: 'hi', timeZone: 'UTC' } });
    expect(res.statusCode).toBe(401);
  });

  it('protects the history and memory endpoints too', async () => {
    for (const [method, url] of [
      ['GET', '/api/ai/history'],
      ['DELETE', '/api/ai/history'],
      ['GET', '/api/ai/memories'],
      ['DELETE', '/api/ai/memories'],
      ['POST', '/api/ai/assist/00000000-0000-4000-8000-000000000000/outcome'],
    ] as const) {
      expect((await app.inject({ method, url })).statusCode).toBe(401);
    }
  });

  it('lets the right key through', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/tasks', headers: { 'x-api-key': KEY } });
    expect(res.statusCode).toBe(200);
  });

  it('keeps the health check open so the host and the app can see it is up', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/health' });
    expect(res.statusCode).toBe(200);
  });
});
