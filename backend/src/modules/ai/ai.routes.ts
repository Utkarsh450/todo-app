import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { assistBody, assistResponse, breakdownBody, breakdownResponse, statusResponse } from './ai.schema.js';
import { AiService } from './ai.service.js';
import { AssistantService } from './assistant.service.js';

export const aiRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new AiService(app.ai);
  const assistant = new AssistantService(app.ai, app.db);

  app.get('/ai/status', { schema: { response: { 200: statusResponse } } }, () => service.status());

  app.post('/ai/breakdown', { schema: { body: breakdownBody, response: { 200: breakdownResponse } } }, async (req) => {
    const started = Date.now();
    const result = await service.breakdown(req.body);
    app.appLogger.info('ai breakdown', { steps: result.subtasks.length, ms: Date.now() - started });
    return result;
  });

  app.post('/ai/assist', { schema: { body: assistBody, response: { 200: assistResponse } } }, async (req) => {
    const started = Date.now();
    const result = await assistant.ask(req.body.message, req.body.timeZone);
    app.appLogger.info('ai assist', { suggestions: result.suggestions.length, ms: Date.now() - started });
    return result;
  });
};
