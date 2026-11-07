import type { FastifyPluginAsyncZod } from 'fastify-type-provider-zod';
import { z } from 'zod';
import { dayString } from '../habits/habits.schema.js';

const noteSchema = z.object({ date: z.string(), text: z.string() });

/** Free-form "Scratchpad" note, one per day. Missing days read as an empty note. */
export const noteRoutes: FastifyPluginAsyncZod = async (app) => {
  const params = z.object({ date: dayString });

  app.get('/notes/:date', { schema: { params, response: { 200: noteSchema } } }, async (req) => {
    const note = await app.db.dailyNote.findUnique({ where: { date: new Date(`${req.params.date}T00:00:00Z`) } });
    return { date: req.params.date, text: note?.text ?? '' };
  });

  app.put('/notes/:date', { schema: { params, body: z.object({ text: z.string().max(20_000) }), response: { 200: noteSchema } } }, async (req) => {
    const date = new Date(`${req.params.date}T00:00:00Z`);
    const note = await app.db.dailyNote.upsert({ where: { date }, create: { date, text: req.body.text }, update: { text: req.body.text } });
    return { date: req.params.date, text: note.text };
  });
};
