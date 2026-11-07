import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import Fastify, { type FastifyInstance } from 'fastify';
import { serializerCompiler, validatorCompiler, hasZodFastifySchemaValidationErrors } from 'fastify-type-provider-zod';
import type { Env } from './config/env.js';
import { AppError } from './lib/errors.js';
import type { Logger } from './lib/logger.js';
import type { Db } from './lib/prisma.js';
import { healthRoutes } from './modules/health/health.routes.js';
import { projectRoutes } from './modules/projects/projects.routes.js';

declare module 'fastify' {
  interface FastifyInstance {
    db: Db;
    appLogger: Logger;
  }
}

export interface AppDeps {
  env: Env;
  db: Db;
  logger: Logger;
}

/** Builds the Fastify app. Dependencies are injected so tests can supply their own. */
export async function buildApp({ env, db, logger }: AppDeps): Promise<FastifyInstance> {
  const app = Fastify({ logger: false });

  app.setValidatorCompiler(validatorCompiler);
  app.setSerializerCompiler(serializerCompiler);
  app.decorate('db', db);
  app.decorate('appLogger', logger);

  await app.register(helmet);
  await app.register(cors, { origin: true });

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
    logger.error('unhandled error', { url: request.url, message: (error as Error).message, stack: (error as Error).stack });
    return reply.status(500).send({ error: 'INTERNAL_ERROR', message: 'Something went wrong' });
  });

  app.setNotFoundHandler((_request, reply) => reply.status(404).send({ error: 'NOT_FOUND', message: 'Route not found' }));

  await app.register(healthRoutes, { prefix: '/api' });
  await app.register(projectRoutes, { prefix: '/api' });

  app.addHook('onClose', async () => {
    await db.$disconnect();
  });

  return app;
}
