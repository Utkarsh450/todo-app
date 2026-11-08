import { z } from 'zod';

export const statusResponse = z.object({ configured: z.boolean(), model: z.string() });

export const breakdownBody = z.object({
  title: z.string().trim().min(1).max(200),
  notes: z.string().max(5000).default(''),
  dueAt: z.coerce.date().nullable().optional(),
});

export const breakdownResponse = z.object({ subtasks: z.array(z.string()) });
