import { z } from 'zod';

/** Calendar day as `YYYY-MM-DD`, validated as a real date. */
export const dayString = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'must be YYYY-MM-DD')
  .refine((s) => !Number.isNaN(Date.parse(`${s}T00:00:00Z`)) && new Date(`${s}T00:00:00Z`).toISOString().startsWith(s), 'not a real date');

export const habitSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  createdAt: z.date(),
  /** Days (YYYY-MM-DD) the habit was kept inside the requested window. */
  days: z.array(z.string()),
});

export const createHabitBody = z.object({ name: z.string().trim().min(1).max(80) });
export const updateHabitBody = createHabitBody;
export const windowQuery = z.object({ from: dayString, to: dayString }).refine((v) => v.to >= v.from, { message: 'to must not be before from', path: ['to'] });
export const logParams = z.object({ id: z.uuid(), date: dayString });
export const logBody = z.object({ done: z.boolean() });
export const idParams = z.object({ id: z.uuid() });
