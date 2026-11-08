import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { breakdownBody, breakdownResponse, statusResponse } from './ai.schema.js';
import { AiService } from './ai.service.js';

export const aiRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new AiService(app.ai);

  app.get('/ai/status', { schema: { response: { 200: statusResponse } } }, () => service.status());

  app.post('/ai/breakdown', { schema: { body: breakdownBody, response: { 200: breakdownResponse } } }, async (req) => {
    const started = Date.now();
    const result = await service.breakdown(req.body);
    app.appLogger.info('ai breakdown', { steps: result.subtasks.length, ms: Date.now() - started });
    return result;
  });
};
