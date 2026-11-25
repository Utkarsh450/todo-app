import { z } from 'zod';
import { MEMORY_CATEGORIES } from './memory.js';

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

export const memoryItem = z.object({ id: z.uuid(), text: z.string(), category: z.string() });
export const memoryFull = memoryItem.extend({ source: z.enum(['AUTO', 'USER']), updatedAt: z.date() });
export const memoriesResponse = z.object({ memories: z.array(memoryFull) });
export const memoryBody = z.object({ text: z.string().trim().min(1).max(1000), category: z.enum(MEMORY_CATEGORIES).optional() });
export const memoryParams = z.object({ id: z.uuid() });

export const historyQuery = z.object({ before: z.uuid().optional(), limit: z.coerce.number().int().min(1).max(50).default(20) });
export const historyResponse = z.object({
  messages: z.array(
    z.object({
      id: z.uuid(),
      role: z.enum(['USER', 'ASSISTANT']),
      content: z.string(),
      suggestions: z.array(z.unknown()).nullable(),
      outcomes: z.record(z.string(), z.string()).nullable(),
      createdAt: z.date(),
    }),
  ),
  nextBefore: z.uuid().nullable(),
});
export const messageParams = z.object({ messageId: z.uuid() });
export const outcomeBody = z.object({ index: z.number().int().min(0), outcome: z.enum(['accepted', 'skipped']) });

export const assistResponse = z.object({
  /** The saved assistant message; outcomes of its suggestions are reported against this id. */
  messageId: z.uuid(),
  reply: z.string(),
  /** Facts the assistant saved about the owner in this turn ("Noted: ..."). */
  noted: z.array(memoryItem),
  /** Ids of facts it forgot in this turn. */
  forgotten: z.array(z.uuid()),
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
