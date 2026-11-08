import type { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app.js';
import { parseEnv } from '../src/config/env.js';
import { createLogger } from '../src/lib/logger.js';
import type { AiClient } from '../src/modules/ai/ai.client.js';
import { createPrisma, type Db } from '../src/lib/prisma.js';

export interface TestContext {
  app: FastifyInstance;
  db: Db;
}

/** Boots the real app against the local `paper_todo_test` PostgreSQL database. */
export async function createTestApp(ai?: AiClient): Promise<TestContext> {
  const env = parseEnv(process.env);
  if (!env.DATABASE_URL.includes('_test')) throw new Error(`Refusing to run tests against non-test database: ${env.DATABASE_URL}`);
  const db = createPrisma(env.DATABASE_URL);
  const app = await buildApp({ env, db, logger: createLogger('error', 'test'), ai });
  await app.ready();
  return { app, db };
}

/** Empties every table so each test starts from a clean slate. */
export async function resetDatabase(db: Db): Promise<void> {
  await db.$executeRawUnsafe(
    'TRUNCATE TABLE "HabitLog", "Habit", "Reminder", "Subtask", "Task", "CalendarEvent", "DailyNote", "Project" RESTART IDENTITY CASCADE',
  );
}
