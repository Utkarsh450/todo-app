// Vercel entry point. Every /api/* request is rewritten to this function (see vercel.json) and handed to the same
// Fastify app that src/server.ts runs locally. It uses the compiled output in dist/ (built by `pnpm build`), and the
// app is built once per warm instance.
import { buildApp } from '../dist/app.js';
import { parseEnv } from '../dist/config/env.js';
import { createLogger } from '../dist/lib/logger.js';
import { createPrisma } from '../dist/lib/prisma.js';

/** @type {Promise<import('fastify').FastifyInstance> | undefined} */
let ready;

async function boot() {
  const env = parseEnv(process.env);
  // On the internet the API must be protected: refuse to start without a key rather than serving everyone's data.
  if (!env.API_KEY) throw new Error('API_KEY is required when the API is hosted. Add it in the Vercel project settings.');
  const app = await buildApp({ env, db: createPrisma(env.DATABASE_URL), logger: createLogger(env.LOG_LEVEL, 'production') });
  await app.ready();
  return app;
}

/**
 * @param {import('node:http').IncomingMessage} req
 * @param {import('node:http').ServerResponse} res
 */
export default async function handler(req, res) {
  try {
    ready ??= boot();
    const app = await ready;
    app.server.emit('request', req, res);
  } catch (error) {
    ready = undefined; // try again on the next request instead of caching a failed start
    res.statusCode = 500;
    res.setHeader('content-type', 'application/json');
    res.end(JSON.stringify({ error: 'STARTUP_FAILED', message: error instanceof Error ? error.message : 'could not start' }));
  }
}
