import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { memoryRefused, notFound } from '../../lib/errors.js';
import {
  assistBody,
  assistResponse,
  brainDumpBody,
  brainDumpResponse,
  breakdownBody,
  breakdownResponse,
  historyQuery,
  historyResponse,
  memoriesResponse,
  memoryBody,
  memoryItem,
  memoryParams,
  messageParams,
  outcomeBody,
  statusResponse,
} from './ai.schema.js';
import { AiService } from './ai.service.js';
import { AssistantService } from './assistant.service.js';
import { BrainDumpService } from './braindump.service.js';
import { ChatService } from './chat.service.js';
import { validateFact } from './memory.js';
import { MemoryService } from './memory.service.js';

export const aiRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new AiService(app.ai);
  const assistant = new AssistantService(app.ai, app.db);
  const brainDump = new BrainDumpService(app.ai, app.db);
  const chat = new ChatService(app.db);
  const memory = new MemoryService(app.db);

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
    app.appLogger.info('ai assist', { suggestions: result.suggestions.length, noted: result.noted.length, forgotten: result.forgotten.length, ms: Date.now() - started });
    return result;
  });

  app.post('/ai/braindump', { schema: { body: brainDumpBody, response: { 200: brainDumpResponse } } }, async (req) => {
    const started = Date.now();
    const tasks = await brainDump.sort(req.body.text, req.body.timeZone);
    app.appLogger.info('ai braindump', { tasks: tasks.length, ms: Date.now() - started });
    return { tasks };
  });

  // ---- the running conversation

  app.get('/ai/history', { schema: { querystring: historyQuery, response: { 200: historyResponse } } }, (req) =>
    chat.page(req.query.before ?? null, req.query.limit),
  );

  app.delete('/ai/history', async (_req, reply) => {
    await chat.clear();
    return reply.status(204).send();
  });

  app.post('/ai/assist/:messageId/outcome', { schema: { params: messageParams, body: outcomeBody } }, async (req, reply) => {
    if (!(await chat.setOutcome(req.params.messageId, req.body.index, req.body.outcome))) throw notFound('Suggestion');
    return reply.status(204).send();
  });

  // ---- what the assistant remembers about the owner

  app.get('/ai/memories', { schema: { response: { 200: memoriesResponse } } }, async () => ({ memories: await memory.listFull() }));

  app.post('/ai/memories', { schema: { body: memoryBody, response: { 201: memoryItem } } }, async (req, reply) => {
    const check = validateFact(req.body.text, req.body.category);
    if (!check.ok) throw memoryRefused(check.reason);
    const saved = await memory.create(check.fact.text, check.fact.category, 'USER');
    if (!saved) throw memoryRefused('full');
    return reply.status(201).send(saved);
  });

  app.patch('/ai/memories/:id', { schema: { params: memoryParams, body: memoryBody, response: { 200: memoryItem } } }, async (req) => {
    const check = validateFact(req.body.text);
    if (!check.ok) throw memoryRefused(check.reason);
    const saved = await memory.update(req.params.id, check.fact.text);
    if (!saved) throw notFound('Memory');
    return saved;
  });

  app.delete('/ai/memories/:id', { schema: { params: memoryParams } }, async (req, reply) => {
    if (!(await memory.remove(req.params.id))) throw notFound('Memory');
    return reply.status(204).send();
  });

  app.delete('/ai/memories', async (_req, reply) => {
    await memory.removeAll();
    return reply.status(204).send();
  });
};

