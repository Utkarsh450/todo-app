import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';
import { createProjectBody, idParams, projectSchema, updateProjectBody } from './projects.schema.js';
import { ProjectsService } from './projects.service.js';

export const projectRoutes: FastifyPluginAsyncZod = async (app) => {
  const service = new ProjectsService(app.db);

  app.get('/projects', { schema: { response: { 200: z.array(projectSchema) } } }, () => service.list());

  app.post('/projects', { schema: { body: createProjectBody, response: { 201: projectSchema } } }, async (req, reply) =>
    reply.status(201).send(await service.create(req.body)),
  );

  app.patch('/projects/:id', { schema: { params: idParams, body: updateProjectBody, response: { 200: projectSchema } } }, (req) =>
    service.update(req.params.id, req.body),
  );

  app.delete('/projects/:id', { schema: { params: idParams } }, async (req, reply) => {
    await service.remove(req.params.id);
    return reply.status(204).send();
  });
};
