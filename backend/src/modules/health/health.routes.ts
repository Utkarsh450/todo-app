import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';

export const healthRoutes: FastifyPluginAsyncZod = async (app) => {
  app.get(
    '/health',
    { schema: { response: { 200: z.object({ status: z.literal('ok'), database: z.literal('up') }) } } },
    async () => {
      await app.db.$queryRaw`SELECT 1`;
      return { status: 'ok' as const, database: 'up' as const };
    },
  );
};
