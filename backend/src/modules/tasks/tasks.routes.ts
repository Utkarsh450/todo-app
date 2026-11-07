import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';
import {
  createSubtaskBody, createTaskBody, idParams, listTasksQuery, subtaskSchema, taskSchema, updateSubtaskBody, updateTaskBody,
} from './tasks.schema.js';
import { TasksService } from './tasks.service.js';

export const taskRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new TasksService(app.db);

  app.get('/tasks', { schema: { querystring: listTasksQuery, response: { 200: z.array(taskSchema) } } }, (req) => service.list(req.query));

  app.post('/tasks', { schema: { body: createTaskBody, response: { 201: taskSchema } } }, async (req, reply) =>
    reply.status(201).send(await service.create(req.body)),
  );

  app.get('/tasks/:id', { schema: { params: idParams, response: { 200: taskSchema } } }, (req) => service.get(req.params.id));

  app.patch('/tasks/:id', { schema: { params: idParams, body: updateTaskBody, response: { 200: taskSchema } } }, (req) =>
    service.update(req.params.id, req.body),
  );

  app.delete('/tasks/:id', { schema: { params: idParams } }, async (req, reply) => {
    await service.remove(req.params.id);
    return reply.status(204).send();
  });

  app.post('/tasks/:id/complete', { schema: { params: idParams, response: { 200: taskSchema } } }, (req) => service.complete(req.params.id));
  app.post('/tasks/:id/reopen', { schema: { params: idParams, response: { 200: taskSchema } } }, (req) => service.reopen(req.params.id));

  app.post('/tasks/:id/subtasks', { schema: { params: idParams, body: createSubtaskBody, response: { 201: subtaskSchema } } }, async (req, reply) =>
    reply.status(201).send(await service.addSubtask(req.params.id, req.body.title)),
  );

  app.patch('/subtasks/:id', { schema: { params: idParams, body: updateSubtaskBody, response: { 200: subtaskSchema } } }, (req) =>
    service.updateSubtask(req.params.id, req.body),
  );

  app.delete('/subtasks/:id', { schema: { params: idParams } }, async (req, reply) => {
    await service.removeSubtask(req.params.id);
    return reply.status(204).send();
  });
};
