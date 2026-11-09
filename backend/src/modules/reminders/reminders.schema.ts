import { z } from 'zod';

export const reminderSchema = z.object({
  id: z.uuid(),
  taskId: z.uuid().nullable(),
  eventId: z.uuid().nullable(),
  title: z.string(),
  remindAt: z.date(),
  repeatRule: z.string().nullable(),
  locationTrigger: z.string().nullable(),
  enabled: z.boolean(),
  snoozedUntil: z.date().nullable(),
  createdAt: z.date(),
});

export const createReminderBody = z.object({
  title: z.string().trim().min(1).max(200),
  remindAt: z.coerce.date(),
  taskId: z.uuid().nullable().optional(),
  eventId: z.uuid().nullable().optional(),
  repeatRule: z.string().trim().max(60).nullable().optional(),
  locationTrigger: z.string().trim().max(120).nullable().optional(),
  enabled: z.boolean().optional(),
});

/** Updates may also clear (`null`) or set the snooze, which is how a repeating reminder rolls forward. */
export const updateReminderBody = createReminderBody
  .extend({ snoozedUntil: z.coerce.date().nullable().optional() })
  .partial().refine((v) => Object.keys(v).length > 0, 'at least one field is required');

/** Snooze either by a number of minutes from now, or until an explicit time. */
export const snoozeBody = z.union([
  z.object({ minutes: z.number().int().min(1).max(7 * 24 * 60) }),
  z.object({ until: z.coerce.date() }),
]);

/** The client supplies its own day boundaries so grouping honours the user's timezone. */
export const groupedQuery = z.object({
  now: z.coerce.date(),
  endOfToday: z.coerce.date(),
  endOfWeek: z.coerce.date(),
});

export const groupedSchema = z.object({
  overdue: z.array(reminderSchema),
  today: z.array(reminderSchema),
  thisWeek: z.array(reminderSchema),
  later: z.array(reminderSchema),
});

export const idParams = z.object({ id: z.uuid() });

export type CreateReminderInput = z.infer<typeof createReminderBody>;
export type UpdateReminderInput = z.infer<typeof updateReminderBody>;
export type SnoozeInput = z.infer<typeof snoozeBody>;
