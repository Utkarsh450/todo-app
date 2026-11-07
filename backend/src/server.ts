import 'dotenv/config';
import { buildApp } from './app.js';
import { parseEnv } from './config/env.js';
import { createLogger } from './lib/logger.js';
import { createPrisma } from './lib/prisma.js';

const env = parseEnv(process.env);
const logger = createLogger(env.LOG_LEVEL, env.NODE_ENV);
const app = await buildApp({ env, db: createPrisma(env.DATABASE_URL), logger });

const shutdown = async (signal: string) => {
  logger.info('shutting down', { signal });
  await app.close();
  process.exit(0);
};
process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

await app.listen({ host: env.HOST, port: env.PORT });
logger.info('server listening', { host: env.HOST, port: env.PORT });
