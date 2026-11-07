import { z } from 'zod';

export const priorityEnum = z.enum(['URGENT', 'HIGH', 'NORMAL', 'LOW']);
export const statusEnum = z.enum(['TODO', 'IN_PROGRESS', 'DONE']);

export const subtaskSchema = z.object({
  id: z.uuid(),
  taskId: z.uuid(),
  title: z.string(),
  done: z.boolean(),
  position: z.number().int(),
});

export const taskSchema = z.object({
  id: z.uuid(),
  title: z.string(),
  notes: z.string(),
  projectId: z.uuid().nullable(),
  priority: priorityEnum,
  status: statusEnum,
  dueAt: z.date().nullable(),
  completedAt: z.date().nullable(),
  archived: z.boolean(),
  pinned: z.boolean(),
  repeatRule: z.string().nullable(),
  createdAt: z.date(),
  updatedAt: z.date(),
  subtasks: z.array(subtaskSchema),
});

export const createTaskBody = z.object({
  title: z.string().trim().min(1).max(200),
  notes: z.string().max(5000).optional(),
  projectId: z.uuid().nullable().optional(),
  priority: priorityEnum.optional(),
  status: statusEnum.optional(),
  dueAt: z.coerce.date().nullable().optional(),
  repeatRule: z.string().trim().max(60).nullable().optional(),
  pinned: z.boolean().optional(),
  subtasks: z.array(z.string().trim().min(1).max(200)).max(50).optional(),
});

export const updateTaskBody = createTaskBody
  .omit({ subtasks: true })
  .extend({ archived: z.boolean().optional() })
  .partial()
  .refine((v) => Object.keys(v).length > 0, 'at least one field is required');

export const listTasksQuery = z.object({
  /** Inclusive lower / exclusive upper bound for `dueAt`; clients compute their own local-day range. */
  from: z.coerce.date().optional(),
  to: z.coerce.date().optional(),
  status: statusEnum.optional(),
  projectId: z.uuid().optional(),
  archived: z.enum(['true', 'false']).default('false').transform((v) => v === 'true'),
  q: z.string().trim().min(1).optional(),
});

export const createSubtaskBody = z.object({ title: z.string().trim().min(1).max(200) });
export const updateSubtaskBody = z
  .object({ title: z.string().trim().min(1).max(200), done: z.boolean() })
  .partial()
  .refine((v) => Object.keys(v).length > 0, 'at least one field is required');

export const idParams = z.object({ id: z.uuid() });

export type CreateTaskInput = z.infer<typeof createTaskBody>;
export type UpdateTaskInput = z.infer<typeof updateTaskBody>;
export type ListTasksQuery = z.infer<typeof listTasksQuery>;
