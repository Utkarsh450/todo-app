import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';
import { createHabitBody, habitSchema, idParams, logBody, logParams, updateHabitBody, windowQuery } from './habits.schema.js';
import { HabitsService } from './habits.service.js';

export const habitRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new HabitsService(app.db);

  app.get('/habits', { schema: { querystring: windowQuery, response: { 200: z.array(habitSchema) } } }, (req) =>
    service.list(req.query.from, req.query.to),
  );

  app.post('/habits', { schema: { body: createHabitBody, response: { 201: habitSchema } } }, async (req, reply) =>
    reply.status(201).send(await service.create(req.body.name)),
  );

  app.patch('/habits/:id', { schema: { params: idParams, body: updateHabitBody, response: { 200: habitSchema } } }, (req) =>
    service.rename(req.params.id, req.body.name),
  );

  app.delete('/habits/:id', { schema: { params: idParams } }, async (req, reply) => {
    await service.remove(req.params.id);
    return reply.status(204).send();
  });

  app.put(
    '/habits/:id/logs/:date',
    { schema: { params: logParams, body: logBody, response: { 200: z.object({ habitId: z.uuid(), date: z.string(), done: z.boolean() }) } } },
    (req) => service.setDone(req.params.id, req.params.date, req.body.done),
  );
};
