import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';
import { agendaItemSchema, createEventBody, eventSchema, idParams, rangeQuery, updateEventBody } from './calendar.schema.js';
import { CalendarService } from './calendar.service.js';

export const calendarRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new CalendarService(app.db);

  app.get('/events', { schema: { querystring: rangeQuery, response: { 200: z.array(eventSchema) } } }, (req) =>
    service.listEvents(req.query.from, req.query.to),
  );

  app.get('/events/:id', { schema: { params: idParams, response: { 200: eventSchema } } }, (req) => service.getEvent(req.params.id));

  app.post('/events', { schema: { body: createEventBody, response: { 201: eventSchema } } }, async (req, reply) =>
    reply.status(201).send(await service.createEvent(req.body)),
  );

  app.patch('/events/:id', { schema: { params: idParams, body: updateEventBody, response: { 200: eventSchema } } }, (req) =>
    service.updateEvent(req.params.id, req.body),
  );

  app.delete('/events/:id', { schema: { params: idParams } }, async (req, reply) => {
    await service.removeEvent(req.params.id);
    return reply.status(204).send();
  });

  app.get('/calendar/agenda', { schema: { querystring: rangeQuery, response: { 200: z.array(agendaItemSchema) } } }, (req) =>
    service.agenda(req.query.from, req.query.to),
  );
};
