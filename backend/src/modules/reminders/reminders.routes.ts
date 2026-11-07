import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';
import {
  createReminderBody, groupedQuery, groupedSchema, idParams, reminderSchema, snoozeBody, updateReminderBody,
} from './reminders.schema.js';
import { RemindersService } from './reminders.service.js';

export const reminderRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new RemindersService(app.db);

  app.get('/reminders', { schema: { response: { 200: z.array(reminderSchema) } } }, () => service.list());

  app.get('/reminders/grouped', { schema: { querystring: groupedQuery, response: { 200: groupedSchema } } }, (req) =>
    service.grouped(req.query),
  );

  app.post('/reminders', { schema: { body: createReminderBody, response: { 201: reminderSchema } } }, async (req, reply) =>
    reply.status(201).send(await service.create(req.body)),
  );

  app.patch('/reminders/:id', { schema: { params: idParams, body: updateReminderBody, response: { 200: reminderSchema } } }, (req) =>
    service.update(req.params.id, req.body),
  );

  app.post('/reminders/:id/snooze', { schema: { params: idParams, body: snoozeBody, response: { 200: reminderSchema } } }, (req) =>
    service.snooze(req.params.id, req.body),
  );

  app.delete('/reminders/:id', { schema: { params: idParams } }, async (req, reply) => {
    await service.remove(req.params.id);
    return reply.status(204).send();
  });
};
