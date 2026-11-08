import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import Fastify, { type FastifyInstance } from 'fastify';
import { serializerCompiler, validatorCompiler, hasZodFastifySchemaValidationErrors } from 'fastify-type-provider-zod';
import type { Env } from './config/env.js';
import { createXaiClient, type AiClient } from './modules/ai/ai.client.js';
import { aiRoutes } from './modules/ai/ai.routes.js';
import { AppError, databaseProblem } from './lib/errors.js';
import type { Logger } from './lib/logger.js';
import type { Db } from './lib/prisma.js';
import { calendarRoutes } from './modules/calendar/calendar.routes.js';
import { habitRoutes } from './modules/habits/habits.routes.js';
import { healthRoutes } from './modules/health/health.routes.js';
import { insightRoutes } from './modules/insights/insights.routes.js';
import { noteRoutes } from './modules/notes/notes.routes.js';
import { projectRoutes } from './modules/projects/projects.routes.js';
import { reminderRoutes } from './modules/reminders/reminders.routes.js';
import { taskRoutes } from './modules/tasks/tasks.routes.js';

declare module 'fastify' {
  interface FastifyInstance {
    db: Db;
    appLogger: Logger;
    ai: AiClient;
  }
}

export interface AppDeps {
  env: Env;
  db: Db;
  logger: Logger;
  /** Replaced in tests so no real AI service is called. */
  ai?: AiClient;
}

/** Builds the Fastify app. Dependencies are injected so tests can supply their own. */
export async function buildApp({ env, db, logger, ai }: AppDeps): Promise<FastifyInstance> {
  const app = Fastify({ logger: false });

  app.setValidatorCompiler(validatorCompiler);
  app.setSerializerCompiler(serializerCompiler);
  app.decorate('db', db);
  app.decorate('appLogger', logger);
  app.decorate(
    'ai',
    ai ?? createXaiClient({ apiKey: env.XAI_API_KEY, baseUrl: env.XAI_BASE_URL, model: env.XAI_MODEL, timeoutMs: env.XAI_TIMEOUT_MS, logger }),
  );

  await app.register(helmet);
  await app.register(cors, { origin: env.CORS_ORIGIN.length > 0 ? env.CORS_ORIGIN : false });

  app.addHook('onResponse', async (request, reply) => {
    logger.http('request', { method: request.method, url: request.url, status: reply.statusCode, ms: Math.round(reply.elapsedTime) });
  });

  app.setErrorHandler((error, request, reply) => {
    if (hasZodFastifySchemaValidationErrors(error)) {
      return reply.status(400).send({ error: 'VALIDATION_ERROR', message: 'Request validation failed', issues: error.validation });
    }
    if (error instanceof AppError) {
      return reply.status(error.statusCode).send({ error: error.code, message: error.message });
    }
    const known = databaseProblem(error);
    if (known) return reply.status(known.statusCode).send({ error: known.code, message: known.message });
    logger.error('unhandled error', { url: request.url, message: (error as Error).message, stack: (error as Error).stack });
    return reply.status(500).send({ error: 'INTERNAL_ERROR', message: 'Something went wrong' });
  });

  app.setNotFoundHandler((_request, reply) => reply.status(404).send({ error: 'NOT_FOUND', message: 'Route not found' }));

  await app.register(healthRoutes, { prefix: '/api' });
  await app.register(projectRoutes, { prefix: '/api' });
  await app.register(taskRoutes, { prefix: '/api' });
  await app.register(reminderRoutes, { prefix: '/api' });
  await app.register(calendarRoutes, { prefix: '/api' });
  await app.register(habitRoutes, { prefix: '/api' });
  await app.register(noteRoutes, { prefix: '/api' });
  await app.register(insightRoutes, { prefix: '/api' });
  await app.register(aiRoutes, { prefix: '/api' });

  app.addHook('onClose', async () => {
    await db.$disconnect();
  });

  return app;
}
