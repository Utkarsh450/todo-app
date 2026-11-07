import 'dotenv/config';
import { parseEnv } from '../src/config/env.js';
import { createLogger } from '../src/lib/logger.js';
import { createPrisma } from '../src/lib/prisma.js';
import { ProjectsService } from '../src/modules/projects/projects.service.js';

const env = parseEnv(process.env);
const logger = createLogger(env.LOG_LEVEL, env.NODE_ENV);
const db = createPrisma(env.DATABASE_URL);

try {
  const created = await new ProjectsService(db).seedDefaults();
  logger.info('seed complete', { projectsCreated: created });
} finally {
  await db.$disconnect();
}
