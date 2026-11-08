import { z } from 'zod';

export const statusResponse = z.object({ configured: z.boolean(), model: z.string() });

export const breakdownBody = z.object({
  title: z.string().trim().min(1).max(200),
  notes: z.string().max(5000).default(''),
  dueAt: z.coerce.date().nullable().optional(),
});

export const breakdownResponse = z.object({ subtasks: z.array(z.string()) });

const validTimeZone = (value: string) => {
  try {
    new Intl.DateTimeFormat('en-GB', { timeZone: value });
    return true;
  } catch {
    return false;
  }
};

export const assistBody = z.object({
  message: z.string().trim().min(1).max(500),
  /** IANA name such as "Asia/Kolkata", so times in the answer match the person's day. */
  timeZone: z.string().refine(validTimeZone, 'unknown time zone').default('UTC'),
});

const suggestionBase = { taskId: z.uuid(), taskTitle: z.string(), reason: z.string() };

export const brainDumpBody = z.object({
  text: z.string().trim().min(1).max(4000),
  timeZone: z.string().refine(validTimeZone, 'unknown time zone').default('UTC'),
});

export const brainDumpResponse = z.object({
  tasks: z.array(
    z.object({
      title: z.string(),
      dueAt: z.date().nullable(),
      priority: z.enum(['URGENT', 'HIGH', 'NORMAL', 'LOW']),
      repeatRule: z.string().nullable(),
      projectId: z.uuid().nullable(),
      projectName: z.string().nullable(),
    }),
  ),
});

export const assistResponse = z.object({
  reply: z.string(),
  suggestions: z.array(
    z.discriminatedUnion('kind', [
      z.object({ kind: z.literal('MOVE'), ...suggestionBase, dueAt: z.date() }),
      z.object({ kind: z.literal('SPLIT'), ...suggestionBase, steps: z.array(z.string()) }),
      z.object({ kind: z.literal('PRIORITY'), ...suggestionBase, priority: z.enum(['URGENT', 'HIGH', 'NORMAL', 'LOW']) }),
      z.object({ kind: z.literal('COMPLETE'), ...suggestionBase }),
      z.object({
        kind: z.literal('CREATE_TASK'),
        title: z.string(),
        reason: z.string(),
        dueAt: z.date().nullable(),
        priority: z.enum(['URGENT', 'HIGH', 'NORMAL', 'LOW']),
        repeatRule: z.string().nullable(),
        projectId: z.uuid().nullable(),
        projectName: z.string().nullable(),
      }),
      z.object({ kind: z.literal('CREATE_EVENT'), title: z.string(), reason: z.string(), startAt: z.date(), endAt: z.date(), allDay: z.boolean() }),
      z.object({ kind: z.literal('CREATE_REMINDER'), title: z.string(), reason: z.string(), remindAt: z.date(), repeatRule: z.string().nullable() }),
    ]),
  ),
});
