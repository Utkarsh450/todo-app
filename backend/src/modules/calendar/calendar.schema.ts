import { z } from 'zod';

export const eventSchema = z.object({
  id: z.uuid(),
  title: z.string(),
  startAt: z.date(),
  endAt: z.date(),
  allDay: z.boolean(),
  projectId: z.uuid().nullable(),
  location: z.string().nullable(),
  videoLink: z.string().nullable(),
  busy: z.boolean(),
  guests: z.array(z.string()),
  createdAt: z.date(),
});

const eventFields = z.object({
  title: z.string().trim().min(1).max(200),
  startAt: z.coerce.date(),
  endAt: z.coerce.date(),
  allDay: z.boolean().optional(),
  projectId: z.uuid().nullable().optional(),
  location: z.string().trim().max(200).nullable().optional(),
  videoLink: z.string().trim().max(500).nullable().optional(),
  busy: z.boolean().optional(),
  guests: z.array(z.string().trim().min(1).max(120)).max(50).optional(),
});

export const createEventBody = eventFields.refine((v) => v.endAt >= v.startAt, { message: 'endAt must not be before startAt', path: ['endAt'] });
export const updateEventBody = eventFields.partial().refine((v) => Object.keys(v).length > 0, 'at least one field is required');

export const rangeQuery = z
  .object({ from: z.coerce.date(), to: z.coerce.date() })
  .refine((v) => v.to > v.from, { message: 'to must be after from', path: ['to'] });

export const agendaItemSchema = z.object({
  kind: z.enum(['event', 'task', 'reminder']),
  id: z.uuid(),
  title: z.string(),
  startAt: z.date(),
  endAt: z.date().nullable(),
  allDay: z.boolean(),
  projectId: z.uuid().nullable(),
  done: z.boolean(),
});

export const idParams = z.object({ id: z.uuid() });

export type CreateEventInput = z.infer<typeof createEventBody>;
export type UpdateEventInput = z.infer<typeof updateEventBody>;
export type AgendaItem = z.infer<typeof agendaItemSchema>;
